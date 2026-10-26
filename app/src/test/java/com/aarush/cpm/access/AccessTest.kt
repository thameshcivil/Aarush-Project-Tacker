package com.aarush.cpm.access

import com.aarush.cpm.data.entity.ProjectMember
import com.aarush.cpm.data.entity.TeamDevice
import com.aarush.cpm.data.entity.TeamRole
import com.aarush.cpm.data.repository.AccessPreview
import com.aarush.cpm.data.repository.TeamState
import com.aarush.cpm.domain.access.Access
import com.aarush.cpm.domain.access.ProjectAccess
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The role matrix: who can open and change what. */
class AccessTest {
    private val p1 = 1L
    private val p2 = 2L

    @Test fun ownerCanDoEverything() {
        val a = Access(TeamRole.OWNER)
        assertThat(a.seesAllProjects).isTrue()
        assertThat(a.canCreateProject).isTrue()
        assertThat(a.canDeleteProject).isTrue()
        assertThat(a.canManageTeam).isTrue()
        assertThat(a.canRestore).isTrue()
        assertThat(a.forProject(p2)).isEqualTo(ProjectAccess.FULL)
    }

    @Test fun managerEditsAllProjectsButCannotDeleteManageOrRestore() {
        val a = Access(TeamRole.MANAGER)
        assertThat(a.canOpenProject(p2)).isTrue()
        assertThat(a.forProject(p2)).isEqualTo(ProjectAccess.FULL)
        assertThat(a.canCreateProject).isTrue()
        assertThat(a.canDeleteProject).isFalse()
        assertThat(a.canManageTeam).isFalse()
        assertThat(a.canRestore).isFalse()
        assertThat(a.canManageDemoData).isFalse()
    }

    @Test fun coordinatorOnlySiteEntriesOnAssignedProjects() {
        val a = Access(TeamRole.COORDINATOR, setOf(p1))
        assertThat(a.canOpenProject(p1)).isTrue()
        assertThat(a.canOpenProject(p2)).isFalse()
        val pa = a.forProject(p1)
        assertThat(pa.editExpenses).isTrue()
        assertThat(pa.logProgress).isTrue()
        assertThat(pa.editMeasurements).isTrue()
        // Money and plan stay with Owner / Manager.
        assertThat(pa.editSetup).isFalse()
        assertThat(pa.editSchedulePlan).isFalse()
        assertThat(pa.editVendors).isFalse()
        assertThat(a.forProject(p2)).isEqualTo(ProjectAccess.NONE)
        assertThat(a.canCreateProject).isFalse()
        assertThat(a.canDeleteProject).isFalse()
    }

    @Test fun viewerIsReadOnlyEvenOnAssignedProjects() {
        val a = Access(TeamRole.VIEWER, setOf(p1))
        assertThat(a.canOpenProject(p1)).isTrue()
        assertThat(a.canOpenProject(p2)).isFalse()
        assertThat(a.forProject(p1).readOnly).isTrue()
    }

    @Test fun loadingIsRestrictiveAndFlagged() {
        assertThat(Access.LOADING.loaded).isFalse()
        assertThat(Access.LOADING.canOpenProject(p1)).isFalse()
        assertThat(Access.LOADING.forProject(p1).readOnly).isTrue()
    }

    private val owner = TeamDevice("A", "Owner phone", TeamRole.OWNER)
    private val ravi = TeamDevice("B", "Ravi – Site", TeamRole.COORDINATOR)
    private val team = TeamState(
        thisDeviceId = "A", devices = listOf(owner, ravi),
        members = listOf(ProjectMember(p1, "B", isLead = true), ProjectMember(p2, "B"))
    )

    @Test fun teamStateResolvesThisPhoneAndLeads() {
        assertThat(team.access.role).isEqualTo(TeamRole.OWNER)
        assertThat(team.leadFor(p1)?.name).isEqualTo("Ravi – Site")
        assertThat(team.leadFor(p2)).isNull()
        assertThat(team.projectsOf("B")).containsExactly(p1, p2)
        assertThat(team.nameOf("B")).isEqualTo("Ravi – Site")
        assertThat(team.nameOf(null)).isNull()
    }

    @Test fun previewChangesEnforcedAccessButNotOwnAccess() {
        val previewing = team.copy(preview = AccessPreview("a Coordinator", TeamRole.COORDINATOR, setOf(p2)))
        assertThat(previewing.access.canOpenProject(p1)).isFalse()
        assertThat(previewing.access.forProject(p2)).isEqualTo(ProjectAccess.SITE)
        // The Team screen still knows this phone is the Owner, so the preview can be stopped.
        assertThat(previewing.ownAccess.canManageTeam).isTrue()
    }

    @Test fun unknownPhoneIsLoadingUntilRegistered() {
        val stranger = team.copy(thisDeviceId = "Z")
        assertThat(stranger.access.loaded).isFalse()
    }

    @Test fun partnerEditsAssignedProjectsExceptBoqAndSetup() {
        val a = Access(TeamRole.PARTNER, setOf(p1))
        assertThat(a.canOpenProject(p1)).isTrue()
        assertThat(a.canOpenProject(p2)).isFalse()
        val pa = a.forProject(p1)
        assertThat(pa.editExpenses).isTrue()
        assertThat(pa.editVendors).isTrue()
        assertThat(pa.editSchedulePlan).isTrue()
        assertThat(pa.logProgress).isTrue()
        assertThat(pa.editMeasurements).isFalse()   // BOQ
        assertThat(pa.editSetup).isFalse()          // project value, area & rate, distribution, profit
        assertThat(a.forProject(p2)).isEqualTo(ProjectAccess.NONE)
        assertThat(a.canCreateProject).isFalse()
        assertThat(a.canDeleteProject).isFalse()
        assertThat(a.canManageTeam).isFalse()
    }
}
