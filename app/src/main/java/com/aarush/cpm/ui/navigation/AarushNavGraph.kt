package com.aarush.cpm.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.aarush.cpm.di.AppContainer
import com.aarush.cpm.ui.common.AppViewModelFactory
import com.aarush.cpm.ui.common.LocalAccess
import com.aarush.cpm.ui.common.LocalTeam
import com.aarush.cpm.ui.team.TeamScreen
import com.aarush.cpm.ui.team.TeamViewModel
import com.aarush.cpm.ui.dashboard.DashboardScreen
import com.aarush.cpm.ui.dashboard.DashboardViewModel
import com.aarush.cpm.ui.login.LoginScreen
import com.aarush.cpm.ui.login.LoginViewModel
import com.aarush.cpm.ui.project.CreateProjectScreen
import com.aarush.cpm.ui.project.CreateProjectViewModel
import com.aarush.cpm.ui.project.ProjectScreen
import com.aarush.cpm.ui.settings.SettingsScreen
import com.aarush.cpm.ui.settings.SettingsViewModel

object Routes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val CREATE_PROJECT = "create_project"
    const val PROJECT = "project/{projectId}"
    const val SETTINGS = "settings"
    const val TEAM = "team"

    fun project(id: Long) = "project/$id"
}

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val bottomTabs = listOf(
    BottomTab(Routes.DASHBOARD, "Projects", Icons.Filled.Apartment),
    BottomTab(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AarushNavGraph(appContainer: AppContainer) {
    val navController = rememberNavController()
    val factory = AppViewModelFactory(appContainer)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == Routes.DASHBOARD || currentRoute == Routes.SETTINGS

    // Team roles: who this phone is and what it may change, for every screen below.
    val team by appContainer.accessRepository.team.collectAsState(initial = null)
    val access = team?.access ?: com.aarush.cpm.domain.access.Access.LOADING
    val preview = team?.preview

    CompositionLocalProvider(LocalTeam provides team, LocalAccess provides access) {
    Column(Modifier.fillMaxSize()) {
    if (preview != null) {
        PreviewBanner(preview.name, preview.role.label, onStop = { appContainer.accessRepository.stopPreview() })
    }
    Scaffold(
        modifier = if (preview != null) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                            // Default colours: maroon-tint indicator from the theme (gold is for "actual").
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN,
            modifier = Modifier.padding(if (showBottomBar) padding else androidx.compose.foundation.layout.PaddingValues(0.dp))
        ) {
            composable(Routes.LOGIN) {
                val vm: LoginViewModel = viewModel(factory = factory)
                LoginScreen(viewModel = vm, onLoginSuccess = {
                    navController.navigate(Routes.DASHBOARD) { popUpTo(Routes.LOGIN) { inclusive = true } }
                })
            }
            composable(Routes.DASHBOARD) {
                val vm: DashboardViewModel = viewModel(factory = factory)
                DashboardScreen(
                    viewModel = vm,
                    onAddProject = { navController.navigate(Routes.CREATE_PROJECT) },
                    onOpenProject = { id -> navController.navigate(Routes.project(id)) }
                )
            }
            composable(Routes.CREATE_PROJECT) {
                val vm: CreateProjectViewModel = viewModel(factory = factory)
                CreateProjectScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.navigate(Routes.project(id)) { popUpTo(Routes.DASHBOARD) }
                    }
                )
            }
            composable(
                Routes.PROJECT,
                arguments = listOf(navArgument("projectId") { type = NavType.LongType })
            ) { entry ->
                val projectId = entry.arguments?.getLong("projectId") ?: 0L
                ProjectScreen(projectId = projectId, factory = factory, onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(factory = factory)
                SettingsScreen(viewModel = vm, onSignOut = {
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }, onOpenTeam = { navController.navigate(Routes.TEAM) })
            }
            composable(Routes.TEAM) {
                val vm: TeamViewModel = viewModel(factory = factory)
                TeamScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
        }
    }
    }
    }
}

/** Shown on every screen while the Owner previews another role, so it is never forgotten. */
@Composable
private fun PreviewBanner(name: String, role: String, onStop: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Previewing as $name ($role)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            TextButton(onClick = onStop, modifier = Modifier.heightIn(min = 48.dp)) { Text("Stop") }
        }
    }
}
