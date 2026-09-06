package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "boq_items")
data class BOQItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val itemCode: String,
    val category: CostCategory,
    val description: String,
    val unit: String,             // m3, m2, kg, bag, nos, sqft, litre...
    val quantity: Double,
    val rate: Double,
    val amount: Double,           // quantity * rate (editable override allowed)
    val labourQuantity: Double = 0.0,
    val labourRate: Double = 0.0,
    val labourAmount: Double = 0.0,
    val materialType: String = "",   // links loosely to Material.name for coefficient lookup
    val wastePercent: Double = 3.0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
