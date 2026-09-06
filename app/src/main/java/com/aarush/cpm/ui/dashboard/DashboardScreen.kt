package com.aarush.cpm.ui.dashboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.entity.Project
import com.aarush.cpm.data.entity.ProjectStatus
import com.aarush.cpm.data.repository.ProjectRepository
import com.aarush.cpm.data.repository.ProjectSummaryRepository
import com.aarush.cpm.data.repository.SampleDataSeeder
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.ui.common.formatCurrency
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProjectCardData(
    val project: Project,
    val totalReceived: Double,
    val totalSpent: Double,
    val profit: Double,
    val progressPercent: Double
)

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
            .flatMapLatest { (q, status) -> projectRepository.search(q) }
            .map { projects ->
                projects.filter { statusFilter.value == null || it.status == statusFilter.value }
                    .map { p ->
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
    val cards by viewModel.projectCards.collectAsState()
    var searchText by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<ProjectStatus?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Aarush CPM") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProject) { Icon(Icons.Filled.Add, contentDescription = "Add Project") }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it; viewModel.onQueryChange(it) },
                label = { Text("Search projects") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            Spacer(Modifier.height(8.dp))
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
                        label = { Text(status.name.replace("_", " ")) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (cards.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No projects yet.", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.seedDemoProject() }) { Text("Load sample demo project") }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    items(cards, key = { it.project.id }) { card ->
                        ProjectCard(card = card, onClick = { onOpenProject(card.project.id) }, onDelete = { viewModel.deleteProject(card.project) })
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(card: ProjectCardData, onClick: () -> Unit, onDelete: () -> Unit) {
    val p = card.project
    var showMenu by remember { mutableStateOf(false) }
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${p.clientName} • ${p.location}", style = MaterialTheme.typography.bodySmall)
                }
                AssistChip(onClick = {}, label = { Text(p.status.name.replace("_", " ")) })
                Box {
                    IconButton(onClick = { showMenu = true }) { Text("⋮") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = (card.progressPercent / 100.0).toFloat().coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(2.dp))
            Text("${"%.1f".format(card.progressPercent)}% complete", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("Value", formatCurrency(p.projectValue))
                MiniStat("Received", formatCurrency(card.totalReceived))
                MiniStat("Spent", formatCurrency(card.totalSpent))
                MiniStat("P/L", formatCurrency(card.profit))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
