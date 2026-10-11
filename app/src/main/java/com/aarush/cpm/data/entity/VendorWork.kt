package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Extra work added to an existing vendor's contract, e.g. a compound wall given to the civil
 * contractor who already has the building (v9). Priced by running feet × rate, or as a lump
 * sum ([isLumpSum]: [amount] entered directly). [amount] is added to Vendor.contractValue when
 * the work is saved, so balances, Budget 2's civil labour and vendor alerts include it.
 * Rows are kept if the vendor is deleted (so Undo reconnects them, like its payments) and
 * removed with the project.
 */
@Entity(tableName = "vendor_works")
data class VendorWork(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val vendorId: Long,
    val label: String,
    val isLumpSum: Boolean = false,
    /** Running feet (rft) when not a lump sum. */
    val quantity: Double = 0.0,
    /** Rate per rft when not a lump sum. */
    val rate: Double = 0.0,
    val amount: Double,
    val createdAt: Long = System.currentTimeMillis()
)
