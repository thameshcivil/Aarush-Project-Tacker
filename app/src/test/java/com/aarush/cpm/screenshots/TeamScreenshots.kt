package com.aarush.cpm.screenshots

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.aarush.cpm.data.entity.*
import com.aarush.cpm.data.repository.TeamState
import com.aarush.cpm.domain.access.ProjectAccess
import com.aarush.cpm.ui.common.AccessNotice
import com.aarush.cpm.ui.common.LocalProjectAccess
import com.aarush.cpm.ui.common.LocalTeam
import com.aarush.cpm.ui.dashboard.ProjectCard
import com.aarush.cpm.ui.dashboard.ProjectCardData
import com.aarush.cpm.ui.project.ProjectValueHeader
import com.aarush.cpm.ui.team.TeamBody
import com.aarush.cpm.ui.theme.AarushCPMTheme
import com.aarush.cpm.ui.vendor.VendorCard
import org.junit.Rule
import org.junit.Test

/* Review screenshots for team roles (Phase 1): the Owner's Team & devices screen, and what a
 * Coordinator sees (notice line, no delete menu, no edit buttons on money/setup). */

private val projects = listOf(
    DemoBudget.project.copy(id = 1, name = "Residential Building - Demo"),
    DemoBudget.project.copy(id = 2, name = "Kumar Residence, G+2")
)
private val team = TeamState(
    thisDeviceId = "owner",
    devices = listOf(
        TeamDevice("owner", "Thamesh – Office", TeamRole.OWNER, lastSeenAt = 1_761_400_000_000),
        TeamDevice("ravi", "Ravi – Site", TeamRole.COORDINATOR, lastSeenAt = 1_761_390_000_000),
        TeamDevice("acc", "Accounts", TeamRole.VIEWER, lastSeenAt = 1_761_300_000_000)
    ),
    members = listOf(ProjectMember(1, "ravi", isLead = true), ProjectMember(2, "ravi"), ProjectMember(1, "acc")),
    ownerPinSet = true
)

private val card = ProjectCardData(
    project = DemoBudget.project.copy(status = ProjectStatus.IN_PROGRESS),
    totalReceived = DemoBudget.summary.totalReceived, totalSpent = DemoBudget.summary.totalSpent, profit = DemoBudget.summary.profit,
    progressPercent = 42.0, plannedPercent = 48.0, delayDays = 6
)
private val vendor = Vendor(id = 1, projectId = 1, name = "Sri Murugan Constructions", contact = "", workCategory = VendorWorkCategory.CIVIL,
    rateType = VendorRateType.PER_SQFT, rate = 450.0, quantity = 2000.0, contractValue = 900_000.0,
    startDate = 0, endDate = 0, amountPaid = 200_000.0)

@Composable
private fun OwnerTeam() {
    AarushCPMTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) { TeamBody(team, projects, message = null) }
    }
}

@Composable
private fun CoordinatorView() {
    AarushCPMTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            CompositionLocalProvider(LocalTeam provides team, LocalProjectAccess provides ProjectAccess.SITE) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AccessNotice(TeamRole.COORDINATOR, ProjectAccess.SITE)
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ProjectCard(card, onClick = {}, onDelete = null, leadName = "Ravi – Site")
                        ProjectValueHeader(DemoBudget.project, onEditAreaRate = {})
                        VendorCard(vendor, payments = emptyList(), works = emptyList(), onEdit = {}, onDelete = {}, onAddWork = {}, onEditWork = {})
                    }
                }
            }
        }
    }
}

class TeamScreenshots {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 590, screenHeight = 2000, xdpi = 240, ydpi = 240, density = Density.HIGH),
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test fun team_owner_view() = paparazzi.snapshot { OwnerTeam() }
    @Test fun team_coordinator_view() = paparazzi.snapshot { CoordinatorView() }
}
