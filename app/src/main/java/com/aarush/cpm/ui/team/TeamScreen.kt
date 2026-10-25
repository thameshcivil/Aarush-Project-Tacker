package com.aarush.cpm.ui.team

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.entity.Project
import com.aarush.cpm.data.entity.TeamDevice
import com.aarush.cpm.data.entity.TeamRole
import com.aarush.cpm.data.repository.AccessPreview
import com.aarush.cpm.data.repository.AccessRepository
import com.aarush.cpm.data.repository.ProjectRepository
import com.aarush.cpm.data.repository.TeamState
import com.aarush.cpm.ui.common.*
import com.aarush.cpm.ui.project.LedgerRow
import com.aarush.cpm.ui.project.LedgerSection
import com.aarush.cpm.ui.theme.SiteTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/*
 * Team & devices (v10). Everyone signs in with the same email, so each PHONE is a team
 * member: the Owner names it, gives it a role and picks its projects. Also hosts the Owner
 * PIN (to take Owner back on a new phone) and the role preview.
 */

class TeamViewModel(
    private val access: AccessRepository,
    projectRepository: ProjectRepository
) : ViewModel() {
    val team: StateFlow<TeamState?> = access.team.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val projects: StateFlow<List<Project>> = projectRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var message by mutableStateOf<String?>(null)
        private set

    private fun report(result: Result<Unit>, ok: String? = null) {
        message = result.fold(onSuccess = { ok }, onFailure = { it.message })
    }

    fun saveDevice(device: TeamDevice, name: String, role: TeamRole, projectIds: Set<Long>, onDone: () -> Unit) {
        viewModelScope.launch {
            val renamed = access.rename(device.deviceId, name)
            if (renamed.isFailure) { report(renamed); return@launch }
            val roled = access.setRole(device.deviceId, role)
            if (roled.isFailure) { report(roled); return@launch }
            access.setProjects(device.deviceId, projectIds)
            message = "Saved ${name.trim()}."
            onDone()
        }
    }

    fun removeDevice(device: TeamDevice, onDone: () -> Unit) {
        viewModelScope.launch {
            val r = access.removeDevice(device.deviceId)
            report(r, "Removed ${device.name}.")
            if (r.isSuccess) onDone()
        }
    }

    fun setMember(projectId: Long, device: TeamDevice, member: Boolean) {
        viewModelScope.launch {
            val current = team.value?.projectsOf(device.deviceId).orEmpty()
            access.setProjects(device.deviceId, if (member) current + projectId else current - projectId)
        }
    }

    fun setLead(projectId: Long, device: TeamDevice?) {
        viewModelScope.launch { access.setLead(projectId, device?.deviceId) }
    }

    fun setOwnerPin(pin: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val r = access.setOwnerPin(pin)
            report(r, "Owner PIN saved.")
            if (r.isSuccess) onDone()
        }
    }

    fun claimOwner(secret: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val r = access.claimOwner(secret)
            report(r, "This phone is now an Owner.")
            if (r.isSuccess) onDone()
        }
    }

    fun preview(p: AccessPreview) = access.startPreview(p)
    fun dismissMessage() { message = null }
}

@Composable
internal fun rolePillColors(role: TeamRole) = when (role) {
    TeamRole.OWNER, TeamRole.MANAGER -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.secondaryContainer
    TeamRole.COORDINATOR, TeamRole.VIEWER -> SiteTheme.colors.planned to SiteTheme.colors.plannedTint
}

@Composable
internal fun RolePill(role: TeamRole, modifier: Modifier = Modifier) {
    val (fg, bg) = rolePillColors(role)
    StatusPill(role.label, fg, bg, modifier)
}

/** "All projects" for Owner / Manager, else the assigned project names. */
internal fun projectsSummary(role: TeamRole, ids: Set<Long>, projects: List<Project>): String = when {
    role == TeamRole.OWNER || role == TeamRole.MANAGER -> "All projects"
    ids.isEmpty() -> "No projects yet"
    else -> projects.filter { it.id in ids }.joinToString { it.name }.ifBlank { "${ids.size} projects" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(viewModel: TeamViewModel, onBack: () -> Unit) {
    val team by viewModel.team.collectAsState()
    val projects by viewModel.projects.collectAsState()
    var editing by remember { mutableStateOf<TeamDevice?>(null) }
    var showPin by remember { mutableStateOf(false) }
    var showClaim by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Team & devices") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
        )
    }) { padding ->
        val t = team
        if (t == null || t.thisDevice == null) {
            SkeletonScreen(Modifier.padding(padding).padding(16.dp), headline = false, sections = 2, rows = 2)
            return@Scaffold
        }
        TeamBody(t, projects, viewModel.message, Modifier.padding(padding),
            onDismissMessage = viewModel::dismissMessage, onEdit = { editing = it },
            onPin = { showPin = true }, onClaim = { showClaim = true }, onPreview = { showPreview = true })

        editing?.let { d ->
            DeviceEditorDialog(
                device = d, isThisPhone = d.deviceId == t.thisDeviceId, assigned = t.projectsOf(d.deviceId), projects = projects,
                onDismiss = { editing = null },
                onSave = { name, role, ids -> viewModel.saveDevice(d, name, role, ids) { editing = null } },
                onRemove = { viewModel.removeDevice(d) { editing = null } },
                onPreview = { role, ids ->
                    viewModel.preview(AccessPreview(d.name, role, ids)); editing = null; onBack()
                }
            )
        }
        if (showPin) PinDialog(onDismiss = { showPin = false }, onSave = { viewModel.setOwnerPin(it) { showPin = false } })
        if (showClaim) ClaimOwnerDialog(pinSet = t.ownerPinSet, onDismiss = { showClaim = false }, onClaim = { viewModel.claimOwner(it) { showClaim = false } })
        if (showPreview) PreviewDialog(projects = projects, onDismiss = { showPreview = false }, onStart = { p ->
            viewModel.preview(p); showPreview = false; onBack()
        })
    }
}

/** The Team screen's content, without dialogs (stateless, so screenshot tests can render it). */
@Composable
internal fun TeamBody(
    t: TeamState, projects: List<Project>, message: String?, modifier: Modifier = Modifier,
    onDismissMessage: () -> Unit = {}, onEdit: (TeamDevice) -> Unit = {},
    onPin: () -> Unit = {}, onClaim: () -> Unit = {}, onPreview: () -> Unit = {}
) {
    val me = t.thisDevice!!
    val isOwner = t.ownAccess.canManageTeam
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        message?.let { msg ->
            item {
                AppCard(Modifier.fillMaxWidth(), onClick = onDismissMessage,
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }
        }

        item { SectionLabel("This phone") }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(me.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        RolePill(me.role)
                    }
                    Text(me.role.summary, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                    Spacer(Modifier.height(4.dp))
                    Text(projectsSummary(me.role, t.projectsOf(me.deviceId), projects), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    if (isOwner) {
                        TextButton(onClick = { onEdit(me) }, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(0.dp)) { Text("Rename this phone") }
                    } else {
                        TextButton(onClick = { onClaim() }, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(0.dp)) { Text("I'm the Owner: unlock this phone") }
                    }
                }
            }
        }

        if (isOwner) {
            item {
                Spacer(Modifier.height(12.dp))
                SectionLabel("Phones")
                Text("Everyone signs in with the same email, so each phone gets its own name and role. A new phone appears here " +
                    "the first time it opens the app, as a Viewer with no projects.",
                    style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
            }
            items(t.devices, key = { it.deviceId }) { d ->
                DeviceRow(d, isThisPhone = d.deviceId == t.thisDeviceId, summary = projectsSummary(d.role, t.projectsOf(d.deviceId), projects),
                    onClick = { onEdit(d) })
            }
            if (t.devices.size == 1) {
                item {
                    Text("Only this phone so far. Other phones join once cloud sync is added (next phase). Meanwhile, use Preview below to see the app as a Coordinator or Viewer.",
                        style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                }
            }

            item { Spacer(Modifier.height(12.dp)); SectionLabel("Owner PIN") }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(if (t.ownerPinSet) "PIN is set." else "No PIN yet. Set one now.", style = MaterialTheme.typography.titleSmall)
                        Text("If you change phones or restore a backup on a new phone, this PIN makes that phone the Owner again. Don't share it.",
                            style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { onPin() }, shape = MaterialTheme.shapes.small) { Text(if (t.ownerPinSet) "Change PIN" else "Set PIN") }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)); SectionLabel("Preview") }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("See the app exactly as a Coordinator or Viewer would, without changing anyone's role.",
                            style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { onPreview() }, shape = MaterialTheme.shapes.small) { Text("Preview a role") }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)); SectionLabel("What each role can do") }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    TeamRole.values().forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(color = SiteTheme.colors.hairline)
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text(r.label, style = MaterialTheme.typography.titleSmall)
                            Text(r.summary, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

}

@Composable
private fun DeviceRow(device: TeamDevice, isThisPhone: Boolean, summary: String, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Smartphone, contentDescription = null, tint = SiteTheme.colors.muted)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name + if (isThisPhone) " (this phone)" else "", style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Last opened ${formatDate(device.lastSeenAt)}", style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
            }
            Spacer(Modifier.width(8.dp))
            RolePill(device.role)
        }
    }
}

/** Role choice with each role's one-line summary (radio rows, 48dp+). */
@Composable
internal fun RolePicker(selected: TeamRole, onSelect: (TeamRole) -> Unit) {
    Column {
        TeamRole.values().forEach { r ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = r == selected, role = Role.RadioButton, onClick = { onSelect(r) }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = r == selected, onClick = null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(r.label, style = MaterialTheme.typography.bodyMedium)
                    Text(r.summary, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                }
            }
        }
    }
}

/** Project checklist for Coordinator / Viewer (Owner and Manager see every project). */
@Composable
internal fun ProjectChecklist(projects: List<Project>, selected: Set<Long>, onChange: (Set<Long>) -> Unit) {
    if (projects.isEmpty()) {
        Text("No projects yet.", style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
        return
    }
    Column {
        projects.forEach { p ->
            val on = p.id in selected
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onChange(if (it) selected + p.id else selected - p.id) }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = on, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
                Text(p.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DeviceEditorDialog(
    device: TeamDevice, isThisPhone: Boolean, assigned: Set<Long>, projects: List<Project>,
    onDismiss: () -> Unit, onSave: (String, TeamRole, Set<Long>) -> Unit, onRemove: () -> Unit,
    onPreview: (TeamRole, Set<Long>) -> Unit
) {
    var name by remember { mutableStateOf(device.name) }
    var role by remember { mutableStateOf(device.role) }
    var ids by remember { mutableStateOf(assigned) }
    var confirmRemove by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isThisPhone) "This phone" else "Edit phone") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Name, e.g. Ravi – Site") },
                    singleLine = true, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                SectionLabel("Role")
                RolePicker(role) { role = it }
                if (role == TeamRole.COORDINATOR || role == TeamRole.VIEWER) {
                    Spacer(Modifier.height(12.dp))
                    SectionLabel("Projects")
                    ProjectChecklist(projects, ids) { ids = it }
                }
                if (!isThisPhone) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { onPreview(role, ids) }, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(0.dp)) {
                        Text("Preview as this phone")
                    }
                    TextButton(onClick = { confirmRemove = true }, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(0.dp)) {
                        Text("Remove phone", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, role, ids) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${device.name}?") },
            text = { Text("It loses its role and projects. If it opens the app again it comes back as a Viewer with no projects.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; onRemove() }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PinDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val valid = pin.matches(Regex("\\d{4,8}")) && pin == again
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Owner PIN") },
        text = {
            Column {
                Text("4 to 8 digits.", style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                Spacer(Modifier.height(8.dp))
                PinField(pin, "PIN") { pin = it }
                Spacer(Modifier.height(8.dp))
                PinField(again, "Repeat PIN") { again = it }
                if (again.length >= pin.length && again.isNotEmpty() && pin != again) {
                    Text("PINs don't match.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(pin) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(8)) }, label = { Text(label) },
        singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ClaimOwnerDialog(pinSet: Boolean, onDismiss: () -> Unit, onClaim: (String) -> Unit) {
    var secret by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unlock as Owner") },
        text = {
            Column {
                Text(
                    if (pinSet) "Enter the Owner PIN to make this phone an Owner."
                    else "No Owner PIN has been set, so enter the app account's password instead.",
                    style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted
                )
                Spacer(Modifier.height(8.dp))
                if (pinSet) PinField(secret, "Owner PIN") { secret = it }
                else OutlinedTextField(value = secret, onValueChange = { secret = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onClaim(secret) }, enabled = secret.isNotEmpty()) { Text("Unlock") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PreviewDialog(projects: List<Project>, onDismiss: () -> Unit, onStart: (AccessPreview) -> Unit) {
    var role by remember { mutableStateOf(TeamRole.COORDINATOR) }
    var ids by remember { mutableStateOf(projects.firstOrNull()?.let { setOf(it.id) } ?: emptySet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preview a role") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                RolePicker(role) { role = it }
                if (role == TeamRole.COORDINATOR || role == TeamRole.VIEWER) {
                    Spacer(Modifier.height(12.dp))
                    SectionLabel("Projects")
                    ProjectChecklist(projects, ids) { ids = it }
                }
                Spacer(Modifier.height(8.dp))
                Text("A bar at the top lets you stop the preview at any time.", style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
            }
        },
        confirmButton = { TextButton(onClick = { onStart(AccessPreview("a ${role.label}", role, ids)) }) { Text("Start preview") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ------------------------------------------------------------------ More > Project team

/**
 * One project's team: lead coordinator and the phones assigned to it. The Owner can tick
 * phones on or off and pick the lead here; everyone else just sees who is on the project.
 */
@Composable
fun ProjectTeamContent(projectId: Long, viewModel: TeamViewModel) {
    val team by viewModel.team.collectAsState()
    val t = team ?: run { SkeletonScreen(Modifier.padding(16.dp), headline = false, sections = 1, rows = 3); return }
    val canManage = t.ownAccess.canManageTeam && !t.previewing
    val lead = t.leadFor(projectId)
    val members = t.membersOf(projectId)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        LedgerSection("Lead coordinator") {
            LedgerRow("Lead", lead?.name ?: "Not set", valueColor = if (lead == null) SiteTheme.colors.muted else androidx.compose.ui.graphics.Color.Unspecified, divider = false)
        }
        Spacer(Modifier.height(16.dp))
        if (!canManage) {
            LedgerSection("On this project") {
                if (members.isEmpty()) Text("No phones assigned yet.", style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted, modifier = Modifier.padding(vertical = 12.dp))
                members.forEachIndexed { i, d -> LedgerRow(d.name, d.role.label, divider = i < members.lastIndex) }
            }
            Spacer(Modifier.height(8.dp))
            Text("Owners and project managers can open every project.", style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
            return@Column
        }
        SectionLabel("Phones")
        Text("Tick the phones that work on this project, and choose the lead.", style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
        Spacer(Modifier.height(6.dp))
        t.devices.forEach { d ->
            val allProjects = d.role == TeamRole.OWNER || d.role == TeamRole.MANAGER
            val on = allProjects || members.any { it.deviceId == d.deviceId }
            AppCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = on, enabled = !allProjects, onCheckedChange = { viewModel.setMember(projectId, d, it) })
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (allProjects) "${d.role.label}: sees all projects" else d.role.label,
                            style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                    }
                    val isLead = lead?.deviceId == d.deviceId
                    FilterChip(
                        selected = isLead,
                        onClick = { viewModel.setLead(projectId, if (isLead) null else d) },
                        label = { Text(if (isLead) "Lead" else "Make lead") }
                    )
                }
            }
        }
    }
}
