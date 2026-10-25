package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What a phone may do (v10). Everyone signs in with the same email, so a phone is identified
 * by its permanent device ID (see DeviceIdentity) and the Owner gives each phone a name and a
 * role here. Permissions themselves live in domain/access/Access.kt.
 */
enum class TeamRole(val label: String, val summary: String) {
    OWNER("Owner", "Everything, including team, delete and restore"),
    MANAGER("Project manager", "Edits every project. Can't delete projects, manage the team or restore"),
    COORDINATOR("Coordinator", "Expenses, progress and BOQ measurements on assigned projects only"),
    VIEWER("Viewer", "Read only, assigned projects only")
}

/** One phone on the team. [deviceId] is generated once per install and never leaves the
 *  phone's no-backup storage, so restoring a backup on another phone can't copy it. */
@Entity(tableName = "team_devices")
data class TeamDevice(
    @PrimaryKey val deviceId: String,
    val name: String,
    val role: TeamRole,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis()
)

/** A phone assigned to a project. [isLead] marks the lead coordinator (at most one per
 *  project, kept so by AccessRepository). Rows are removed with the project or the device. */
@Entity(tableName = "project_members", primaryKeys = ["projectId", "deviceId"])
data class ProjectMember(
    val projectId: Long,
    val deviceId: String,
    val isLead: Boolean = false
)
