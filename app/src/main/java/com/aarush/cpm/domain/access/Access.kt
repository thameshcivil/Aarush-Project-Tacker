package com.aarush.cpm.domain.access

import com.aarush.cpm.data.entity.TeamRole

/**
 * What one project allows this phone to change. Every edit button in the project reads one
 * of these flags; when a flag is false the control is hidden. "Setup" covers project value,
 * area & rate, details, status, cost distribution, notations, coefficients and rates.
 * "Measurements" are the BOQ sheet's entries (Nos × L × B × D lines).
 */
data class ProjectAccess(
    val editSetup: Boolean,
    val editMeasurements: Boolean,
    val editSchedulePlan: Boolean,
    val logProgress: Boolean,
    val editExpenses: Boolean,
    val editVendors: Boolean
) {
    val readOnly: Boolean get() = !(editSetup || editMeasurements || editSchedulePlan ||
        logProgress || editExpenses || editVendors)

    companion object {
        val FULL = ProjectAccess(true, true, true, true, true, true)
        val NONE = ProjectAccess(false, false, false, false, false, false)
        /** Coordinator on an assigned project: day-to-day site entries only. */
        val SITE = NONE.copy(editMeasurements = true, logProgress = true, editExpenses = true)
    }
}

/**
 * This phone's permissions, worked out from its role and the projects it is assigned to.
 * Pure logic (no Android) so the whole matrix is unit-tested.
 */
data class Access(
    val role: TeamRole,
    val assignedProjectIds: Set<Long> = emptySet(),
    /** False until this phone's team record has been read; screens wait rather than flash. */
    val loaded: Boolean = true
) {
    val seesAllProjects: Boolean get() = role == TeamRole.OWNER || role == TeamRole.MANAGER
    val canCreateProject: Boolean get() = role == TeamRole.OWNER || role == TeamRole.MANAGER
    val canDeleteProject: Boolean get() = role == TeamRole.OWNER
    val canManageTeam: Boolean get() = role == TeamRole.OWNER
    val canRestore: Boolean get() = role == TeamRole.OWNER
    val canManageDemoData: Boolean get() = role == TeamRole.OWNER

    fun canOpenProject(projectId: Long): Boolean = seesAllProjects || projectId in assignedProjectIds

    fun forProject(projectId: Long): ProjectAccess = when {
        role == TeamRole.OWNER || role == TeamRole.MANAGER -> ProjectAccess.FULL
        role == TeamRole.COORDINATOR && projectId in assignedProjectIds -> ProjectAccess.SITE
        else -> ProjectAccess.NONE
    }

    companion object {
        /** Default for previews, screenshots and tests: unrestricted. */
        val OWNER = Access(TeamRole.OWNER)
        val LOADING = Access(TeamRole.VIEWER, loaded = false)
    }
}
