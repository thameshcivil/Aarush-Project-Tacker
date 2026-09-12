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
        ProjectAreaComponent::class,
        BOQNotation::class,
        MaterialRateCard::class
    ],
    version = 6,
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
    abstract fun boqNotationDao(): BOQNotationDao
    abstract fun materialRateCardDao(): MaterialRateCardDao

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

        /** v2 → v3: adds boq_notations (the item-code dropdown source) and material_rates
         *  (the editable built-in rate card), plus five new columns on boq_items for the
         *  Nos-vs-L×B×D quantity entry mode. All additive, no data loss. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS boq_notations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        code TEXT NOT NULL,
                        description TEXT NOT NULL,
                        defaultUnit TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS material_rates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        unit TEXT NOT NULL,
                        rate REAL NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE boq_items ADD COLUMN quantityMode TEXT NOT NULL DEFAULT 'NOS'")
                db.execSQL("ALTER TABLE boq_items ADD COLUMN sets REAL NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE boq_items ADD COLUMN length REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE boq_items ADD COLUMN breadth REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE boq_items ADD COLUMN depth REAL NOT NULL DEFAULT 0")
            }
        }

        /** v3 → v4: adds boq_items.perMember for the unified "No. of Member × No Per Member
         *  × L × B × D" quantity calculator (replaces the old Nos-vs-LBD toggle). The old
         *  quantityMode column is left in place, unused — dropping columns needs a table
         *  rebuild in SQLite and there's nothing to gain by doing that here. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE boq_items ADD COLUMN perMember REAL NOT NULL DEFAULT 1")
            }
        }

        /** v4 → v5: adds expenses.direction (EXPENSE vs RECEIVED) for the redesigned expense
         *  entry form. Existing rows default to 'EXPENSE', preserving today's behavior. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN direction TEXT NOT NULL DEFAULT 'EXPENSE'")
            }
        }

        /** v5 → v6: adds material_rates.linkedVendorId — the vendor↔material-picker link
         *  used to log vendor payments through the Expense form's material dropdown instead
         *  of a separate vendor selector. Additive, no data loss. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE material_rates ADD COLUMN linkedVendorId INTEGER")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aarush_cpm.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build().also { INSTANCE = it }
            }
    }
}
