package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Created automatically whenever an Expense of type MATERIAL is added; also directly editable. */
@Entity(tableName = "material_purchases")
data class MaterialPurchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val materialName: String,
    val expenseId: Long? = null,   // link back to originating Expense, if any
    val date: Long,
    val quantity: Double,
    val rate: Double,
    val amount: Double,
    val vendorId: Long? = null
)

/** Site consumption of material, entered directly or derived from progress updates. */
@Entity(tableName = "material_usages")
data class MaterialUsage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val materialName: String,
    val date: Long,
    val quantityUsed: Double,
    val notes: String = ""
)
