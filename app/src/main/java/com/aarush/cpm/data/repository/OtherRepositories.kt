package com.aarush.cpm.data.repository

import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.entity.*
import androidx.room.withTransaction
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.domain.calculation.CategoryRules
import kotlinx.coroutines.flow.Flow

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

    /**
     * Full replace-all save for the project's Settings screen: the notation list and its
     * per-material coefficients are edited together as one table, so they're saved together.
     * [renames] (old code → new code) are applied first to every BOQ item and schedule
     * activity using the old code, so renaming a notation never orphans existing measurements.
     * Runs in one transaction.
     */
    suspend fun replaceNotationsAndCoefficients(
        projectId: Long,
        notations: List<BOQNotation>,
        coefficients: List<MaterialCoefficient>,
        renames: Map<String, String> = emptyMap()
    ) = db.withTransaction {
        renames.filter { (old, new) -> old.isNotBlank() && new.isNotBlank() && old != new }.forEach { (old, new) ->
            db.boqDao().renameItemCode(projectId, old, new)
            db.boqDao().renameMaterialType(projectId, old, new)
            db.scheduleActivityDao().renameNotation(projectId, old, new)
        }
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
     * isn't referenced by any coefficient/BOQ item.
     */
    suspend fun inferCategoryForMaterial(projectId: Long, materialName: String): CostCategory? {
        val relevantWorkItemKeys = db.materialCoefficientDao().getForProject(projectId)
            .filter { it.materialName == materialName && it.coefficientPerUnit > 0 }
            .map { it.workItemKey }
            .toSet()
        if (relevantWorkItemKeys.isEmpty()) return null

        val matchingBoqItems = db.boqDao().getForProject(projectId).filter { it.materialType in relevantWorkItemKeys }
        if (matchingBoqItems.isEmpty()) return null

        return matchingBoqItems.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key
    }

    /**
     * Category for an expense line: BOQ/coefficient trace first, then name keywords
     * (Electrical → MEP, Floor Tile → Tiling, Door → Joinery, Paint → Painting, Cement → Civil),
     * then Other/Misc. Previously every non-coefficient item (electrical, tiles, doors…)
     * silently landed in Other/Misc.
     */
    suspend fun resolveCategory(projectId: Long, materialName: String): CostCategory =
        inferCategoryForMaterial(projectId, materialName)
            ?: CategoryRules.categoryForMaterialName(materialName)
            ?: CostCategory.OTHER_MISC

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

    /**
     * Replace-all save of the editable (non-vendor) rate card rows from project Settings.
     * Vendor-linked "Labour Cost for <Vendor>" rows are owned by the Vendors tab and are
     * NEVER touched here — the previous version rebuilt every row without linkedVendorId,
     * which silently broke every vendor↔expense link on the first Settings save.
     * [renames] (old name → new name) are propagated to coefficients, materials, purchases,
     * usages and past expenses first, so renaming "Cement" keeps its whole history attached.
     */
    suspend fun replaceMaterialRates(
        projectId: Long,
        rates: List<MaterialRateCard>,
        renames: Map<String, String> = emptyMap()
    ) = db.withTransaction {
        renames.filter { (old, new) -> old.isNotBlank() && new.isNotBlank() && old != new }
            .forEach { (old, new) -> renameMaterialInternal(projectId, old, new) }
        val existing = db.materialRateCardDao().getForProject(projectId)
        existing.filter { it.linkedVendorId == null }.forEach { db.materialRateCardDao().delete(it) }
        db.materialRateCardDao().insertAll(rates.filter { it.linkedVendorId == null }.map { it.copy(id = 0, projectId = projectId) })
    }

    private suspend fun renameMaterialInternal(projectId: Long, old: String, new: String) {
        db.materialDao().rename(projectId, old, new)
        db.materialCoefficientDao().renameMaterial(projectId, old, new)
        db.materialPurchaseDao().rename(projectId, old, new)
        db.materialUsageDao().rename(projectId, old, new)
        db.expenseDao().renameMaterial(projectId, old, new)
    }

    /**
     * Material quantity needed per 1 unit of each notation, waste included:
     * notation → (material → coefficient × (1 + waste%)). Waste is the coefficient's own
     * override, else the quantity-weighted average waste % of that notation's BOQ items
     * (3% if none). Used by the schedule for consumption and look-ahead requirements.
     */
    suspend fun materialPerUnitByNotation(projectId: Long): Map<String, Map<String, Double>> {
        val boq = db.boqDao().getForProject(projectId)
        val coefficients = db.materialCoefficientDao().getForProject(projectId)
        return coefficients.filter { it.coefficientPerUnit > 0 }.groupBy { it.workItemKey }.mapValues { (code, coeffs) ->
            val items = boq.filter { it.materialType == code }
            val qty = items.sumOf { it.quantity }
            val avgWaste = if (qty > 0) items.sumOf { it.quantity * it.wastePercent } / qty else 3.0
            coeffs.associate { c ->
                c.materialName to CalculationEngine.totalRequiredQuantity(c.coefficientPerUnit, c.wastePercentOverride ?: avgWaste)
            }
        }
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
        val rateCards = db.materialRateCardDao().getForProject(projectId)
        val rateByName = rateCards.associateBy { it.description }
        val vendors = db.vendorDao().getForProject(projectId)
        val expenses = db.expenseDao().getForProject(projectId).filter { it.direction == TransactionDirection.EXPENSE }

        val vendorLinkedNames = rateCards.filter { it.linkedVendorId != null }.associateBy { it.description }
        val trackedNames = (materials.map { it.name } + coefficients.filter { it.coefficientPerUnit > 0 }.map { it.materialName }).toSet()
        val materialNames = (materials.map { it.name } +
                coefficients.filter { it.coefficientPerUnit > 0 }.map { it.materialName } +
                purchases.map { it.materialName } +
                vendorLinkedNames.keys).distinct()

        return materialNames.map { name ->
            val linkedVendor = vendorLinkedNames[name]?.linkedVendorId?.let { id -> vendors.find { it.id == id } }

            if (linkedVendor != null) {
                // A vendor-linked "Labour Cost for <Vendor>" line isn't a stock material: its
                // spend is the expenses tagged with the vendor and its budget the contract value.
                val vendorPayments = expenses.filter { it.vendorId == linkedVendor.id }
                val expensedAmount = vendorPayments.sumOf { it.totalAmount }
                val requiredBudget = linkedVendor.contractValue
                val history = vendorPayments.sortedByDescending { it.date }
                    .map { MaterialPurchaseEntry(date = it.date, quantity = it.quantity, amount = it.totalAmount) }
                MaterialStatusRow(
                    materialName = name,
                    unit = rateByName[name]?.unit ?: "",
                    requiredQuantity = 0.0,
                    requiredBudget = requiredBudget,
                    purchasedQuantity = 0.0,
                    usedQuantity = 0.0,
                    currentStock = 0.0,
                    balanceToPurchase = 0.0,
                    expensedAmount = expensedAmount,
                    averageRate = 0.0,
                    estimatedRemainingCost = 0.0,
                    balanceHoldingAmount = requiredBudget - expensedAmount,
                    isVendorLinked = true,
                    isStockTracked = false,
                    purchaseHistory = history
                )
            } else {
                var required = 0.0
                coefficients.filter { it.materialName == name }.forEach { coeff ->
                    boqItems.filter { it.materialType == coeff.workItemKey }.forEach { boq ->
                        val base = CalculationEngine.requiredMaterialQuantity(boq.quantity, coeff.coefficientPerUnit)
                        val wastePct = coeff.wastePercentOverride ?: boq.wastePercent
                        required += CalculationEngine.totalRequiredQuantity(base, wastePct)
                    }
                }
                val myPurchases = purchases.filter { it.materialName == name }
                val purchasedQty = myPurchases.sumOf { it.quantity }
                val expensedAmount = myPurchases.sumOf { it.amount }
                val history = myPurchases.sortedByDescending { it.date }
                    .map { MaterialPurchaseEntry(date = it.date, quantity = it.quantity, amount = it.amount) }
                val usedQty = usages.filter { it.materialName == name }.sumOf { it.quantityUsed }
                val stock = CalculationEngine.currentStock(purchasedQty, usedQty)
                val balance = CalculationEngine.balanceToPurchase(required, purchasedQty)
                val avgRate = CalculationEngine.averagePurchaseRate(expensedAmount, purchasedQty)
                val rate = rateByName[name]?.rate ?: 0.0
                // Remaining cost: at what you've actually been paying, else the rate card.
                val estRemaining = CalculationEngine.estimatedRemainingCost(balance, if (avgRate > 0) avgRate else rate)
                val unit = materials.find { it.name == name }?.unit ?: rateByName[name]?.unit
                    ?: myPurchases.firstOrNull()?.let { p -> expenses.find { it.id == p.expenseId }?.unit } ?: ""
                val requiredBudget = required * rate
                val isTracked = name in trackedNames

                MaterialStatusRow(
                    materialName = name,
                    unit = unit,
                    requiredQuantity = required,
                    requiredBudget = requiredBudget,
                    purchasedQuantity = purchasedQty,
                    usedQuantity = usedQty,
                    currentStock = stock,
                    balanceToPurchase = balance,
                    expensedAmount = expensedAmount,
                    averageRate = avgRate,
                    estimatedRemainingCost = estRemaining,
                    balanceHoldingAmount = requiredBudget - expensedAmount,
                    isVendorLinked = false,
                    isStockTracked = isTracked,
                    purchaseHistory = history
                )
            }
        }.sortedWith(compareBy({ !it.isStockTracked && !it.isVendorLinked }, { it.isVendorLinked }, { it.materialName }))
    }
}

data class MaterialStatusRow(
    val materialName: String,
    val unit: String,
    val requiredQuantity: Double,
    /** Required qty × this material's rate-card rate — the budgeted cost for it. For
     *  vendor-linked rows this is the vendor's contract value instead. */
    val requiredBudget: Double,
    val purchasedQuantity: Double,
    val usedQuantity: Double,
    val currentStock: Double,
    /** "Remaining Qty" in the Material Status tab — required not yet purchased. */
    val balanceToPurchase: Double,
    /** Total actually spent so far. */
    val expensedAmount: Double,
    val averageRate: Double,
    val estimatedRemainingCost: Double,
    /** requiredBudget − expensedAmount: how much budget is left (negative once overspent). */
    val balanceHoldingAmount: Double,
    /** True for an auto-generated "Labour Cost for <Vendor>" row. */
    val isVendorLinked: Boolean = false,
    /** True for materials the BOQ coefficient engine computes a requirement for (cement,
     *  sand, steel…). False for one-off purchases (doors, electrical, tiles…), which have no
     *  measured requirement, so the tab shows only what was bought and spent for them. */
    val isStockTracked: Boolean = true,
    /** Individual purchases/payments behind expensedAmount, newest first. */
    val purchaseHistory: List<MaterialPurchaseEntry> = emptyList()
)

/** One dated purchase (or vendor payment) line for the Material Status detail popup. */
data class MaterialPurchaseEntry(
    val date: Long,
    val quantity: Double,
    val amount: Double
)

class VendorRepository(private val db: AppDatabase) {
    fun observeForProject(projectId: Long): Flow<List<Vendor>> = db.vendorDao().observeForProject(projectId)
    suspend fun getForProject(projectId: Long): List<Vendor> = db.vendorDao().getForProject(projectId)

    private fun linkedLine(vendor: Vendor): MaterialRateCard {
        val (unit, rate) = when (vendor.rateType) {
            VendorRateType.PER_SQFT -> "Sqft" to vendor.rate
            else -> "LS" to 0.0
        }
        return MaterialRateCard(
            projectId = vendor.projectId, description = linkedLineName(vendor.name),
            unit = unit, rate = rate, linkedVendorId = vendor.id
        )
    }

    fun linkedLineName(vendorName: String) = "Labour Cost for $vendorName"

    // ------------------------------------------------------------------ extra work (v9)

    fun observeWorks(projectId: Long): Flow<List<VendorWork>> = db.vendorWorkDao().observeForProject(projectId)

    /** Sum of a vendor's extra work, part of its contract value. */
    suspend fun worksTotal(vendorId: Long): Double = db.vendorWorkDao().getForVendor(vendorId).sumOf { it.amount }

    /** Value of one piece of extra work: running feet × rate, or the lump sum itself. */
    fun workAmount(isLumpSum: Boolean, quantity: Double, rate: Double, lumpSum: Double): Double =
        if (isLumpSum) lumpSum else quantity * rate

    /** Adds extra work (e.g. a compound wall) to an existing vendor; the vendor's contract
     *  value grows by its amount, so balances, Budget 2 civil labour and alerts include it. */
    suspend fun addWork(vendorId: Long, label: String, isLumpSum: Boolean, quantity: Double, rate: Double, amount: Double) = db.withTransaction {
        val vendor = db.vendorDao().getById(vendorId) ?: return@withTransaction
        db.vendorWorkDao().insert(VendorWork(projectId = vendor.projectId, vendorId = vendorId, label = label,
            isLumpSum = isLumpSum, quantity = if (isLumpSum) 0.0 else quantity, rate = if (isLumpSum) 0.0 else rate, amount = amount))
        db.vendorDao().update(vendor.copy(contractValue = vendor.contractValue + amount))
    }

    /** Edits extra work; the contract value moves by the change in its amount. */
    suspend fun updateWork(original: VendorWork, updated: VendorWork) = db.withTransaction {
        db.vendorWorkDao().update(updated)
        db.vendorDao().getById(original.vendorId)?.let { v ->
            db.vendorDao().update(v.copy(contractValue = v.contractValue - original.amount + updated.amount))
        }
    }

    /** Removes extra work and takes its amount off the contract value. */
    suspend fun deleteWork(work: VendorWork) = db.withTransaction {
        db.vendorWorkDao().delete(work)
        db.vendorDao().getById(work.vendorId)?.let { v ->
            db.vendorDao().update(v.copy(contractValue = (v.contractValue - work.amount).coerceAtLeast(0.0)))
        }
    }

    /** Undo for [deleteWork]. */
    suspend fun restoreWork(work: VendorWork) = db.withTransaction {
        db.vendorWorkDao().insert(work.copy(id = 0))
        db.vendorDao().getById(work.vendorId)?.let { v -> db.vendorDao().update(v.copy(contractValue = v.contractValue + work.amount)) }
    }

    suspend fun addVendor(
        projectId: Long, name: String, contact: String, workCategory: VendorWorkCategory,
        rateType: VendorRateType, rate: Double, quantity: Double, projectValue: Double,
        startDate: Long, endDate: Long, paymentTerms: String, notes: String
    ): Long = db.withTransaction {
        val contractValue = CalculationEngine.vendorContractValue(rateType, rate, quantity, projectValue)
        val vendor = Vendor(
            projectId = projectId, name = name, contact = contact, workCategory = workCategory,
            rateType = rateType, rate = rate, quantity = quantity, contractValue = contractValue,
            startDate = startDate, endDate = endDate, paymentTerms = paymentTerms, notes = notes
        )
        val vendorId = db.vendorDao().insert(vendor)
        // Auto-create the linked material line ("Labour Cost for <Vendor>") so this vendor
        // immediately shows up in the Expense form's material picker.
        db.materialRateCardDao().insertAll(listOf(linkedLine(vendor.copy(id = vendorId))))
        vendorId
    }

    /** Saves edits and keeps the linked material line (name, unit, rate) and the names on
     *  past payments in step — previously renaming a vendor left "Labour Cost for <old name>". */
    suspend fun updateVendor(vendor: Vendor) = db.withTransaction {
        db.vendorDao().update(vendor.copy(amountPaid = db.expenseDao().sumPaidToVendor(vendor.id)))
        val existingLines = db.materialRateCardDao().getForVendor(vendor.id)
        val fresh = linkedLine(vendor)
        if (existingLines.isEmpty()) db.materialRateCardDao().insertAll(listOf(fresh))
        else existingLines.forEach { db.materialRateCardDao().update(fresh.copy(id = it.id)) }
        db.expenseDao().renameVendorLine(vendor.id, fresh.description)
    }

    /** Deletes the vendor and its linked material line. Its past payments stay as expenses
     *  (real money that was spent) and keep their vendorId, so Undo re-links them. */
    suspend fun deleteVendor(vendor: Vendor) = db.withTransaction {
        db.materialRateCardDao().deleteForVendor(vendor.id)
        db.scheduleActivityDao().clearVendor(vendor.id)
        db.vendorDao().delete(vendor)
    }

    /** Undo for delete: re-inserts the vendor with its ORIGINAL id, so every expense still
     *  tagged with it reconnects, and recreates the linked material line. */
    suspend fun restoreVendor(vendor: Vendor) = db.withTransaction {
        db.vendorDao().insert(vendor.copy(amountPaid = db.expenseDao().sumPaidToVendor(vendor.id)))
        if (db.materialRateCardDao().getForVendor(vendor.id).isEmpty()) {
            db.materialRateCardDao().insertAll(listOf(linkedLine(vendor)))
        }
    }
}

class ClientPaymentRepository(private val db: AppDatabase) {
    fun observeForProject(projectId: Long): Flow<List<ClientPayment>> = db.clientPaymentDao().observeForProject(projectId)
    suspend fun getForProject(projectId: Long): List<ClientPayment> = db.clientPaymentDao().getForProject(projectId)
    fun observeTotalReceived(projectId: Long): Flow<Double> = db.clientPaymentDao().observeTotalReceived(projectId)
    suspend fun addPayment(payment: ClientPayment): Long = db.clientPaymentDao().insert(payment)
    suspend fun updatePayment(payment: ClientPayment) = db.clientPaymentDao().update(payment)
    suspend fun deletePayment(payment: ClientPayment) = db.clientPaymentDao().delete(payment)
}

class NotificationRepository(private val db: AppDatabase) {
    fun observeForProject(projectId: Long): Flow<List<AppNotification>> = db.notificationDao().observeForProject(projectId)
    suspend fun add(notification: AppNotification): Long = db.notificationDao().insert(notification)
    suspend fun markRead(notification: AppNotification) = db.notificationDao().update(notification.copy(isRead = true))
}
