package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CostCategory { CIVIL_STRUCTURAL, MEP, PAINTING, JOINERY, OTHER_MISC }

/**
 * One row per project per category. Holds the thumb-rule % of total project value,
 * plus the material/labour split % within that category budget.
 * Budgets are DERIVED (not stored) by the CalculationEngine from Project.projectValue.
 */
@Entity(tableName = "cost_allocations")
data class CostAllocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val category: CostCategory,
    val percentOfProjectValue: Double,   // e.g. 50.0 for 50%
    val materialPercent: Double = 65.0,  // within this category's budget
    val labourPercent: Double = 35.0,
    val updatedAt: Long = System.currentTimeMillis()
)
