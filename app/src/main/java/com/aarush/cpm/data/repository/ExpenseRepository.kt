package com.aarush.cpm.data.repository

import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.entity.Expense
import com.aarush.cpm.data.entity.ExpenseType
import com.aarush.cpm.data.entity.MaterialPurchase
import com.aarush.cpm.data.entity.Vendor
import kotlinx.coroutines.flow.Flow

/**
 * Section 13 & 36: "Enter once, calculate everywhere."
 * Adding one Expense automatically:
 *  - records the expense itself (project + category spend, cash outflow, profit all read from this)
 *  - if type == MATERIAL: creates a MaterialPurchase row (feeds stock/balance-to-purchase)
 *  - if a vendor is tagged: bumps that vendor's cached amountPaid (feeds vendor payable)
 * Nothing is asked twice on separate screens.
 */
class ExpenseRepository(private val db: AppDatabase) {

    fun observeForProject(projectId: Long): Flow<List<Expense>> = db.expenseDao().observeForProject(projectId)

    fun observeTotalSpent(projectId: Long): Flow<Double> = db.expenseDao().observeTotalSpent(projectId)

    suspend fun getForProject(projectId: Long): List<Expense> = db.expenseDao().getForProject(projectId)

    suspend fun addExpense(expense: Expense): Long {
        val expenseId = db.expenseDao().insert(expense)

        if (expense.type == ExpenseType.MATERIAL) {
            db.materialPurchaseDao().insert(
                MaterialPurchase(
                    projectId = expense.projectId,
                    materialName = expense.itemOrMaterialName,
                    expenseId = expenseId,
                    date = expense.date,
                    quantity = expense.quantity,
                    rate = expense.rate,
                    amount = expense.totalAmount,
                    vendorId = expense.vendorId
                )
            )
        }

        expense.vendorId?.let { vendorId ->
            db.vendorDao().getById(vendorId)?.let { vendor: Vendor ->
                db.vendorDao().update(vendor.copy(amountPaid = vendor.amountPaid + expense.totalAmount))
            }
        }

        return expenseId
    }

    suspend fun updateExpense(oldExpense: Expense, newExpense: Expense) {
        // Reverse old vendor impact, apply new — keeps vendor payable accurate on edits.
        oldExpense.vendorId?.let { vendorId ->
            db.vendorDao().getById(vendorId)?.let { vendor ->
                db.vendorDao().update(vendor.copy(amountPaid = (vendor.amountPaid - oldExpense.totalAmount).coerceAtLeast(0.0)))
            }
        }
        db.expenseDao().update(newExpense)
        newExpense.vendorId?.let { vendorId ->
            db.vendorDao().getById(vendorId)?.let { vendor ->
                db.vendorDao().update(vendor.copy(amountPaid = vendor.amountPaid + newExpense.totalAmount))
            }
        }
    }

    suspend fun deleteExpense(expense: Expense) {
        expense.vendorId?.let { vendorId ->
            db.vendorDao().getById(vendorId)?.let { vendor ->
                db.vendorDao().update(vendor.copy(amountPaid = (vendor.amountPaid - expense.totalAmount).coerceAtLeast(0.0)))
            }
        }
        db.expenseDao().delete(expense)
    }
}
