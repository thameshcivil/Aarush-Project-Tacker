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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.entity.Project
import com.aarush.cpm.data.entity.ProjectStatus
import com.aarush.cpm.data.repository.ProjectRepository
import com.aarush.cpm.data.repository.ProjectSummaryRepository
import com.aarush.cpm.data.repository.SampleDataSeeder
import com.aarush.cpm.data.repository.ScheduleRepository
import com.aarush.cpm.ui.project.Figure
import com.aarush.cpm.ui.project.FigureGrid
import com.aarush.cpm.ui.project.ProgressSummary
import com.aarush.cpm.ui.project.ProjectIdentity
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
    /** Actual progress: the schedule's live earned-value % when a schedule exists, otherwise
     *  the project's last recorded progress (both existing calculations). */
    val progressPercent: Double,
    /** Planned % for today from the schedule baseline; null when there is no schedule. */
    val plannedPercent: Double? = null,
    /** Working days the forecast finish is behind baseline; null when there is no schedule. */
    val delayDays: Int? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val projectRepository: ProjectRepository,
    private val summaryRepository: ProjectSummaryRepository,
    private val sampleDataSeeder: SampleDataSeeder,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val statusFilter = MutableStateFlow<ProjectStatus?>(null)
    private val refreshTrigger = MutableStateFlow(0)

    /** Null until the first list has been built (the screen shows a skeleton meanwhile). */
    val projectCards: StateFlow<List<ProjectCardData>?> =
        combine(query, statusFilter, refreshTrigger) { q, status, _ -> Pair(q, status) }
            .flatMapLatest { (q, status) -> projectRepository.search(q).map { list -> list.filter { status == null || it.status == status } } }
            .map { projects ->
                projects.map { p ->
                    val summary = summaryRepository.buildSummary(p.id)
                    // Read-only report (no writes); a failure just leaves planned/delay unknown.
                    val report = runCatching { scheduleRepository.buildReport(p.id) }.getOrNull()?.takeIf { it.hasSchedule }
                    val ev = report?.earnedValue
                    ProjectCardData(
                        project = p,
                        totalReceived = summary?.totalReceived ?: 0.0,
                        totalSpent = summary?.totalSpent ?: 0.0,
                        profit = summary?.profit ?: 0.0,
                        progressPercent = ev?.actualPercent ?: summary?.progressPercent ?: 0.0,
                        plannedPercent = ev?.plannedPercent,
                        delayDays = report?.takeIf { it.forecastFinish != null }?.projectDelayDays
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
    val access = LocalAccess.current
    val team = LocalTeam.current
    // Coordinators and Viewers only see the projects assigned to their phone. Wait for the
    // phone's team record so nothing flashes before it is filtered.
    val loadedCards = viewModel.projectCards.collectAsState().value
        ?.takeIf { access.loaded }
        ?.filter { access.canOpenProject(it.project.id) }
    val cards = loadedCards.orEmpty()
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
            if (access.canCreateProject) ExtendedFloatingActionButton(
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

            if (loadedCards == null) {
                SkeletonScreen(headline = false, sections = 3, rows = 2)
            } else if (cards.isEmpty()) {
                if (!access.seesAllProjects && searchText.isBlank() && selectedStatus == null) {
                    EmptyState(
                        title = "No projects for this phone yet",
                        body = "Ask the Owner to assign you to a project in Settings > Team & devices."
                    )
                } else if (searchText.isBlank() && selectedStatus == null) {
                    EmptyState(
                        title = "No projects yet",
                        body = "Create a project from its plinth area and rate, or load the demo G+1 residence to explore BOQ, expenses and the schedule.",
                        action = if (access.canManageDemoData) {
                            { OutlinedButton(onClick = { viewModel.seedDemoProject() }, shape = MaterialTheme.shapes.small) { Text("Load demo project") } }
                        } else null
                    )
                } else {
                    EmptyState(title = "No matching projects", body = "Try a different search or status filter.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    items(cards, key = { it.project.id }) { card ->
                        ProjectCard(
                            card = card, onClick = { onOpenProject(card.project.id) },
                            onDelete = if (access.canDeleteProject) ({ pendingDelete = card.project }) else null,
                            leadName = team?.leadFor(card.project.id)?.name
                        )
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
                Text("\"${project.name}\" and everything in it (BOQ, expenses, vendors, schedule and progress) will be permanently deleted. " +
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

/** One project in the list: name and client with the status tag on the right and the ⋮ menu,
 *  overall progress (gold actual, dark-neutral planned tick, red when late), then the four key
 *  figures in an aligned grid (four across, two on narrow phones). */
@Composable
internal fun ProjectCard(card: ProjectCardData, onClick: () -> Unit, onDelete: (() -> Unit)?, leadName: String? = null) {
    val p = card.project
    var showMenu by remember { mutableStateOf(false) }
    AppCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            ProjectIdentity(p, large = false, trailing = {
                // Without delete permission there is no menu; the spacer keeps the same layout.
                if (onDelete == null) Spacer(Modifier.width(12.dp))
                else Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More actions for ${p.name}") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Delete project", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() })
                    }
                }
            })
            if (leadName != null) {
                Text("Coordinator: $leadName", style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 12.dp, top = 2.dp))
            }
            Column(Modifier.padding(end = 12.dp)) {
                Spacer(Modifier.height(10.dp))
                ProgressSummary(card.progressPercent, card.plannedPercent, card.delayDays, barHeight = 6.dp)
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = SiteTheme.colors.hairline)
                Spacer(Modifier.height(4.dp))
                // Same figures as before (Value − spent = project value − total spent; neutral
                // unless negative).
                FigureGrid(
                    listOf(
                        Figure("Value", formatCurrencyShort(p.projectValue)),
                        Figure("Received", formatCurrencyShort(card.totalReceived)),
                        Figure("Spent", formatCurrencyShort(card.totalSpent)),
                        Figure("Value − spent", formatCurrencyShort(card.profit),
                            if (card.profit < 0) SiteTheme.colors.bad else androidx.compose.ui.graphics.Color.Unspecified)
                    ),
                    wideColumns = 4, valueSize = 15f
                )
            }
        }
    }
}
