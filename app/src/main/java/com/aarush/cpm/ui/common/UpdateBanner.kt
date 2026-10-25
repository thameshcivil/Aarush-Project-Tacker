package com.aarush.cpm.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aarush.cpm.data.update.AppUpdater
import com.aarush.cpm.data.update.UpdateState
import com.aarush.cpm.ui.theme.SiteTheme
import kotlinx.coroutines.launch

/** The app's updater; null in previews and screenshot tests, so the banner never shows there. */
val LocalAppUpdater = staticCompositionLocalOf<AppUpdater?> { null }

/** "Update available" card: shown on the project list and in Settings when a newer build exists. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val updater = LocalAppUpdater.current ?: return
    val state by updater.state.collectAsState()
    UpdateBannerContent(
        state, modifier,
        onUpdate = updater::update,
        onAllow = updater::openInstallPermission
    )
}

@Composable
internal fun UpdateBannerContent(
    state: UpdateState, modifier: Modifier = Modifier,
    onUpdate: (com.aarush.cpm.data.update.UpdateInfo) -> Unit = {}, onAllow: () -> Unit = {}
) {
    val info = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.NeedsPermission -> state.info
        else -> return
    }
    AppCard(modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Update available: ${info.versionName}", style = MaterialTheme.typography.titleSmall)
                    if (info.notes.isNotBlank()) {
                        Text(info.notes, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            when (state) {
                is UpdateState.Downloading -> {
                    Text("Downloading… ${state.percent}%", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.NeedsPermission -> {
                    Text("Android needs your OK once: tap Allow, switch on \"Allow from this source\", come back and tap Update.",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onAllow, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) { Text("Allow") }
                        Button(onClick = { onUpdate(info) }, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) { Text("Update") }
                    }
                }
                else -> Button(onClick = { onUpdate(info) }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Update now")
                }
            }
        }
    }
}

/** Settings > App updates: this phone's version and a "Check for updates" button. */
@Composable
fun AppVersionCard(modifier: Modifier = Modifier) {
    val updater = LocalAppUpdater.current ?: return
    val state by updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth()) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("This phone: version ${updater.currentVersionName.ifBlank { "unknown" }}", style = MaterialTheme.typography.titleSmall)
                Text(
                    when (state) {
                        UpdateState.Checking -> "Checking…"
                        UpdateState.UpToDate -> "You have the latest version."
                        is UpdateState.Failed -> (state as UpdateState.Failed).message
                        is UpdateState.Available, is UpdateState.Downloading, is UpdateState.NeedsPermission -> "A newer version is ready (below)."
                        UpdateState.Idle -> "New versions appear here and on the project list automatically."
                    },
                    style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { scope.launch { updater.check() } },
                    enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
                    shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()
                ) { Text("Check for updates") }
            }
        }
        UpdateBanner(Modifier.padding(top = 8.dp))
    }
}
