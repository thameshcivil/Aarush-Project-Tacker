package com.aarush.cpm.di

import android.content.Context
import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.repository.*

/**
 * Lightweight manual dependency container. No Hilt/Koin required to keep the project
 * simple to open and build, but every dependency is interface-shaped so a DI framework
 * can be dropped in later without touching ViewModels.
 */
class AppContainer(context: Context) {
    private val db: AppDatabase = AppDatabase.getInstance(context)

    /** Exposed (not just private) so the backup/restore feature can run a WAL checkpoint
     *  directly against the live connection before copying the database file. */
    val database: AppDatabase get() = db

    val authRepository by lazy { AuthRepository(db) }
    val authPreferencesRepository by lazy { AuthPreferencesRepository(context.applicationContext) }
    val projectRepository by lazy { ProjectRepository(db) }
    val boqRepository by lazy { BOQRepository(db) }
    val materialRepository by lazy { MaterialRepository(db) }
    val expenseRepository by lazy { ExpenseRepository(db) }
    val vendorRepository by lazy { VendorRepository(db) }
    val clientPaymentRepository by lazy { ClientPaymentRepository(db) }
    val scheduleRepository by lazy { ScheduleRepository(db) }
    val notificationRepository by lazy { NotificationRepository(db) }
    val projectSummaryRepository by lazy { ProjectSummaryRepository(db) }
    val sampleDataSeeder by lazy { SampleDataSeeder(db, projectRepository) }
    val builtInDefaultsSeeder by lazy { BuiltInDefaultsSeeder(db) }
}
