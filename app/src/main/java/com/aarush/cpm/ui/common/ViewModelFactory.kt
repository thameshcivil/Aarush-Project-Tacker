package com.aarush.cpm.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.aarush.cpm.di.AppContainer

/** One small factory that knows how to build every ViewModel from the shared AppContainer.
 *  Keeps the app free of a DI framework while staying testable (container is swappable). */
class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return when {
            modelClass.isAssignableFrom(com.aarush.cpm.ui.login.LoginViewModel::class.java) ->
                com.aarush.cpm.ui.login.LoginViewModel(container.authRepository, container.authPreferencesRepository) as T

            modelClass.isAssignableFrom(com.aarush.cpm.ui.dashboard.DashboardViewModel::class.java) ->
                com.aarush.cpm.ui.dashboard.DashboardViewModel(container.projectRepository, container.projectSummaryRepository, container.sampleDataSeeder) as T

            modelClass.isAssignableFrom(com.aarush.cpm.ui.home.HomeViewModel::class.java) ->
                com.aarush.cpm.ui.home.HomeViewModel(
                    container.projectRepository, container.boqRepository, container.expenseRepository,
                    container.vendorRepository, container.clientPaymentRepository
                ) as T

            modelClass.isAssignableFrom(com.aarush.cpm.ui.project.CreateProjectViewModel::class.java) ->
                com.aarush.cpm.ui.project.CreateProjectViewModel(container.projectRepository, container.builtInDefaultsSeeder) as T

            modelClass.isAssignableFrom(com.aarush.cpm.ui.project.ProjectDetailViewModel::class.java) ->
                com.aarush.cpm.ui.project.ProjectDetailViewModel(
                    container.projectRepository, container.projectSummaryRepository, container.expenseRepository,
                    container.materialRepository, container.scheduleRepository, container.clientPaymentRepository
                ) as T

            modelClass.isAssignableFrom(com.aarush.cpm.ui.boq.BOQViewModel::class.java) ->
                com.aarush.cpm.ui.boq.BOQViewModel(container.boqRepository) as T

            modelClass.isAssignableFrom(com.aarush.cpm.ui.expense.ExpenseViewModel::class.java) ->
                com.aarush.cpm.ui.expense.ExpenseViewModel(container.expenseRepository, container.vendorRepository) as T

            modelClass.isAssignableFrom(com.aarush.cpm.ui.vendor.VendorViewModel::class.java) ->
                com.aarush.cpm.ui.vendor.VendorViewModel(container.vendorRepository, container.projectRepository) as T

            modelClass.isAssignableFrom(com.aarush.cpm.ui.payment.ClientPaymentViewModel::class.java) ->
                com.aarush.cpm.ui.payment.ClientPaymentViewModel(container.clientPaymentRepository, container.projectRepository) as T

            modelClass.isAssignableFrom(com.aarush.cpm.ui.settings.SettingsViewModel::class.java) ->
                com.aarush.cpm.ui.settings.SettingsViewModel(container.sampleDataSeeder) as T

            modelClass.isAssignableFrom(com.aarush.cpm.ui.settings.ProjectSettingsViewModel::class.java) ->
                com.aarush.cpm.ui.settings.ProjectSettingsViewModel(container.boqRepository, container.materialRepository) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
