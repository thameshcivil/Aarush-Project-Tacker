package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per "area & rate" line item on a project — e.g. a residence built at ₹2,500/sqft
 * plus a staircase built at a different ₹/sqft, tracked separately instead of forcing a
 * single blended area × rate for the whole project. Project.projectValue is the sum of
 * (areaSqft × ratePerSqft) across every component for that project, unless manually overridden.
 */
@Entity(tableName = "project_area_components")
data class ProjectAreaComponent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val label: String,       // e.g. "Residence", "Staircase", "Portico"
    val areaSqft: Double,
    val ratePerSqft: Double
)
