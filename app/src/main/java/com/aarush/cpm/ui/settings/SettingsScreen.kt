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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.backup.DatabaseBackupHelper
import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.repository.AuthPreferencesRepository
import com.aarush.cpm.data.repository.ProjectRepository
import com.aarush.cpm.data.repository.SampleDataSeeder
import com.aarush.cpm.ui.common.AppCard
import com.aarush.cpm.ui.common.SectionLabel
import com.aarush.cpm.ui.theme.SiteTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val sampleDataSeeder: SampleDataSeeder,
    private val database: AppDatabase,
    private val projectRepository: ProjectRepository,
    private val authPreferences: AuthPreferencesRepository,
    private val cloudSync: com.aarush.cpm.data.sync.CloudSync,
    private val accessRepository: com.aarush.cpm.data.repository.AccessRepository
) : ViewModel() {
    // ---------------------------------------------------------------- cloud sync (Phase 2)
    val syncStatus = cloudSync.status
    /** Set after sign-in when the person must choose: upload this phone, or take the cloud copy. */
    var syncChoice by mutableStateOf<com.aarush.cpm.data.sync.CloudState?>(null)
        private set
    var syncWorking by mutableStateOf(false)
        private set

    fun turnOnSync(context: android.content.Context) {
        viewModelScope.launch {
            syncWorking = true
            statusMessage = null
            val account = com.aarush.cpm.ui.login.GoogleSignInHelper.signIn(context, context.getString(com.aarush.cpm.R.string.google_web_client_id))
            val token = account.getOrNull()?.idToken
            if (token.isNullOrBlank()) {
                statusMessage = "Google sign-in didn't finish: ${account.exceptionOrNull()?.message ?: "no account chosen"}"
                syncWorking = false
                return@launch
            }
            cloudSync.connect(token).fold(
                onSuccess = { state ->
                    if (state == com.aarush.cpm.data.sync.CloudState.RESUME) {
                        cloudSync.resume().onFailure { statusMessage = "Couldn't resume sync: ${it.message}" }
                    } else syncChoice = state
                },
                onFailure = { statusMessage = it.message }
            )
            syncWorking = false
        }
    }

    fun confirmFirstUpload() {
        syncChoice = null
        viewModelScope.launch {
            syncWorking = true
            statusMessage = cloudSync.uploadFirstCopy().fold(
                onSuccess = { "Cloud sync is on. This phone's projects are now the team's shared copy." },
                onFailure = { "Upload failed: ${it.message}. Nothing on this phone was changed; try again." }
            )
            syncWorking = false
        }
    }

    fun confirmUseCloudCopy() {
        syncChoice = null
        viewModelScope.launch {
            syncWorking = true
            statusMessage = cloudSync.replaceWithCloudCopy { accessRepository.registerThisDevice() }.fold(
                onSuccess = { "This phone now has the team's projects. A safety copy of its old data was saved as ${it.name} " +
                    "(Files > Android > data > com.aarush.cpm > files)." },
                onFailure = { "Couldn't take the cloud copy: ${it.message}" }
            )
            syncWorking = false
        }
    }

    fun cancelSyncChoice() { syncChoice = null; cloudSync.turnOff() }
    fun syncNow() { viewModelScope.launch { cloudSync.syncNow() } }
    fun turnOffSync() { cloudSync.turnOff(); statusMessage = "Cloud sync is off on this phone. Its data stays here." }

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
            statusMessage = null
            runCatching { sampleDataSeeder.seedDemoProject() }
                .onSuccess { statusMessage = "Demo project added. Open it from Projects." }
                .onFailure { statusMessage = "Could not create the demo: ${it.message}" }
            isSeeding = false
            onDone()
        }
    }

    /** Deletes every project flagged as demo data, with all of its rows. */
    fun removeDemoData() {
        viewModelScope.launch {
            isSeeding = true
            val n = projectRepository.deleteDemoProjects()
            isSeeding = false
            statusMessage = if (n == 0) "No demo projects to remove." else "Removed $n demo project${if (n == 1) "" else "s"}."
        }
    }

    /** Signs out; [forget] also clears the remembered account (disables biometric sign-in). */
    fun signOut(forget: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            if (forget) authPreferences.clearRememberedEmail()
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
                onSuccess = { "Backup saved. Keep it somewhere safe: Drive, Downloads, wherever you like." },
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
fun SettingsScreen(viewModel: SettingsViewModel, onSignOut: () -> Unit = {}, onOpenTeam: () -> Unit = {}) {
    val context = LocalContext.current
    val access = com.aarush.cpm.ui.common.LocalAccess.current
    val team = com.aarush.cpm.ui.common.LocalTeam.current
    var showRestoreConfirm by remember { mutableStateOf<Uri?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { viewModel.backup(context, it) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { showRestoreConfirm = it }
    }

    Scaffold(topBar = {
        LargeTopAppBar(title = { com.aarush.cpm.ui.common.BrandHeader("Settings") })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            viewModel.statusMessage?.let {
                AppCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
            CloudSyncSection(
                status = viewModel.syncStatus.collectAsState().value,
                working = viewModel.syncWorking,
                canStartCloud = team?.ownAccess?.canManageTeam == true,
                onTurnOn = { viewModel.turnOnSync(context) },
                onSyncNow = viewModel::syncNow,
                onTurnOff = viewModel::turnOffSync
            )
            Spacer(Modifier.height(20.dp))
            SectionLabel("Team")
            Spacer(Modifier.height(6.dp))
            AppCard(Modifier.fillMaxWidth(), onClick = onOpenTeam) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Groups, contentDescription = null, tint = SiteTheme.colors.muted)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Team & devices", style = MaterialTheme.typography.titleSmall)
                        val me = team?.thisDevice
                        Text(
                            if (me != null) "This phone: ${me.name}, ${me.role.label}" else "Phone names, roles and projects",
                            style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SiteTheme.colors.muted)
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Backup & restore")
            Spacer(Modifier.height(6.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Uninstalling the app deletes its data. Back up regularly, and always before uninstalling, to Downloads, " +
                            "Google Drive or a USB drive. Restore checks the file first and keeps your current data if anything is wrong.",
                        style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { backupLauncher.launch(DatabaseBackupHelper.suggestedFileName()) },
                        enabled = !viewModel.isBackingUp && !viewModel.isRestoring,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (viewModel.isBackingUp) "Backing up…" else "Back up now")
                    }
                    if (access.canRestore) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                        enabled = !viewModel.isBackingUp && !viewModel.isRestoring,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (viewModel.isRestoring) "Restoring…" else "Restore from backup")
                    }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text("Only the Owner can restore a backup.", style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Project defaults")
            Spacer(Modifier.height(6.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Text(
                    "Each project's cost distribution, material/labour split, details and status are edited in the project's Settings → Project tab. " +
                        "Notations, coefficients, waste and rates are in Settings → Coefficients / Rates. The construction schedule lives in the project's Schedule tab.",
                    style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted, modifier = Modifier.padding(16.dp)
                )
            }

            if (access.canManageDemoData) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("Demo data")
            Spacer(Modifier.height(6.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("A G+1 residence with BOQ, a civil vendor, purchases, receipts, a generated schedule and some site progress.",
                        style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.loadDemoProject {} }, enabled = !viewModel.isSeeding, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) {
                            Text(if (viewModel.isSeeding) "Working…" else "Load demo")
                        }
                        OutlinedButton(onClick = { viewModel.removeDemoData() }, enabled = !viewModel.isSeeding, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) {
                            Text("Remove demo")
                        }
                    }
                }
            }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Account")
            Spacer(Modifier.height(6.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedButton(onClick = { viewModel.signOut(forget = false, onDone = onSignOut) }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Text("Sign out")
                    }
                    TextButton(onClick = { viewModel.signOut(forget = true, onDone = onSignOut) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Sign out and forget this device's account")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("About")
            Spacer(Modifier.height(10.dp))
            com.aarush.cpm.ui.common.AarushLogo(Modifier.width(180.dp))
            Spacer(Modifier.height(8.dp))
            Text("Aarush Construction & Consultant Works", style = MaterialTheme.typography.titleSmall)
            Text("Aarush Construction Project Management System, v1.1.0", style = MaterialTheme.typography.bodySmall)
            Text("Works offline. With cloud sync on, projects are shared between the team's phones.", style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
            Spacer(Modifier.height(32.dp))
        }
    }

    viewModel.syncChoice?.let { choice ->
        CloudChoiceDialog(
            choice = choice,
            isOwner = team?.ownAccess?.canManageTeam == true,
            onUpload = viewModel::confirmFirstUpload,
            onUseCloud = viewModel::confirmUseCloudCopy,
            onCancel = viewModel::cancelSyncChoice
        )
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
