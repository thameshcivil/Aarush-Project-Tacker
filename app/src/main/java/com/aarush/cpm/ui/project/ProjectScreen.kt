package com.aarush.cpm.ui.project

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aarush.cpm.ui.boq.BOQTabContent
import com.aarush.cpm.ui.boq.BOQViewModel
import com.aarush.cpm.ui.common.AppViewModelFactory
import com.aarush.cpm.ui.expense.ExpenseTabContent
import com.aarush.cpm.ui.expense.ExpenseViewModel
import com.aarush.cpm.ui.settings.ProjectSettingsTabContent
import com.aarush.cpm.ui.settings.ProjectSettingsViewModel
import com.aarush.cpm.ui.vendor.VendorTabContent
import com.aarush.cpm.ui.vendor.VendorViewModel

private sealed class ProjectTab(val label: String, val icon: ImageVector) {
    data object Home : ProjectTab("Home", Icons.Filled.Home)
    data object BOQ : ProjectTab("BOQ", Icons.Filled.ListAlt)
    data object Expenses : ProjectTab("Expenses", Icons.Filled.Receipt)
    data object Vendors : ProjectTab("Vendors", Icons.Filled.Engineering)
    data object Settings : ProjectTab("Settings", Icons.Filled.Tune)
}

private val projectTabs = listOf(
    ProjectTab.Home, ProjectTab.BOQ, ProjectTab.Expenses, ProjectTab.Vendors, ProjectTab.Settings
)

/** One project's whole workspace: a single shared top bar + bottom tab bar, with Home
 *  positioned right next to BOQ — Home holds the project details, budget vs actual, and
 *  material status. Payments was folded into Expenses (the Expense/Received toggle there
 *  covers both money out and money in, so it no longer needs its own tab). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(projectId: Long, factory: AppViewModelFactory, onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf<ProjectTab>(ProjectTab.Home) }

    val homeViewModel: ProjectDetailViewModel = viewModel(factory = factory)
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
            NavigationBar {
                projectTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                ProjectTab.Home -> ProjectHomeTabContent(projectId = projectId, viewModel = homeViewModel)
                ProjectTab.BOQ -> BOQTabContent(projectId = projectId, viewModel = boqViewModel)
                ProjectTab.Expenses -> ExpenseTabContent(projectId = projectId, viewModel = expenseViewModel)
                ProjectTab.Vendors -> VendorTabContent(projectId = projectId, viewModel = vendorViewModel)
                ProjectTab.Settings -> ProjectSettingsTabContent(projectId = projectId, viewModel = settingsViewModel)
            }
        }
    }
}
