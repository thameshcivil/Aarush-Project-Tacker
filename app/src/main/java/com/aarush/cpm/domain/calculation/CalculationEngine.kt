package com.aarush.cpm.domain.calculation

import com.aarush.cpm.data.entity.*

/**
 * Pure, side-effect-free business calculations. No Android framework, no Room, no Compose.
 * Every formula in this file maps 1:1 to a formula named in the product spec (section 26),
 * so it can be unit tested in isolation and never has to be duplicated inside UI code.
 */
object CalculationEngine {

    // ---------- Section 5: Project value ----------
    fun projectValue(plinthAreaSqft: Double, ratePerSqft: Double): Double =
        plinthAreaSqft * ratePerSqft

    // ---------- Section 6: Cost distribution ----------
    fun categoryBudget(projectValue: Double, categoryPercent: Double): Double =
        projectValue * (categoryPercent / 100.0)

    fun totalAllocatedPercent(allocations: List<CostAllocation>): Double =
        allocations.sumOf { it.percentOfProjectValue }

    fun isAllocationValid(allocations: List<CostAllocation>, tolerance: Double = 0.01): Boolean =
        kotlin.math.abs(totalAllocatedPercent(allocations) - 100.0) <= tolerance

    // ---------- Section 7: Material / Labour split ----------
    fun materialBudget(categoryBudget: Double, materialPercent: Double): Double =
        categoryBudget * (materialPercent / 100.0)

    fun labourBudget(categoryBudget: Double, labourPercent: Double): Double =
        categoryBudget * (labourPercent / 100.0)

    // ---------- Section 9: Vendor contract value ----------
    fun vendorContractValue(rateType: VendorRateType, rate: Double, quantity: Double, projectValue: Double): Double =
        when (rateType) {
            VendorRateType.PER_SQFT -> rate * quantity
            VendorRateType.QUANTITY_X_RATE -> rate * quantity
            VendorRateType.LUMP_SUM -> rate
            VendorRateType.PERCENTAGE -> projectValue * (rate / 100.0)
            VendorRateType.CUSTOM -> rate
        }

    // ---------- Section 10/11: BOQ + Material coefficient engine ----------
    fun boqAmount(quantity: Double, rate: Double): Double = quantity * rate

    fun requiredMaterialQuantity(boqQuantity: Double, coefficientPerUnit: Double): Double =
        boqQuantity * coefficientPerUnit

    fun wasteQuantity(requiredQuantity: Double, wastePercent: Double): Double =
        requiredQuantity * (wastePercent / 100.0)

    fun totalRequiredQuantity(requiredQuantity: Double, wastePercent: Double): Double =
        requiredQuantity + wasteQuantity(requiredQuantity, wastePercent)

    // ---------- Section 12: Material inventory ----------
    fun currentStock(purchasedQuantity: Double, usedQuantity: Double): Double =
        purchasedQuantity - usedQuantity

    fun balanceToPurchase(requiredQuantity: Double, purchasedQuantity: Double): Double =
        (requiredQuantity - purchasedQuantity).coerceAtLeast(0.0)

    fun averagePurchaseRate(totalPurchaseCost: Double, totalPurchasedQuantity: Double): Double =
        if (totalPurchasedQuantity == 0.0) 0.0 else totalPurchaseCost / totalPurchasedQuantity

    fun estimatedRemainingCost(balanceToPurchase: Double, averageRate: Double): Double =
        balanceToPurchase * averageRate

    // ---------- Section 13: Expense ----------
    fun expenseTotal(quantity: Double, rate: Double): Double = quantity * rate

    // ---------- Section 15: Client payments ----------
    fun clientBalance(projectValue: Double, totalReceived: Double): Double =
        projectValue - totalReceived

    // ---------- Section 16: Profit & loss ----------
    fun grossProfit(revenue: Double, totalExpenses: Double): Double = revenue - totalExpenses

    fun profitPercent(profit: Double, revenue: Double): Double =
        if (revenue == 0.0) 0.0 else (profit / revenue) * 100.0

    fun costOverrun(budget: Double, actual: Double): Double = (actual - budget).coerceAtLeast(0.0)

    fun savings(budget: Double, actual: Double): Double = (budget - actual).coerceAtLeast(0.0)

    fun variance(budget: Double, actual: Double): Double = budget - actual

    // ---------- Section 17: Cash flow ----------
    fun cashBalance(totalReceived: Double, totalSpent: Double): Double = totalReceived - totalSpent

    // ---------- Section 19: Labour prediction ----------
    /** Whole teams needed, rounded up, given remaining quantity and per-team-per-day productivity. */
    fun requiredTeams(remainingQuantity: Double, productivityPerTeamPerDay: Double): Int =
        if (productivityPerTeamPerDay <= 0.0) 0
        else kotlin.math.ceil(remainingQuantity / productivityPerTeamPerDay).toInt()

    fun expectedDurationDays(remainingQuantity: Double, productivityPerDay: Double): Int =
        if (productivityPerDay <= 0.0) 0
        else kotlin.math.ceil(remainingQuantity / productivityPerDay).toInt()

    // ---------- Section 20: Schedule / material shortage prediction ----------
    fun materialShortage(requiredInWindow: Double, currentStock: Double): Double =
        (requiredInWindow - currentStock).coerceAtLeast(0.0)

    fun labourShortage(requiredCount: Int, availableCount: Int): Int =
        (requiredCount - availableCount).coerceAtLeast(0)

    /**
     * BOQ quantity from dimensions ("L×B×D" entry mode) instead of a typed Nos count.
     * `sets` lets the same dimensions repeat (e.g. 20 identical footings). A depth or
     * breadth of 0 means "not applicable to this item" (e.g. a plastering job entered as
     * just length × breadth) rather than zeroing the whole quantity, so it's treated as 1.
     */
    fun lbdQuantity(sets: Double, length: Double, breadth: Double, depth: Double): Double {
        val effBreadth = if (breadth <= 0.0) 1.0 else breadth
        val effDepth = if (depth <= 0.0) 1.0 else depth
        return (if (sets <= 0.0) 1.0 else sets) * length.coerceAtLeast(0.0) * effBreadth * effDepth
    }

    // ---------- Progress-based completion prediction ----------
    /**
     * Naive linear extrapolation: if X% of work took Y days, project remaining days from
     * elapsed days and current progress. Returns null if there isn't enough data yet.
     */
    fun predictedCompletionDaysRemaining(progressPercent: Double, daysElapsed: Int): Int? {
        if (progressPercent <= 0.0 || daysElapsed <= 0) return null
        val totalEstimatedDays = daysElapsed / (progressPercent / 100.0)
        return kotlin.math.ceil(totalEstimatedDays - daysElapsed).toInt().coerceAtLeast(0)
    }
}
