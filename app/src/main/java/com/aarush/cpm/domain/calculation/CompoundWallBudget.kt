package com.aarush.cpm.domain.calculation

import com.aarush.cpm.data.entity.CostAllocation
import com.aarush.cpm.data.entity.CostCategory
import com.aarush.cpm.data.entity.ProjectAreaComponent

/**
 * Category budgets when the project value includes a compound wall.
 *
 * A compound wall needs no plumbing, electrical, tiling or joinery, so its value must not
 * enlarge those budgets. Rule (agreed with the owner):
 *  - Every category gets its thumb-rule % of the BUILDING value (project value − wall value).
 *  - The wall value is split between Civil and Painting only, in the project's own
 *    Civil : Painting % ratio (e.g. 45 : 8). If Painting has no share, it all goes to Civil.
 * Totals still add up to the project value. With no compound wall the result is exactly
 * CalculationEngine.categoryBudget(projectValue, percent), as before.
 */
object CompoundWallBudget {

    /** Value of the compound wall rows (running feet × rate, or lump sum). */
    fun wallValue(components: List<ProjectAreaComponent>): Double =
        components.filter { it.isCompoundWall }.sumOf { CalculationEngine.projectValue(it.areaSqft, it.ratePerSqft) }

    /** Building rows only: what plinth area and the blended rate are based on. */
    fun buildingRows(components: List<ProjectAreaComponent>): List<ProjectAreaComponent> = components.filterNot { it.isCompoundWall }

    /** Budget for one category. [allocations] supplies the Civil and Painting % for the split. */
    fun categoryBudget(
        category: CostCategory,
        percentOfValue: Double,
        projectValue: Double,
        wallValue: Double,
        allocations: List<CostAllocation>
    ): Double {
        if (wallValue <= 0.0) return CalculationEngine.categoryBudget(projectValue, percentOfValue)
        val wall = wallValue.coerceAtMost(projectValue).coerceAtLeast(0.0)
        val building = projectValue - wall
        val base = CalculationEngine.categoryBudget(building, percentOfValue)
        return base + wall * wallShare(category, allocations)
    }

    /** Share of the wall value that goes to [category]: Civil and Painting only. */
    fun wallShare(category: CostCategory, allocations: List<CostAllocation>): Double {
        val civil = allocations.firstOrNull { it.category == CostCategory.CIVIL_STRUCTURAL }?.percentOfProjectValue ?: 0.0
        val painting = allocations.firstOrNull { it.category == CostCategory.PAINTING }?.percentOfProjectValue ?: 0.0
        val total = civil + painting
        return when (category) {
            CostCategory.CIVIL_STRUCTURAL -> if (total > 0) civil / total else 1.0
            CostCategory.PAINTING -> if (total > 0) painting / total else 0.0
            else -> 0.0
        }
    }
}
