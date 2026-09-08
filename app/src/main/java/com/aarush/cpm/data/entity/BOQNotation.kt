package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per work-item "notation" (short code) available for a project — e.g. "PCC" for
 * Plain Cement Concrete, "BW" for 9" Brick work. These populate the item-code dropdown on
 * the BOQ screen; picking one auto-fills the BOQ item's description, default unit, and the
 * material-coefficient work-item key. Seeded with a built-in default set per new project
 * (see BuiltInDefaultsSeeder), fully editable/addable from the project's Settings tab.
 */
@Entity(tableName = "boq_notations")
data class BOQNotation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val code: String,          // "PCC", "RCC", "BW", "IPL"...
    val description: String,   // "Plain Cement Concrete"
    val defaultUnit: String    // "Cft", "Sqft"
)
