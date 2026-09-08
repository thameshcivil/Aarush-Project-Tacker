package com.aarush.cpm.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.aarush.cpm.di.AppContainer
import com.aarush.cpm.ui.boq.BOQScreen
import com.aarush.cpm.ui.boq.BOQViewModel
import com.aarush.cpm.ui.common.AppViewModelFactory
import com.aarush.cpm.ui.dashboard.DashboardScreen
import com.aarush.cpm.ui.dashboard.DashboardViewModel
import com.aarush.cpm.ui.expense.ExpenseScreen
import com.aarush.cpm.ui.expense.ExpenseViewModel
import com.aarush.cpm.ui.login.LoginScreen
import com.aarush.cpm.ui.login.LoginViewModel
import com.aarush.cpm.ui.payment.ClientPaymentScreen
import com.aarush.cpm.ui.payment.ClientPaymentViewModel
import com.aarush.cpm.ui.project.CreateProjectScreen
import com.aarush.cpm.ui.project.CreateProjectViewModel
import com.aarush.cpm.ui.project.ProjectDetailScreen
import com.aarush.cpm.ui.project.ProjectDetailViewModel
import com.aarush.cpm.ui.settings.SettingsScreen
import com.aarush.cpm.ui.settings.SettingsViewModel
import com.aarush.cpm.ui.settings.ProjectSettingsScreen
import com.aarush.cpm.ui.settings.ProjectSettingsViewModel
import com.aarush.cpm.ui.vendor.VendorScreen
import com.aarush.cpm.ui.vendor.VendorViewModel

object Routes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val CREATE_PROJECT = "create_project"
    const val PROJECT_DETAIL = "project_detail/{projectId}"
    const val BOQ = "boq/{projectId}"
    const val EXPENSES = "expenses/{projectId}"
    const val VENDORS = "vendors/{projectId}"
    const val CLIENT_PAYMENTS = "client_payments/{projectId}"
    const val PROJECT_SETTINGS = "project_settings/{projectId}"
    const val SETTINGS = "settings"

    fun projectDetail(id: Long) = "project_detail/$id"
    fun boq(id: Long) = "boq/$id"
    fun expenses(id: Long) = "expenses/$id"
    fun vendors(id: Long) = "vendors/$id"
    fun clientPayments(id: Long) = "client_payments/$id"
    fun projectSettings(id: Long) = "project_settings/$id"
}

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val bottomTabs = listOf(
    BottomTab(Routes.DASHBOARD, "Projects", Icons.Filled.Home),
    BottomTab(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

@Composable
fun AarushNavGraph(appContainer: AppContainer) {
    val navController = rememberNavController()
    val factory = AppViewModelFactory(appContainer)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == Routes.DASHBOARD || currentRoute == Routes.SETTINGS

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
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
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN,
            modifier = Modifier.padding(if (showBottomBar) padding else androidx.compose.foundation.layout.PaddingValues(0.dp)
            )
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
                    onOpenProject = { id -> navController.navigate(Routes.projectDetail(id)) }
                )
            }
            composable(Routes.CREATE_PROJECT) {
                val vm: CreateProjectViewModel = viewModel(factory = factory)
                CreateProjectScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.navigate(Routes.projectDetail(id)) { popUpTo(Routes.DASHBOARD) }
                    }
                )
            }
            composable(
                Routes.PROJECT_DETAIL,
                arguments = listOf(navArgument("projectId") { type = NavType.LongType })
            ) { entry ->
                val projectId = entry.arguments?.getLong("projectId") ?: 0L
                val vm: ProjectDetailViewModel = viewModel(factory = factory)
                ProjectDetailScreen(
                    projectId = projectId,
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenBOQ = { navController.navigate(Routes.boq(projectId)) },
                    onOpenExpenses = { navController.navigate(Routes.expenses(projectId)) },
                    onOpenVendors = { navController.navigate(Routes.vendors(projectId)) },
                    onOpenClientPayments = { navController.navigate(Routes.clientPayments(projectId)) },
                    onOpenSettings = { navController.navigate(Routes.projectSettings(projectId)) }
                )
            }
            composable(
                Routes.BOQ,
                arguments = listOf(navArgument("projectId") { type = NavType.LongType })
            ) { entry ->
                val projectId = entry.arguments?.getLong("projectId") ?: 0L
                val vm: BOQViewModel = viewModel(factory = factory)
                BOQScreen(projectId = projectId, viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(
                Routes.EXPENSES,
                arguments = listOf(navArgument("projectId") { type = NavType.LongType })
            ) { entry ->
                val projectId = entry.arguments?.getLong("projectId") ?: 0L
                val vm: ExpenseViewModel = viewModel(factory = factory)
                ExpenseScreen(projectId = projectId, viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(
                Routes.VENDORS,
                arguments = listOf(navArgument("projectId") { type = NavType.LongType })
            ) { entry ->
                val projectId = entry.arguments?.getLong("projectId") ?: 0L
                val vm: VendorViewModel = viewModel(factory = factory)
                VendorScreen(projectId = projectId, viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(
                Routes.CLIENT_PAYMENTS,
                arguments = listOf(navArgument("projectId") { type = NavType.LongType })
            ) { entry ->
                val projectId = entry.arguments?.getLong("projectId") ?: 0L
                val vm: ClientPaymentViewModel = viewModel(factory = factory)
                ClientPaymentScreen(projectId = projectId, viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(factory = factory)
                SettingsScreen(viewModel = vm)
            }
            composable(
                Routes.PROJECT_SETTINGS,
                arguments = listOf(navArgument("projectId") { type = NavType.LongType })
            ) { entry ->
                val projectId = entry.arguments?.getLong("projectId") ?: 0L
                val vm: ProjectSettingsViewModel = viewModel(factory = factory)
                ProjectSettingsScreen(projectId = projectId, viewModel = vm, onBack = { navController.popBackStack() })
            }
        }
    }
}
