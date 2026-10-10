package com.aarush.cpm.ui.boq

import com.aarush.cpm.data.entity.BOQItem
import com.aarush.cpm.data.entity.MaterialCoefficient
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.ui.common.formatQuantity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Redesign Phase 3 regression checks: the measurement sheet must show the same quantities,
 * group totals and material roll-ups as the old BOQ list for the same data.
 */
class BOQSheetMathTest {

    // ---- the pre-redesign helpers, copied verbatim from BOQScreen.kt (commit 28c62a6) ----
    private fun legacyQuantityTotals(items: List<BOQItem>): String =
        items.groupBy { it.unit }.entries.joinToString(" + ") { (unit, list) -> formatQuantity(list.sumOf { it.quantity }, unit) }

    private fun legacyMaterialSummary(items: List<BOQItem>, coefficients: List<MaterialCoefficient>): String {
        val totals = mutableMapOf<String, Double>()
        items.forEach { boq ->
            coefficients.filter { it.workItemKey == boq.materialType && it.coefficientPerUnit > 0 }.forEach { coeff ->
                val base = CalculationEngine.requiredMaterialQuantity(boq.quantity, coeff.coefficientPerUnit)
                val withWaste = CalculationEngine.totalRequiredQuantity(base, coeff.wastePercentOverride ?: boq.wastePercent)
                totals[coeff.materialName] = (totals[coeff.materialName] ?: 0.0) + withWaste
            }
        }
        if (totals.isEmpty()) return "No material coefficients for this item"
        return totals.entries.sortedBy { it.key }.joinToString("  •  ") { (name, qty) -> "$name ${formatQuantity(qty, "")}" }
    }
    // -----------------------------------------------------------------------------------

    private val items = DemoBoq.items
    private val coeffs = DemoBoq.coefficients

    /** Every grouping the screen can show: whole groups and sub-groups, in both sort modes. */
    private fun allGroups(): List<List<BOQItem>> = BOQSortMode.values().flatMap { mode ->
        items.groupBy { mainKeyOf(it, mode) }.values.flatMap { main ->
            listOf(main) + main.groupBy { subKeyOf(it, mode) }.values
        }
    }

    @Test fun groupQuantityTotalsUnchanged() {
        allGroups().forEach { assertEquals(legacyQuantityTotals(it), quantityTotals(it)) }
    }

    @Test fun materialRollUpsUnchangedExceptSeparator() {
        allGroups().forEach { g ->
            assertEquals(legacyMaterialSummary(g, coeffs).replace("  •  ", ", "), materialSummary(g, coeffs))
        }
    }

    @Test fun groupingKeysUnchanged() {
        // Old screen: Notations mode = itemCode then description (blank -> itemCode); Description mode = the reverse.
        items.forEach {
            assertEquals(it.itemCode, mainKeyOf(it, BOQSortMode.NOTATIONS))
            assertEquals(it.description.ifBlank { it.itemCode }, subKeyOf(it, BOQSortMode.NOTATIONS))
            assertEquals(it.description.ifBlank { it.itemCode }, mainKeyOf(it, BOQSortMode.DESCRIPTION))
            assertEquals(it.itemCode, subKeyOf(it, BOQSortMode.DESCRIPTION))
        }
    }

    @Test fun storedQuantitiesMatchTheEngine() {
        items.forEach {
            assertEquals(CalculationEngine.boqDimensionalQuantity(it.numberOfMembers, it.perMember, it.length, it.breadth, it.depth), it.quantity, 1e-9)
        }
    }

    @Test fun knownTotals() {
        val rcc = items.filter { it.itemCode == "003-Reinforced Cement Concrete" }
        assertEquals("1580 Cft", quantityTotals(rcc))      // 320 + 45 + 195 + 90 + 420 + 90 + 420
        assertEquals("2880 Cft", quantityTotals(items.filter { it.itemCode == "001-Excavation" }))
    }

    @Test fun displayHelpers() {
        val e = items.first() // 16 nos, 6 x 6 x 5
        assertEquals("16", nosText(e))
        assertEquals("16×4", nosText(e.copy(perMember = 4.0)))
        assertEquals("6", dimText(6.0)); assertEquals("0.33", dimText(0.33)); assertEquals("-", dimText(0.0))
    }

    @Test fun floorOrderIsStableAndBuildingOrdered() {
        val walls = items.filter { it.description == "External walls" }.reversed() // First Floor, Ground Floor
        assertEquals(listOf("Ground Floor", "First Floor"), inFloorOrder(walls, DemoBoq.floorOrder).map { it.floor })
        val mixed = items.filter { it.itemCode == "003-Reinforced Cement Concrete" }
        assertEquals(mixed.map { it.id }.sortedBy { id -> DemoBoq.floorOrder.indexOf(mixed.first { it.id == id }.floor) },
            inFloorOrder(mixed, DemoBoq.floorOrder).map { it.id })
        assertEquals(items.size, inFloorOrder(items, DemoBoq.floorOrder).size)
    }
}
