package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ProjectStatus { PLANNING, STARTED, IN_PROGRESS, ON_HOLD, COMPLETED }

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val clientName: String,
    val clientContact: String,
    val location: String,
    val projectType: String,        // e.g. "G+1", "G+Terrace"
    val numberOfFloors: Int,
    val plinthAreaSqft: Double,
    val ratePerSqft: Double,
    val projectValue: Double,       // plinthAreaSqft * ratePerSqft, user-overridable
    val isProjectValueManuallyOverridden: Boolean = false,
    val startDate: Long,
    val expectedCompletionDate: Long,
    val notes: String = "",
    val status: ProjectStatus = ProjectStatus.PLANNING,
    val isDemoData: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
