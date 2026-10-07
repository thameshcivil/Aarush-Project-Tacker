package com.aarush.cpm.data.repository

import androidx.room.withTransaction
import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.entity.Expense
import com.aarush.cpm.data.entity.ExpenseType
import com.aarush.cpm.data.entity.MaterialPurchase
import com.aarush.cpm.data.entity.TransactionDirection
import kotlinx.coroutines.flow.Flow

/**
 * Section 13 & 36: "Enter once, calculate everywhere."
 * Adding one Expense automatically:
 *  - records the expense itself (project + category spend, cash outflow, profit all read from this)
 *  - if direction == EXPENSE and type == MATERIAL: creates a MaterialPurchase row (feeds stock/balance-to-purchase)
 *  - if a vendor is tagged: re-derives that vendor's cached amountPaid (feeds vendor payable)
 *  - if direction == RECEIVED: it's money coming in — no purchase/vendor side effects;
 *    ProjectSummaryRepository folds RECEIVED rows into total received instead.
 *
 * Every write runs in one database transaction, and edit/delete keep the MaterialPurchase
 * row in step with its Expense (previously an edited/deleted expense left its purchase
 * behind, and delete + Undo doubled the stock). Vendor amountPaid is recomputed from the
 * expenses table rather than incremented, so it can never drift.
 */
class ExpenseRepository(private val db: AppDatabase) {

    fun observeForProject(projectId: Long): Flow<List<Expense>> = db.expenseDao().observeForProject(projectId)

    fun observeTotalSpent(projectId: Long): Flow<Double> = db.expenseDao().observeTotalSpent(projectId)

    suspend fun getForProject(projectId: Long): List<Expense> = db.expenseDao().getForProject(projectId)

    suspend fun addExpense(expense: Expense): Long = db.withTransaction {
        val expenseId = db.expenseDao().insert(expense)
        applySideEffects(expense.copy(id = expenseId))
        expense.vendorId?.let { recalculateVendorPaid(it) }
        expenseId
    }

    suspend fun updateExpense(oldExpense: Expense, newExpense: Expense) = db.withTransaction {
        db.materialPurchaseDao().deleteForExpense(oldExpense.id)
        db.expenseDao().update(newExpense)
        applySideEffects(newExpense)
        setOfNotNull(oldExpense.vendorId, newExpense.vendorId).forEach { recalculateVendorPaid(it) }
    }

    suspend fun deleteExpense(expense: Expense) = db.withTransaction {
        db.materialPurchaseDao().deleteForExpense(expense.id)
        db.expenseDao().delete(expense)
        expense.vendorId?.let { recalculateVendorPaid(it) }
    }

    private suspend fun applySideEffects(expense: Expense) {
        if (expense.direction == TransactionDirection.EXPENSE && expense.type == ExpenseType.MATERIAL) {
            db.materialPurchaseDao().insert(
                MaterialPurchase(
                    projectId = expense.projectId,
                    materialName = expense.itemOrMaterialName,
                    expenseId = expense.id,
                    date = expense.date,
                    quantity = expense.quantity,
                    rate = expense.rate,
                    amount = expense.totalAmount,
                    vendorId = expense.vendorId
                )
            )
        }
    }

    private suspend fun recalculateVendorPaid(vendorId: Long) {
        val vendor = db.vendorDao().getById(vendorId) ?: return
        db.vendorDao().update(vendor.copy(amountPaid = db.expenseDao().sumPaidToVendor(vendorId)))
    }
}
