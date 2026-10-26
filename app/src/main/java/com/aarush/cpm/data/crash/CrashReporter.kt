package com.aarush.cpm.data.crash

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Keeps the details of the last crash so they can be shown (and copied) the next time the app
 * opens. "The app keeps stopping" says nothing about the cause; this report does.
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
                File(app.filesDir, FILE).writeText(
                    "Aarush CPM $version · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "Time: ${java.text.SimpleDateFormat("dd MMM yyyy, h:mm:ss a", java.util.Locale.US).format(java.util.Date())}\n" +
                        "Thread: ${thread.name}\n\n" + trace.take(12_000)
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The saved report, or null if the app didn't crash since the report was dismissed. */
    fun lastReport(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) { File(context.filesDir, FILE).delete() }
}
