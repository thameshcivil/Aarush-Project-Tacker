package com.aarush.cpm.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Long, val versionName: String, val notes: String, val apkUrl: String)

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateState
    /** Android must allow this app to install updates (asked once per phone). */
    data class NeedsPermission(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * In-app updates. CI publishes every build from main as a GitHub release "build-<n>" with the
 * APK attached (version code 100 + n, see app/build.gradle.kts). The app compares that with
 * its own version code, downloads the APK and opens Android's installer. All builds are
 * signed with the same key, so the update installs over the app and keeps its data.
 */
class AppUpdater(private val context: Context, private val scope: kotlinx.coroutines.CoroutineScope) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    val currentVersionCode: Long = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }.getOrDefault(0L)
    val currentVersionName: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    private var lastCheck = 0L

    /** Looks for a newer build. [quiet]: at app start, at most every few hours, no error shown. */
    suspend fun check(quiet: Boolean = false) {
        if (quiet && System.currentTimeMillis() - lastCheck < 3 * 60 * 60 * 1000L) return
        if (_state.value is UpdateState.Downloading) return
        lastCheck = System.currentTimeMillis()
        if (!quiet) _state.value = UpdateState.Checking
        val result = runCatching { withContext(Dispatchers.IO) { fetchLatest() } }
        _state.value = result.fold(
            onSuccess = { info -> if (info != null && info.versionCode > currentVersionCode) UpdateState.Available(info) else UpdateState.UpToDate },
            onFailure = { if (quiet) UpdateState.Idle else UpdateState.Failed("Couldn't check for updates. Check the internet connection.") }
        )
    }

    private fun fetchLatest(): UpdateInfo? {
        val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 15_000; readTimeout = 15_000
        }
        val json = conn.inputStream.bufferedReader().use { it.readText() }.let(::JSONObject)
        val run = json.optString("tag_name").removePrefix("build-").toLongOrNull() ?: return null
        val assets = json.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") } ?: return null
        return UpdateInfo(
            versionCode = 100 + run,
            versionName = "1.2.$run",
            notes = json.optString("body"),
            apkUrl = apk.getString("browser_download_url")
        )
    }

    /** Starts the update in the app's own scope, so leaving the screen doesn't stop it. */
    fun update(info: UpdateInfo) { scope.launch { downloadAndInstall(info) } }

    /** Downloads the update, then opens the installer (or asks for install permission first). */
    suspend fun downloadAndInstall(info: UpdateInfo) {
        if (!canInstall()) { _state.value = UpdateState.NeedsPermission(info); return }
        _state.value = UpdateState.Downloading(info, 0)
        val file = runCatching { withContext(Dispatchers.IO) { download(info) } }.getOrElse {
            _state.value = UpdateState.Failed("Download failed. Check the internet connection and try again.")
            return
        }
        _state.value = UpdateState.Available(info)
        install(file)
    }

    private fun download(info: UpdateInfo): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "AarushCPM-${info.versionName}.apk")
        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true   // GitHub redirects the download to its file server
            connectTimeout = 20_000; readTimeout = 60_000
        }
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done += n
                    if (total > 0) _state.value = UpdateState.Downloading(info, (done * 100 / total).toInt())
                }
            }
        }
        return out
    }

    private fun canInstall() = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens Android's "Install unknown apps" switch for this app. */
    fun openInstallPermission() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        private const val LATEST = "https://api.github.com/repos/thameshcivil/Aarush-Project-Tacker/releases/latest"
    }
}
