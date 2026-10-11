package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per "area & rate" line item on a project — e.g. a residence built at ₹2,500/sqft
 * plus a staircase built at a different ₹/sqft, tracked separately instead of forcing a
 * single blended area × rate for the whole project. Project.projectValue is the sum of
 * (areaSqft × ratePerSqft) across every component for that project, unless manually overridden.
 *
 * Compound wall rows ([isCompoundWall], v9): priced by running feet × rate (areaSqft holds the
 * running feet, ratePerSqft the rate per rft) or as a lump sum ([isLumpSum]: areaSqft = 1,
 * ratePerSqft = the amount), so value is still areaSqft × ratePerSqft. Their value is part of
 * the project value but is budgeted to Civil and Painting only (see CompoundWallBudget), and
 * they are left out of the plinth area.
 */
@Entity(tableName = "project_area_components")
data class ProjectAreaComponent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val label: String,       // e.g. "Residence", "Staircase", "Portico", "Compound wall"
    val areaSqft: Double,
    val ratePerSqft: Double,
    val isCompoundWall: Boolean = false,
    val isLumpSum: Boolean = false
)
