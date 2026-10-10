package com.aarush.cpm.ui.project

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aarush.cpm.ui.boq.BOQTabContent
import com.aarush.cpm.ui.boq.BOQViewModel
import com.aarush.cpm.ui.common.AppViewModelFactory
import com.aarush.cpm.ui.common.SectionLabel
import com.aarush.cpm.ui.expense.ExpenseTabContent
import com.aarush.cpm.ui.expense.ExpenseViewModel
import com.aarush.cpm.ui.material.MaterialStatusTabContent
import com.aarush.cpm.ui.material.MaterialStatusViewModel
import com.aarush.cpm.ui.schedule.ScheduleTabContent
import com.aarush.cpm.ui.schedule.ScheduleViewModel
import com.aarush.cpm.ui.settings.ProjectSettingsTabContent
import com.aarush.cpm.ui.settings.ProjectSettingsViewModel
import com.aarush.cpm.ui.theme.SiteTheme
import com.aarush.cpm.ui.vendor.VendorTabContent
import com.aarush.cpm.ui.vendor.VendorViewModel

/** The five primary destinations (redesign Phase 5; was seven scrolling tabs). */
internal enum class ProjectTab(val label: String, val icon: ImageVector) {
    Overview("Overview", Icons.Filled.Dashboard),
    BOQ("BOQ", Icons.AutoMirrored.Filled.ListAlt),
    Schedule("Schedule", Icons.Filled.CalendarMonth),
    Expenses("Expenses", Icons.Filled.Receipt),
    More("More", Icons.Filled.MoreHoriz)
}

/** Destinations under More: the same screens that used to be tabs 4, 6 and 7. */
internal enum class MoreDestination(val label: String, val hint: String, val icon: ImageVector) {
    Materials("Materials", "Stock, purchases and labour contract balances", Icons.Filled.Inventory),
    Vendors("Vendors", "Contracts, rates and payments", Icons.Filled.Engineering),
    Settings("Project settings", "Details, cost distribution, coefficients and rates", Icons.Filled.Tune)
}

/** The Expenses tab's two views: the expense ledger and Budget tracking. */
internal enum class MoneyView(val label: String) { Expenses("Expenses"), Budget("Budget") }

/** One project's whole workspace: shared top bar, five-destination bottom navigation. Tabs
 *  are in-screen state (not routes), so app routes and back behaviour are unchanged; Back
 *  inside a More destination returns to the More list first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(projectId: Long, factory: AppViewModelFactory, onBack: () -> Unit) {
    var selectedTab by rememberSaveable { mutableStateOf(ProjectTab.Overview) }
    var moreDestination by rememberSaveable { mutableStateOf<MoreDestination?>(null) }
    var moneyView by rememberSaveable { mutableStateOf(MoneyView.Expenses) }
    val snackbarHostState = remember { SnackbarHostState() }

    val homeViewModel: ProjectDetailViewModel = viewModel(factory = factory)
    val scheduleViewModel: ScheduleViewModel = viewModel(factory = factory)
    val materialStatusViewModel: MaterialStatusViewModel = viewModel(factory = factory)
    val boqViewModel: BOQViewModel = viewModel(factory = factory)
    val expenseViewModel: ExpenseViewModel = viewModel(factory = factory)
    val vendorViewModel: VendorViewModel = viewModel(factory = factory)
    val settingsViewModel: ProjectSettingsViewModel = viewModel(factory = factory)
    val projectName by homeViewModel.projectName.collectAsState()
    LaunchedEffect(projectId) { homeViewModel.observeName(projectId) }

    val inMoreDestination = selectedTab == ProjectTab.More && moreDestination != null
    BackHandler(enabled = inMoreDestination) { moreDestination = null }
    val title = when {
        inMoreDestination -> moreDestination!!.label
        selectedTab == ProjectTab.Expenses && moneyView == MoneyView.Budget -> "Budget tracking"
        else -> selectedTab.label
    }

    Scaffold(
        topBar = {
            ProjectTopBar(projectName, title, backToMore = inMoreDestination,
                onBack = { if (inMoreDestination) moreDestination = null else onBack() })
        },
        bottomBar = {
            ProjectNavigationBar(selectedTab = selectedTab, onSelect = { tab ->
                // Re-tapping More returns to its list.
                if (tab == ProjectTab.More && selectedTab == ProjectTab.More) moreDestination = null
                selectedTab = tab
            })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                ProjectTab.Overview -> ProjectOverviewTabContent(
                    projectId = projectId, viewModel = homeViewModel,
                    onOpenSchedule = { selectedTab = ProjectTab.Schedule },
                    onOpenBudget = { moneyView = MoneyView.Budget; selectedTab = ProjectTab.Expenses }
                )
                ProjectTab.BOQ -> BOQTabContent(projectId = projectId, viewModel = boqViewModel, snackbarHostState = snackbarHostState)
                ProjectTab.Schedule -> ScheduleTabContent(projectId = projectId, viewModel = scheduleViewModel, snackbarHostState = snackbarHostState)
                ProjectTab.Expenses -> Column(Modifier.fillMaxSize()) {
                    MoneyViewSwitch(moneyView, onSelect = { moneyView = it })
                    when (moneyView) {
                        MoneyView.Expenses -> ExpenseTabContent(projectId = projectId, viewModel = expenseViewModel, snackbarHostState = snackbarHostState)
                        MoneyView.Budget -> BudgetTrackingTabContent(projectId = projectId, viewModel = homeViewModel, snackbarHostState = snackbarHostState)
                    }
                }
                ProjectTab.More -> when (moreDestination) {
                    null -> MoreList(onOpen = { moreDestination = it })
                    MoreDestination.Materials -> MaterialStatusTabContent(projectId = projectId, viewModel = materialStatusViewModel)
                    MoreDestination.Vendors -> VendorTabContent(projectId = projectId, viewModel = vendorViewModel, snackbarHostState = snackbarHostState)
                    MoreDestination.Settings -> ProjectSettingsTabContent(projectId = projectId, viewModel = settingsViewModel, snackbarHostState = snackbarHostState)
                }
            }
        }
    }
}

/** Logo mark, project name and the current destination; Back goes to the project list, or to
 *  the More list from inside Materials / Vendors / Project settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectTopBar(projectName: String, title: String, backToMore: Boolean, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.aarush.cpm.ui.common.AarushMark(Modifier.height(24.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    SectionLabel(projectName.ifBlank { "Project" })
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (backToMore) "Back to More" else "Back to projects")
            }
        }
    )
}

/** Material bottom navigation with five fixed destinations; the selected one gets the
 *  maroon-tint indicator (theme's secondaryContainer) and a filled label. */
@Composable
internal fun ProjectNavigationBar(selectedTab: ProjectTab, onSelect: (ProjectTab) -> Unit) {
    // Narrow phones (< 360dp): the smaller label size so all five labels fit unclipped.
    val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360
    val labelStyle = if (narrow) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    Column {
        HorizontalDivider(color = SiteTheme.colors.hairline)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
            ProjectTab.values().forEach { tab ->
                NavigationBarItem(
                    selected = tab == selectedTab,
                    onClick = { onSelect(tab) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(tab.label, style = labelStyle, maxLines = 1, softWrap = false) },
                    alwaysShowLabel = true
                )
            }
        }
    }
}

/** Expenses | Budget switch at the top of the Expenses tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoneyViewSwitch(view: MoneyView, onSelect: (MoneyView) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
        val all = MoneyView.values()
        all.forEachIndexed { i, v ->
            SegmentedButton(
                selected = view == v, onClick = { onSelect(v) },
                shape = SegmentedButtonDefaults.itemShape(i, all.size, baseShape = MaterialTheme.shapes.small)
            ) { Text(v.label, maxLines = 1) }
        }
    }
}

/** The More list: Materials, Vendors, Project settings (48dp+ rows, hairline dividers). */
@Composable
internal fun MoreList(onOpen: (MoreDestination) -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        MoreDestination.values().forEachIndexed { i, d ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = { onOpen(d) }).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(d.icon, contentDescription = null, tint = SiteTheme.colors.muted)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.label, style = MaterialTheme.typography.titleSmall)
                    Text(d.hint, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SiteTheme.colors.muted)
            }
            if (i < MoreDestination.values().lastIndex) HorizontalDivider(Modifier.padding(start = 56.dp), color = SiteTheme.colors.hairline)
        }
    }
}
