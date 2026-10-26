package com.aarush.cpm.calculation

import com.aarush.cpm.data.entity.CostAllocation
import com.aarush.cpm.data.entity.CostCategory
import com.aarush.cpm.data.entity.ProjectAreaComponent
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.domain.calculation.CompoundWallBudget
import org.junit.Assert.assertEquals
import org.junit.Test

/** Compound wall: value budgeted to Civil and Painting only, in the project's Civil:Painting ratio. */
class CompoundWallBudgetTest {
    // The app's default thumb-rule distribution (sums to 100%).
    private val alloc = listOf(
        CostAllocation(projectId = 1, category = CostCategory.CIVIL_STRUCTURAL, percentOfProjectValue = 45.0),
        CostAllocation(projectId = 1, category = CostCategory.MEP, percentOfProjectValue = 15.0),
        CostAllocation(projectId = 1, category = CostCategory.PAINTING, percentOfProjectValue = 8.0),
        CostAllocation(projectId = 1, category = CostCategory.JOINERY, percentOfProjectValue = 12.0),
        CostAllocation(projectId = 1, category = CostCategory.TILING, percentOfProjectValue = 8.0),
        CostAllocation(projectId = 1, category = CostCategory.OTHER_MISC, percentOfProjectValue = 12.0)
    )
    private fun budget(c: CostCategory, value: Double, wall: Double, a: List<CostAllocation> = alloc) =
        CompoundWallBudget.categoryBudget(c, a.first { it.category == c }.percentOfProjectValue, value, wall, a)

    @Test fun noWallIsExactlyTheOldRule() {
        alloc.forEach { assertEquals(CalculationEngine.categoryBudget(5_000_000.0, it.percentOfProjectValue), budget(it.category, 5_000_000.0, 0.0), 1e-6) }
    }

    @Test fun wallGoesOnlyToCivilAndPaintingInTheirRatio() {
        val wall = 180.0 * 1_800.0             // 180 rft x Rs 1,800 = Rs 3,24,000
        val value = 5_000_000.0 + wall         // building 50 L + wall
        assertEquals(5_000_000.0 * 0.45 + wall * 45.0 / 53.0, budget(CostCategory.CIVIL_STRUCTURAL, value, wall), 1e-6)
        assertEquals(5_000_000.0 * 0.08 + wall * 8.0 / 53.0, budget(CostCategory.PAINTING, value, wall), 1e-6)
        assertEquals(5_000_000.0 * 0.15, budget(CostCategory.MEP, value, wall), 1e-6)       // unchanged by the wall
        assertEquals(5_000_000.0 * 0.08, budget(CostCategory.TILING, value, wall), 1e-6)
        assertEquals(5_000_000.0 * 0.12, budget(CostCategory.JOINERY, value, wall), 1e-6)
        assertEquals(5_000_000.0 * 0.12, budget(CostCategory.OTHER_MISC, value, wall), 1e-6)
    }

    @Test fun budgetsStillAddUpToTheProjectValue() {
        val wall = 324_000.0; val value = 5_324_000.0
        assertEquals(value, alloc.sumOf { budget(it.category, value, wall) }, 1e-6)
    }

    @Test fun noPaintingShareMeansAllWallToCivil() {
        val a = alloc.filter { it.category != CostCategory.PAINTING }
        assertEquals(1_000_000.0 * 0.45 + 200_000.0, budget(CostCategory.CIVIL_STRUCTURAL, 1_200_000.0, 200_000.0, a), 1e-6)
    }

    @Test fun wallLargerThanAManualProjectValueIsCapped() {
        // Manual override below the wall value: nothing left for the building; no negatives.
        alloc.forEach { assertEquals(true, budget(it.category, 100_000.0, 300_000.0) >= 0.0) }
        assertEquals(100_000.0, alloc.sumOf { budget(it.category, 100_000.0, 300_000.0) }, 1e-6)
    }

    @Test fun wallValueCountsRunningFeetAndLumpSumRowsOnly() {
        val rows = listOf(
            ProjectAreaComponent(projectId = 1, label = "Residence", areaSqft = 2000.0, ratePerSqft = 2500.0),
            ProjectAreaComponent(projectId = 1, label = "Compound wall", areaSqft = 180.0, ratePerSqft = 1800.0, isCompoundWall = true),
            ProjectAreaComponent(projectId = 1, label = "Gate wall", areaSqft = 1.0, ratePerSqft = 50_000.0, isCompoundWall = true, isLumpSum = true)
        )
        assertEquals(324_000.0 + 50_000.0, CompoundWallBudget.wallValue(rows), 1e-6)
        assertEquals(listOf("Residence"), CompoundWallBudget.buildingRows(rows).map { it.label })
    }

    // ---------------------------------------------------------------- profit % (v11)

    @Test fun zeroProfitChangesNothing() {
        CostCategory.values().forEach { c ->
            val pct = alloc.first { it.category == c }.percentOfProjectValue
            assertEquals(budget(c, 5_000_000.0, 324_000.0),
                CompoundWallBudget.categoryBudget(c, pct, 5_000_000.0, 324_000.0, alloc, profitPercent = 0.0), 0.001)
        }
    }

    @Test fun profitIsKeptAsideAndBudgetsShareTheRest() {
        val value = 5_000_000.0
        // 15% profit: Civil 45% of the remaining 85% = 0.45 × 4,250,000.
        val civil = CompoundWallBudget.categoryBudget(CostCategory.CIVIL_STRUCTURAL, 45.0, value, 0.0, alloc, profitPercent = 15.0)
        assertEquals(1_912_500.0, civil, 0.001)
        assertEquals(750_000.0, CompoundWallBudget.profitAmount(value, 15.0), 0.001)
        // With a compound wall too, budgets + profit still add up to the project value.
        val wall = 324_000.0
        val total = alloc.sumOf { CompoundWallBudget.categoryBudget(it.category, it.percentOfProjectValue, value, wall, alloc, 15.0) }
        assertEquals(value, total + CompoundWallBudget.profitAmount(value, 15.0), 0.01)
    }
}
