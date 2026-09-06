package com.aarush.cpm.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
        AppSettings::class
    ],
    version = 1,
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

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aarush_cpm.db"
                ).build().also { INSTANCE = it }
            }
    }
}
