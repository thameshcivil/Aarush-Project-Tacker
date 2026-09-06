package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "materials")
data class Material(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,             // Cement, Steel, M-Sand, River Sand, Aggregate, Bricks...
    val unit: String,             // bag, kg, m3, nos, litre, sqft
    val notes: String = ""
)

/**
 * Defines, per work-item type (identified by a free-text key such as "Concrete M20" or
 * "Brick Masonry"), how much of a given Material is needed per 1 unit of BOQ quantity.
 * Fully user-editable, never hard-coded as mandatory.
 */
@Entity(tableName = "material_coefficients")
data class MaterialCoefficient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val workItemKey: String,      // e.g. "Concrete M20", "Brick Masonry", "Plastering"
    val materialName: String,     // must match a Material.name for this project
    val coefficientPerUnit: Double, // e.g. cement bags per 1 m3 of concrete
    val wastePercentOverride: Double? = null // if null, BOQItem.wastePercent is used
)
