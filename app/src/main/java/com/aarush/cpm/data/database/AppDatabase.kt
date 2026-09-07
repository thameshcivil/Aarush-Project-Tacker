package com.aarush.cpm.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aarush.cpm.data.dao.*
import com.aarush.cpm.data.entity.*

@Database(
    entities = [
        User::class,
        Project::class,
        CostAllocation::class,
        Vendor::class,
        BOQItem::class,
        Material::class,
        MaterialCoefficient::class,
        MaterialPurchase::class,
        MaterialUsage::class,
        Expense::class,
        LabourRate::class,
        LabourEntry::class,
        ClientPayment::class,
        ScheduleActivity::class,
        ScheduleDependency::class,
        ProjectProgress::class,
        AppNotification::class,
        AppSettings::class,
        ProjectAreaComponent::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun costAllocationDao(): CostAllocationDao
    abstract fun vendorDao(): VendorDao
    abstract fun boqDao(): BOQDao
    abstract fun materialDao(): MaterialDao
    abstract fun materialCoefficientDao(): MaterialCoefficientDao
    abstract fun materialPurchaseDao(): MaterialPurchaseDao
    abstract fun materialUsageDao(): MaterialUsageDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun labourRateDao(): LabourRateDao
    abstract fun labourEntryDao(): LabourEntryDao
    abstract fun clientPaymentDao(): ClientPaymentDao
    abstract fun scheduleActivityDao(): ScheduleActivityDao
    abstract fun scheduleDependencyDao(): ScheduleDependencyDao
    abstract fun projectProgressDao(): ProjectProgressDao
    abstract fun notificationDao(): NotificationDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun userDao(): UserDao
    abstract fun projectAreaComponentDao(): ProjectAreaComponentDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** v1 → v2: adds the project_area_components table (multi area/rate line items per
         *  project) and a users.authProvider column (LOCAL vs GOOGLE accounts). Both additive,
         *  no data loss for existing installs. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS project_area_components (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        label TEXT NOT NULL,
                        areaSqft REAL NOT NULL,
                        ratePerSqft REAL NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "ALTER TABLE users ADD COLUMN authProvider TEXT NOT NULL DEFAULT 'LOCAL'"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aarush_cpm.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
    }
}
