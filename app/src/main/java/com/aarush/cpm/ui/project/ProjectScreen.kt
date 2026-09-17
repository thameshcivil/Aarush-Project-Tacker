package com.aarush.cpm.ui.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aarush.cpm.ui.boq.BOQTabContent
import com.aarush.cpm.ui.boq.BOQViewModel
import com.aarush.cpm.ui.common.AppViewModelFactory
import com.aarush.cpm.ui.expense.ExpenseTabContent
import com.aarush.cpm.ui.expense.ExpenseViewModel
import com.aarush.cpm.ui.material.MaterialStatusTabContent
import com.aarush.cpm.ui.material.MaterialStatusViewModel
import com.aarush.cpm.ui.settings.ProjectSettingsTabContent
import com.aarush.cpm.ui.settings.ProjectSettingsViewModel
import com.aarush.cpm.ui.vendor.VendorTabContent
import com.aarush.cpm.ui.vendor.VendorViewModel

private sealed class ProjectTab(val label: String, val icon: ImageVector) {
    data object Home : ProjectTab("Home", Icons.Filled.Home)
    data object MaterialStatus : ProjectTab("Material Status", Icons.Filled.Inventory)
    data object BOQ : ProjectTab("BOQ", Icons.Filled.ListAlt)
    data object Expenses : ProjectTab("Expenses", Icons.Filled.Receipt)
    data object Vendors : ProjectTab("Vendors", Icons.Filled.Engineering)
    data object Settings : ProjectTab("Settings", Icons.Filled.Tune)
}

private val projectTabs = listOf(
    ProjectTab.Home, ProjectTab.MaterialStatus, ProjectTab.BOQ, ProjectTab.Expenses, ProjectTab.Vendors, ProjectTab.Settings
)

/** One project's whole workspace: a single shared top bar + bottom tab bar, with Home
 *  positioned right next to Material Status/BOQ — Home holds the project details, budget vs
 *  actual, and a quick material summary; Material Status has the full per-material budget
 *  breakdown. Six tabs are too many for a standard fixed-width NavigationBar to show without
 *  squeezing labels unreadably, so the bar scrolls horizontally instead. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(projectId: Long, factory: AppViewModelFactory, onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf<ProjectTab>(ProjectTab.Home) }
    val snackbarHostState = remember { SnackbarHostState() }

    val homeViewModel: ProjectDetailViewModel = viewModel(factory = factory)
    val materialStatusViewModel: MaterialStatusViewModel = viewModel(factory = factory)
    val boqViewModel: BOQViewModel = viewModel(factory = factory)
    val expenseViewModel: ExpenseViewModel = viewModel(factory = factory)
    val vendorViewModel: VendorViewModel = viewModel(factory = factory)
    val settingsViewModel: ProjectSettingsViewModel = viewModel(factory = factory)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedTab.label) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        bottomBar = {
            ScrollableProjectBottomBar(selectedTab = selectedTab, onSelect = { selectedTab = it })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                ProjectTab.Home -> ProjectHomeTabContent(projectId = projectId, viewModel = homeViewModel, snackbarHostState = snackbarHostState)
                ProjectTab.MaterialStatus -> MaterialStatusTabContent(projectId = projectId, viewModel = materialStatusViewModel)
                ProjectTab.BOQ -> BOQTabContent(projectId = projectId, viewModel = boqViewModel, snackbarHostState = snackbarHostState)
                ProjectTab.Expenses -> ExpenseTabContent(projectId = projectId, viewModel = expenseViewModel, snackbarHostState = snackbarHostState)
                ProjectTab.Vendors -> VendorTabContent(projectId = projectId, viewModel = vendorViewModel, snackbarHostState = snackbarHostState)
                ProjectTab.Settings -> ProjectSettingsTabContent(projectId = projectId, viewModel = settingsViewModel)
            }
        }
    }
}

/** A custom bottom tab bar instead of Material3's NavigationBar — NavigationBar always
 *  squeezes every item to fit the screen width with no scroll option, which turns unreadable
 *  with six tabs. This scrolls horizontally instead, at fixed-width per tab. */
@Composable
private fun ScrollableProjectBottomBar(selectedTab: ProjectTab, onSelect: (ProjectTab) -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .height(80.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            projectTabs.forEach { tab ->
                val isSelected = tab == selectedTab
                val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .width(84.dp)
                        .fillMaxHeight()
                        .clickable(onClick = { onSelect(tab) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(tab.icon, contentDescription = tab.label, tint = contentColor)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
    }
}
