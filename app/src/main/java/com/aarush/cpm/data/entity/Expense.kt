package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ExpenseType { MATERIAL, LABOUR, VENDOR, OTHER }
enum class PaymentMode { CASH, BANK_TRANSFER, CHEQUE, UPI, CREDIT, OTHER }
enum class PaymentStatus { PAID, CREDIT, PARTIALLY_PAID }
/** Cash direction for this line: EXPENSE (money out — the default) or RECEIVED (money in,
 *  e.g. a refund/adjustment). Independent of `type`, which is just categorization. */
enum class TransactionDirection { EXPENSE, RECEIVED }

/**
 * THE single "actual spending" entry point. One row here fans out (via CalculationEngine /
 * ExpenseRepository) into: project expense totals, category spend, material purchase +
 * stock, vendor payable, and cash outflow. Nothing about an expense is re-entered elsewhere.
 */
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val date: Long,
    val category: CostCategory,
    val type: ExpenseType,
    val direction: TransactionDirection = TransactionDirection.EXPENSE,
    val vendorId: Long? = null,
    val itemOrMaterialName: String,   // e.g. "Cement", "Mason wages", "Electrical vendor advance"
    val quantity: Double = 1.0,
    val unit: String = "",
    val rate: Double,
    val totalAmount: Double,          // quantity * rate, editable override allowed
    val paymentMode: PaymentMode,
    val paymentStatus: PaymentStatus,
    val invoiceNumber: String = "",
    val photoUri: String? = null,
    val notes: String = "",
    val isDemoData: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
