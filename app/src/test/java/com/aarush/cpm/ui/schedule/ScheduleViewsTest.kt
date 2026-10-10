package com.aarush.cpm.ui.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Redesign Phase 4: the floor grouping is display-only and must not drop, duplicate or
 *  reorder activities within a floor. Uses the demo project's real schedule. */
class ScheduleViewsTest {
    private val acts = DemoSchedule.activities
    private val floors = listOf("Foundation", "Plinth", "Ground Floor", "First Floor", "Terrace")

    @Test fun everyActivityAppearsExactlyOnce() {
        val grouped = activitiesByFloor(acts, floors).flatMap { it.second }
        assertEquals(acts.size, grouped.size)
        assertEquals(acts.map { it.activity.id }.toSet(), grouped.map { it.activity.id }.toSet())
    }

    @Test fun scheduleOrderKeptInsideEachFloor() {
        activitiesByFloor(acts, floors).forEach { (_, list) ->
            val positions = list.map { v -> acts.indexOf(v) }
            assertEquals(positions.sorted(), positions)
        }
    }

    @Test fun floorsInBuildingOrderWholeBuildingFirst() {
        val order = activitiesByFloor(acts, floors).map { it.first }
        assertEquals(WHOLE_BUILDING, order.first())
        val known = order.filter { it in floors }
        assertEquals(known.sortedBy { floors.indexOf(it) }, known)
        assertTrue("demo has GF and 1F groups", "Ground Floor" in order && "First Floor" in order)
    }

    @Test fun groupedActivitiesKeepTheirTrackingUntouched() {
        val byId = acts.associateBy { it.activity.id }
        activitiesByFloor(acts, floors).flatMap { it.second }.forEach { v -> assertTrue(byId[v.activity.id] === v) }
    }
}
