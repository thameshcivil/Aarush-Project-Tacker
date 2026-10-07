package com.aarush.cpm.ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
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

private enum class ProjectTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Schedule("Schedule", Icons.Filled.CalendarMonth),
    BOQ("BOQ", Icons.AutoMirrored.Filled.ListAlt),
    MaterialStatus("Materials", Icons.Filled.Inventory),
    Expenses("Expenses", Icons.Filled.Receipt),
    Vendors("Vendors", Icons.Filled.Engineering),
    Settings("Settings", Icons.Filled.Tune)
}

/** One project's whole workspace: a shared top bar + a horizontally scrollable tab bar
 *  (seven tabs don't fit Material's fixed NavigationBar legibly). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(projectId: Long, factory: AppViewModelFactory, onBack: () -> Unit) {
    var selectedTab by rememberSaveable { mutableStateOf(ProjectTab.Home) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        SectionLabel(projectName.ifBlank { "Project" })
                        Text(selectedTab.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        bottomBar = {
            ScrollableProjectBottomBar(selectedTab = selectedTab, onSelect = { selectedTab = it })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                ProjectTab.Home -> ProjectHomeTabContent(
                    projectId = projectId, viewModel = homeViewModel, snackbarHostState = snackbarHostState,
                    onOpenSchedule = { selectedTab = ProjectTab.Schedule }
                )
                ProjectTab.Schedule -> ScheduleTabContent(projectId = projectId, viewModel = scheduleViewModel, snackbarHostState = snackbarHostState)
                ProjectTab.MaterialStatus -> MaterialStatusTabContent(projectId = projectId, viewModel = materialStatusViewModel)
                ProjectTab.BOQ -> BOQTabContent(projectId = projectId, viewModel = boqViewModel, snackbarHostState = snackbarHostState)
                ProjectTab.Expenses -> ExpenseTabContent(projectId = projectId, viewModel = expenseViewModel, snackbarHostState = snackbarHostState)
                ProjectTab.Vendors -> VendorTabContent(projectId = projectId, viewModel = vendorViewModel, snackbarHostState = snackbarHostState)
                ProjectTab.Settings -> ProjectSettingsTabContent(projectId = projectId, viewModel = settingsViewModel, snackbarHostState = snackbarHostState)
            }
        }
    }
}

@Composable
private fun ScrollableProjectBottomBar(selectedTab: ProjectTab, onSelect: (ProjectTab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column {
            HorizontalDivider(color = SiteTheme.colors.hairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .horizontalScroll(rememberScrollState())
                    .height(68.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProjectTab.values().forEach { tab ->
                    val isSelected = tab == selectedTab
                    val contentColor = if (isSelected) MaterialTheme.colorScheme.onSurface else SiteTheme.colors.muted
                    Column(
                        modifier = Modifier
                            .width(78.dp)
                            .fillMaxHeight()
                            .clickable(onClick = { onSelect(tab) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Accent bar marks the active tab — the single orange on the bar.
                        Box(
                            Modifier.width(24.dp).height(3.dp)
                                .background(if (isSelected) SiteTheme.colors.accent else androidx.compose.ui.graphics.Color.Transparent)
                        )
                        Spacer(Modifier.height(6.dp))
                        Icon(tab.icon, contentDescription = tab.label, tint = if (isSelected) SiteTheme.colors.accent else contentColor)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
