package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ActivityStatus { NOT_STARTED, IN_PROGRESS, DELAYED, COMPLETED }

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
    val progressPercent: Double = 0.0
)

@Entity(tableName = "schedule_dependencies")
data class ScheduleDependency(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val dependsOnActivityId: Long
)

/** Daily/weekly overall project progress snapshot (can be manual % or rolled up from activities). */
@Entity(tableName = "project_progress")
data class ProjectProgress(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val date: Long,
    val overallProgressPercent: Double,
    val notes: String = ""
)
