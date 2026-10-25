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
        MaterialRateCard::class,
        ActivityProgressLog::class,
        VendorWork::class,
        TeamDevice::class,
        ProjectMember::class
    ],
    version = AppDatabase.VERSION,
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
    abstract fun activityProgressLogDao(): ActivityProgressLogDao
    abstract fun vendorWorkDao(): VendorWorkDao
    abstract fun teamDao(): TeamDao

    companion object {
        /** File name of the underlying SQLite database — also used by the backup/restore
         *  feature (DatabaseBackupHelper) to locate the live file via context.getDatabasePath(). */
        const val DB_NAME = "aarush_cpm.db"

        /** Current schema version. Also used by restore-from-backup to reject a backup made
         *  by a newer app version (Room cannot downgrade a schema). */
        const val VERSION = 10

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

        /** v6 → v7: adds expenses.freightAmount — the freight/unloading portion now folded
         *  into an expense's totalAmount rather than stored as its own separate line. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN freightAmount REAL NOT NULL DEFAULT 0")
            }
        }

        /**
         * v7 → v8:
         *  1. Rebuilds boq_items. MIGRATION_2_3 added a `quantityMode` column that the entity
         *     dropped in v4 — Room's schema validation rejects the extra column, so any install
         *     (or restored backup) that ever passed through v3 crashed on open. The rebuild
         *     copies every row into the exact v8 shape (minus quantityMode, plus `floor`).
         *  2. Schedule module: new columns on schedule_activities / schedule_dependencies,
         *     the activity_progress_logs table, and material_usages.progressLogId.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS boq_items_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        itemCode TEXT NOT NULL,
                        category TEXT NOT NULL,
                        description TEXT NOT NULL,
                        unit TEXT NOT NULL,
                        quantity REAL NOT NULL,
                        rate REAL NOT NULL,
                        amount REAL NOT NULL,
                        labourQuantity REAL NOT NULL,
                        labourRate REAL NOT NULL,
                        labourAmount REAL NOT NULL,
                        materialType TEXT NOT NULL,
                        wastePercent REAL NOT NULL,
                        sets REAL NOT NULL,
                        perMember REAL NOT NULL,
                        length REAL NOT NULL,
                        breadth REAL NOT NULL,
                        depth REAL NOT NULL,
                        notes TEXT NOT NULL,
                        floor TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO boq_items_new (id, projectId, itemCode, category, description, unit, quantity,
                        rate, amount, labourQuantity, labourRate, labourAmount, materialType, wastePercent,
                        sets, perMember, length, breadth, depth, notes, floor, createdAt)
                    SELECT id, projectId, itemCode, category, description, unit, quantity,
                        rate, amount, labourQuantity, labourRate, labourAmount, materialType, wastePercent,
                        sets, perMember, length, breadth, depth, notes, '', createdAt
                    FROM boq_items
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE boq_items")
                db.execSQL("ALTER TABLE boq_items_new RENAME TO boq_items")

                val activityColumns = listOf(
                    "phase TEXT NOT NULL DEFAULT ''",
                    "sequence INTEGER NOT NULL DEFAULT 0",
                    "floorLabel TEXT NOT NULL DEFAULT ''",
                    "templateKey TEXT NOT NULL DEFAULT ''",
                    "category TEXT NOT NULL DEFAULT 'OTHER_MISC'",
                    "linkedNotationCode TEXT NOT NULL DEFAULT ''",
                    "boqSharePercent REAL NOT NULL DEFAULT 100",
                    "quantityOverride REAL",
                    "durationDays INTEGER NOT NULL DEFAULT 1",
                    "isDurationManual INTEGER NOT NULL DEFAULT 0",
                    "productivityPerDay REAL NOT NULL DEFAULT 0",
                    "budgetWeight REAL NOT NULL DEFAULT 1",
                    "budgetOverride REAL",
                    "completedQuantity REAL NOT NULL DEFAULT 0",
                    "isMilestone INTEGER NOT NULL DEFAULT 0",
                    "notes TEXT NOT NULL DEFAULT ''"
                )
                activityColumns.forEach { db.execSQL("ALTER TABLE schedule_activities ADD COLUMN $it") }
                db.execSQL("ALTER TABLE schedule_dependencies ADD COLUMN lagDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS activity_progress_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        activityId INTEGER NOT NULL,
                        date INTEGER NOT NULL,
                        quantityDone REAL NOT NULL,
                        percentComplete REAL,
                        labourCount INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE material_usages ADD COLUMN progressLogId INTEGER")
            }
        }

        /** v8 → v9: compound wall. Adds project_area_components.isCompoundWall / isLumpSum
         *  (existing rows: ordinary building rows) and the vendor_works table (extra work on a
         *  vendor's contract). Additive only, no data loss. */
        /** SQL for v8 → v9, kept as constants so a unit test can check them against the
         *  schema Room generates from the entities (a mismatch would crash on open). */
        internal val SQL_8_9_ADD_AREA_COLUMNS = listOf(
            "ALTER TABLE project_area_components ADD COLUMN isCompoundWall INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE project_area_components ADD COLUMN isLumpSum INTEGER NOT NULL DEFAULT 0"
        )
        internal const val SQL_8_9_CREATE_VENDOR_WORKS =
            "CREATE TABLE IF NOT EXISTS vendor_works (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, projectId INTEGER NOT NULL, vendorId INTEGER NOT NULL, " +
                "label TEXT NOT NULL, isLumpSum INTEGER NOT NULL, quantity REAL NOT NULL, rate REAL NOT NULL, " +
                "amount REAL NOT NULL, createdAt INTEGER NOT NULL)"

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                SQL_8_9_ADD_AREA_COLUMNS.forEach { db.execSQL(it) }
                db.execSQL(SQL_8_9_CREATE_VENDOR_WORKS)
            }
        }

        /** v9 → v10: team roles. Adds team_devices (one row per phone: name + role) and
         *  project_members (which phones work on which project, with a lead), plus a nullable
         *  enteredBy device ID on expenses and progress logs. Additive only; existing rows get
         *  enteredBy = NULL and the first phone to open v10 registers itself as Owner. */
        internal const val SQL_9_10_CREATE_TEAM_DEVICES =
            "CREATE TABLE IF NOT EXISTS team_devices (deviceId TEXT NOT NULL, name TEXT NOT NULL, role TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL, lastSeenAt INTEGER NOT NULL, PRIMARY KEY(deviceId))"
        internal const val SQL_9_10_CREATE_PROJECT_MEMBERS =
            "CREATE TABLE IF NOT EXISTS project_members (projectId INTEGER NOT NULL, deviceId TEXT NOT NULL, " +
                "isLead INTEGER NOT NULL, PRIMARY KEY(projectId, deviceId))"
        internal val SQL_9_10_ADD_ENTERED_BY = listOf(
            "ALTER TABLE expenses ADD COLUMN enteredBy TEXT",
            "ALTER TABLE activity_progress_logs ADD COLUMN enteredBy TEXT"
        )

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(SQL_9_10_CREATE_TEAM_DEVICES)
                db.execSQL(SQL_9_10_CREATE_PROJECT_MEMBERS)
                SQL_9_10_ADD_ENTERED_BY.forEach { db.execSQL(it) }
            }
        }

        /** Cloud sync bookkeeping (change-tracking triggers and the sync_* tables) is kept
         *  outside Room's entities and (re)created on every open, so it needs no schema
         *  version bump and a restored backup gets it too. See data/sync/SyncEngine. */
        private object SyncSetupCallback : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                com.aarush.cpm.data.sync.SyncEngine(com.aarush.cpm.data.sync.RoomSyncSql(db)).ensureSchema()
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .addCallback(SyncSetupCallback)
                    .build().also { INSTANCE = it }
            }

        /** Closes and forgets the current singleton. Used by restore-from-backup, which
         *  replaces the live database file on disk out from under any open connection —
         *  the app process is restarted right after, so nothing tries to use a stale
         *  AppDatabase/DAO reference against the swapped-out file. */
        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
