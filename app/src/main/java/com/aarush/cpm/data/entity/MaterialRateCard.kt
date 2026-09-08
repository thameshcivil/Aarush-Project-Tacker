package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A simple, editable reference rate card per project — e.g. "Cement, Bags, ₹325". Used to
 * track budget assumptions for raw materials/lump-sum items (Door, Window, Electrical, etc.
 * are typically "LS" with rate left at 0 and tracked as a lump sum instead). Seeded with a
 * built-in default set per new project, fully editable/addable from the Settings tab.
 */
@Entity(tableName = "material_rates")
data class MaterialRateCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val description: String,  // "Cement", "P.Sand", "Steel"...
    val unit: String,         // "Bags", "Units", "Nos", "MT", "Sqft", "LS"
    val rate: Double = 0.0
)
