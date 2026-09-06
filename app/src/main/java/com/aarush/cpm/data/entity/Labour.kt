package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Default/editable productivity + wage rate per labour skill type, used for predictions. */
@Entity(tableName = "labour_rates")
data class LabourRate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val skillType: String,           // Mason, Helper, Carpenter, Electrician, Plumber...
    val wagePerDay: Double,
    val productivityPerDay: Double,  // units of work per worker per day (unit depends on activity)
    val productivityUnit: String = ""
)

/** Daily attendance / requirement log, entered directly or derived from schedule + progress. */
@Entity(tableName = "labour_entries")
data class LabourEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val date: Long,
    val skillType: String,
    val requiredCount: Int = 0,
    val availableCount: Int = 0,
    val scheduleActivityId: Long? = null
)
