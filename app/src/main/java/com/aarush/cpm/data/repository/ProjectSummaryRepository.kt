package com.aarush.cpm.data.repository

import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.entity.CostAllocation
import com.aarush.cpm.data.entity.CostCategory
import com.aarush.cpm.data.entity.Project
import com.aarush.cpm.data.entity.TransactionDirection
import com.aarush.cpm.data.entity.VendorWorkCategory
import com.aarush.cpm.domain.calculation.CalculationEngine

data class CategoryBudgetVsActual(
    val allocation: CostAllocation,
    val budget: Double,
    val actual: Double,
    val remaining: Double,
    val variance: Double
)

data class ProjectSummary(
    val project: Project,
    val totalReceived: Double,
    val totalSpent: Double,
    val clientBalance: Double,
    val cashBalance: Double,
    val profit: Double,
    val profitPercent: Double,
    val progressPercent: Double,
    val categories: List<CategoryBudgetVsActual>
)

/**
 * Budget 1 vs Budget 2, per the project's actual working method:
 *  - Budget 1 is the fixed plinth-area figure (Project.projectValue) — what the client is
 *    quoted, never recalculated from measurements.
 *  - Budget 2 is calculated bottom-up for Civil work — every BOQ measurement × its material
 *    coefficients × the material rate card, plus civil-labour vendor contracts — while MEP,
 *    Painting, Joinery, and Other still use the thumb-rule percentage of Budget 1 (unchanged,
 *    per the spec: only Civil gets the detailed treatment).
 *  - Comparing Budget 2 to actual expense is a cost-control check: are you spending more than
 *    what the real measured work should cost? Comparing Budget 1 to actual expense is the
 *    true profit: what the client pays minus what it actually cost.
 */
data class BudgetComparison(
    val budget1: Double,
    val civilMaterialCost: Double,
    val civilLabourCost: Double,
    val civilTotal: Double,
    val otherCategoriesTotal: Double,
    val budget2: Double,
    val actualExpense: Double,
    val costControlVariance: Double,
    val expectedProfit: Double
)

/** Single place that stitches Project + Expenses + ClientPayments + Progress into the numbers
 *  every card on the Dashboard / Project Detail screen needs. Pure aggregation, no UI. */
class ProjectSummaryRepository(private val db: AppDatabase) {

    /** Vendor categories counted as "civil labour" for Budget 2's civil total. Other
     *  categories (Electrician, Plumbing, HVAC, Painting, etc.) stay covered by their
     *  respective category's percentage-of-Budget-1 allocation instead, so a vendor's
     *  contract is never counted toward both. */
    private val civilLabourCategories = setOf(
        VendorWorkCategory.CIVIL, VendorWorkCategory.STRUCTURAL, VendorWorkCategory.MASON, VendorWorkCategory.CARPENTER
    )

    suspend fun buildSummary(projectId: Long): ProjectSummary? {
        val project = db.projectDao().getById(projectId) ?: return null
        val allExpenses = db.expenseDao().getForProject(projectId)
        // Expense rows can be either direction now — EXPENSE (money out, the historical
        // default) or RECEIVED (money in, e.g. a refund/adjustment logged from the same
        // form). Only EXPENSE rows count as spend or against a category's budget; RECEIVED
        // rows fold into total received alongside client payments.
        val expenses = allExpenses.filter { it.direction == TransactionDirection.EXPENSE }
        val receivedViaExpenses = allExpenses.filter { it.direction == TransactionDirection.RECEIVED }.sumOf { it.totalAmount }
        val payments = db.clientPaymentDao().getForProject(projectId)
        val allocations = db.costAllocationDao().getForProject(projectId)
        val latestProgress = db.projectProgressDao().getLatest(projectId)

        val totalReceived = payments.sumOf { it.amountReceived } + receivedViaExpenses
        val totalSpent = expenses.sumOf { it.totalAmount }
        val clientBalance = CalculationEngine.clientBalance(project.projectValue, totalReceived)
        val cashBalance = CalculationEngine.cashBalance(totalReceived, totalSpent)
        val profit = CalculationEngine.grossProfit(project.projectValue, totalSpent)
        val profitPercent = CalculationEngine.profitPercent(profit, project.projectValue)

        val categories = allocations.map { alloc ->
            val budget = CalculationEngine.categoryBudget(project.projectValue, alloc.percentOfProjectValue)
            val actual = expenses.filter { it.category == alloc.category }.sumOf { it.totalAmount }
            CategoryBudgetVsActual(
                allocation = alloc,
                budget = budget,
                actual = actual,
                remaining = (budget - actual).coerceAtLeast(0.0),
                variance = CalculationEngine.variance(budget, actual)
            )
        }

        return ProjectSummary(
            project = project,
            totalReceived = totalReceived,
            totalSpent = totalSpent,
            clientBalance = clientBalance,
            cashBalance = cashBalance,
            profit = profit,
            profitPercent = profitPercent,
            progressPercent = latestProgress?.overallProgressPercent ?: 0.0,
            categories = categories
        )
    }

    suspend fun buildBudgetComparison(projectId: Long): BudgetComparison? {
        val project = db.projectDao().getById(projectId) ?: return null
        val boqItems = db.boqDao().getForProject(projectId)
        val coefficients = db.materialCoefficientDao().getForProject(projectId)
        val rateByMaterialName = db.materialRateCardDao().getForProject(projectId).associateBy { it.description }
        val vendors = db.vendorDao().getForProject(projectId)
        val allocations = db.costAllocationDao().getForProject(projectId)
        val actualExpense = db.expenseDao().getForProject(projectId)
            .filter { it.direction == TransactionDirection.EXPENSE }
            .sumOf { it.totalAmount }

        // Civil material cost: every BOQ item's measured quantity, traced through its
        // notation's coefficients (with waste %) to a required quantity per material, priced
        // at that material's rate-card rate — the same tracing MaterialRepository.materialStatus()
        // uses for stock, just multiplied by rate here instead of compared to purchases.
        var civilMaterialCost = 0.0
        for (boq in boqItems) {
            val matchingCoefficients = coefficients.filter { it.workItemKey == boq.materialType && it.coefficientPerUnit > 0 }
            for (coeff in matchingCoefficients) {
                val required = CalculationEngine.requiredMaterialQuantity(boq.quantity, coeff.coefficientPerUnit)
                val wastePercent = coeff.wastePercentOverride ?: boq.wastePercent
                val totalRequired = CalculationEngine.totalRequiredQuantity(required, wastePercent)
                val rate = rateByMaterialName[coeff.materialName]?.rate ?: 0.0
                civilMaterialCost += totalRequired * rate
            }
        }

        val civilLabourCost = vendors.filter { it.workCategory in civilLabourCategories }.sumOf { it.contractValue }
        val civilTotal = civilMaterialCost + civilLabourCost

        val otherCategoriesTotal = allocations
            .filter { it.category != CostCategory.CIVIL_STRUCTURAL }
            .sumOf { CalculationEngine.categoryBudget(project.projectValue, it.percentOfProjectValue) }

        val budget2 = civilTotal + otherCategoriesTotal

        return BudgetComparison(
            budget1 = project.projectValue,
            civilMaterialCost = civilMaterialCost,
            civilLabourCost = civilLabourCost,
            civilTotal = civilTotal,
            otherCategoriesTotal = otherCategoriesTotal,
            budget2 = budget2,
            actualExpense = actualExpense,
            costControlVariance = budget2 - actualExpense,
            expectedProfit = project.projectValue - actualExpense
        )
    }
}
