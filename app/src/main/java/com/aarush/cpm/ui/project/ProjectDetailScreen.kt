package com.aarush.cpm.ui.project

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.entity.ProjectAreaComponent
import com.aarush.cpm.data.repository.*
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.ui.common.*
import com.aarush.cpm.ui.theme.FigureStyle
import com.aarush.cpm.ui.theme.SiteTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.TimeUnit

data class ProjectDetailUiState(
    val summary: ProjectSummary? = null,
    val budgetComparison: BudgetComparison? = null,
    val schedule: ScheduleReport? = null,
    val areaComponents: List<ProjectAreaComponent> = emptyList(),
    val daysRemaining: Long? = null,
    /** True only for the very first load (nothing to show yet): the screen shows a skeleton. */
    val isLoading: Boolean = true,
    /** True while existing figures are being re-read: they stay on screen under a thin bar. */
    val isRefreshing: Boolean = false,
    /** Set when reading the project failed; the screen offers Retry. */
    val error: String? = null
)

class ProjectDetailViewModel(
    private val projectRepository: ProjectRepository,
    private val summaryRepository: ProjectSummaryRepository,
    private val expenseRepository: ExpenseRepository,
    private val materialRepository: MaterialRepository,
    private val scheduleRepository: ScheduleRepository,
    private val clientPaymentRepository: ClientPaymentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectDetailUiState())
    val uiState: StateFlow<ProjectDetailUiState> = _uiState.asStateFlow()
    private val _projectName = MutableStateFlow("")
    val projectName: StateFlow<String> = _projectName.asStateFlow()
    private var currentProjectId: Long = 0L
    private var nameObserved = false

    fun observeName(projectId: Long) {
        if (nameObserved) return
        nameObserved = true
        viewModelScope.launch { projectRepository.observeById(projectId).collectLatest { _projectName.value = it?.name ?: "" } }
    }

    fun load(projectId: Long) {
        currentProjectId = projectId
        viewModelScope.launch {
            val hasData = _uiState.value.summary != null
            _uiState.value = _uiState.value.copy(isLoading = !hasData, isRefreshing = hasData, error = null)
            refreshSafely()
        }
    }

    /** Retry after a failed load; keeps whatever is already on screen. */
    fun retry() = load(currentProjectId)

    /** Same reads as before; a failure now ends in an error state with Retry instead of an
     *  endless spinner, and the previous figures (if any) are kept. */
    private suspend fun refreshSafely() {
        try {
            refresh()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false, isRefreshing = false,
                error = "Couldn't read this project's figures. ${e.message ?: ""}".trim()
            )
        }
    }

    private suspend fun refresh() {
        val summary = summaryRepository.buildSummary(currentProjectId)
        val budgetComparison = summaryRepository.buildBudgetComparison(currentProjectId)
        val schedule = scheduleRepository.buildReport(currentProjectId)
        val areaComponents = projectRepository.getAreaComponents(currentProjectId)
        val end = schedule.forecastFinish?.let { com.aarush.cpm.domain.schedule.WorkCalendar.toEpochMillis(it) }
            ?: summary?.project?.expectedCompletionDate
        val daysRemaining = end?.let { TimeUnit.MILLISECONDS.toDays(it - System.currentTimeMillis()) }
        _uiState.value = ProjectDetailUiState(
            summary = summary, budgetComparison = budgetComparison, schedule = schedule,
            areaComponents = areaComponents, daysRemaining = daysRemaining, isLoading = false
        )
    }

    fun addAreaComponent(label: String, area: Double, rate: Double) {
        viewModelScope.launch { projectRepository.addAreaComponent(currentProjectId, label, area, rate); refreshSafely() }
    }

    fun updateAreaComponent(component: ProjectAreaComponent, label: String, area: Double, rate: Double) {
        viewModelScope.launch {
            projectRepository.updateAreaComponent(component.copy(label = label, areaSqft = area, ratePerSqft = rate)); refreshSafely()
        }
    }

    fun deleteAreaComponent(component: ProjectAreaComponent) {
        viewModelScope.launch { projectRepository.deleteAreaComponent(component); refreshSafely() }
    }
}

/** The project's "Home" tab: header (tap to edit area/rate), money, schedule health, alerts,
 *  Budget 1 vs Budget 2, and budget-vs-actual by category. */
@Composable
fun ProjectHomeTabContent(
    projectId: Long,
    viewModel: ProjectDetailViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenSchedule: () -> Unit = {}
) {
    LaunchedEffect(projectId) { viewModel.load(projectId) }
    val state by viewModel.uiState.collectAsState()
    var showAreaDialog by remember { mutableStateOf(false) }
    var pendingDeleteComponent by remember { mutableStateOf<ProjectAreaComponent?>(null) }
    val scope = rememberCoroutineScope()

    val summary = state.summary
    when {
        summary == null && state.error != null -> { ErrorState(message = state.error!!, onRetry = viewModel::retry); return }
        summary == null && state.isLoading -> { SkeletonScreen(headline = true, sections = 3, rows = 4); return }
        summary == null -> {
            ErrorState(title = "Project not found", message = "This project may have been deleted.", onRetry = viewModel::retry)
            return
        }
    }
    val s = summary!!

    Column(Modifier.fillMaxSize()) {
        RefreshBar(visible = state.isRefreshing)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            state.error?.let { msg -> item { ErrorBanner(msg, onRetry = viewModel::retry) } }
            item { ProjectValueHeader(s.project, onEditAreaRate = { showAreaDialog = true }) }
            item { MoneySummaryTable(s) }
            item {
                TimeProgressTable(s.progressPercent, state.daysRemaining, hasSchedule = state.schedule?.hasSchedule == true)
            }
            state.schedule?.let { r -> item { ScheduleHealthCard(r, onOpenSchedule) } }
            state.schedule?.alerts?.takeIf { it.isNotEmpty() }?.let { alerts ->
                item { AlertsCard(alerts) }
            }
            state.budgetComparison?.let { bc -> item { BudgetComparisonTable(bc) } }
            item { CategoryBudgetTable(s.categories) }
        }
    }

    if (showAreaDialog) {
        AreaRateBreakdownDialog(
            components = state.areaComponents,
            onDismiss = { showAreaDialog = false },
            onAdd = { label, area, rate -> viewModel.addAreaComponent(label, area, rate) },
            onUpdate = { comp, label, area, rate -> viewModel.updateAreaComponent(comp, label, area, rate) },
            onDelete = { comp -> pendingDeleteComponent = comp }
        )
    }
    pendingDeleteComponent?.let { comp ->
        ConfirmDeleteDialog(
            itemLabel = "\"${comp.label}\"",
            onConfirm = {
                pendingDeleteComponent = null
                viewModel.deleteAreaComponent(comp)
                scope.showUndoDeleteSnackbar(snackbarHostState, "Area removed") {
                    viewModel.addAreaComponent(comp.label, comp.areaSqft, comp.ratePerSqft)
                }
            },
            onDismiss = { pendingDeleteComponent = null }
        )
    }
}

@Composable
private fun ScheduleHealthCard(r: ScheduleReport, onOpenSchedule: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenSchedule) {
        Column(Modifier.padding(16.dp)) {
            SectionLabel("Schedule health")
            Spacer(Modifier.height(8.dp))
            val ev = r.earnedValue
            if (!r.hasSchedule || ev == null) {
                Text("No construction schedule yet", style = MaterialTheme.typography.titleSmall)
                Text("Generate the residential schedule from your BOQ and budget to track progress, delays and earned value.",
                    style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onOpenSchedule, shape = MaterialTheme.shapes.small) { Text("Open Schedule") }
            } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatPercent(ev.actualPercent), style = FigureStyle)
                Spacer(Modifier.width(8.dp))
                Text("done · ${formatPercent(ev.plannedPercent)} planned", style = MaterialTheme.typography.bodySmall,
                    color = SiteTheme.colors.muted, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
            ProgressRail(progress = (ev.actualPercent / 100).toFloat(), plannedMarker = (ev.plannedPercent / 100).toFloat(), height = 8.dp)
            Spacer(Modifier.height(10.dp))
            val delayColor = if (r.projectDelayDays > 0) SiteTheme.colors.bad else SiteTheme.colors.good
            MetricRow("Forecast handover", r.forecastFinish?.let { formatDate(it) } ?: NO_VALUE)
            MetricRow("Against baseline",
                when {
                    r.projectDelayDays > 0 -> "${r.projectDelayDays} working days late"
                    r.projectDelayDays < 0 -> "${-r.projectDelayDays} working days early"
                    else -> "On time"
                }, valueColor = delayColor)
            MetricRow("Schedule index (SPI)", ev.spi?.let { "%.2f".format(it) } ?: NO_VALUE,
                valueColor = ev.spi?.let { if (it < 0.95) SiteTheme.colors.bad else SiteTheme.colors.good } ?: androidx.compose.ui.graphics.Color.Unspecified)
            MetricRow("Cost index (CPI)", ev.cpi?.let { "%.2f".format(it) } ?: NO_VALUE,
                valueColor = ev.cpi?.let { if (it < 0.95) SiteTheme.colors.bad else SiteTheme.colors.good } ?: androidx.compose.ui.graphics.Color.Unspecified)
            MetricRow("Activities", "${r.completedCount} done · ${r.inProgressCount} running · ${r.delayedCount} delayed")
            }
        }
    }
}

@Composable
internal fun AlertsCard(alerts: List<ScheduleAlert>, max: Int = 6) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionLabel("Alerts · ${alerts.size}")
            Spacer(Modifier.height(6.dp))
            alerts.take(max).forEachIndexed { i, a ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        if (a.isCritical) Icons.Filled.ErrorOutline else Icons.Filled.Info, contentDescription = null,
                        tint = if (a.isCritical) SiteTheme.colors.bad else SiteTheme.colors.planned, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        SectionLabel(humanizeLabel(a.type.name))
                        Text(a.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (i < alerts.take(max).lastIndex) HorizontalDivider(color = SiteTheme.colors.hairline)
            }
            if (alerts.size > max) Text("+${alerts.size - max} more on the Schedule tab", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AreaRateBreakdownDialog(
    components: List<ProjectAreaComponent>,
    onDismiss: () -> Unit,
    onAdd: (String, Double, Double) -> Unit,
    onUpdate: (ProjectAreaComponent, String, Double, Double) -> Unit,
    onDelete: (ProjectAreaComponent) -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("") }
    var newArea by remember { mutableStateOf("") }
    var newRate by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Area & rate breakdown") },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = false)) {
                    items(components, key = { it.id }) { comp ->
                        AreaRateEditableRow(component = comp, canDelete = components.size > 1, onUpdate = onUpdate, onDelete = onDelete)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (!showAddForm) {
                    OutlinedButton(onClick = { showAddForm = true }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add item")
                    }
                } else {
                    SectionLabel("New item")
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(newLabel, { newLabel = it }, label = { Text("Label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Row {
                        OutlinedTextField(newArea, { newArea = it }, label = { Text("Area (sqft)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(newRate, { newRate = it }, label = { Text("Rate (₹/sqft)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { showAddForm = false; newLabel = ""; newArea = ""; newRate = "" }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(shape = MaterialTheme.shapes.small, 
                            onClick = {
                                val area = newArea.toDoubleOrNull() ?: 0.0
                                val rate = newRate.toDoubleOrNull() ?: 0.0
                                if (area > 0 && rate > 0) {
                                    onAdd(newLabel.ifBlank { "Area" }, area, rate)
                                    newLabel = ""; newArea = ""; newRate = ""
                                    showAddForm = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Add") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun AreaRateEditableRow(
    component: ProjectAreaComponent,
    canDelete: Boolean,
    onUpdate: (ProjectAreaComponent, String, Double, Double) -> Unit,
    onDelete: (ProjectAreaComponent) -> Unit
) {
    var label by remember(component.id) { mutableStateOf(component.label) }
    var area by remember(component.id) { mutableStateOf(if (component.areaSqft == 0.0) "" else "%.2f".format(component.areaSqft)) }
    var rate by remember(component.id) { mutableStateOf(if (component.ratePerSqft == 0.0) "" else "%.2f".format(component.ratePerSqft)) }
    val a = area.toDoubleOrNull() ?: 0.0
    val r = rate.toDoubleOrNull() ?: 0.0
    val changed = label != component.label || a != component.areaSqft || r != component.ratePerSqft

    AppCard {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(label, { label = it }, label = { Text("Label") }, singleLine = true, modifier = Modifier.weight(1f))
                // The last row can't be deleted — a project always needs at least one area/rate.
                if (canDelete) IconButton(onClick = { onDelete(component) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
            Spacer(Modifier.height(4.dp))
            Row {
                OutlinedTextField(area, { area = it }, label = { Text("Area (sqft)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(rate, { rate = it }, label = { Text("Rate (₹/sqft)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Subtotal: ${formatCurrency(CalculationEngine.projectValue(a, r))}", style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { if (a > 0 && r > 0) onUpdate(component, label.ifBlank { "Area" }, a, r) }, enabled = changed && a > 0 && r > 0) { Text("Save") }
            }
        }
    }
}
