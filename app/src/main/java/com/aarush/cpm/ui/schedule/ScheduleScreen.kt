package com.aarush.cpm.ui.schedule

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.entity.*
import com.aarush.cpm.data.repository.*
import com.aarush.cpm.domain.schedule.WorkCalendar
import com.aarush.cpm.ui.common.*
import com.aarush.cpm.ui.project.AlertsCard
import com.aarush.cpm.ui.theme.FigureStyle
import com.aarush.cpm.ui.theme.SiteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class ScheduleUiState(
    val report: ScheduleReport? = null,
    val project: Project? = null,
    val notations: List<BOQNotation> = emptyList(),
    val vendors: List<Vendor> = emptyList(),
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val message: String? = null
)

class ScheduleViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val projectRepository: ProjectRepository,
    private val boqRepository: BOQRepository,
    private val vendorRepository: VendorRepository
) : ViewModel() {
    private val _ui = MutableStateFlow(ScheduleUiState())
    val ui: StateFlow<ScheduleUiState> = _ui.asStateFlow()
    private var projectId = 0L

    fun load(projectId: Long) {
        this.projectId = projectId
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val report = scheduleRepository.buildReport(projectId)
        _ui.value = _ui.value.copy(
            report = report,
            project = projectRepository.getById(projectId),
            notations = boqRepository.getNotations(projectId),
            vendors = vendorRepository.getForProject(projectId),
            isLoading = false
        )
    }

    private fun run(success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isBusy = true, message = null)
            val result = runCatching { block() }
            refresh()
            _ui.value = _ui.value.copy(
                isBusy = false,
                message = result.fold({ success }, { "Something went wrong: ${it.message}" })
            )
        }
    }

    fun consumeMessage() { _ui.value = _ui.value.copy(message = null) }

    fun generate(start: LocalDate, floors: Int, sundayOff: Boolean) = run("Schedule generated from BOQ and budget") {
        scheduleRepository.generateFromTemplate(projectId, start, floors, sundayOff)
    }

    fun rebaseline(start: LocalDate, sundayOff: Boolean) = run("Baseline re-planned") {
        scheduleRepository.rebaseline(projectId, start, sundayOff)
    }

    fun syncFromBoq() = run("Quantities and durations updated from the BOQ") { scheduleRepository.syncFromBoq(projectId) }

    fun saveActivity(existing: ScheduleActivity?, input: ActivityInput) =
        run(if (existing == null) "Activity added" else "Activity saved") { scheduleRepository.saveActivity(projectId, existing, input) }

    fun deleteActivity(activity: ScheduleActivity) = run("Activity deleted") { scheduleRepository.deleteActivity(activity) }

    fun deleteSchedule() = run("Schedule deleted") { scheduleRepository.deleteSchedule(projectId) }

    fun logProgress(activityId: Long, date: LocalDate, qty: Double, percent: Double?, labour: Int, notes: String) =
        run("Progress logged — stock and earned value updated") {
            scheduleRepository.logProgress(activityId, date, qty, percent, labour, notes)
        }

    fun deleteLog(log: ActivityProgressLog) = run("Progress entry removed") { scheduleRepository.deleteLog(log) }

    fun exportCsv(context: Context, uri: Uri) = run("Schedule exported") {
        val csv = scheduleRepository.exportCsv(projectId)
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                ?: throw IllegalStateException("Could not open the chosen file")
        }
    }
}

private enum class ScheduleSection(val label: String) { OVERVIEW("Overview"), ACTIVITIES("Activities"), GANTT("Gantt"), LOG("Site log") }
internal enum class ActivityFilter(val label: String) { ALL("All"), CRITICAL("Critical"), DELAYED("Delayed"), RUNNING("In progress"), UPCOMING("Next 14 days"), DONE("Done") }

/** The project's Schedule tab: generate the residential schedule from BOQ + budget, then
 *  track it — overview (earned value, S-curve, look-ahead, alerts), activity list, Gantt
 *  chart and the site progress log. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleTabContent(projectId: Long, viewModel: ScheduleViewModel, snackbarHostState: SnackbarHostState) {
    LaunchedEffect(projectId) { viewModel.load(projectId) }
    val state by viewModel.ui.collectAsState()
    val context = LocalContext.current
    var section by rememberSaveableEnum(ScheduleSection.OVERVIEW)
    var showGenerate by remember { mutableStateOf(false) }
    var showRebaseline by remember { mutableStateOf(false) }
    var confirmDeleteSchedule by remember { mutableStateOf(false) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<ScheduleActivityView?>(null) }
    var adding by remember { mutableStateOf(false) }
    var loggingFor by remember { mutableStateOf<ScheduleActivityView?>(null) }
    var pickActivityToLog by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.exportCsv(context, it) }
    }

    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    val report = state.report
    if (state.isLoading || report == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SiteTheme.colors.accent) }
        return
    }

    Box(Modifier.fillMaxSize()) {
        if (!report.hasSchedule) {
            EmptyState(
                title = "No construction schedule yet",
                body = "Generate the complete residential schedule — approvals, foundation, each floor's frame and masonry, services, finishes and handover. " +
                    "Quantities come from your BOQ (tag BOQ entries by floor for exact figures), durations from crew output, and activity budgets from Budget 2.",
                action = {
                    Button(onClick = { showGenerate = true }, shape = MaterialTheme.shapes.small, enabled = !state.isBusy) {
                        Text(if (state.isBusy) "Generating…" else "Generate schedule")
                    }
                }
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScheduleSection.values().forEach { s ->
                        FilterChip(selected = section == s, onClick = { section = s }, label = { Text(s.label) })
                    }
                }
                if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SiteTheme.colors.accent)
                when (section) {
                    ScheduleSection.OVERVIEW -> OverviewSection(
                        report = report,
                        onOpenActivity = { detailId = it },
                        onSync = { viewModel.syncFromBoq() },
                        onRebaseline = { showRebaseline = true },
                        onExport = { exportLauncher.launch("Schedule_${state.project?.name?.replace(Regex("[^A-Za-z0-9]+"), "_") ?: "project"}.csv") },
                        onRegenerate = { showGenerate = true },
                        onDelete = { confirmDeleteSchedule = true }
                    )
                    ScheduleSection.ACTIVITIES -> ActivitiesSection(report, onOpen = { detailId = it })
                    ScheduleSection.GANTT -> GanttSection(report, onOpen = { detailId = it })
                    ScheduleSection.LOG -> SiteLogSection(report, onDeleteLog = { viewModel.deleteLog(it) })
                }
            }
            when (section) {
                ScheduleSection.ACTIVITIES -> ExtendedFloatingActionButton(
                    onClick = { adding = true }, shape = MaterialTheme.shapes.small,
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Activity") },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                )
                else -> ExtendedFloatingActionButton(
                    onClick = { pickActivityToLog = true }, shape = MaterialTheme.shapes.small,
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.EditNote, null) }, text = { Text("Log progress") },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                )
            }
        }
    }

    // ---------------- dialogs
    if (showGenerate) {
        GenerateScheduleDialog(
            defaultStart = state.project?.startDate?.let { WorkCalendar.toLocalDate(it) } ?: LocalDate.now(),
            defaultFloors = state.project?.numberOfFloors ?: 1,
            sundayOff = report.sundayOff,
            replacesExisting = report.hasSchedule,
            onDismiss = { showGenerate = false },
            onGenerate = { start, floors, sunday -> showGenerate = false; viewModel.generate(start, floors, sunday) }
        )
    }
    if (showRebaseline) {
        RebaselineDialog(
            defaultStart = report.baselineStart ?: LocalDate.now(),
            sundayOff = report.sundayOff,
            onDismiss = { showRebaseline = false },
            onConfirm = { start, sunday -> showRebaseline = false; viewModel.rebaseline(start, sunday) }
        )
    }
    if (confirmDeleteSchedule) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSchedule = false },
            title = { Text("Delete the schedule?") },
            text = { Text("All activities, links and progress logs are removed, along with the material consumption booked from them. BOQ, expenses and purchases are not affected.") },
            confirmButton = { TextButton(onClick = { confirmDeleteSchedule = false; viewModel.deleteSchedule() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDeleteSchedule = false }) { Text("Cancel") } }
        )
    }
    val detailView = detailId?.let { id -> report.activities.find { it.activity.id == id } }
    detailView?.let { view ->
        run {
            ActivityDetailSheet(
                view = view,
                all = report.activities,
                onDismiss = { detailId = null },
                onLog = { loggingFor = view },
                onEdit = { editing = view },
                onDelete = { detailId = null; viewModel.deleteActivity(view.activity) },
                onDeleteLog = { viewModel.deleteLog(it) },
                onMarkComplete = {
                    viewModel.logProgress(view.activity.id, LocalDate.now(), 0.0, 100.0, view.activity.requiredLabourCount, "Marked complete")
                }
            )
        }
    }
    if (pickActivityToLog) {
        PickActivityDialog(
            activities = report.activities.filter { it.tracking.status != ActivityStatus.COMPLETED },
            onDismiss = { pickActivityToLog = false },
            onPick = { pickActivityToLog = false; loggingFor = it }
        )
    }
    loggingFor?.let { view ->
        LogProgressDialog(
            view = view,
            onDismiss = { loggingFor = null },
            onSave = { date, qty, pct, labour, notes ->
                loggingFor = null
                viewModel.logProgress(view.activity.id, date, qty, pct, labour, notes)
            }
        )
    }
    if (adding || editing != null) {
        ActivityEditDialog(
            existing = editing,
            all = report.activities,
            notations = state.notations,
            vendors = state.vendors,
            floorOptions = com.aarush.cpm.domain.schedule.ResidentialScheduleTemplate.floorOptions(state.project?.numberOfFloors ?: 1),
            onDismiss = { adding = false; editing = null },
            onSave = { input ->
                viewModel.saveActivity(editing?.activity, input)
                adding = false; editing = null
            }
        )
    }
}

/** rememberSaveable for an enum without a custom saver. */
@Composable
private fun <T : Enum<T>> rememberSaveableEnum(initial: T): MutableState<T> =
    androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initial) }

// ===================================================================== overview

@Composable
private fun OverviewSection(
    report: ScheduleReport,
    onOpenActivity: (Long) -> Unit,
    onSync: () -> Unit,
    onRebaseline: () -> Unit,
    onExport: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit
) {
    val ev = report.earnedValue ?: return
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionLabel("Overall progress · budget-weighted")
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(formatPercent(ev.actualPercent), style = FigureStyle.copy(fontSize = FigureStyle.fontSize * 1.4f))
                        Spacer(Modifier.width(10.dp))
                        Text("vs ${formatPercent(ev.plannedPercent)} planned today", style = MaterialTheme.typography.bodySmall,
                            color = SiteTheme.colors.muted, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    ProgressRail((ev.actualPercent / 100).toFloat(), plannedMarker = (ev.plannedPercent / 100).toFloat(), height = 10.dp)
                    Spacer(Modifier.height(4.dp))
                    Row {
                        LegendDot(SiteTheme.colors.accent, "Actual"); Spacer(Modifier.width(14.dp)); LegendDot(SiteTheme.colors.planned, "Planned today")
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val tile = Modifier.fillMaxHeight()
                StatRow {
                    StatTile("Baseline finish", report.baselineFinish?.let { formatShortDate(it) } ?: "—", tile.weight(1f),
                        caption = report.baselineStart?.let { "from ${formatShortDate(it)}" })
                    StatTile("Forecast finish", report.forecastFinish?.let { formatShortDate(it) } ?: "—", tile.weight(1f),
                        valueColor = if (report.projectDelayDays > 0) SiteTheme.colors.bad else SiteTheme.colors.good,
                        caption = when {
                            report.projectDelayDays > 0 -> "${report.projectDelayDays} working days late"
                            report.projectDelayDays < 0 -> "${-report.projectDelayDays} working days early"
                            else -> "on baseline"
                        })
                }
                StatRow {
                    StatTile("SPI", ev.spi?.let { "%.2f".format(it) } ?: "—", tile.weight(1f),
                        valueColor = indexColor(ev.spi), caption = "Schedule: earned ÷ planned")
                    StatTile("CPI", ev.cpi?.let { "%.2f".format(it) } ?: "—", tile.weight(1f),
                        valueColor = indexColor(ev.cpi), caption = "Cost: earned ÷ cost of work")
                }
                StatRow {
                    StatTile("Done", "${report.completedCount}", tile.weight(1f))
                    StatTile("Running", "${report.inProgressCount}", tile.weight(1f))
                    StatTile("Delayed", "${report.delayedCount}", tile.weight(1f),
                        valueColor = if (report.delayedCount > 0) SiteTheme.colors.bad else androidx.compose.ui.graphics.Color.Unspecified)
                    StatTile("To start", "${report.notStartedCount}", tile.weight(1f))
                }
            }
        }
        if (report.alerts.isNotEmpty()) item { AlertsCard(report.alerts, max = 8) }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionLabel("S-curve · cumulative % complete")
                    Spacer(Modifier.height(10.dp))
                    SCurveChart(report.sCurve, report.today, Modifier.fillMaxWidth().height(180.dp))
                    Spacer(Modifier.height(6.dp))
                    Row { LegendDot(SiteTheme.colors.planned, "Planned (baseline)"); Spacer(Modifier.width(14.dp)); LegendDot(SiteTheme.colors.accent, "Actual") }
                }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionLabel("Earned value")
                    Spacer(Modifier.height(6.dp))
                    MetricRow("Budget at completion (scheduled)", formatCurrency(ev.budgetAtCompletion))
                    MetricRow("Planned value to date", formatCurrency(ev.plannedValue))
                    MetricRow("Earned value (work done)", formatCurrency(ev.earnedValue))
                    MetricRow("Actual cost of work done", formatCurrency(ev.actualCost))
                    Text("Total spent ${formatCurrency(report.totalSpent)} − material still in store ${formatCurrency(report.stockValue)}",
                        style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
                    HorizontalDivider(color = SiteTheme.colors.hairline, modifier = Modifier.padding(vertical = 4.dp))
                    MetricRow("Schedule variance", formatCurrency(ev.scheduleVariance), valueColor = signColor(ev.scheduleVariance))
                    MetricRow("Cost variance", formatCurrency(ev.costVariance), valueColor = signColor(ev.costVariance))
                    MetricRow("Estimate at completion", formatCurrency(ev.estimateAtCompletion), emphasize = true)
                    MetricRow("Variance at completion", formatCurrency(ev.varianceAtCompletion), valueColor = signColor(ev.varianceAtCompletion))
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = SiteTheme.colors.hairline)
                    Spacer(Modifier.height(8.dp))
                    MetricRow("Budget 2 (measured)", formatCurrency(report.budget2))
                    MetricRow("Assigned to activities", formatCurrency(report.scheduledBudget))
                    if (report.unscheduledCategories.isNotEmpty()) {
                        Text("No activities yet for: ${report.unscheduledCategories.joinToString { it.name.replace('_', ' ') }} — their budget isn't tracked in earned value.",
                            style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.warn)
                    }
                }
            }
        }
        item { LookaheadCard(report, onOpenActivity) }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionLabel("Phases")
                    report.phases.forEach { ph ->
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ph.phase, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${formatPercent(ph.actualPercent)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                        ProgressRail((ph.actualPercent / 100).toFloat(), plannedMarker = (ph.plannedPercent / 100).toFloat())
                        Text("${formatShortDate(ph.start)} → ${formatShortDate(ph.finish)}" +
                            (if (ph.forecastFinish.isAfter(ph.finish)) " · forecast ${formatShortDate(ph.forecastFinish)}" else "") +
                            " · ${ph.completedCount}/${ph.activityCount} done · ${formatCurrencyShort(ph.budget)}",
                            style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
                    }
                }
            }
        }
        if (report.criticalPath.isNotEmpty()) item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionLabel("Critical path · ${report.criticalPath.size} remaining")
                    Text("Any delay to these moves the handover date.", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
                    report.criticalPath.take(8).forEach { v -> ActivityLine(v, onClick = { onOpenActivity(v.activity.id) }) }
                }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionLabel("Schedule tools")
                    Spacer(Modifier.height(8.dp))
                    ToolButton("Update quantities from BOQ", "Pull the latest BOQ measurements into every activity and recompute durations.", onSync)
                    ToolButton("Re-baseline", "Re-plan baseline dates from a new start date (use after a major change).", onRebaseline)
                    ToolButton("Export to CSV", "Open the schedule and tracking status in Excel or Sheets.", onExport)
                    ToolButton("Regenerate from template", "Start again (e.g. after changing the number of floors). Progress logs are cleared.", onRegenerate)
                    TextButton(onClick = onDelete) { Text("Delete schedule", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
        }
    }
}

@Composable
internal fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
    }
}

@Composable
private fun indexColor(v: Double?) = when {
    v == null -> androidx.compose.ui.graphics.Color.Unspecified
    v < 0.9 -> SiteTheme.colors.bad
    v < 1.0 -> SiteTheme.colors.warn
    else -> SiteTheme.colors.good
}

@Composable
private fun signColor(v: Double) = if (v < 0) SiteTheme.colors.bad else SiteTheme.colors.good

@Composable
private fun LookaheadCard(report: ScheduleReport, onOpenActivity: (Long) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionLabel("Look-ahead · ${formatShortDate(report.lookaheadStart)} – ${formatShortDate(report.lookaheadEnd)}")
            Spacer(Modifier.height(6.dp))
            if (report.upcoming.isEmpty()) {
                Text("Nothing scheduled in the next two weeks.", style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
            } else {
                report.upcoming.take(10).forEach { v -> ActivityLine(v, onClick = { onOpenActivity(v.activity.id) }) }
                if (report.upcoming.size > 10) Text("+${report.upcoming.size - 10} more", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
            }
            if (report.labourNeeds.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                SectionLabel("Labour to arrange")
                report.labourNeeds.forEach { MetricRow("${it.skill} (${it.activities} activit${if (it.activities == 1) "y" else "ies"})", "${it.workers} workers") }
            }
            if (report.materialNeeds.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                SectionLabel("Materials needed vs stock")
                Row(Modifier.padding(top = 4.dp)) {
                    Text("Material", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted, modifier = Modifier.weight(1.4f))
                    Text("Need", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted, modifier = Modifier.weight(1f))
                    Text("Stock", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted, modifier = Modifier.weight(1f))
                    Text("Buy", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted, modifier = Modifier.weight(1f))
                }
                report.materialNeeds.forEach { m ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(m.material, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.4f))
                        Text(formatQuantity(round1(m.required), m.unit), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(formatQuantity(round1(m.inStock), m.unit), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(if (m.shortage > 0) formatQuantity(round1(m.shortage), m.unit) else "—", style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold, color = if (m.shortage > 0) SiteTheme.colors.bad else SiteTheme.colors.good, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

internal fun round1(v: Double) = kotlin.math.round(v * 10) / 10.0

// ===================================================================== activities

@Composable
internal fun statusColors(status: ActivityStatus) = when (status) {
    ActivityStatus.COMPLETED -> SiteTheme.colors.good to SiteTheme.colors.goodTint
    ActivityStatus.DELAYED -> SiteTheme.colors.bad to SiteTheme.colors.badTint
    ActivityStatus.IN_PROGRESS -> SiteTheme.colors.accent to SiteTheme.colors.accentTint
    ActivityStatus.NOT_STARTED -> SiteTheme.colors.muted to SiteTheme.colors.hairline
}

/** One compact activity line: name, dates, progress against plan. */
@Composable
internal fun ActivityLine(v: ScheduleActivityView, onClick: () -> Unit) {
    val t = v.tracking
    val (fg, bg) = statusColors(t.status)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (v.activity.isMilestone) Icon(Icons.Filled.Flag, null, tint = SiteTheme.colors.accent, modifier = Modifier.size(14.dp).padding(end = 2.dp))
                    Text(v.activity.activityName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    StatusPill(if (t.isCritical && t.status != ActivityStatus.COMPLETED) "${t.status.name.replace('_', ' ')} · crit" else t.status.name.replace('_', ' '), fg, bg)
                }
                Spacer(Modifier.height(4.dp))
                ProgressRail((t.actualPercent / 100).toFloat(), plannedMarker = (t.plannedPercent / 100).toFloat(),
                    color = if (t.status == ActivityStatus.DELAYED) SiteTheme.colors.bad else SiteTheme.colors.accent, height = 5.dp)
                Text(
                    "${formatShortDate(t.forecastStart)} → ${formatShortDate(t.forecastFinish)}" +
                        (if (t.delayDays > 0) " · +${t.delayDays}d" else "") +
                        " · ${formatPercent(t.actualPercent)}" +
                        (if (v.plannedQuantity > 0) " · ${formatQuantity(round1(v.activity.completedQuantity), "")}/${formatQuantity(round1(v.plannedQuantity), v.activity.unit)}" else ""),
                    style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted
                )
            }
        }
    }
}

@Composable
private fun ActivitiesSection(report: ScheduleReport, onOpen: (Long) -> Unit) {
    var filter by remember { mutableStateOf(ActivityFilter.ALL) }
    val list = report.activities.filter { v ->
        val t = v.tracking
        when (filter) {
            ActivityFilter.ALL -> true
            ActivityFilter.CRITICAL -> t.isCritical
            ActivityFilter.DELAYED -> t.status == ActivityStatus.DELAYED
            ActivityFilter.RUNNING -> t.status == ActivityStatus.IN_PROGRESS || (t.status == ActivityStatus.DELAYED && t.actualPercent > 0)
            ActivityFilter.UPCOMING -> v in report.upcoming
            ActivityFilter.DONE -> t.status == ActivityStatus.COMPLETED
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActivityFilter.values().forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
        }
        if (list.isEmpty()) {
            EmptyState("Nothing here", "No activities match this filter.")
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.groupBy { it.activity.phase.ifBlank { "Other" } }.toSortedMap().forEach { (phase, acts) ->
                    item(key = "ph-$phase") { SectionLabel(phase, modifier = Modifier.padding(top = 8.dp)) }
                    items(acts, key = { it.activity.id }) { v ->
                        AppCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 12.dp)) {
                                ActivityLine(v, onClick = { onOpen(v.activity.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===================================================================== gantt & log

@Composable
private fun GanttSection(report: ScheduleReport, onOpen: (Long) -> Unit) {
    var dayWidth by remember { mutableStateOf(7f) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = dayWidth == 4f, onClick = { dayWidth = 4f }, label = { Text("Months") })
            FilterChip(selected = dayWidth == 7f, onClick = { dayWidth = 7f }, label = { Text("Weeks") })
            FilterChip(selected = dayWidth == 14f, onClick = { dayWidth = 14f }, label = { Text("Days") })
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            LegendDot(SiteTheme.colors.planned, "Baseline"); Spacer(Modifier.width(12.dp))
            LegendDot(SiteTheme.colors.accent, "Forecast / done"); Spacer(Modifier.width(12.dp))
            LegendDot(SiteTheme.colors.bad, "Delayed")
        }
        GanttChart(report = report, dayWidthDp = dayWidth, onRowClick = onOpen, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun SiteLogSection(report: ScheduleReport, onDeleteLog: (ActivityProgressLog) -> Unit) {
    var pendingDelete by remember { mutableStateOf<ActivityProgressLog?>(null) }
    if (report.recentLogs.isEmpty()) {
        EmptyState("No site progress logged yet", "Use \"Log progress\" each day to record quantities done and labour on site. Each entry updates progress, stock consumption and earned value.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionLabel("Latest ${report.recentLogs.size} entries") }
        report.recentLogs.groupBy { localDayOf(it.first.date) }.forEach { (day, entries) ->
            item(key = "d-$day") { Text(formatDate(day), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp)) }
            items(entries, key = { it.first.id }) { (log, name) ->
                val unit = report.activities.find { it.activity.id == log.activityId }?.activity?.unit ?: ""
                AppCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    log.quantityDone.takeIf { it > 0 }?.let { "${formatQuantity(round1(it), unit)} done" },
                                    log.percentComplete?.let { "${formatPercent(it)} complete" },
                                    log.labourCount.takeIf { it > 0 }?.let { "$it workers" },
                                    log.notes.takeIf { it.isNotBlank() }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted
                            )
                        }
                        TextButton(onClick = { pendingDelete = log }) { Text("Remove") }
                    }
                }
            }
        }
    }
    pendingDelete?.let { log ->
        ConfirmDeleteDialog(itemLabel = "this progress entry (its material consumption is removed too)", onConfirm = {
            pendingDelete = null; onDeleteLog(log)
        }, onDismiss = { pendingDelete = null })
    }
}
