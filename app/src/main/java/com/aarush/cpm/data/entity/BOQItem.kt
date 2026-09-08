package com.aarush.cpm.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Standard unit choices offered on the BOQ entry form. Stored as plain text on BOQItem.unit,
 *  same as before — this is just the fixed dropdown list rather than free typing. */
object BOQUnits {
    val OPTIONS = listOf("Rmt", "Rft", "Sqft", "Sqm", "Cft", "Cum")
}

@Entity(tableName = "boq_items")
data class BOQItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val itemCode: String,           // e.g. "001-Excavation" — see BOQNotation
    val category: CostCategory,
    val description: String,
    val unit: String,               // Rmt, Rft, Sqft, Sqm, Cft, Cum
    /** Always the *final* computed quantity — members × perMember × length × breadth × depth
     *  (see CalculationEngine.boqDimensionalQuantity) — used everywhere else in the app. */
    val quantity: Double,
    val rate: Double,
    val amount: Double,             // quantity * rate (editable override allowed)
    val labourQuantity: Double = 0.0,
    val labourRate: Double = 0.0,
    val labourAmount: Double = 0.0,
    val materialType: String = "",  // work item key for the coefficient engine — matches a BOQNotation.code
    val wastePercent: Double = 3.0,
    /** "No. of Member" — e.g. 20 identical footings. Column kept as "sets" from the schema's
     *  first version to avoid an unnecessary migration; the meaning is the same. */
    @ColumnInfo(name = "sets") val numberOfMembers: Double = 1.0,
    val perMember: Double = 1.0,    // "No. Per Member"
    val length: Double = 0.0,
    val breadth: Double = 0.0,
    val depth: Double = 0.0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
