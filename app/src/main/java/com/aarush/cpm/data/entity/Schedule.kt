package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ActivityStatus { NOT_STARTED, IN_PROGRESS, DELAYED, COMPLETED }

/**
 * One activity of the construction schedule (e.g. "RCC beams & slab — First Floor").
 *
 * Quantities and budgets are DERIVED from the BOQ and the budget at read time (see
 * ScheduleEngine / ScheduleRepository) — only the inputs that drive them are stored here:
 *  - [linkedNotationCode] + [floorLabel] + [boqSharePercent] → planned quantity from BOQ
 *    (unless [quantityOverride] is set)
 *  - [category] + [budgetWeight] → share of that category's budget (unless [budgetOverride])
 *  - [productivityPerDay] → duration from quantity (unless [isDurationManual])
 * [plannedQuantity] is a cached copy of the last derived quantity, kept for display/export.
 */
@Entity(tableName = "schedule_activities")
data class ScheduleActivity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val activityName: String,
    val plannedStart: Long,
    val plannedEnd: Long,
    val plannedQuantity: Double,
    val unit: String,
    val requiredLabourSkill: String = "",
    val requiredLabourCount: Int = 0,
    val requiredMaterialName: String = "",
    val vendorId: Long? = null,
    val status: ActivityStatus = ActivityStatus.NOT_STARTED,
    val actualStart: Long? = null,
    val actualCompletion: Long? = null,
    val progressPercent: Double = 0.0,
    // ---- v8 additions ----
    val phase: String = "",
    val sequence: Int = 0,
    val floorLabel: String = "",
    val templateKey: String = "",
    val category: CostCategory = CostCategory.OTHER_MISC,
    val linkedNotationCode: String = "",
    val boqSharePercent: Double = 100.0,
    val quantityOverride: Double? = null,
    val durationDays: Int = 1,
    val isDurationManual: Boolean = false,
    /** Output of the whole assigned crew per working day, in [unit]. 0 = fixed duration. */
    val productivityPerDay: Double = 0.0,
    val budgetWeight: Double = 1.0,
    val budgetOverride: Double? = null,
    val completedQuantity: Double = 0.0,
    val isMilestone: Boolean = false,
    val notes: String = ""
)

/** Finish-to-start link: [activityId] can start [lagDays] working days after
 *  [dependsOnActivityId] finishes (lag is used for curing periods etc.). */
@Entity(tableName = "schedule_dependencies")
data class ScheduleDependency(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val dependsOnActivityId: Long,
    val lagDays: Int = 0
)

/** Daily/weekly overall project progress snapshot — written automatically every time an
 *  activity's progress is logged, which builds the "actual" line of the S-curve. */
@Entity(tableName = "project_progress")
data class ProjectProgress(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val date: Long,
    val overallProgressPercent: Double,
    val notes: String = ""
)

/**
 * One site progress entry against an activity: quantity executed that day (for BOQ-linked
 * activities) or a cumulative % complete (for activities without a measurable quantity).
 * Each log auto-generates MaterialUsage rows (stock consumption) and a LabourEntry.
 */
@Entity(tableName = "activity_progress_logs")
data class ActivityProgressLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val activityId: Long,
    val date: Long,
    val quantityDone: Double = 0.0,
    /** Cumulative % complete after this log, used when the activity has no quantity. */
    val percentComplete: Double? = null,
    val labourCount: Int = 0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Device ID of the phone that logged this (v10); null for older rows. */
    val enteredBy: String? = null
)
