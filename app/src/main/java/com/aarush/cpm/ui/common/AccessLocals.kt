package com.aarush.cpm.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aarush.cpm.data.entity.TeamRole
import com.aarush.cpm.data.repository.TeamState
import com.aarush.cpm.domain.access.Access
import com.aarush.cpm.domain.access.ProjectAccess
import com.aarush.cpm.ui.theme.SiteTheme

/*
 * Role-based access, handed down the UI tree (v10). The app root provides LocalTeam and
 * LocalAccess; ProjectScreen provides LocalProjectAccess for one project. Defaults are
 * unrestricted so previews and screenshot tests render every control, as before.
 */

/** This phone's permissions across the app. */
val LocalAccess = compositionLocalOf { Access.OWNER }

/** Team snapshot (device names, project members); null in previews and tests. */
val LocalTeam = compositionLocalOf<TeamState?> { null }

/** What the open project lets this phone change. */
val LocalProjectAccess = compositionLocalOf { ProjectAccess.FULL }

/** Name of the phone that entered a record, or null if unknown. */
@Composable
fun enteredByName(deviceId: String?): String? = LocalTeam.current?.nameOf(deviceId)

/** One-line note shown under the project top bar when this phone can't change everything. */
@Composable
fun AccessNotice(role: TeamRole, access: ProjectAccess, modifier: Modifier = Modifier) {
    if (access == ProjectAccess.FULL) return
    val text = when {
        access.readOnly -> "View only. Ask the Owner if you need to make changes."
        role == TeamRole.COORDINATOR -> "Coordinator: you can add expenses, log progress and enter BOQ measurements."
        role == TeamRole.PARTNER -> "Partner: you can change everything here except the BOQ and the project setup."
        else -> "Some changes are limited for this phone."
    }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = SiteTheme.colors.muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
    }
}
