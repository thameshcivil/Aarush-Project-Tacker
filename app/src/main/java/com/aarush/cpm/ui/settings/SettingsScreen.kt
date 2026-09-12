package com.aarush.cpm.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.backup.DatabaseBackupHelper
import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.repository.SampleDataSeeder
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val sampleDataSeeder: SampleDataSeeder,
    private val database: AppDatabase
) : ViewModel() {
    var isSeeding by mutableStateOf(false)
        private set
    var isBackingUp by mutableStateOf(false)
        private set
    var isRestoring by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set

    fun loadDemoProject(onDone: () -> Unit) {
        viewModelScope.launch {
            isSeeding = true
            sampleDataSeeder.seedDemoProject()
            isSeeding = false
            onDone()
        }
    }

    fun backup(context: android.content.Context, destination: Uri) {
        viewModelScope.launch {
            isBackingUp = true
            statusMessage = null
            val result = DatabaseBackupHelper.backupTo(context, destination, database)
            isBackingUp = false
            statusMessage = result.fold(
                onSuccess = { "Backup saved. Keep it somewhere safe — Drive, Downloads, wherever you like." },
                onFailure = { "Backup failed: ${it.message}" }
            )
        }
    }

    /** On success, this deliberately does not just "reload" — see SettingsScreen for the
     *  full-process restart that follows, which is required for the restored data to
     *  actually take effect (see DatabaseBackupHelper's restoreFrom doc for why). */
    fun restore(context: android.content.Context, source: Uri, onRestored: () -> Unit) {
        viewModelScope.launch {
            isRestoring = true
            statusMessage = null
            val result = DatabaseBackupHelper.restoreFrom(context, source)
            isRestoring = false
            result.fold(
                onSuccess = { onRestored() },
                onFailure = { statusMessage = "Restore failed: ${it.message}" }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var showRestoreConfirm by remember { mutableStateOf<Uri?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { viewModel.backup(context, it) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { showRestoreConfirm = it }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("Backup & Restore", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Uninstalling the app deletes its data — back up regularly, and always " +
                    "before uninstalling or reinstalling, so nothing gets lost. This saves " +
                    "a file to a location you choose (Downloads, Google Drive, a USB drive), " +
                    "which is what makes it survive an uninstall.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { backupLauncher.launch(DatabaseBackupHelper.suggestedFileName()) },
                enabled = !viewModel.isBackingUp && !viewModel.isRestoring,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (viewModel.isBackingUp) "Backing up…" else "Back up now")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                enabled = !viewModel.isBackingUp && !viewModel.isRestoring,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (viewModel.isRestoring) "Restoring…" else "Restore from backup")
            }
            viewModel.statusMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(24.dp))
            Text("Defaults", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Thumb-rule percentages, material/labour splits, coefficients and waste % are all " +
                    "editable per-project from the project's cost distribution and BOQ screens.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            Text("Demo data", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { viewModel.loadDemoProject {} }, enabled = !viewModel.isSeeding) {
                Text(if (viewModel.isSeeding) "Loading…" else "Load sample demo project")
            }
            Spacer(Modifier.height(24.dp))
            Text("About", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Aarush Construction Project Management System — v1.0.0", style = MaterialTheme.typography.bodySmall)
            Text("Offline-first. All data stored locally on this device.", style = MaterialTheme.typography.bodySmall)
        }
    }

    showRestoreConfirm?.let { uri ->
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = null },
            title = { Text("Restore from backup?") },
            text = { Text("This replaces everything currently in the app with the backup's data. This can't be undone. The app will restart afterward.") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = null
                    viewModel.restore(context, uri) { restartApp(context) }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = null }) { Text("Cancel") } }
        )
    }
}

/** Full process restart — required after restore swaps the database file out from under
 *  every already-open connection/repository/ViewModel; simply popping back to a screen
 *  would still be reading from stale, now-closed state. */
private fun restartApp(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    (context as? Activity)?.finish()
    Runtime.getRuntime().exit(0)
}
