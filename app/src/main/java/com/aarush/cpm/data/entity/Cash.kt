package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cash between the office and the partners (v12).
 *
 * A partner takes a profit share of the project value (e.g. 10% each), whenever they choose.
 * Partners may also pay project expenses from their own pocket (Expense.paidByPartnerId); the
 * office later refills them from office cash. See domain/calculation/CashLedger.
 */
@Entity(tableName = "project_partners")
data class ProjectPartner(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    /** Profit share as % of the project value (10.0 = 10%). Separate from Project.profitPercent. */
    val sharePercent: Double,
    /** The partner's phone (team_devices.deviceId): expenses entered on it default to "Paid by" them. */
    val deviceId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class CashEntryType {
    /** Partner takes part of their profit share out of office cash. */
    PROFIT_TAKEN,
    /** Office cash pays a partner back for expenses they paid themselves. */
    REFILL
}

@Entity(tableName = "cash_entries")
data class CashEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val partnerId: Long,
    val type: CashEntryType,
    val amount: Double,
    val date: Long,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Phone that recorded it. */
    val enteredBy: String? = null
)
