package com.aarush.cpm.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.aarush.cpm.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Backs up / restores the whole app database as a single file, written to (and read from)
 * a location the person picks via the system file picker — that's what survives an
 * uninstall. A raw SQLite copy captures every table automatically.
 *
 * Restore is defensive (it used to overwrite the live database with whatever file was
 * picked): the file is first copied to a temp file and validated — SQLite header, a
 * readable schema containing this app's tables, and a schema version this build can open —
 * and the current database is kept as a safety copy that is put back if anything fails.
 */
object DatabaseBackupHelper {

    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun suggestedFileName(): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())
        return "AarushCPM_Backup_$stamp.db"
    }

    private fun liveDatabaseFile(context: Context): File = context.getDatabasePath(AppDatabase.DB_NAME)

    /** Flushes Room's write-ahead log into the main .db file so the copy is complete. */
    private fun checkpointWal(db: AppDatabase) {
        db.query("PRAGMA wal_checkpoint(FULL)", null).close()
    }

    suspend fun backupTo(context: Context, destination: Uri, db: AppDatabase): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkpointWal(db)
            val dbFile = liveDatabaseFile(context)
            val output = context.contentResolver.openOutputStream(destination)
                ?: return@withContext Result.failure(IllegalStateException("Could not open the chosen location for writing."))
            output.use { out ->
                FileInputStream(dbFile).use { input -> input.copyTo(out) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Validates a candidate file; returns an error message, or null if it's a usable backup. */
    internal fun validate(file: File): String? {
        if (file.length() < 1024) return "That file is too small to be an Aarush CPM backup."
        val header = ByteArray(16)
        FileInputStream(file).use { if (it.read(header) != 16) return "That file isn't a database backup." }
        if (!header.contentEquals(SQLITE_HEADER)) return "That file isn't an Aarush CPM backup (not a SQLite database)."
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { sql ->
                val version = sql.version
                if (version > AppDatabase.VERSION) return "This backup was made by a newer version of the app (schema $version). Update the app first."
                if (version < 1) return "That database has no version — it isn't an Aarush CPM backup."
                sql.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('projects','expenses','boq_items')", null).use { c ->
                    if (c.count < 3) return "That database doesn't contain Aarush CPM data."
                }
            }
            null
        } catch (e: Exception) {
            "Could not read that backup: ${e.message}"
        }
    }

    /** Replaces the live database with a validated backup. The app must restart afterward
     *  (see SettingsScreen) — older migrations then upgrade an older backup automatically. */
    suspend fun restoreFrom(context: Context, source: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val dbFile = liveDatabaseFile(context)
        val candidate = File(context.cacheDir, "restore_candidate.db")
        val safety = File(dbFile.path + ".pre-restore")
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: return@withContext Result.failure(IllegalStateException("Could not open the chosen backup file."))
            input.use { inp -> FileOutputStream(candidate).use { out -> inp.copyTo(out) } }
            validate(candidate)?.let { msg -> candidate.delete(); return@withContext Result.failure(IllegalArgumentException(msg)) }

            // Flush and close the live database, keep a safety copy, then swap.
            AppDatabase.getInstance(context).let { runCatching { checkpointWal(it) } }
            AppDatabase.closeInstance()
            if (dbFile.exists()) dbFile.copyTo(safety, overwrite = true)
            try {
                dbFile.parentFile?.mkdirs()
                candidate.copyTo(dbFile, overwrite = true)
                File(dbFile.path + "-wal").delete()
                File(dbFile.path + "-shm").delete()
                File(dbFile.path + "-journal").delete()
            } catch (e: Exception) {
                if (safety.exists()) safety.copyTo(dbFile, overwrite = true)
                throw e
            }
            candidate.delete()
            Result.success(Unit)
        } catch (e: Exception) {
            candidate.delete()
            Result.failure(e)
        }
    }
}
