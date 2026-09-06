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
}

/** Section 11/12: material master, coefficients, and derived requirement/stock/balance figures. */
class MaterialRepository(private val db: AppDatabase) {
    fun observeMaterials(projectId: Long): Flow<List<Material>> = db.materialDao().observeForProject(projectId)
    suspend fun addMaterial(material: Material): Long = db.materialDao().insert(material)

    fun observeCoefficients(projectId: Long): Flow<List<MaterialCoefficient>> =
        db.materialCoefficientDao().observeForProject(projectId)
    suspend fun addCoefficient(coefficient: MaterialCoefficient): Long = db.materialCoefficientDao().insert(coefficient)
    suspend fun updateCoefficient(coefficient: MaterialCoefficient) = db.materialCoefficientDao().update(coefficient)

    fun observePurchases(projectId: Long): Flow<List<MaterialPurchase>> = db.materialPurchaseDao().observeForProject(projectId)
    fun observeUsages(projectId: Long): Flow<List<MaterialUsage>> = db.materialUsageDao().observeForProject(projectId)
    suspend fun addUsage(usage: MaterialUsage): Long = db.materialUsageDao().insert(usage)

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

    suspend fun addVendor(
        projectId: Long, name: String, contact: String, workCategory: VendorWorkCategory,
        rateType: VendorRateType, rate: Double, quantity: Double, projectValue: Double,
        startDate: Long, endDate: Long, paymentTerms: String, notes: String
    ): Long {
        val contractValue = CalculationEngine.vendorContractValue(rateType, rate, quantity, projectValue)
        return db.vendorDao().insert(
            Vendor(
                projectId = projectId, name = name, contact = contact, workCategory = workCategory,
                rateType = rateType, rate = rate, quantity = quantity, contractValue = contractValue,
                startDate = startDate, endDate = endDate, paymentTerms = paymentTerms, notes = notes
            )
        )
    }

    suspend fun updateVendor(vendor: Vendor) = db.vendorDao().update(vendor)
    suspend fun deleteVendor(vendor: Vendor) = db.vendorDao().delete(vendor)
}

class ClientPaymentRepository(private val db: AppDatabase) {
    fun observeForProject(projectId: Long): Flow<List<ClientPayment>> = db.clientPaymentDao().observeForProject(projectId)
    fun observeTotalReceived(projectId: Long): Flow<Double> = db.clientPaymentDao().observeTotalReceived(projectId)
    suspend fun addPayment(payment: ClientPayment): Long = db.clientPaymentDao().insert(payment)
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
