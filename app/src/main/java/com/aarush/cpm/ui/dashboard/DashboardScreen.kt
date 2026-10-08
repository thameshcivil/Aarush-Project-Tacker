package com.aarush.cpm.ui.dashboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.entity.Project
import com.aarush.cpm.data.entity.ProjectStatus
import com.aarush.cpm.data.repository.ProjectRepository
import com.aarush.cpm.data.repository.ProjectSummaryRepository
import com.aarush.cpm.data.repository.SampleDataSeeder
import com.aarush.cpm.ui.common.*
import com.aarush.cpm.ui.theme.SiteTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProjectCardData(
    val project: Project,
    val totalReceived: Double,
    val totalSpent: Double,
    val profit: Double,
    val progressPercent: Double
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val projectRepository: ProjectRepository,
    private val summaryRepository: ProjectSummaryRepository,
    private val sampleDataSeeder: SampleDataSeeder
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val statusFilter = MutableStateFlow<ProjectStatus?>(null)
    private val refreshTrigger = MutableStateFlow(0)

    val projectCards: StateFlow<List<ProjectCardData>> =
        combine(query, statusFilter, refreshTrigger) { q, status, _ -> Pair(q, status) }
            .flatMapLatest { (q, status) -> projectRepository.search(q).map { list -> list.filter { status == null || it.status == status } } }
            .map { projects ->
                projects.map { p ->
                    val summary = summaryRepository.buildSummary(p.id)
                    ProjectCardData(
                        project = p,
                        totalReceived = summary?.totalReceived ?: 0.0,
                        totalSpent = summary?.totalSpent ?: 0.0,
                        profit = summary?.profit ?: 0.0,
                        progressPercent = summary?.progressPercent ?: 0.0
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(q: String) { query.value = q }
    fun onStatusFilterChange(s: ProjectStatus?) { statusFilter.value = s }
    /** Re-reads totals — called whenever the list comes back on screen, since spend/receipts
     *  live in other tables that the projects query doesn't observe. */
    fun refresh() { refreshTrigger.value += 1 }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            projectRepository.deleteProject(project)
            refresh()
        }
    }

    fun seedDemoProject() {
        viewModelScope.launch {
            sampleDataSeeder.seedDemoProject()
            refresh()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onAddProject: () -> Unit,
    onOpenProject: (Long) -> Unit
) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val cards by viewModel.projectCards.collectAsState()
    var searchText by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<ProjectStatus?>(null) }
    var pendingDelete by remember { mutableStateOf<Project?>(null) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { BrandHeader("Projects") }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProject,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.small,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New project") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it; viewModel.onQueryChange(it) },
                placeholder = { Text("Search by project, client or location") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedStatus == null,
                    onClick = { selectedStatus = null; viewModel.onStatusFilterChange(null) },
                    label = { Text("All") }
                )
                ProjectStatus.values().forEach { status ->
                    FilterChip(
                        selected = selectedStatus == status,
                        onClick = { selectedStatus = status; viewModel.onStatusFilterChange(status) },
                        label = { Text(status.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (cards.isEmpty()) {
                if (searchText.isBlank() && selectedStatus == null) {
                    EmptyState(
                        title = "No projects yet",
                        body = "Create a project from its plinth area and rate, or load the demo G+1 residence to explore BOQ, expenses and the schedule.",
                        action = {
                            OutlinedButton(onClick = { viewModel.seedDemoProject() }, shape = MaterialTheme.shapes.small) { Text("Load demo project") }
                        }
                    )
                } else {
                    EmptyState(title = "No matching projects", body = "Try a different search or status filter.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    items(cards, key = { it.project.id }) { card ->
                        ProjectCard(card = card, onClick = { onOpenProject(card.project.id) }, onDelete = { pendingDelete = card.project })
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }

    pendingDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete project?") },
            text = {
                Text("\"${project.name}\" and everything in it — BOQ, expenses, vendors, schedule and progress — will be permanently deleted. " +
                    "Take a backup first (Settings) if you may need it.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteProject(project); pendingDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun statusColors(status: ProjectStatus) = when (status) {
    ProjectStatus.COMPLETED -> SiteTheme.colors.good to SiteTheme.colors.goodTint
    ProjectStatus.ON_HOLD -> SiteTheme.colors.bad to SiteTheme.colors.badTint
    ProjectStatus.IN_PROGRESS, ProjectStatus.STARTED -> SiteTheme.colors.accentText to SiteTheme.colors.accentTint
    ProjectStatus.PLANNING -> SiteTheme.colors.planned to SiteTheme.colors.plannedTint
}

@Composable
private fun ProjectCard(card: ProjectCardData, onClick: () -> Unit, onDelete: () -> Unit) {
    val p = card.project
    var showMenu by remember { mutableStateOf(false) }
    AppCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    val (fg, bg) = statusColors(p.status)
                    StatusPill(p.status.name.replace("_", " "), fg, bg)
                    Spacer(Modifier.height(8.dp))
                    Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(p.clientName, p.location).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Delete project") }, onClick = { showMenu = false; onDelete() })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRail(progress = (card.progressPercent / 100.0).toFloat(), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text(formatPercent(card.progressPercent), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = SiteTheme.colors.hairline)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("Value", formatCurrencyShort(p.projectValue))
                MiniStat("Received", formatCurrencyShort(card.totalReceived))
                MiniStat("Spent", formatCurrencyShort(card.totalSpent))
                MiniStat("P / L", formatCurrencyShort(card.profit), if (card.profit < 0) SiteTheme.colors.bad else SiteTheme.colors.good)
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    Column(horizontalAlignment = Alignment.Start) {
        SectionLabel(label)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}
