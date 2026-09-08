package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BOQQuantityMode { NOS, LBD }

@Entity(tableName = "boq_items")
data class BOQItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val itemCode: String,           // typically the notation code, e.g. "PCC", "BW" — see BOQNotation
    val category: CostCategory,
    val description: String,
    val unit: String,               // Cft, Sqft, Nos, MT, LS...
    /** Always the *final* quantity used everywhere else in the app, regardless of entry mode:
     *  either the typed Nos value, or sets × length × breadth × depth when quantityMode == LBD. */
    val quantity: Double,
    val rate: Double,
    val amount: Double,             // quantity * rate (editable override allowed)
    val labourQuantity: Double = 0.0,
    val labourRate: Double = 0.0,
    val labourAmount: Double = 0.0,
    val materialType: String = "",  // work item key for the coefficient engine — matches a BOQNotation.code
    val wastePercent: Double = 3.0,
    val quantityMode: BOQQuantityMode = BOQQuantityMode.NOS,
    val sets: Double = 1.0,
    val length: Double = 0.0,
    val breadth: Double = 0.0,
    val depth: Double = 0.0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
