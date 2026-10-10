package com.aarush.cpm.ui.project

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.aarush.cpm.screenshots.DemoBudget
import com.aarush.cpm.ui.boq.BOQSheet
import com.aarush.cpm.ui.boq.BOQSortMode
import com.aarush.cpm.ui.boq.DemoBoq
import com.aarush.cpm.ui.common.BrandHeader
import com.aarush.cpm.ui.dashboard.ProjectCard
import com.aarush.cpm.ui.dashboard.ProjectCardData
import com.aarush.cpm.ui.schedule.DemoSchedule
import com.aarush.cpm.ui.schedule.ScheduleOverview
import com.aarush.cpm.ui.schedule.ScheduleSection
import com.aarush.cpm.ui.schedule.ScheduleTopRow
import com.aarush.cpm.ui.theme.AarushCPMTheme
import org.junit.Rule
import org.junit.Test

/*
 * Final review screenshots (redesign Phase 5): every main screen inside the real project top
 * bar and five-destination bottom navigation. Not assertions. Data: the demo project
 * (DemoBudget summary, DemoSchedule report, DemoBoq entries), unchanged.
 */
private const val NAME = "Residential Building - Demo"

@Composable
private fun Frame(tab: ProjectTab, title: String, dark: Boolean = false, backToMore: Boolean = false, content: @Composable () -> Unit) {
    AarushCPMTheme(darkTheme = dark) {
        Scaffold(
            topBar = { ProjectTopBar(NAME, title, backToMore, onBack = {}) },
            bottomBar = { ProjectNavigationBar(tab, onSelect = {}) }
        ) { pad -> Box(Modifier.padding(pad).fillMaxSize()) { content() } }
    }
}

@Composable
private fun OverviewBody() {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        OverviewHeader(DemoBudget.summary, DemoSchedule.report)
        OverviewSchedule(DemoSchedule.report, daysRemaining = 170, onOpenSchedule = {})
        OverviewBudgetLink(DemoBudget.summary, onOpenBudget = {})
        AlertsCard(DemoSchedule.report.alerts)
    }
}

@Composable
private fun BudgetBody() {
    Column {
        MoneyViewSwitch(MoneyView.Budget, onSelect = {})
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ProjectValueHeader(DemoBudget.project, onEditAreaRate = {})
            MoneySummaryTable(DemoBudget.summary)
            BudgetComparisonTable(DemoBudget.comparison)
            CategoryBudgetTable(DemoBudget.categories)
        }
    }
}

private val demoCard = ProjectCardData(
    project = DemoBudget.project.copy(status = com.aarush.cpm.data.entity.ProjectStatus.IN_PROGRESS),
    totalReceived = DemoBudget.summary.totalReceived, totalSpent = DemoBudget.summary.totalSpent, profit = DemoBudget.summary.profit,
    progressPercent = DemoSchedule.report.earnedValue!!.actualPercent, plannedPercent = DemoSchedule.report.earnedValue!!.plannedPercent,
    delayDays = DemoSchedule.report.projectDelayDays
)

@Composable
private fun ProjectsList(dark: Boolean = false) {
    AarushCPMTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BrandHeader("Projects")
                ProjectCard(demoCard, onClick = {}, onDelete = {})
            }
        }
    }
}

private fun phone(widthDp: Int, heightPx: Int = 1000) = DeviceConfig.PIXEL_5.copy(
    screenWidth = widthDp * 3 / 2, screenHeight = heightPx, xdpi = 240, ydpi = 240, density = Density.HIGH
)
private const val THEME = "android:Theme.Material.Light.NoActionBar"

class AppScreenshots {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone(393), theme = THEME)

    @Test fun final_1_projects() = paparazzi.snapshot { ProjectsList() }
    @Test fun final_2_overview() = paparazzi.snapshot { Frame(ProjectTab.Overview, "Overview") { OverviewBody() } }
    @Test fun final_3_boq() = paparazzi.snapshot {
        Frame(ProjectTab.BOQ, "BOQ") {
            val rcc = "003-Reinforced Cement Concrete"
            BOQSheet(DemoBoq.items, DemoBoq.coefficients, DemoBoq.floorOrder, BOQSortMode.NOTATIONS, {}, setOf(rcc), {},
                setOf("$rcc|Columns"), {}, null, {}, {}, {}, {}, {}, {})
        }
    }
    @Test fun final_4_schedule() = paparazzi.snapshot {
        Frame(ProjectTab.Schedule, "Schedule") {
            Column { ScheduleTopRow(ScheduleSection.OVERVIEW, {}, true, {}, {}, {}, {}, {}); ScheduleOverview(DemoSchedule.report, onOpenActivity = {}) }
        }
    }
    @Test fun final_5_expenses_budget() = paparazzi.snapshot { Frame(ProjectTab.Expenses, "Budget tracking") { BudgetBody() } }
    @Test fun final_6_more() = paparazzi.snapshot { Frame(ProjectTab.More, "More") { MoreList(onOpen = {}) } }
    @Test fun final_7_overview_dark() = paparazzi.snapshot { Frame(ProjectTab.Overview, "Overview", dark = true) { OverviewBody() } }
}

class AppTallScreenshots {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone(393, heightPx = 2600), theme = THEME)

    @Test fun final_2b_overview_full() = paparazzi.snapshot { Frame(ProjectTab.Overview, "Overview") { OverviewBody() } }
}

class AppNarrowScreenshots {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone(320), theme = THEME)

    @Test fun final_narrow320_projects() = paparazzi.snapshot { ProjectsList() }
    @Test fun final_narrow320_overview() = paparazzi.snapshot { Frame(ProjectTab.Overview, "Overview") { OverviewBody() } }
    @Test fun final_narrow320_more() = paparazzi.snapshot { Frame(ProjectTab.More, "More") { MoreList(onOpen = {}) } }
}

/** Wide (tablet / desktop-class window) check of the same layout. */
class AppWideScreenshots {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 1200, screenHeight = 1000, xdpi = 160, ydpi = 160, density = Density.MEDIUM), theme = THEME)

    @Test fun final_wide1200_overview() = paparazzi.snapshot { Frame(ProjectTab.Overview, "Overview") { OverviewBody() } }
}
