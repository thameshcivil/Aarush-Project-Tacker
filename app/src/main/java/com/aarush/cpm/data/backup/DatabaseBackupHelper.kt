package com.aarush.cpm.data.backup

import android.content.Context
import android.net.Uri
import com.aarush.cpm.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Backs up / restores the whole app database as a single file, written to (and read from)
 * a location the person picks via the system file picker — Downloads, Google Drive, a USB
 * drive, wherever. That's the key thing that makes this survive an uninstall: the app's own
 * storage (where Room's .db file normally lives) is wiped on uninstall, but a file saved
 * through the picker lives outside the app's private storage entirely.
 *
 * A raw SQLite file copy (rather than a JSON export) is used deliberately: it captures every
 * table automatically, including ones added after this helper was written, with no export
 * code to keep in sync as the schema grows.
 */
object DatabaseBackupHelper {

    fun suggestedFileName(): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())
        return "AarushCPM_Backup_$stamp.db"
    }

    private fun liveDatabaseFile(context: Context): File = context.getDatabasePath(AppDatabase.DB_NAME)

    /** Flushes Room's write-ahead log into the main .db file so the copy taken right after
     *  is complete and self-contained — otherwise recent writes could still be sitting in a
     *  separate -wal file that a plain copy of the .db alone would miss. */
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

    /** Replaces the live database file with the picked backup, then closes the current
     *  connection. Restoring only takes effect after the app process is restarted (see
     *  SettingsScreen) — any ViewModel/repository already holding the old connection would
     *  otherwise keep reading/writing the closed, swapped-out file. */
    suspend fun restoreFrom(context: Context, source: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dbFile = liveDatabaseFile(context)
            val input = context.contentResolver.openInputStream(source)
                ?: return@withContext Result.failure(IllegalStateException("Could not open the chosen backup file."))

            AppDatabase.closeInstance()

            dbFile.parentFile?.mkdirs()
            input.use { inp ->
                FileOutputStream(dbFile).use { out -> inp.copyTo(out) }
            }
            // Drop any leftover WAL/SHM files from the connection we just closed so Room
            // doesn't try to replay old journal data against the newly restored main file.
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
