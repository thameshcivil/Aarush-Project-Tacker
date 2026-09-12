package com.aarush.cpm.data.repository

import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.entity.*
import com.aarush.cpm.domain.calculation.CalculationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class BOQRepository(private val db: AppDatabase) {
    fun observeForProject(projectId: Long): Flow<List<BOQItem>> = db.boqDao().observeForProject(projectId)
    suspend fun getForProject(projectId: Long): List<BOQItem> = db.boqDao().getForProject(projectId)
    suspend fun addItem(item: BOQItem): Long = db.boqDao().insert(item)
    suspend fun addItems(items: List<BOQItem>) = db.boqDao().insertAll(items)
    suspend fun updateItem(item: BOQItem) = db.boqDao().update(item)
    suspend fun deleteItem(item: BOQItem) = db.boqDao().delete(item)

    // --- BOQ notation dropdown (linked item-code list) ---
    fun observeNotations(projectId: Long): Flow<List<BOQNotation>> = db.boqNotationDao().observeForProject(projectId)
    suspend fun getNotations(projectId: Long): List<BOQNotation> = db.boqNotationDao().getForProject(projectId)

    /** Full replace-all save for the project's Settings screen: the notation list and its
     *  per-material coefficients are edited together as one table, so they're saved together —
     *  simpler and safer than diffing individual row edits/inserts/deletes. */
    suspend fun replaceNotationsAndCoefficients(projectId: Long, notations: List<BOQNotation>, coefficients: List<MaterialCoefficient>) {
        db.boqNotationDao().deleteForProject(projectId)
        db.materialCoefficientDao().deleteForProject(projectId)
        db.boqNotationDao().insertAll(notations)
        db.materialCoefficientDao().insertAll(coefficients)
    }
}

/** Section 11/12: material master, coefficients, and derived requirement/stock/balance figures. */
class MaterialRepository(private val db: AppDatabase) {
    fun observeMaterials(projectId: Long): Flow<List<Material>> = db.materialDao().observeForProject(projectId)
    suspend fun addMaterial(material: Material): Long = db.materialDao().insert(material)

    /**
     * Infers a CostCategory for a material name by tracing it through the coefficient engine:
     * material → the BOQ notation(s) that consume it (via MaterialCoefficient.workItemKey) →
     * the category of the BOQ item(s) using that notation. Returns null when the material
     * isn't referenced by any coefficient/BOQ item (e.g. a vendor's linked "Labour Cost for
     * <Vendor>" line, which was never meant to be tracked as a stock material) — the caller
     * falls back to a default category in that case. Keeps Budget-vs-Actual meaningful even
     * though the simplified Expense form no longer asks for category directly.
     */
    suspend fun inferCategoryForMaterial(projectId: Long, materialName: String): CostCategory? {
        val relevantWorkItemKeys = db.materialCoefficientDao().getForProject(projectId)
            .filter { it.materialName == materialName && it.coefficientPerUnit > 0 }
            .map { it.workItemKey }
            .toSet()
        if (relevantWorkItemKeys.isEmpty()) return null

        val matchingBoqItems = db.boqDao().getForProject(projectId).filter { it.materialType in relevantWorkItemKeys }
        if (matchingBoqItems.isEmpty()) return null

        // Most common category among the BOQ items that consume this material, in case it's
        // used by notations spanning more than one category.
        return matchingBoqItems.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key
    }

    fun observeCoefficients(projectId: Long): Flow<List<MaterialCoefficient>> =
        db.materialCoefficientDao().observeForProject(projectId)
    suspend fun getCoefficients(projectId: Long): List<MaterialCoefficient> =
        db.materialCoefficientDao().getForProject(projectId)
    suspend fun addCoefficient(coefficient: MaterialCoefficient): Long = db.materialCoefficientDao().insert(coefficient)
    suspend fun updateCoefficient(coefficient: MaterialCoefficient) = db.materialCoefficientDao().update(coefficient)

    fun observePurchases(projectId: Long): Flow<List<MaterialPurchase>> = db.materialPurchaseDao().observeForProject(projectId)
    fun observeUsages(projectId: Long): Flow<List<MaterialUsage>> = db.materialUsageDao().observeForProject(projectId)
    suspend fun addUsage(usage: MaterialUsage): Long = db.materialUsageDao().insert(usage)

    // --- Material rate card (editable per-project budget reference) ---
    fun observeMaterialRates(projectId: Long): Flow<List<MaterialRateCard>> = db.materialRateCardDao().observeForProject(projectId)
    suspend fun getMaterialRates(projectId: Long): List<MaterialRateCard> = db.materialRateCardDao().getForProject(projectId)

    /** Full replace-all save for the project's Settings screen — see BOQRepository's
     *  equivalent for notations/coefficients; same reasoning applies here. */
    suspend fun replaceMaterialRates(projectId: Long, rates: List<MaterialRateCard>) {
        db.materialRateCardDao().deleteForProject(projectId)
        db.materialRateCardDao().insertAll(rates)
    }

    /**
     * Rolls up, per material name: required (from BOQ x coefficient + waste), purchased,
     * used, current stock, balance to purchase, avg rate, estimated remaining cost.
     */
    suspend fun materialStatus(projectId: Long): List<MaterialStatusRow> {
        val boqItems = db.boqDao().getForProject(projectId)
        val coefficients = db.materialCoefficientDao().getForProject(projectId)
        val purchases = db.materialPurchaseDao().getForProject(projectId)
        val usages = db.materialUsageDao().getForProject(projectId)
        val materials = db.materialDao().getForProject(projectId)

        val materialNames = (materials.map { it.name } +
                coefficients.map { it.materialName } +
                purchases.map { it.materialName }).distinct()

        return materialNames.map { name ->
            // Required = sum over BOQ items whose materialType matches a coefficient row for this material.
            var required = 0.0
            coefficients.filter { it.materialName == name }.forEach { coeff ->
                boqItems.filter { it.materialType == coeff.workItemKey }.forEach { boq ->
                    val base = CalculationEngine.requiredMaterialQuantity(boq.quantity, coeff.coefficientPerUnit)
                    val wastePct = coeff.wastePercentOverride ?: boq.wastePercent
                    required += CalculationEngine.totalRequiredQuantity(base, wastePct)
                }
            }
            val purchasedQty = purchases.filter { it.materialName == name }.sumOf { it.quantity }
            val purchaseCost = purchases.filter { it.materialName == name }.sumOf { it.amount }
            val usedQty = usages.filter { it.materialName == name }.sumOf { it.quantityUsed }
            val stock = CalculationEngine.currentStock(purchasedQty, usedQty)
            val balance = CalculationEngine.balanceToPurchase(required, purchasedQty)
            val avgRate = CalculationEngine.averagePurchaseRate(purchaseCost, purchasedQty)
            val estRemaining = CalculationEngine.estimatedRemainingCost(balance, avgRate)
            val unit = materials.find { it.name == name }?.unit ?: ""

            MaterialStatusRow(
                materialName = name,
                unit = unit,
                requiredQuantity = required,
                purchasedQuantity = purchasedQty,
                usedQuantity = usedQty,
                currentStock = stock,
                balanceToPurchase = balance,
                purchaseCost = purchaseCost,
                averageRate = avgRate,
                estimatedRemainingCost = estRemaining
            )
        }
    }
}

data class MaterialStatusRow(
    val materialName: String,
    val unit: String,
    val requiredQuantity: Double,
    val purchasedQuantity: Double,
    val usedQuantity: Double,
    val currentStock: Double,
    val balanceToPurchase: Double,
    val purchaseCost: Double,
    val averageRate: Double,
    val estimatedRemainingCost: Double
)

class VendorRepository(private val db: AppDatabase) {
    fun observeForProject(projectId: Long): Flow<List<Vendor>> = db.vendorDao().observeForProject(projectId)
    suspend fun getForProject(projectId: Long): List<Vendor> = db.vendorDao().getForProject(projectId)

    suspend fun addVendor(
        projectId: Long, name: String, contact: String, workCategory: VendorWorkCategory,
        rateType: VendorRateType, rate: Double, quantity: Double, projectValue: Double,
        startDate: Long, endDate: Long, paymentTerms: String, notes: String
    ): Long {
        val contractValue = CalculationEngine.vendorContractValue(rateType, rate, quantity, projectValue)
        val vendorId = db.vendorDao().insert(
            Vendor(
                projectId = projectId, name = name, contact = contact, workCategory = workCategory,
                rateType = rateType, rate = rate, quantity = quantity, contractValue = contractValue,
                startDate = startDate, endDate = endDate, paymentTerms = paymentTerms, notes = notes
            )
        )
        // Auto-create the linked material line ("Labour Cost for <Vendor>") so this vendor
        // immediately shows up in the Expense form's material picker — that's how vendor
        // payments get logged and tracked, no separate vendor selector needed there.
        val (linkedUnit, linkedRate) = when (rateType) {
            VendorRateType.PER_SQFT -> "Sqft" to rate
            else -> "LS" to 0.0
        }
        db.materialRateCardDao().insertAll(
            listOf(
                MaterialRateCard(
                    projectId = projectId, description = "Labour Cost for $name",
                    unit = linkedUnit, rate = linkedRate, linkedVendorId = vendorId
                )
            )
        )
        return vendorId
    }

    suspend fun updateVendor(vendor: Vendor) = db.vendorDao().update(vendor)
    suspend fun deleteVendor(vendor: Vendor) = db.vendorDao().delete(vendor)
}

class ClientPaymentRepository(private val db: AppDatabase) {
    fun observeForProject(projectId: Long): Flow<List<ClientPayment>> = db.clientPaymentDao().observeForProject(projectId)
    suspend fun getForProject(projectId: Long): List<ClientPayment> = db.clientPaymentDao().getForProject(projectId)
    fun observeTotalReceived(projectId: Long): Flow<Double> = db.clientPaymentDao().observeTotalReceived(projectId)
    suspend fun addPayment(payment: ClientPayment): Long = db.clientPaymentDao().insert(payment)
    suspend fun updatePayment(payment: ClientPayment) = db.clientPaymentDao().update(payment)
    suspend fun deletePayment(payment: ClientPayment) = db.clientPaymentDao().delete(payment)
}

class ScheduleRepository(private val db: AppDatabase) {
    fun observeActivities(projectId: Long): Flow<List<ScheduleActivity>> = db.scheduleActivityDao().observeForProject(projectId)
    suspend fun addActivity(activity: ScheduleActivity): Long = db.scheduleActivityDao().insert(activity)
    suspend fun updateActivity(activity: ScheduleActivity) = db.scheduleActivityDao().update(activity)
    suspend fun deleteActivity(activity: ScheduleActivity) = db.scheduleActivityDao().delete(activity)

    fun observeLabourRates(projectId: Long): Flow<List<LabourRate>> = db.labourRateDao().observeForProject(projectId)
    suspend fun setLabourRates(rates: List<LabourRate>) = db.labourRateDao().insertAll(rates)

    fun observeProgress(projectId: Long): Flow<List<ProjectProgress>> = db.projectProgressDao().observeForProject(projectId)
    suspend fun addProgress(progress: ProjectProgress): Long = db.projectProgressDao().insert(progress)
}

class NotificationRepository(private val db: AppDatabase) {
    fun observeForProject(projectId: Long): Flow<List<AppNotification>> = db.notificationDao().observeForProject(projectId)
    suspend fun add(notification: AppNotification): Long = db.notificationDao().insert(notification)
    suspend fun markRead(notification: AppNotification) = db.notificationDao().update(notification.copy(isRead = true))
}
