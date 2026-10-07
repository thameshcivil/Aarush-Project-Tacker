package com.aarush.cpm.schedule

import com.aarush.cpm.data.entity.ActivityStatus
import com.aarush.cpm.data.entity.CostCategory
import com.aarush.cpm.data.entity.VendorWorkCategory
import com.aarush.cpm.domain.calculation.CategoryRules
import com.aarush.cpm.domain.schedule.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ScheduleEngineTest {

    // Monday 5 Jan 2026
    private val monday = LocalDate.of(2026, 1, 5)
    private val cal = WorkCalendar(monday, sundayOff = true)

    @Test
    fun `calendar skips Sundays and round-trips indexes`() {
        assertEquals(monday, cal.dateOf(0))
        assertEquals(monday.plusDays(5), cal.dateOf(5))          // Saturday
        assertEquals(monday.plusDays(7), cal.dateOf(6))          // skips Sunday → next Monday
        for (i in -20..400) assertEquals(i, cal.indexOf(cal.dateOf(i)))
        assertEquals(6, cal.indexOf(monday.plusDays(6)))         // a Sunday maps to next working day
        assertTrue((0..300).none { cal.dateOf(it).dayOfWeek == DayOfWeek.SUNDAY })
        val sevenDay = WorkCalendar(monday, sundayOff = false)
        assertEquals(monday.plusDays(6), sevenDay.dateOf(6))
    }

    @Test
    fun `planned quantity takes floor-tagged BOQ exactly and shares the rest`() {
        val acts = listOf(
            ActivityQuantityInput(1, "RCC", "Foundation", 20.0, null),
            ActivityQuantityInput(2, "RCC", "Ground Floor", 40.0, null),
            ActivityQuantityInput(3, "RCC", "First Floor", 40.0, null),
            ActivityQuantityInput(4, "", "", 100.0, null),
            ActivityQuantityInput(5, "PCC", "", 100.0, 77.0)
        )
        val boq = listOf(
            BoqQuantity("RCC", "Ground Floor", 300.0),   // exact to activity 2
            BoqQuantity("RCC", "", 1000.0),             // pool
            BoqQuantity("RCC", "Terrace", 100.0),       // no Terrace activity → pool
            BoqQuantity("PCC", "", 50.0)
        )
        val q = ScheduleEngine.plannedQuantities(acts, boq)
        assertEquals(220.0, q.getValue(1), 1e-9)
        assertEquals(300.0 + 440.0, q.getValue(2), 1e-9)
        assertEquals(440.0, q.getValue(3), 1e-9)
        assertEquals(0.0, q.getValue(4), 1e-9)
        assertEquals(77.0, q.getValue(5), 1e-9)              // override wins
        // all BOQ quantity is allocated
        assertEquals(1400.0, q.getValue(1) + q.getValue(2) + q.getValue(3), 1e-9)
    }

    @Test
    fun `two activities on the same floor split that floor's quantity, never double count`() {
        val acts = listOf(
            ActivityQuantityInput(1, "RCC", "Ground Floor", 30.0, null),   // columns
            ActivityQuantityInput(2, "RCC", "Ground Floor", 70.0, null),   // slab
            ActivityQuantityInput(3, "RCC", "First Floor", 100.0, null)
        )
        val boq = listOf(BoqQuantity("RCC", "Ground Floor", 500.0), BoqQuantity("RCC", "First Floor", 400.0))
        val q = ScheduleEngine.plannedQuantities(acts, boq)
        assertEquals(150.0, q.getValue(1), 1e-9)
        assertEquals(350.0, q.getValue(2), 1e-9)
        assertEquals(400.0, q.getValue(3), 1e-9)
        assertEquals(900.0, q.values.sum(), 1e-9)
    }

    @Test
    fun `duration from quantity rounds up with fallback`() {
        assertEquals(3, ScheduleEngine.durationFromQuantity(1001.0, 500.0, 9))
        assertEquals(9, ScheduleEngine.durationFromQuantity(0.0, 500.0, 9))
        assertEquals(1, ScheduleEngine.durationFromQuantity(10.0, 500.0, 9))
        assertEquals(4, ScheduleEngine.durationFromQuantity(10.0, 0.0, 4))
    }

    @Test
    fun `budgets reconcile with pools and overrides`() {
        val inputs = listOf(
            BudgetInput(1, CostCategory.CIVIL_STRUCTURAL, 1.0, null, materialCost = 1000.0, manDays = 30.0),
            BudgetInput(2, CostCategory.CIVIL_STRUCTURAL, 1.0, null, materialCost = 0.0, manDays = 10.0),
            BudgetInput(3, CostCategory.MEP, 3.0, null, 0.0, 0.0),
            BudgetInput(4, CostCategory.MEP, 1.0, null, 0.0, 0.0),
            BudgetInput(5, CostCategory.PAINTING, 1.0, 999.0, 0.0, 0.0)
        )
        val b = ScheduleEngine.allocateBudgets(inputs, civilLabourPool = 4000.0,
            categoryPools = mapOf(CostCategory.MEP to 800.0, CostCategory.PAINTING to 500.0))
        assertEquals(1000.0 + 3000.0, b.getValue(1), 1e-9)
        assertEquals(1000.0, b.getValue(2), 1e-9)
        assertEquals(600.0, b.getValue(3), 1e-9)
        assertEquals(200.0, b.getValue(4), 1e-9)
        assertEquals(999.0, b.getValue(5), 1e-9)
    }

    private fun node(id: Long, dur: Int, vararg preds: Pair<Long, Int>, pct: Double = 0.0, start: LocalDate? = null, finish: LocalDate? = null) =
        ActivityNetworkInput(id, dur, preds.toList(), pct, start, finish)

    @Test
    fun `critical path finds the longest chain and float`() {
        // 1(3) → 2(5) → 4(2) ; 1 → 3(2) → 4
        val nodes = listOf(node(1, 3), node(2, 5, 1L to 0), node(3, 2, 1L to 0), node(4, 2, 2L to 0, 3L to 0))
        val cpm = ScheduleEngine.criticalPath(nodes, cal)
        assertEquals(cal.dateOf(0), cpm.getValue(1).start)
        assertEquals(cal.dateOf(3), cpm.getValue(2).start)
        assertEquals(cal.dateOf(8), cpm.getValue(4).start)
        assertEquals(cal.dateOf(9), cpm.getValue(4).finish)
        assertTrue(cpm.getValue(1).isCritical && cpm.getValue(2).isCritical && cpm.getValue(4).isCritical)
        assertFalse(cpm.getValue(3).isCritical)
        assertEquals(3, cpm.getValue(3).totalFloat)
    }

    @Test
    fun `lag delays the successor`() {
        val cpm = ScheduleEngine.criticalPath(listOf(node(1, 2), node(2, 1, 1L to 14)), cal)
        assertEquals(cal.dateOf(16), cpm.getValue(2).start)
    }

    @Test
    fun `cycles never hang and are detected`() {
        val nodes = listOf(node(1, 2, 2L to 0), node(2, 2, 1L to 0))
        assertEquals(2, ScheduleEngine.topologicalOrder(nodes).size)
        ScheduleEngine.criticalPath(nodes, cal) // must not throw
        val deps = listOf(2L to 1L, 3L to 2L) // 2 depends on 1, 3 depends on 2
        assertTrue(ScheduleEngine.wouldCreateCycle(deps, activityId = 1, newPredecessorId = 3))
        assertFalse(ScheduleEngine.wouldCreateCycle(deps, activityId = 3, newPredecessorId = 1))
        assertTrue(ScheduleEngine.wouldCreateCycle(deps, 5, 5))
    }

    @Test
    fun `tracking forecasts delays from actual progress`() {
        val nodes = listOf(node(1, 4), node(2, 4, 1L to 0))
        val base = ScheduleEngine.criticalPath(nodes, cal).mapValues { it.value.start to it.value.finish }
        // Today is index 6 (activity 1 should have finished at index 3), it is only 50% done.
        val today = cal.dateOf(6)
        val tracked = ScheduleEngine.track(
            listOf(node(1, 4, pct = 50.0, start = cal.dateOf(1)), node(2, 4, 1L to 0)), base, cal, today
        )
        val a1 = tracked.getValue(1); val a2 = tracked.getValue(2)
        assertEquals(ActivityStatus.DELAYED, a1.status)
        assertEquals(cal.dateOf(7), a1.forecastFinish)          // 2 remaining days from today
        assertEquals(4, a1.delayDays)
        assertEquals(cal.dateOf(8), a2.forecastStart)
        assertEquals(ActivityStatus.DELAYED, a2.status)          // its baseline start has passed
        assertEquals(100.0, a1.plannedPercent, 1e-9)
        assertEquals(50.0, a1.actualPercent, 1e-9)
    }

    @Test
    fun `completed and future activities`() {
        val nodes = listOf(node(1, 2), node(2, 3, 1L to 0))
        val base = ScheduleEngine.criticalPath(nodes, cal).mapValues { it.value.start to it.value.finish }
        val tracked = ScheduleEngine.track(
            listOf(node(1, 2, pct = 100.0, start = cal.dateOf(0), finish = cal.dateOf(1)), node(2, 3, 1L to 0)), base, cal, cal.dateOf(1)
        )
        assertEquals(ActivityStatus.COMPLETED, tracked.getValue(1).status)
        assertEquals(0, tracked.getValue(1).delayDays)
        assertEquals(ActivityStatus.NOT_STARTED, tracked.getValue(2).status)
        assertEquals(cal.dateOf(2), tracked.getValue(2).forecastStart)
    }

    @Test
    fun `earned value indices`() {
        val tr = mapOf(
            1L to ActivityTracking(1, monday, monday, monday, monday, plannedPercent = 100.0, actualPercent = 100.0,
                status = ActivityStatus.COMPLETED, delayDays = 0, isCritical = false, totalFloat = 0),
            2L to ActivityTracking(2, monday, monday, monday, monday, plannedPercent = 100.0, actualPercent = 0.0,
                status = ActivityStatus.DELAYED, delayDays = 3, isCritical = true, totalFloat = 0)
        )
        val ev = ScheduleEngine.earnedValue(mapOf(1L to 1000.0, 2L to 1000.0), mapOf(1L to 1, 2L to 1), tr, actualCost = 1250.0)
        assertEquals(2000.0, ev.budgetAtCompletion, 1e-9)
        assertEquals(2000.0, ev.plannedValue, 1e-9)
        assertEquals(1000.0, ev.earnedValue, 1e-9)
        assertEquals(0.5, ev.spi!!, 1e-9)
        assertEquals(0.8, ev.cpi!!, 1e-9)
        assertEquals(1250.0 + 1000.0 / 0.8, ev.estimateAtCompletion, 1e-9)
        // no budget yet → percent from durations, no money figures, no CPI
        val ev2 = ScheduleEngine.earnedValue(emptyMap(), mapOf(1L to 3, 2L to 1), tr, 0.0)
        assertEquals(75.0, ev2.actualPercent, 1e-9)
        assertNull(ev2.cpi)
    }

    @Test
    fun `planned percent is linear over working days`() {
        val s = cal.dateOf(0); val f = cal.dateOf(3)
        assertEquals(0.0, ScheduleEngine.plannedPercentAt(s, f, s.minusDays(1), cal), 1e-9)
        assertEquals(25.0, ScheduleEngine.plannedPercentAt(s, f, s, cal), 1e-9)
        assertEquals(50.0, ScheduleEngine.plannedPercentAt(s, f, cal.dateOf(1), cal), 1e-9)
        assertEquals(100.0, ScheduleEngine.plannedPercentAt(s, f, f, cal), 1e-9)
    }

    @Test
    fun `remaining quantity in window spreads evenly`() {
        val q = ScheduleEngine.remainingQuantityInWindow(100.0, 50.0, cal.dateOf(0), cal.dateOf(9), cal.dateOf(0), cal.dateOf(4), cal)
        assertEquals(25.0, q, 1e-9)
        assertEquals(0.0, ScheduleEngine.remainingQuantityInWindow(100.0, 0.0, cal.dateOf(20), cal.dateOf(25), cal.dateOf(0), cal.dateOf(4), cal), 1e-9)
    }

    @Test
    fun `residential template is complete and consistent`() {
        for (floors in 1..5) {
            val t = ResidentialScheduleTemplate.build(floors)
            val keys = t.map { it.key }
            assertEquals("unique keys", keys.size, keys.toSet().size)
            t.forEach { a -> a.predecessors.forEach { p -> assertTrue("${a.key} → ${p.key}", p.key in keys) } }
            // every phase present, every built-in notation used
            assertEquals(7, t.map { it.phase }.toSet().size)
            val notations = t.map { it.notationCode }.filter { it.isNotBlank() }.toSet()
            assertEquals(12, notations.size)
            // shares of each notation sum to 100
            t.filter { it.notationCode.isNotBlank() }.groupBy { it.notationCode }.forEach { (code, acts) ->
                assertEquals(code, 100.0, acts.sumOf { it.sharePercent }, 1e-6)
            }
            // per-floor activities scale with floors
            assertEquals(floors, t.count { it.key.endsWith("-SLAB") })
            // the network schedules with no cycles and ends at handover
            val idx = keys.withIndex().associate { it.value to it.index.toLong() }
            val nodes = t.map { a -> ActivityNetworkInput(idx.getValue(a.key), a.defaultDurationDays, a.predecessors.map { idx.getValue(it.key) to it.lagDays }) }
            val cpm = ScheduleEngine.criticalPath(nodes, cal)
            val last = cpm.maxByOrNull { it.value.finish }!!.key
            assertEquals("EXT-HAND", keys[last.toInt()])
            assertTrue(cpm.getValue(idx.getValue("EXT-HAND")).isCritical)
        }
    }

    @Test
    fun `category rules`() {
        assertEquals(CostCategory.MEP, CategoryRules.categoryForMaterialName("Electrical"))
        assertEquals(CostCategory.TILING, CategoryRules.categoryForMaterialName("Floor Tile"))
        assertEquals(CostCategory.JOINERY, CategoryRules.categoryForMaterialName("Door"))
        assertEquals(CostCategory.PAINTING, CategoryRules.categoryForMaterialName("Internal Painting"))
        assertEquals(CostCategory.CIVIL_STRUCTURAL, CategoryRules.categoryForMaterialName("Cement"))
        assertEquals(CostCategory.CIVIL_STRUCTURAL, CategoryRules.categoryForMaterialName("Extra Labours & Machinaries"))
        assertNull(CategoryRules.categoryForMaterialName("Misc item"))
        assertEquals(CostCategory.TILING, CategoryRules.categoryForNotation("007-Tile Laying"))
        assertEquals(CostCategory.CIVIL_STRUCTURAL, CategoryRules.categoryForNotation("011-Internal Plastering"))
        assertEquals(CostCategory.MEP, CategoryRules.categoryForVendor(VendorWorkCategory.PLUMBING))
    }
}
