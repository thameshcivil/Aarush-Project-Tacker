package com.aarush.cpm.domain.schedule

import com.aarush.cpm.data.entity.ActivityStatus
import com.aarush.cpm.data.entity.CostCategory
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Minimal BOQ view the engine needs: which notation, which floor, how much. */
data class BoqQuantity(val notationCode: String, val floor: String, val quantity: Double)

/** Minimal activity view for quantity/budget allocation. */
data class ActivityQuantityInput(
    val id: Long,
    val notationCode: String,
    val floorLabel: String,
    val sharePercent: Double,
    val quantityOverride: Double?
)

/** One node of the network for CPM scheduling and tracking. */
data class ActivityNetworkInput(
    val id: Long,
    val durationDays: Int,
    /** predecessor id → lag (working days) */
    val predecessors: List<Pair<Long, Int>>,
    // tracking inputs (ignored for the baseline pass)
    val progressPercent: Double = 0.0,
    val actualStart: LocalDate? = null,
    val actualFinish: LocalDate? = null
)

data class ScheduledDates(
    val start: LocalDate,
    val finish: LocalDate,
    val totalFloat: Int,
    val isCritical: Boolean
)

data class BudgetInput(
    val id: Long,
    val category: CostCategory,
    val budgetWeight: Double,
    val budgetOverride: Double?,
    val materialCost: Double,
    val manDays: Double
)

/** Per-activity tracking figures as of a status date. */
data class ActivityTracking(
    val id: Long,
    val baselineStart: LocalDate,
    val baselineFinish: LocalDate,
    val forecastStart: LocalDate,
    val forecastFinish: LocalDate,
    val plannedPercent: Double,
    val actualPercent: Double,
    val status: ActivityStatus,
    /** Working days the forecast finish is behind the baseline finish (0 if on time). */
    val delayDays: Int,
    val isCritical: Boolean,
    val totalFloat: Int
)

data class EarnedValue(
    val budgetAtCompletion: Double,
    val plannedValue: Double,
    val earnedValue: Double,
    val actualCost: Double,
    val plannedPercent: Double,
    val actualPercent: Double,
    /** Schedule Performance Index — EV / PV. >1 ahead, <1 behind. Null if PV is 0. */
    val spi: Double?,
    /** Cost Performance Index — EV / AC. >1 under budget, <1 over. Null if AC is 0. */
    val cpi: Double?,
    val scheduleVariance: Double,
    val costVariance: Double,
    val estimateAtCompletion: Double,
    val varianceAtCompletion: Double
)

data class SCurvePoint(val date: LocalDate, val plannedPercent: Double, val actualPercent: Double?)

/**
 * Pure schedule mathematics — no Android, no Room. Every number on the Schedule tab is
 * produced here so it can be unit-tested (see ScheduleEngineTest):
 *  - BOQ → planned quantity per activity (exact floor tags first, shares of the rest)
 *  - quantity ÷ crew productivity → duration
 *  - budget → activity budgets (civil = BOQ material cost + civil labour by man-days;
 *    other categories = their % of Budget 1 split by weight)
 *  - CPM forward/backward pass → baseline dates, float, critical path
 *  - tracking pass → forecast dates from actual progress, delays, status
 *  - earned value (PV/EV/AC, SPI, CPI, EAC) and the S-curve
 */
object ScheduleEngine {

    // ---------------------------------------------------------------- quantities

    /**
     * Planned quantity for every activity from the BOQ, allocating every unit exactly once:
     *  1. BOQ items tagged with a floor go to the activities of the same notation on that
     *     floor; if several share it (e.g. a floor's columns and its slab, both RCC) the
     *     floor's quantity is split between them by their share %.
     *  2. Everything else for that notation (untagged items, or tagged with a floor no
     *     activity covers) is a shared pool split across all its activities by share %.
     *  3. A manual quantity override always wins.
     */
    fun plannedQuantities(activities: List<ActivityQuantityInput>, boq: List<BoqQuantity>): Map<Long, Double> {
        val result = mutableMapOf<Long, Double>()
        val byNotation = activities.filter { it.notationCode.isNotBlank() }.groupBy { it.notationCode }
        for ((code, acts) in byNotation) {
            val items = boq.filter { it.notationCode == code }
            val coveredFloors = acts.map { it.floorLabel }.filter { it.isNotBlank() }.toSet()
            val pool = items.filter { it.floor.isBlank() || it.floor !in coveredFloors }.sumOf { it.quantity }
            fun weight(a: ActivityQuantityInput) = a.sharePercent.coerceAtLeast(0.0)
            val shareTotal = acts.sumOf { weight(it) }
            val floorShareTotal = acts.filter { it.floorLabel.isNotBlank() }.groupBy { it.floorLabel }
                .mapValues { (_, same) -> same.sumOf { weight(it) } }
            for (a in acts) {
                val exact = if (a.floorLabel.isBlank()) 0.0 else {
                    val floorQty = items.filter { it.floor == a.floorLabel }.sumOf { it.quantity }
                    val sameFloorTotal = floorShareTotal[a.floorLabel] ?: 0.0
                    val sameFloorCount = acts.count { it.floorLabel == a.floorLabel }
                    floorQty * (if (sameFloorTotal > 0) weight(a) / sameFloorTotal else 1.0 / sameFloorCount)
                }
                val share = if (shareTotal > 0) weight(a) / shareTotal else 0.0
                result[a.id] = a.quantityOverride ?: (exact + pool * share)
            }
        }
        activities.filter { it.notationCode.isBlank() }.forEach { result[it.id] = it.quantityOverride ?: 0.0 }
        return result
    }

    /** Working days needed: quantity ÷ crew output per day, rounded up, at least 1. */
    fun durationFromQuantity(quantity: Double, productivityPerDay: Double, fallbackDays: Int): Int =
        if (productivityPerDay > 0 && quantity > 0) max(1, ceil(quantity / productivityPerDay).toInt())
        else max(1, fallbackDays)

    // ---------------------------------------------------------------- budgets

    /**
     * Distributes the cost budget over activities:
     *  - CIVIL_STRUCTURAL: the activity's own BOQ material cost + a share of the civil labour
     *    contracts proportional to its man-days (duration × crew size).
     *  - Every other category: that category's pool (its % of Budget 1) split by budgetWeight.
     *  - A manual budget override always wins.
     * The total therefore reconciles with Budget 2 when every category has activities.
     */
    fun allocateBudgets(
        inputs: List<BudgetInput>,
        civilLabourPool: Double,
        categoryPools: Map<CostCategory, Double>
    ): Map<Long, Double> {
        val result = mutableMapOf<Long, Double>()
        val civil = inputs.filter { it.category == CostCategory.CIVIL_STRUCTURAL }
        val civilManDays = civil.filter { it.budgetOverride == null }.sumOf { it.manDays.coerceAtLeast(0.0) }
        for (a in civil) {
            result[a.id] = a.budgetOverride ?: (a.materialCost +
                if (civilManDays > 0) civilLabourPool * a.manDays.coerceAtLeast(0.0) / civilManDays else 0.0)
        }
        inputs.filter { it.category != CostCategory.CIVIL_STRUCTURAL }.groupBy { it.category }.forEach { (cat, acts) ->
            val pool = categoryPools[cat] ?: 0.0
            val weightTotal = acts.filter { it.budgetOverride == null }.sumOf { it.budgetWeight.coerceAtLeast(0.0) }
            for (a in acts) {
                result[a.id] = a.budgetOverride ?: if (weightTotal > 0) pool * a.budgetWeight.coerceAtLeast(0.0) / weightTotal else 0.0
            }
        }
        return result
    }

    // ---------------------------------------------------------------- CPM

    /** Ids in dependency order (Kahn). Activities caught in a cycle are appended last and
     *  their offending links ignored, so a bad edit can never hang or crash scheduling. */
    fun topologicalOrder(nodes: List<ActivityNetworkInput>): List<Long> {
        val ids = nodes.map { it.id }.toSet()
        val preds = nodes.associate { n -> n.id to n.predecessors.map { it.first }.filter { it in ids && it != n.id }.toSet() }
        val remaining = preds.mapValues { it.value.toMutableSet() }.toMutableMap()
        val order = mutableListOf<Long>()
        val originalIndex = nodes.mapIndexed { i, n -> n.id to i }.toMap()
        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { it.value.isEmpty() }.keys.sortedBy { originalIndex[it] }
            if (ready.isEmpty()) {
                // cycle: release the earliest remaining node
                val forced = remaining.keys.minByOrNull { originalIndex[it] ?: 0 }!!
                order += forced; remaining.remove(forced)
                remaining.values.forEach { it.remove(forced) }
                continue
            }
            for (id in ready) {
                order += id; remaining.remove(id)
                remaining.values.forEach { it.remove(id) }
            }
        }
        return order
    }

    /** True if making [activityId] depend on [newPredecessorId] would create a loop. */
    fun wouldCreateCycle(dependencies: List<Pair<Long, Long>>, activityId: Long, newPredecessorId: Long): Boolean {
        if (activityId == newPredecessorId) return true
        // A cycle appears if activityId is already (transitively) a predecessor of newPredecessorId.
        val predsOf = dependencies.groupBy({ it.first }, { it.second })
        val seen = mutableSetOf<Long>()
        val stack = ArrayDeque<Long>().apply { add(newPredecessorId) }
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (cur == activityId) return true
            if (seen.add(cur)) predsOf[cur]?.forEach { stack.add(it) }
        }
        return false
    }

    /**
     * Baseline critical-path schedule from [projectStart]. Finish-to-start links with lag.
     * Returns early start/finish dates, total float (working days) and critical flag.
     */
    fun criticalPath(nodes: List<ActivityNetworkInput>, calendar: WorkCalendar): Map<Long, ScheduledDates> {
        if (nodes.isEmpty()) return emptyMap()
        val byId = nodes.associateBy { it.id }
        val order = topologicalOrder(nodes)
        val position = order.withIndex().associate { it.value to it.index }
        val es = mutableMapOf<Long, Int>()
        val ef = mutableMapOf<Long, Int>() // exclusive finish index
        for (id in order) {
            val n = byId.getValue(id)
            val start = n.predecessors
                .filter { (p, _) -> p in ef && (position[p] ?: 0) < (position[id] ?: 0) }
                .maxOfOrNull { (p, lag) -> ef.getValue(p) + lag } ?: 0
            es[id] = max(0, start)
            ef[id] = es.getValue(id) + max(1, n.durationDays)
        }
        val projectFinish = ef.values.maxOrNull() ?: 0
        val successors = mutableMapOf<Long, MutableList<Pair<Long, Int>>>()
        nodes.forEach { n -> n.predecessors.forEach { (p, lag) ->
            if (p in byId && (position[p] ?: 0) < (position[n.id] ?: 0)) successors.getOrPut(p) { mutableListOf() } += n.id to lag
        } }
        val lf = mutableMapOf<Long, Int>()
        val ls = mutableMapOf<Long, Int>()
        for (id in order.asReversed()) {
            val n = byId.getValue(id)
            val finish = successors[id]?.minOfOrNull { (s, lag) -> ls.getValue(s) - lag } ?: projectFinish
            lf[id] = finish
            ls[id] = finish - max(1, n.durationDays)
        }
        return order.associateWith { id ->
            val float = ls.getValue(id) - es.getValue(id)
            ScheduledDates(
                start = calendar.dateOf(es.getValue(id)),
                finish = calendar.dateOf(ef.getValue(id) - 1),
                totalFloat = float,
                isCritical = float <= 0
            )
        }
    }

    // ---------------------------------------------------------------- tracking

    /** Share of the baseline that should be done by [asOf], linear over working days. */
    fun plannedPercentAt(start: LocalDate, finish: LocalDate, asOf: LocalDate, calendar: WorkCalendar): Double {
        if (asOf.isBefore(start)) return 0.0
        if (!asOf.isBefore(finish)) return 100.0
        val total = calendar.indexOf(finish) - calendar.indexOf(start) + 1
        val done = calendar.indexOf(asOf) - calendar.indexOf(start) + (if (calendar.isWorkingDay(asOf)) 1 else 0)
        return if (total <= 0) 100.0 else (done.toDouble() / total * 100.0).coerceIn(0.0, 100.0)
    }

    fun actualPercent(plannedQuantity: Double, completedQuantity: Double, manualPercent: Double): Double =
        if (plannedQuantity > 0) min(100.0, completedQuantity / plannedQuantity * 100.0).coerceAtLeast(0.0)
        else manualPercent.coerceIn(0.0, 100.0)

    /**
     * Forecast pass: re-runs the network from [today] using actual progress —
     * completed activities keep their actual dates, in-progress ones finish after their
     * remaining share of duration, and nothing unstarted can begin before today.
     * Compared with the baseline this yields each activity's delay and status.
     */
    fun track(
        nodes: List<ActivityNetworkInput>,
        baseline: Map<Long, Pair<LocalDate, LocalDate>>,
        calendar: WorkCalendar,
        today: LocalDate
    ): Map<Long, ActivityTracking> {
        if (nodes.isEmpty()) return emptyMap()
        val byId = nodes.associateBy { it.id }
        val order = topologicalOrder(nodes)
        val position = order.withIndex().associate { it.value to it.index }
        val t = calendar.indexOf(today)
        val fs = mutableMapOf<Long, Int>()
        val ff = mutableMapOf<Long, Int>() // inclusive finish index
        for (id in order) {
            val n = byId.getValue(id)
            val dur = max(1, n.durationDays)
            val predReady = n.predecessors
                .filter { (p, _) -> p in ff && (position[p] ?: 0) < (position[id] ?: 0) }
                .maxOfOrNull { (p, lag) -> ff.getValue(p) + 1 + lag }
            val baseStart = baseline[id]?.first?.let { calendar.indexOf(it) } ?: 0
            when {
                n.progressPercent >= 100.0 -> {
                    val finish = n.actualFinish?.let { calendar.indexOf(it) } ?: min(t, baseStart + dur - 1)
                    val start = n.actualStart?.let { calendar.indexOf(it) } ?: (finish - dur + 1)
                    fs[id] = min(start, finish); ff[id] = finish
                }
                n.progressPercent > 0.0 || n.actualStart != null -> {
                    val start = n.actualStart?.let { calendar.indexOf(it) } ?: t
                    val remaining = max(1, ceil(dur * (1.0 - n.progressPercent / 100.0)).toInt())
                    fs[id] = start; ff[id] = max(t, start) + remaining - 1
                }
                else -> {
                    // Not started: no earlier than today, its baseline start, or its
                    // predecessors' forecast finish (+lag) — whichever is latest.
                    val start = maxOf(t, baseStart, predReady ?: Int.MIN_VALUE)
                    fs[id] = start; ff[id] = start + dur - 1
                }
            }
        }
        // float on the forecast network for critical flags
        val tmpNodes = nodes.map { n -> n.copy(durationDays = (ff.getValue(n.id) - fs.getValue(n.id) + 1).coerceAtLeast(1)) }
        val cpm = criticalPath(tmpNodes, calendar)

        return order.associateWith { id ->
            val n = byId.getValue(id)
            val (bStart, bFinish) = baseline[id] ?: (calendar.dateOf(fs.getValue(id)) to calendar.dateOf(ff.getValue(id)))
            val fStart = calendar.dateOf(fs.getValue(id))
            val fFinish = calendar.dateOf(ff.getValue(id))
            val delay = max(0, ff.getValue(id) - calendar.indexOf(bFinish))
            val status = when {
                n.progressPercent >= 100.0 -> ActivityStatus.COMPLETED
                n.progressPercent > 0.0 || n.actualStart != null ->
                    if (delay > 0) ActivityStatus.DELAYED else ActivityStatus.IN_PROGRESS
                today.isAfter(bStart) -> ActivityStatus.DELAYED
                else -> ActivityStatus.NOT_STARTED
            }
            ActivityTracking(
                id = id,
                baselineStart = bStart, baselineFinish = bFinish,
                forecastStart = fStart, forecastFinish = fFinish,
                plannedPercent = plannedPercentAt(bStart, bFinish, today, calendar),
                actualPercent = n.progressPercent.coerceIn(0.0, 100.0),
                status = status,
                delayDays = if (status == ActivityStatus.COMPLETED) max(0, calendar.indexOf(fFinish) - calendar.indexOf(bFinish)) else delay,
                isCritical = n.progressPercent < 100.0 && (cpm[id]?.isCritical ?: false),
                totalFloat = cpm[id]?.totalFloat ?: 0
            )
        }
    }

    // ---------------------------------------------------------------- earned value

    /**
     * Earned-value summary. Weights are activity budgets; if no activity has a budget yet
     * (e.g. BOQ and budget still empty), durations are used as weights so the % figures
     * are still meaningful (money figures are then 0).
     */
    fun earnedValue(
        budgets: Map<Long, Double>,
        durations: Map<Long, Int>,
        tracking: Map<Long, ActivityTracking>,
        actualCost: Double
    ): EarnedValue {
        val bac = budgets.values.sum()
        val weights: Map<Long, Double> = if (bac > 0) budgets else durations.mapValues { it.value.toDouble() }
        val totalWeight = weights.values.sum()
        var pvW = 0.0; var evW = 0.0
        tracking.values.forEach { tr ->
            val w = weights[tr.id] ?: 0.0
            pvW += w * tr.plannedPercent / 100.0
            evW += w * tr.actualPercent / 100.0
        }
        val plannedPct = if (totalWeight > 0) pvW / totalWeight * 100.0 else 0.0
        val actualPct = if (totalWeight > 0) evW / totalWeight * 100.0 else 0.0
        val pv = if (bac > 0) pvW else 0.0
        val ev = if (bac > 0) evW else 0.0
        val spi = if (plannedPct > 0) actualPct / plannedPct else null
        val cpi = if (actualCost > 0 && ev > 0) ev / actualCost else null
        val eac = when {
            bac <= 0 -> actualCost
            cpi != null && cpi > 0 -> actualCost + (bac - ev) / cpi
            else -> max(bac, actualCost)
        }
        return EarnedValue(
            budgetAtCompletion = bac, plannedValue = pv, earnedValue = ev, actualCost = actualCost,
            plannedPercent = plannedPct, actualPercent = actualPct, spi = spi, cpi = cpi,
            scheduleVariance = ev - pv, costVariance = ev - actualCost,
            estimateAtCompletion = eac, varianceAtCompletion = bac - eac
        )
    }

    /**
     * Weekly S-curve: cumulative planned % (from baseline) and actual % (from dated progress
     * history) — actual stops at [today]. [actualPercentAt] returns an activity's actual %
     * as of a date (built from its progress logs by the caller).
     */
    fun sCurve(
        weights: Map<Long, Double>,
        baseline: Map<Long, Pair<LocalDate, LocalDate>>,
        calendar: WorkCalendar,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
        actualPercentAt: (Long, LocalDate) -> Double
    ): List<SCurvePoint> {
        val total = weights.values.sum()
        if (total <= 0 || baseline.isEmpty()) return emptyList()
        val points = mutableListOf<SCurvePoint>()
        var d = from
        var guard = 0
        while (!d.isAfter(to) && guard < 400) {
            val planned = baseline.entries.sumOf { (id, se) ->
                (weights[id] ?: 0.0) * plannedPercentAt(se.first, se.second, d, calendar) / 100.0
            } / total * 100.0
            val actual = if (d.isAfter(today)) null else
                baseline.keys.sumOf { id -> (weights[id] ?: 0.0) * actualPercentAt(id, d) / 100.0 } / total * 100.0
            points += SCurvePoint(d, planned, actual)
            if (d == to) break
            d = d.plusWeeks(1).let { if (it.isAfter(to)) to else it }
            guard++
        }
        return points
    }

    /** Remaining quantity of an activity that falls inside [windowStart, windowEnd],
     *  assuming the remaining work is spread evenly over its forecast dates. */
    fun remainingQuantityInWindow(
        plannedQuantity: Double, actualPercent: Double,
        forecastStart: LocalDate, forecastFinish: LocalDate,
        windowStart: LocalDate, windowEnd: LocalDate, calendar: WorkCalendar
    ): Double {
        val remaining = plannedQuantity * (1.0 - actualPercent / 100.0)
        if (remaining <= 0) return 0.0
        val s = maxOf(forecastStart, windowStart)
        val e = minOf(forecastFinish, windowEnd)
        if (e.isBefore(s)) return 0.0
        val effectiveStart = maxOf(forecastStart, windowStart)
        val totalDays = calendar.indexOf(forecastFinish) - calendar.indexOf(effectiveStart) + 1
        val overlap = calendar.indexOf(e) - calendar.indexOf(s) + 1
        return if (totalDays <= 0) remaining else remaining * (overlap.toDouble() / totalDays).coerceIn(0.0, 1.0)
    }
}
