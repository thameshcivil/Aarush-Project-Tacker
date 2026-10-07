package com.aarush.cpm.data.repository

import androidx.room.withTransaction
import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.entity.*
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.domain.schedule.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import kotlin.math.max

/** Everything the Schedule tab shows for one activity. */
data class ScheduleActivityView(
    val activity: ScheduleActivity,
    val plannedQuantity: Double,
    val budget: Double,
    val materialCost: Double,
    val tracking: ActivityTracking,
    val predecessors: List<ScheduleDependency>,
    val earnedValue: Double,
    val plannedValue: Double,
    val logs: List<ActivityProgressLog>
)

data class PhaseSummary(
    val phase: String,
    val activityCount: Int,
    val completedCount: Int,
    val plannedPercent: Double,
    val actualPercent: Double,
    val budget: Double,
    val start: LocalDate,
    val finish: LocalDate,
    val forecastFinish: LocalDate
)

data class LabourNeed(val skill: String, val workers: Int, val activities: Int)

data class MaterialNeed(
    val material: String,
    val unit: String,
    val required: Double,
    val inStock: Double,
    val shortage: Double
)

data class ScheduleAlert(val type: NotificationType, val message: String, val isCritical: Boolean)

/** Read model for the whole Schedule tab (and the schedule cards on Home). */
data class ScheduleReport(
    val hasSchedule: Boolean,
    val today: LocalDate,
    val sundayOff: Boolean,
    val activities: List<ScheduleActivityView> = emptyList(),
    val phases: List<PhaseSummary> = emptyList(),
    val earnedValue: EarnedValue? = null,
    val baselineStart: LocalDate? = null,
    val baselineFinish: LocalDate? = null,
    val forecastFinish: LocalDate? = null,
    /** Working days the forecast finish is behind the baseline finish (negative = ahead). */
    val projectDelayDays: Int = 0,
    val criticalPath: List<ScheduleActivityView> = emptyList(),
    val sCurve: List<SCurvePoint> = emptyList(),
    val lookaheadStart: LocalDate = today,
    val lookaheadEnd: LocalDate = today,
    val upcoming: List<ScheduleActivityView> = emptyList(),
    val labourNeeds: List<LabourNeed> = emptyList(),
    val materialNeeds: List<MaterialNeed> = emptyList(),
    val alerts: List<ScheduleAlert> = emptyList(),
    val budget2: Double = 0.0,
    val scheduledBudget: Double = 0.0,
    val unscheduledCategories: List<CostCategory> = emptyList(),
    val completedCount: Int = 0,
    val inProgressCount: Int = 0,
    val delayedCount: Int = 0,
    val notStartedCount: Int = 0,
    val recentLogs: List<Pair<ActivityProgressLog, String>> = emptyList(),
    /** Total expenses and the value of unused material in store (actual cost = spent − stock). */
    val totalSpent: Double = 0.0,
    val stockValue: Double = 0.0
)

/** Input bundle for adding/editing an activity from the UI. */
data class ActivityInput(
    val name: String,
    val phase: String,
    val floorLabel: String,
    val category: CostCategory,
    val notationCode: String,
    val sharePercent: Double,
    val quantityOverride: Double?,
    val unit: String,
    val durationDays: Int,
    val isDurationManual: Boolean,
    val productivityPerDay: Double,
    val labourSkill: String,
    val labourCount: Int,
    val budgetWeight: Double,
    val budgetOverride: Double?,
    val vendorId: Long?,
    val isMilestone: Boolean,
    val notes: String,
    /** predecessor activity id → lag days */
    val predecessors: List<Pair<Long, Int>>
)

/**
 * Construction schedule & tracking, driven by the BOQ and the budget:
 *  - [generateFromTemplate] lays out the standard residential sequence for the project's
 *    floors, takes quantities from the BOQ, durations from crew productivity, and computes a
 *    critical-path baseline from the chosen start date.
 *  - [logProgress] records site progress; each log also books material consumption into
 *    stock (quantity × coefficients × waste) and snapshots overall progress for the S-curve.
 *  - [buildReport] produces baseline vs forecast, delays, critical path, earned value
 *    (PV/EV/AC, SPI, CPI, EAC), look-ahead labour & material needs, and alerts.
 */
class ScheduleRepository(private val db: AppDatabase) {

    private val materialRepository = MaterialRepository(db)

    fun observeActivities(projectId: Long): Flow<List<ScheduleActivity>> = db.scheduleActivityDao().observeForProject(projectId)
    fun observeLogs(projectId: Long): Flow<List<ActivityProgressLog>> = db.activityProgressLogDao().observeForProject(projectId)
    fun observeDependencies(projectId: Long): Flow<List<ScheduleDependency>> = db.scheduleDependencyDao().observeForProject(projectId)

    // Legacy data-layer API kept for compatibility.
    suspend fun addActivity(activity: ScheduleActivity): Long = db.scheduleActivityDao().insert(activity)
    suspend fun updateActivity(activity: ScheduleActivity) = db.scheduleActivityDao().update(activity)
    fun observeLabourRates(projectId: Long): Flow<List<LabourRate>> = db.labourRateDao().observeForProject(projectId)
    suspend fun setLabourRates(rates: List<LabourRate>) = db.labourRateDao().insertAll(rates)
    fun observeProgress(projectId: Long): Flow<List<ProjectProgress>> = db.projectProgressDao().observeForProject(projectId)
    suspend fun addProgress(progress: ProjectProgress): Long = db.projectProgressDao().insert(progress)

    // ------------------------------------------------------------------ settings

    private fun sundayKey(projectId: Long) = "schedule.sundayOff.$projectId"

    suspend fun isSundayOff(projectId: Long): Boolean =
        db.appSettingsDao().get(sundayKey(projectId))?.value?.toBooleanStrictOrNull() ?: true

    suspend fun setSundayOff(projectId: Long, value: Boolean) =
        db.appSettingsDao().set(AppSettings(sundayKey(projectId), value.toString()))

    private suspend fun calendarFor(project: Project, sundayOff: Boolean): WorkCalendar {
        val activities = db.scheduleActivityDao().getForProject(project.id)
        // Ignore rows not yet scheduled (plannedStart = 0) — e.g. an activity being added.
        val origin = activities.filter { it.plannedStart > 0 }.minOfOrNull { it.plannedStart }?.let { WorkCalendar.toLocalDate(it) }
            ?: WorkCalendar.toLocalDate(project.startDate)
        return WorkCalendar(origin, sundayOff)
    }

    // ------------------------------------------------------------------ generation

    /**
     * Creates the full residential schedule for the project (replacing any existing one,
     * including its progress logs and their auto-booked material consumption).
     */
    suspend fun generateFromTemplate(projectId: Long, startDate: LocalDate, numberOfFloors: Int, sundayOff: Boolean) =
        db.withTransaction {
            clearSchedule(projectId)
            setSundayOff(projectId, sundayOff)
            val notations = db.boqNotationDao().getForProject(projectId).associateBy { it.code }
            val template = ResidentialScheduleTemplate.build(numberOfFloors)
            val boq = db.boqDao().getForProject(projectId).map { BoqQuantity(it.materialType, it.floor, it.quantity) }

            // Pre-compute quantities using template keys as temporary ids.
            val tempIds = template.mapIndexed { i, t -> t.key to (i + 1).toLong() }.toMap()
            val qty = ScheduleEngine.plannedQuantities(
                template.map { ActivityQuantityInput(tempIds.getValue(it.key), it.notationCode, it.floorLabel, it.sharePercent, null) },
                boq
            )
            val idByKey = mutableMapOf<String, Long>()
            template.forEachIndexed { index, t ->
                val q = qty[tempIds.getValue(t.key)] ?: 0.0
                val id = db.scheduleActivityDao().insert(
                    ScheduleActivity(
                        projectId = projectId,
                        activityName = t.name,
                        plannedStart = 0, plannedEnd = 0,
                        plannedQuantity = q,
                        unit = notations[t.notationCode]?.defaultUnit ?: "",
                        requiredLabourSkill = t.labourSkill,
                        requiredLabourCount = t.labourCount,
                        phase = t.phase,
                        sequence = index,
                        floorLabel = t.floorLabel,
                        templateKey = t.key,
                        category = t.category,
                        linkedNotationCode = t.notationCode,
                        boqSharePercent = t.sharePercent,
                        durationDays = ScheduleEngine.durationFromQuantity(q, t.productivityPerDay, t.defaultDurationDays),
                        productivityPerDay = t.productivityPerDay,
                        budgetWeight = t.budgetWeight,
                        isMilestone = t.isMilestone
                    )
                )
                idByKey[t.key] = id
            }
            db.scheduleDependencyDao().insertAll(template.flatMap { t ->
                t.predecessors.mapNotNull { p ->
                    idByKey[p.key]?.let { predId -> ScheduleDependency(activityId = idByKey.getValue(t.key), dependsOnActivityId = predId, lagDays = p.lagDays) }
                }
            })
            rebaselineInternal(projectId, startDate, sundayOff)
        }

    private suspend fun clearSchedule(projectId: Long) {
        db.activityProgressLogDao().getForProject(projectId).forEach { db.materialUsageDao().deleteForProgressLog(it.id) }
        db.scheduleDependencyDao().deleteForProject(projectId)
        db.activityProgressLogDao().deleteForProject(projectId)
        db.scheduleActivityDao().deleteForProject(projectId)
        db.projectProgressDao().deleteForProject(projectId)
    }

    suspend fun deleteSchedule(projectId: Long) = db.withTransaction { clearSchedule(projectId) }

    /** Recomputes the baseline (planned dates) by critical path from [startDate], and sets
     *  the project's expected completion to the new baseline finish. */
    suspend fun rebaseline(projectId: Long, startDate: LocalDate, sundayOff: Boolean) = db.withTransaction {
        setSundayOff(projectId, sundayOff)
        rebaselineInternal(projectId, startDate, sundayOff)
    }

    private suspend fun rebaselineInternal(projectId: Long, startDate: LocalDate, sundayOff: Boolean) {
        val activities = db.scheduleActivityDao().getForProject(projectId)
        if (activities.isEmpty()) return
        val deps = db.scheduleDependencyDao().getForProject(projectId)
        val calendar = WorkCalendar(startDate, sundayOff)
        val cpm = ScheduleEngine.criticalPath(networkNodes(activities, deps, withProgress = false), calendar)
        db.scheduleActivityDao().updateAll(activities.map { a ->
            val d = cpm.getValue(a.id)
            a.copy(plannedStart = WorkCalendar.toEpochMillis(d.start), plannedEnd = WorkCalendar.toEpochMillis(d.finish))
        })
        val finish = cpm.values.maxOf { it.finish }
        db.projectDao().getById(projectId)?.let { p ->
            db.projectDao().update(p.copy(
                startDate = WorkCalendar.toEpochMillis(startDate),
                expectedCompletionDate = WorkCalendar.toEpochMillis(finish),
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    /**
     * Pulls the latest BOQ quantities into every activity and recomputes durations for
     * activities whose duration isn't manually locked. If no progress has been logged yet the
     * baseline is re-planned too; once work has started the baseline is kept (so delays stay
     * measurable) and only the forecast moves.
     */
    suspend fun syncFromBoq(projectId: Long) = db.withTransaction {
        val activities = db.scheduleActivityDao().getForProject(projectId)
        if (activities.isEmpty()) return@withTransaction
        val qty = derivedQuantities(projectId, activities)
        val notations = db.boqNotationDao().getForProject(projectId).associateBy { it.code }
        db.scheduleActivityDao().updateAll(activities.map { a ->
            val q = qty[a.id] ?: 0.0
            val unit = if (a.linkedNotationCode.isNotBlank()) notations[a.linkedNotationCode]?.defaultUnit ?: a.unit else a.unit
            val duration = if (a.isDurationManual) a.durationDays else ScheduleEngine.durationFromQuantity(q, a.productivityPerDay, a.durationDays)
            a.copy(plannedQuantity = q, unit = unit, durationDays = duration)
        })
        activities.forEach { recomputeActivityProgress(it.id) }
        if (db.activityProgressLogDao().getForProject(projectId).isEmpty()) {
            val start = activities.filter { it.plannedStart > 0 }.minOfOrNull { it.plannedStart }?.let { WorkCalendar.toLocalDate(it) }
                ?: db.projectDao().getById(projectId)?.startDate?.let { WorkCalendar.toLocalDate(it) } ?: LocalDate.now()
            rebaselineInternal(projectId, start, isSundayOff(projectId))
        }
    }

    // ------------------------------------------------------------------ editing

    suspend fun saveActivity(projectId: Long, existing: ScheduleActivity?, input: ActivityInput): Long = db.withTransaction {
        val all = db.scheduleActivityDao().getForProject(projectId)
        val base = existing ?: ScheduleActivity(
            projectId = projectId, activityName = input.name, plannedStart = 0, plannedEnd = 0,
            plannedQuantity = 0.0, unit = input.unit, sequence = (all.maxOfOrNull { it.sequence } ?: 0) + 1
        )
        var updated = base.copy(
            activityName = input.name, phase = input.phase, floorLabel = input.floorLabel, category = input.category,
            linkedNotationCode = input.notationCode, boqSharePercent = input.sharePercent,
            quantityOverride = input.quantityOverride, unit = input.unit, durationDays = max(1, input.durationDays),
            isDurationManual = input.isDurationManual, productivityPerDay = input.productivityPerDay,
            requiredLabourSkill = input.labourSkill, requiredLabourCount = input.labourCount,
            budgetWeight = input.budgetWeight, budgetOverride = input.budgetOverride, vendorId = input.vendorId,
            isMilestone = input.isMilestone, notes = input.notes
        )
        val id = if (existing == null) db.scheduleActivityDao().insert(updated) else { db.scheduleActivityDao().update(updated); updated.id }
        updated = updated.copy(id = id)

        // Quantity + duration from BOQ unless locked.
        val refreshed = db.scheduleActivityDao().getForProject(projectId)
        val q = derivedQuantities(projectId, refreshed)[id] ?: 0.0
        val duration = if (updated.isDurationManual) updated.durationDays
            else ScheduleEngine.durationFromQuantity(q, updated.productivityPerDay, updated.durationDays)
        updated = updated.copy(plannedQuantity = q, durationDays = duration)

        // Predecessors (cycle-safe).
        db.scheduleDependencyDao().deletePredecessorsOf(id)
        val otherDeps = db.scheduleDependencyDao().getForProject(projectId).map { it.activityId to it.dependsOnActivityId }
        val accepted = mutableListOf<Pair<Long, Long>>()
        input.predecessors.distinctBy { it.first }.forEach { (predId, lag) ->
            if (!ScheduleEngine.wouldCreateCycle(otherDeps + accepted, id, predId)) {
                db.scheduleDependencyDao().insert(ScheduleDependency(activityId = id, dependsOnActivityId = predId, lagDays = lag))
                accepted += id to predId
            }
        }

        // A new activity gets baseline dates from the network; an edited one keeps its baseline.
        if (existing == null || updated.plannedStart == 0L) {
            val project = db.projectDao().getById(projectId)!!
            val sundayOff = isSundayOff(projectId)
            val calendar = calendarFor(project, sundayOff)
            val activities = db.scheduleActivityDao().getForProject(projectId).map { if (it.id == id) updated else it }
            val cpm = ScheduleEngine.criticalPath(networkNodes(activities, db.scheduleDependencyDao().getForProject(projectId), false), calendar)
            cpm[id]?.let { updated = updated.copy(plannedStart = WorkCalendar.toEpochMillis(it.start), plannedEnd = WorkCalendar.toEpochMillis(it.finish)) }
        }
        db.scheduleActivityDao().update(updated)
        recomputeActivityProgress(id)
        id
    }

    suspend fun deleteActivity(activity: ScheduleActivity) = db.withTransaction {
        db.activityProgressLogDao().getForActivity(activity.id).forEach { db.materialUsageDao().deleteForProgressLog(it.id) }
        db.activityProgressLogDao().deleteForActivity(activity.id)
        db.scheduleDependencyDao().deleteForActivity(activity.id)
        db.scheduleActivityDao().delete(activity)
        snapshotOverallProgress(activity.projectId, LocalDate.now())
    }

    // ------------------------------------------------------------------ progress

    /** Logs site progress. [quantityDone] for BOQ-linked activities (that day's executed
     *  quantity); [percentComplete] (cumulative) for activities without a quantity. */
    suspend fun logProgress(
        activityId: Long, date: LocalDate, quantityDone: Double, percentComplete: Double?, labourCount: Int, notes: String
    ): Long = db.withTransaction {
        val activity = db.scheduleActivityDao().getById(activityId) ?: return@withTransaction 0L
        val logId = db.activityProgressLogDao().insert(
            ActivityProgressLog(
                projectId = activity.projectId, activityId = activityId, date = WorkCalendar.toEpochMillis(date),
                quantityDone = quantityDone.coerceAtLeast(0.0), percentComplete = percentComplete?.coerceIn(0.0, 100.0),
                labourCount = labourCount.coerceAtLeast(0), notes = notes
            )
        )
        bookConsumption(activity, logId, date, quantityDone)
        recomputeActivityProgress(activityId)
        snapshotOverallProgress(activity.projectId, date)
        logId
    }

    suspend fun updateLog(log: ActivityProgressLog) = db.withTransaction {
        db.activityProgressLogDao().update(log)
        db.materialUsageDao().deleteForProgressLog(log.id)
        db.scheduleActivityDao().getById(log.activityId)?.let {
            bookConsumption(it, log.id, WorkCalendar.toLocalDate(log.date), log.quantityDone)
        }
        recomputeActivityProgress(log.activityId)
        snapshotOverallProgress(log.projectId, WorkCalendar.toLocalDate(log.date))
    }

    suspend fun deleteLog(log: ActivityProgressLog) = db.withTransaction {
        db.materialUsageDao().deleteForProgressLog(log.id)
        db.activityProgressLogDao().delete(log)
        recomputeActivityProgress(log.activityId)
        snapshotOverallProgress(log.projectId, LocalDate.now())
    }

    /** Material actually consumed by this much work: quantity × coefficient × (1 + waste). */
    private suspend fun bookConsumption(activity: ScheduleActivity, logId: Long, date: LocalDate, quantityDone: Double) {
        if (quantityDone <= 0 || activity.linkedNotationCode.isBlank()) return
        val perUnit = materialRepository.materialPerUnitByNotation(activity.projectId)[activity.linkedNotationCode] ?: return
        db.materialUsageDao().insertAll(perUnit.filter { it.value > 0 }.map { (material, perQty) ->
            MaterialUsage(
                projectId = activity.projectId, materialName = material, date = WorkCalendar.toEpochMillis(date),
                quantityUsed = quantityDone * perQty, notes = "Auto: ${activity.activityName}", progressLogId = logId
            )
        })
    }

    private suspend fun recomputeActivityProgress(activityId: Long) {
        val a = db.scheduleActivityDao().getById(activityId) ?: return
        val logs = db.activityProgressLogDao().getForActivity(activityId).sortedWith(compareBy({ it.date }, { it.id }))
        val completedQty = logs.sumOf { it.quantityDone }
        val percent = progressFromLogs(a.plannedQuantity, logs)
        val actualStart = logs.firstOrNull { it.quantityDone > 0 || (it.percentComplete ?: 0.0) > 0 }?.date
        var completion: Long? = null
        if (percent >= 100.0) {
            for (i in logs.indices) {
                if (progressFromLogs(a.plannedQuantity, logs.subList(0, i + 1)) >= 100.0) { completion = logs[i].date; break }
            }
        }
        db.scheduleActivityDao().update(a.copy(
            completedQuantity = completedQty,
            progressPercent = percent,
            actualStart = actualStart,
            actualCompletion = completion,
            status = when {
                percent >= 100.0 -> ActivityStatus.COMPLETED
                actualStart != null -> ActivityStatus.IN_PROGRESS
                else -> ActivityStatus.NOT_STARTED
            }
        ))
        updateProjectStatus(a.projectId)
    }

    /** % complete from logs: the higher of executed-quantity share and the latest
     *  cumulative % entry (so "Mark complete" works for quantity activities too). */
    private fun progressFromLogs(plannedQuantity: Double, logs: List<ActivityProgressLog>): Double {
        val qtyPct = if (plannedQuantity > 0) logs.sumOf { it.quantityDone } / plannedQuantity * 100.0 else 0.0
        val lastPct = logs.lastOrNull { it.percentComplete != null }?.percentComplete ?: 0.0
        return max(qtyPct, lastPct).coerceIn(0.0, 100.0)
    }

    private suspend fun updateProjectStatus(projectId: Long) {
        val project = db.projectDao().getById(projectId) ?: return
        val activities = db.scheduleActivityDao().getForProject(projectId)
        if (activities.isEmpty()) return
        val newStatus = when {
            activities.all { it.progressPercent >= 100.0 } -> ProjectStatus.COMPLETED
            activities.any { it.progressPercent > 0.0 } &&
                project.status in setOf(ProjectStatus.PLANNING, ProjectStatus.STARTED, ProjectStatus.COMPLETED) -> ProjectStatus.IN_PROGRESS
            else -> project.status
        }
        if (newStatus != project.status) db.projectDao().update(project.copy(status = newStatus, updatedAt = System.currentTimeMillis()))
    }

    /** Writes today's overall progress (budget-weighted) to ProjectProgress — feeds the
     *  project cards, Home progress, and the historic actual line of the S-curve. */
    private suspend fun snapshotOverallProgress(projectId: Long, @Suppress("UNUSED_PARAMETER") changedDate: LocalDate) {
        // The % is computed as of today, so the snapshot is dated today (dating it with a
        // backdated log's date would let an older snapshot shadow it as "latest"). The
        // S-curve's historic actual line is rebuilt from the dated logs themselves.
        val report = buildReport(projectId, LocalDate.now())
        val pct = report.earnedValue?.actualPercent ?: 0.0
        db.projectProgressDao().insert(ProjectProgress(projectId = projectId, date = System.currentTimeMillis(), overallProgressPercent = pct))
    }

    // ------------------------------------------------------------------ derivations

    private suspend fun derivedQuantities(projectId: Long, activities: List<ScheduleActivity>): Map<Long, Double> {
        val boq = db.boqDao().getForProject(projectId).map { BoqQuantity(it.materialType, it.floor, it.quantity) }
        return ScheduleEngine.plannedQuantities(
            activities.map { ActivityQuantityInput(it.id, it.linkedNotationCode, it.floorLabel, it.boqSharePercent, it.quantityOverride) },
            boq
        )
    }

    private fun networkNodes(
        activities: List<ScheduleActivity>, deps: List<ScheduleDependency>, withProgress: Boolean
    ): List<ActivityNetworkInput> {
        val predsById = deps.groupBy { it.activityId }
        return activities.map { a ->
            ActivityNetworkInput(
                id = a.id,
                durationDays = max(1, a.durationDays),
                predecessors = predsById[a.id].orEmpty().map { it.dependsOnActivityId to it.lagDays },
                progressPercent = if (withProgress) a.progressPercent else 0.0,
                actualStart = if (withProgress) a.actualStart?.let { WorkCalendar.toLocalDate(it) } else null,
                actualFinish = if (withProgress) a.actualCompletion?.let { WorkCalendar.toLocalDate(it) } else null
            )
        }
    }

    /** The complete Schedule read model as of [today]. */
    suspend fun buildReport(projectId: Long, today: LocalDate = LocalDate.now(), lookaheadDays: Int = 14): ScheduleReport {
        val sundayOff = isSundayOff(projectId)
        val project = db.projectDao().getById(projectId) ?: return ScheduleReport(false, today, sundayOff)
        val activities = db.scheduleActivityDao().getForProject(projectId)
        if (activities.isEmpty()) return ScheduleReport(false, today, sundayOff)

        val deps = db.scheduleDependencyDao().getForProject(projectId)
        val logs = db.activityProgressLogDao().getForProject(projectId)
        val boqItems = db.boqDao().getForProject(projectId)
        val coefficients = db.materialCoefficientDao().getForProject(projectId)
        val rates = db.materialRateCardDao().getForProject(projectId)
        val vendors = db.vendorDao().getForProject(projectId)
        val allocations = db.costAllocationDao().getForProject(projectId)
        val expenses = db.expenseDao().getForProject(projectId)
        val totalSpent = expenses.filter { it.direction == TransactionDirection.EXPENSE }.sumOf { it.totalAmount }
        // Cost of work performed = spend − value of material still in the store. Bulk buying
        // cement or steel ahead of use isn't "cost of work done" yet; counting it would make
        // every early purchase look like a cost overrun (CPI) on a building site.
        val statusRows = materialRepository.materialStatus(projectId)
        val stockValue = statusRows.filter { it.isStockTracked && it.currentStock > 0 }
            .sumOf { it.currentStock * (if (it.averageRate > 0) it.averageRate else 0.0) }
        val actualCost = (totalSpent - stockValue).coerceAtLeast(0.0)
        val received = expenses.filter { it.direction == TransactionDirection.RECEIVED }.sumOf { it.totalAmount } +
            db.clientPaymentDao().getForProject(projectId).sumOf { it.amountReceived }

        val calendar = calendarFor(project, sundayOff)
        val quantities = derivedQuantities(projectId, activities)

        // ---- budgets
        val civilCostByNotation = BudgetMath.civilMaterialCostByNotation(boqItems, coefficients, rates)
        val qtyByNotation = activities.filter { it.category == CostCategory.CIVIL_STRUCTURAL && it.linkedNotationCode.isNotBlank() }
            .groupBy { it.linkedNotationCode }.mapValues { (_, acts) -> acts.sumOf { quantities[it.id] ?: 0.0 } }
        val materialCost = activities.associate { a ->
            val code = a.linkedNotationCode
            val totalQ = qtyByNotation[code] ?: 0.0
            a.id to if (a.category == CostCategory.CIVIL_STRUCTURAL && code.isNotBlank() && totalQ > 0)
                (civilCostByNotation[code] ?: 0.0) * (quantities[a.id] ?: 0.0) / totalQ else 0.0
        }
        val pools = allocations.filter { it.category != CostCategory.CIVIL_STRUCTURAL }
            .associate { it.category to CalculationEngine.categoryBudget(project.projectValue, it.percentOfProjectValue) }
        val civilLabourPool = BudgetMath.civilLabourPool(vendors)
        val budgets = ScheduleEngine.allocateBudgets(
            activities.map { BudgetInput(it.id, it.category, it.budgetWeight, it.budgetOverride, materialCost[it.id] ?: 0.0,
                (it.durationDays * it.requiredLabourCount).toDouble().coerceAtLeast(it.durationDays.toDouble())) },
            civilLabourPool, pools
        )
        val budget2 = civilCostByNotation.values.sum() + civilLabourPool + pools.values.sum()
        val coveredCategories = activities.map { it.category }.toSet()
        val unscheduled = (pools.filter { it.value > 0 }.keys - coveredCategories).toList()

        // ---- tracking
        val baseline = activities.associate { a ->
            a.id to (WorkCalendar.toLocalDate(a.plannedStart) to WorkCalendar.toLocalDate(a.plannedEnd).let {
                if (it.isBefore(WorkCalendar.toLocalDate(a.plannedStart))) WorkCalendar.toLocalDate(a.plannedStart) else it
            })
        }
        // Progress % must use the *current* BOQ quantity (cached plannedQuantity can lag).
        val withProgress = activities.map { a ->
            val q = quantities[a.id] ?: a.plannedQuantity
            val logsFor = logs.filter { it.activityId == a.id }
            a.copy(progressPercent = progressFromLogs(q, logsFor))
        }
        val tracking = ScheduleEngine.track(networkNodes(withProgress, deps, true), baseline, calendar, today)
        val ev = ScheduleEngine.earnedValue(budgets, activities.associate { it.id to it.durationDays }, tracking, actualCost)

        val depsByActivity = deps.groupBy { it.activityId }
        val logsByActivity = logs.groupBy { it.activityId }
        val views = withProgress.map { a ->
            val tr = tracking.getValue(a.id)
            val b = budgets[a.id] ?: 0.0
            ScheduleActivityView(
                activity = a, plannedQuantity = quantities[a.id] ?: 0.0, budget = b, materialCost = materialCost[a.id] ?: 0.0,
                tracking = tr, predecessors = depsByActivity[a.id].orEmpty(),
                earnedValue = b * tr.actualPercent / 100.0, plannedValue = b * tr.plannedPercent / 100.0,
                logs = logsByActivity[a.id].orEmpty().sortedByDescending { it.date }
            )
        }.sortedWith(compareBy({ it.activity.sequence }, { it.tracking.baselineStart }))

        // ---- phases
        val weightOf: (ScheduleActivityView) -> Double = if (ev.budgetAtCompletion > 0) { v -> v.budget } else { v -> v.activity.durationDays.toDouble() }
        val phases = views.groupBy { it.activity.phase.ifBlank { "Other" } }.map { (phase, list) ->
            val w = list.sumOf(weightOf)
            PhaseSummary(
                phase = phase, activityCount = list.size,
                completedCount = list.count { it.tracking.status == ActivityStatus.COMPLETED },
                plannedPercent = if (w > 0) list.sumOf { weightOf(it) * it.tracking.plannedPercent } / w else 0.0,
                actualPercent = if (w > 0) list.sumOf { weightOf(it) * it.tracking.actualPercent } / w else 0.0,
                budget = list.sumOf { it.budget },
                start = list.minOf { it.tracking.baselineStart },
                finish = list.maxOf { it.tracking.baselineFinish },
                forecastFinish = list.maxOf { it.tracking.forecastFinish }
            )
        }.sortedBy { it.phase }

        val baselineStart = views.minOf { it.tracking.baselineStart }
        val baselineFinish = views.maxOf { it.tracking.baselineFinish }
        val forecastFinish = views.maxOf { it.tracking.forecastFinish }
        val projectDelay = calendar.indexOf(forecastFinish) - calendar.indexOf(baselineFinish)

        // ---- S-curve (actual % at a past date rebuilt from dated logs)
        val weights = views.associate { it.activity.id to weightOf(it) }
        val qtyById = views.associate { it.activity.id to it.plannedQuantity }
        val sCurve = ScheduleEngine.sCurve(
            weights, baseline, calendar, baselineStart, maxOf(baselineFinish, forecastFinish), today
        ) { id, date ->
            val upTo = logsByActivity[id].orEmpty().filter { !WorkCalendar.toLocalDate(it.date).isAfter(date) }
                .sortedWith(compareBy({ it.date }, { it.id }))
            progressFromLogs(qtyById[id] ?: 0.0, upTo)
        }

        // ---- look-ahead
        val windowEnd = today.plusDays(lookaheadDays.toLong() - 1)
        val upcoming = views.filter {
            it.tracking.status != ActivityStatus.COMPLETED &&
                !it.tracking.forecastStart.isAfter(windowEnd) && !it.tracking.forecastFinish.isBefore(today)
        }.sortedBy { it.tracking.forecastStart }
        val labour = upcoming.filter { it.activity.requiredLabourCount > 0 }
            .groupBy { it.activity.requiredLabourSkill.ifBlank { "Unspecified" } }
            .map { (skill, list) -> LabourNeed(skill, list.sumOf { it.activity.requiredLabourCount }, list.size) }
            .sortedByDescending { it.workers }
        val perUnit = materialRepository.materialPerUnitByNotation(projectId)
        val needs = mutableMapOf<String, Double>()
        upcoming.forEach { v ->
            val code = v.activity.linkedNotationCode
            if (code.isBlank()) return@forEach
            val q = ScheduleEngine.remainingQuantityInWindow(
                v.plannedQuantity, v.tracking.actualPercent, v.tracking.forecastStart, v.tracking.forecastFinish,
                today, windowEnd, calendar
            )
            perUnit[code]?.forEach { (m, per) -> needs[m] = (needs[m] ?: 0.0) + q * per }
        }
        val status = statusRows.associateBy { it.materialName }
        val materialNeeds = needs.filter { it.value > 0.0001 }.map { (m, req) ->
            val stock = status[m]?.currentStock ?: 0.0
            MaterialNeed(m, status[m]?.unit ?: "", req, stock, CalculationEngine.materialShortage(req, stock))
        }.sortedByDescending { it.shortage }

        // ---- alerts
        val alerts = mutableListOf<ScheduleAlert>()
        val delayed = views.filter { it.tracking.status == ActivityStatus.DELAYED }
        if (delayed.isNotEmpty()) {
            val worst = delayed.maxByOrNull { it.tracking.delayDays }!!
            alerts += ScheduleAlert(NotificationType.SCHEDULE_DELAY,
                "${delayed.size} activit${if (delayed.size == 1) "y is" else "ies are"} behind schedule — worst: ${worst.activity.activityName}" +
                    (if (worst.tracking.delayDays > 0) " (+${worst.tracking.delayDays} working days)" else " (not started on time)"),
                isCritical = delayed.any { it.tracking.isCritical })
        }
        if (projectDelay > 0) alerts += ScheduleAlert(NotificationType.SCHEDULE_DELAY,
            "Forecast handover is $projectDelay working days after the baseline finish.", true)
        materialNeeds.filter { it.shortage > 0 }.forEach {
            alerts += ScheduleAlert(NotificationType.MATERIAL_SHORTAGE,
                "Buy ${"%.1f".format(it.shortage)} ${it.unit} ${it.material} — needed in the next $lookaheadDays days, ${"%.1f".format(it.inStock)} in stock.", true)
        }
        upcoming.filter { it.tracking.status == ActivityStatus.NOT_STARTED && it.tracking.isCritical &&
            !it.tracking.forecastStart.isAfter(today.plusDays(3)) }.forEach {
            alerts += ScheduleAlert(NotificationType.UPCOMING_ACTIVITY,
                "Critical activity starts ${if (it.tracking.forecastStart <= today) "today" else "on ${it.tracking.forecastStart}"}: ${it.activity.activityName} (${it.activity.requiredLabourCount} ${it.activity.requiredLabourSkill}).", false)
        }
        ev.cpi?.let { if (it < 0.95) alerts += ScheduleAlert(NotificationType.BUDGET_EXCEEDED,
            "Cost performance ${"%.2f".format(it)} — work done is costing more than budgeted. Forecast cost at completion ₹${"%,.0f".format(ev.estimateAtCompletion)}.", it < 0.85) }
        allocations.forEach { alloc ->
            val budget = CalculationEngine.categoryBudget(project.projectValue, alloc.percentOfProjectValue)
            val actual = expenses.filter { it.direction == TransactionDirection.EXPENSE && it.category == alloc.category }.sumOf { it.totalAmount }
            if (budget > 0 && actual > budget) alerts += ScheduleAlert(NotificationType.BUDGET_EXCEEDED,
                "${alloc.category.name.replace('_', ' ')} spend ₹${"%,.0f".format(actual)} exceeds its budget ₹${"%,.0f".format(budget)}.", true)
        }
        // Vendor dues: civil progress × contract vs paid.
        val civilViews = views.filter { it.activity.category == CostCategory.CIVIL_STRUCTURAL }
        val civilW = civilViews.sumOf(weightOf)
        val civilPct = if (civilW > 0) civilViews.sumOf { weightOf(it) * it.tracking.actualPercent } / civilW else 0.0
        vendors.forEach { v ->
            if (v.amountPaid > v.contractValue && v.contractValue > 0) alerts += ScheduleAlert(NotificationType.VENDOR_PAYMENT_DUE,
                "${v.name} is paid ₹${"%,.0f".format(v.amountPaid - v.contractValue)} over the contract value.", true)
            else if (v.workCategory in BudgetMath.civilLabourCategories) {
                val earned = v.contractValue * civilPct / 100.0
                if (earned - v.amountPaid > 1000) alerts += ScheduleAlert(NotificationType.VENDOR_PAYMENT_DUE,
                    "${v.name}: work done is worth about ₹${"%,.0f".format(earned)} (${"%.0f".format(civilPct)}% of civil) — paid ₹${"%,.0f".format(v.amountPaid)}.", false)
            }
        }
        val billable = project.projectValue * ev.actualPercent / 100.0
        if (billable - received > 1000) alerts += ScheduleAlert(NotificationType.CLIENT_PAYMENT_PENDING,
            "Work done is worth about ₹${"%,.0f".format(billable)} of the project value; received ₹${"%,.0f".format(received)}. Consider raising a bill.", false)
        val daysToFinish = java.time.temporal.ChronoUnit.DAYS.between(today, forecastFinish)
        if (ev.actualPercent < 100 && daysToFinish in 0..30) alerts += ScheduleAlert(NotificationType.PROJECT_COMPLETION_APPROACHING,
            "Forecast handover in $daysToFinish days (${forecastFinish}).", false)

        val activityNames = activities.associate { it.id to it.activityName }
        return ScheduleReport(
            hasSchedule = true, today = today, sundayOff = sundayOff,
            activities = views, phases = phases, earnedValue = ev,
            baselineStart = baselineStart, baselineFinish = baselineFinish, forecastFinish = forecastFinish,
            projectDelayDays = projectDelay,
            criticalPath = views.filter { it.tracking.isCritical }.sortedBy { it.tracking.forecastStart },
            sCurve = sCurve, lookaheadStart = today, lookaheadEnd = windowEnd, upcoming = upcoming,
            labourNeeds = labour, materialNeeds = materialNeeds,
            alerts = alerts.sortedByDescending { it.isCritical },
            budget2 = budget2, scheduledBudget = budgets.values.sum(), unscheduledCategories = unscheduled,
            completedCount = views.count { it.tracking.status == ActivityStatus.COMPLETED },
            inProgressCount = views.count { it.tracking.status == ActivityStatus.IN_PROGRESS },
            delayedCount = delayed.size,
            notStartedCount = views.count { it.tracking.status == ActivityStatus.NOT_STARTED },
            recentLogs = logs.sortedWith(compareByDescending<ActivityProgressLog> { it.date }.thenByDescending { it.id })
                .take(30).map { it to (activityNames[it.activityId] ?: "") },
            totalSpent = totalSpent, stockValue = stockValue
        )
    }

    /** CSV export of the schedule & tracking status (opened in Excel / Sheets). */
    suspend fun exportCsv(projectId: Long): String {
        val r = buildReport(projectId)
        val sb = StringBuilder()
        fun esc(v: Any?) = "\"" + (v?.toString() ?: "").replace("\"", "\"\"") + "\""
        sb.appendLine(listOf("Phase", "Activity", "Floor", "Category", "BOQ notation", "Planned qty", "Unit", "Done qty",
            "Duration (days)", "Baseline start", "Baseline finish", "Forecast start", "Forecast finish", "Planned %",
            "Actual %", "Status", "Delay (working days)", "Critical", "Budget (Rs)", "Earned value (Rs)",
            "Labour", "Crew size").joinToString(",") { esc(it) })
        r.activities.forEach { v ->
            val a = v.activity; val t = v.tracking
            sb.appendLine(listOf(a.phase, a.activityName, a.floorLabel, a.category.name, a.linkedNotationCode,
                "%.2f".format(v.plannedQuantity), a.unit, "%.2f".format(a.completedQuantity), a.durationDays,
                t.baselineStart, t.baselineFinish, t.forecastStart, t.forecastFinish, "%.1f".format(t.plannedPercent),
                "%.1f".format(t.actualPercent), t.status.name, t.delayDays, if (t.isCritical) "Yes" else "No",
                "%.0f".format(v.budget), "%.0f".format(v.earnedValue), a.requiredLabourSkill, a.requiredLabourCount
            ).joinToString(",") { esc(it) })
        }
        r.earnedValue?.let { ev ->
            sb.appendLine()
            sb.appendLine("${esc("Budget at completion")},${esc("%.0f".format(ev.budgetAtCompletion))}")
            sb.appendLine("${esc("Planned value")},${esc("%.0f".format(ev.plannedValue))}")
            sb.appendLine("${esc("Earned value")},${esc("%.0f".format(ev.earnedValue))}")
            sb.appendLine("${esc("Actual cost")},${esc("%.0f".format(ev.actualCost))}")
            sb.appendLine("${esc("SPI")},${esc(ev.spi?.let { "%.2f".format(it) } ?: "-")}")
            sb.appendLine("${esc("CPI")},${esc(ev.cpi?.let { "%.2f".format(it) } ?: "-")}")
            sb.appendLine("${esc("Forecast finish")},${esc(r.forecastFinish)}")
        }
        return sb.toString()
    }
}
