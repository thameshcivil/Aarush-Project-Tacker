package com.aarush.cpm.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aarush.cpm.data.sync.CloudState
import com.aarush.cpm.data.sync.SyncConfig
import com.aarush.cpm.data.sync.SyncStatus
import com.aarush.cpm.ui.common.AppCard
import com.aarush.cpm.ui.common.SectionLabel
import com.aarush.cpm.ui.theme.SiteTheme

/** "Synced just now" / "5 min ago" / "3 h ago" / a date. */
internal fun syncedAgo(at: Long?, now: Long = System.currentTimeMillis()): String {
    if (at == null) return "Not synced yet"
    val min = (now - at) / 60_000
    return when {
        min < 1 -> "Synced just now"
        min < 60 -> "Synced $min min ago"
        min < 24 * 60 -> "Synced ${min / 60} h ago"
        else -> "Synced ${com.aarush.cpm.ui.common.formatDate(at)}"
    }
}

/** Settings > Cloud sync: off (with the button to turn it on) or on (status and actions). */
@Composable
internal fun CloudSyncSection(
    status: SyncStatus, working: Boolean, canStartCloud: Boolean,
    onTurnOn: () -> Unit, onSyncNow: () -> Unit, onTurnOff: () -> Unit
) {
    SectionLabel("Cloud sync")
    Spacer(Modifier.height(6.dp))
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (status.enabled) Icons.Filled.Cloud else Icons.Filled.CloudOff, contentDescription = null,
                    tint = if (status.enabled && status.error == null) MaterialTheme.colorScheme.primary else SiteTheme.colors.muted
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (status.enabled) "On" else "Off", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            working || status.busy -> "Syncing…"
                            status.enabled -> syncedAgo(status.lastSyncAt)
                            else -> "Projects stay on this phone only"
                        },
                        style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted
                    )
                }
            }
            if (status.enabled) {
                if (status.pending > 0) {
                    Text("${status.pending} change${if (status.pending == 1L) "" else "s"} waiting to upload",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                if (status.waitingForParents > 0) {
                    Text("${status.waitingForParents} item${if (status.waitingForParents == 1L) "" else "s"} from other phones waiting for related data",
                        style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                }
                status.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.warn, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSyncNow, enabled = !working && !status.busy, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sync now")
                    }
                    OutlinedButton(onClick = onTurnOff, enabled = !working, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) {
                        Text("Turn off")
                    }
                }
            } else {
                Text(
                    "Share projects between the team's phones. Sign in with the team account (${SyncConfig.TEAM_EMAIL}). " +
                        if (canStartCloud) "As the Owner, turn it on here first; your projects become the shared copy."
                        else "The Owner's phone turns it on first; this phone then takes the team's copy.",
                    style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted, modifier = Modifier.padding(top = 8.dp)
                )
                status.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.warn, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = onTurnOn, enabled = !working, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(if (working) "Connecting…" else "Turn on cloud sync")
                }
            }
        }
    }
}

/** After sign-in: upload this phone (empty cloud, Owner) or take the cloud copy. */
@Composable
internal fun CloudChoiceDialog(choice: CloudState, isOwner: Boolean, onUpload: () -> Unit, onUseCloud: () -> Unit, onCancel: () -> Unit) {
    when {
        choice == CloudState.EMPTY && isOwner -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Start the team's cloud copy?") },
            text = { Text("This phone's projects, BOQ, schedules, expenses, vendors and team list are uploaded. Other phones then " +
                "switch to this copy when they turn on sync. Login accounts stay on each phone.") },
            confirmButton = { TextButton(onClick = onUpload) { Text("Upload") } },
            dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
        )
        choice == CloudState.EMPTY -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Owner first") },
            text = { Text("The team's cloud copy hasn't been started yet. Turn on cloud sync on the Owner's phone first, then here.") },
            confirmButton = { TextButton(onClick = onCancel) { Text("OK") } }
        )
        else -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Use the team's cloud copy?") },
            text = { Text("This phone's current projects are replaced by the team's shared copy. A safety copy of this phone's " +
                "data is saved first. From then on, changes on any phone appear on all of them.") },
            confirmButton = { TextButton(onClick = onUseCloud) { Text("Use cloud copy") } },
            dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
        )
    }
}
