package com.aarush.cpm.data.repository

import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.entity.CostAllocation
import com.aarush.cpm.data.entity.Project
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

/** Single place that stitches Project + Expenses + ClientPayments + Progress into the numbers
 *  every card on the Dashboard / Project Detail screen needs. Pure aggregation, no UI. */
class ProjectSummaryRepository(private val db: AppDatabase) {

    suspend fun buildSummary(projectId: Long): ProjectSummary? {
        val project = db.projectDao().getById(projectId) ?: return null
        val expenses = db.expenseDao().getForProject(projectId)
        val payments = db.clientPaymentDao().getForProject(projectId)
        val allocations = db.costAllocationDao().getForProject(projectId)
        val latestProgress = db.projectProgressDao().getLatest(projectId)

        val totalReceived = payments.sumOf { it.amountReceived }
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
}
