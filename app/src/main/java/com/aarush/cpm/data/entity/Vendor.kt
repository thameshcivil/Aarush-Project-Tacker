package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class VendorWorkCategory {
    CIVIL, STRUCTURAL, MASON, CARPENTER, ELECTRICIAN, PLUMBING,
    HVAC, PAINTING, FLOORING, ALUMINIUM, FABRICATION, OTHER
}

enum class VendorRateType { PER_SQFT, LUMP_SUM, QUANTITY_X_RATE, PERCENTAGE, CUSTOM }

@Entity(tableName = "vendors")
data class Vendor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val contact: String,
    val workCategory: VendorWorkCategory,
    val rateType: VendorRateType,
    val rate: Double = 0.0,           // meaning depends on rateType
    val quantity: Double = 0.0,       // used when rateType == QUANTITY_X_RATE or PER_SQFT
    val contractValue: Double,        // computed at creation, editable
    val startDate: Long,
    val endDate: Long,
    val paymentTerms: String = "",
    val amountPaid: Double = 0.0,     // derived from Expense entries tagged to this vendor, cached here
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
