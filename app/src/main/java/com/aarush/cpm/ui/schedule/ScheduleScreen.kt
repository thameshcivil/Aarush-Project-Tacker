package com.aarush.cpm.ui.schedule

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class ScheduleUiState(
    val report: ScheduleReport? = null,
    val project: Project? = null,
    val notations: List<BOQNotation> = emptyList(),
    val vendors: List<Vendor> = emptyList(),
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val message: String? = null,
    /** Set when the schedule could not be read; the screen offers Retry. */
    val loadError: String? = null
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

    /** Re-reads the schedule after a failed load. */
    fun retry() {
        _ui.value = _ui.value.copy(loadError = null, isLoading = _ui.value.report == null)
        viewModelScope.launch { refresh() }
    }

    /** Same reads as before. A failure now ends in an error with Retry instead of an endless
     *  spinner; any schedule already on screen is kept. */
    private suspend fun refresh() {
        try {
            val report = scheduleRepository.buildReport(projectId)
            _ui.value = _ui.value.copy(
                report = report,
                project = projectRepository.getById(projectId),
                notations = boqRepository.getNotations(projectId),
                vendors = vendorRepository.getForProject(projectId),
                isLoading = false,
                loadError = null
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.value = _ui.value.copy(isLoading = false, loadError = "Couldn't read the schedule. ${e.message ?: ""}".trim())
        }
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
        run("Progress logged. Stock and earned value updated.") {
            scheduleRepository.logProgress(activityId, date, qty, percent, labour, notes)
        }

    /** Removes a progress entry. No message here — the screen shows its own snackbar with Undo. */
    fun deleteLog(log: ActivityProgressLog) = run(null) { scheduleRepository.deleteLog(log) }

    /** Undo for [deleteLog]: logs the same entry again through the normal logging path, so
     *  progress and the material consumption it books are re-applied exactly as when first
     *  entered (the restored entry gets a new id). */
    fun restoreLog(log: ActivityProgressLog) = run("Progress entry restored") {
        scheduleRepository.logProgress(
            log.activityId, WorkCalendar.toLocalDate(log.date), log.quantityDone, log.percentComplete, log.labourCount, log.notes
        )
    }

    fun exportCsv(context: Context, uri: Uri) = run("Schedule exported") {
        val csv = scheduleRepository.exportCsv(projectId)
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                ?: throw IllegalStateException("Could not open the chosen file")
        }
    }
}


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
    val scope = rememberCoroutineScope()
    /** Called after the user confirmed removal: removes the entry, then offers Undo. */
    val removeLog: (ActivityProgressLog) -> Unit = { log ->
        val booked = log.quantityDone > 0 && state.report?.activities
            ?.find { it.activity.id == log.activityId }?.activity?.linkedNotationCode?.isNotBlank() == true
        viewModel.deleteLog(log)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = if (booked) "Progress entry removed. Materials returned to stock." else "Progress entry removed",
                actionLabel = "Undo",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreLog(log)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.exportCsv(context, it) }
    }

    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    val report = state.report
    if (report == null) {
        if (state.loadError != null) ErrorState(message = state.loadError!!, onRetry = viewModel::retry)
        else SkeletonScreen(headline = false, sections = 3, rows = 4)
        return
    }
    val exportFileName = "Schedule_${state.project?.name?.replace(Regex("[^A-Za-z0-9]+"), "_") ?: "project"}.csv"

    val pa = com.aarush.cpm.ui.common.LocalProjectAccess.current
    Box(Modifier.fillMaxSize()) {
        if (!report.hasSchedule && !pa.editSchedulePlan) {
            EmptyState(
                title = "No construction schedule yet",
                body = "The Owner or a project manager can generate the schedule. You'll see it here once it's ready."
            )
        } else if (!report.hasSchedule) {
            EmptyState(
                title = "No construction schedule yet",
                body = "Generate the complete residential schedule: approvals, foundation, each floor's frame and masonry, services, finishes and handover. " +
                    "Quantities come from your BOQ (tag BOQ entries by floor for exact figures), durations from crew output, and activity budgets from Budget 2.",
                action = {
                    Button(onClick = { showGenerate = true }, shape = MaterialTheme.shapes.small, enabled = !state.isBusy) {
                        Text(if (state.isBusy) "Generating…" else "Generate schedule")
                    }
                }
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                ScheduleTopRow(
                    section = section,
                    onSection = { section = it },
                    toolsEnabled = !state.isBusy,
                    onSync = { viewModel.syncFromBoq() },
                    onRebaseline = { showRebaseline = true },
                    onExport = { exportLauncher.launch(exportFileName) },
                    onRegenerate = { showGenerate = true },
                    onDelete = { confirmDeleteSchedule = true }
                )
                // Thin bar while a tool or log is being applied; the schedule stays on screen.
                RefreshBar(visible = state.isBusy)
                state.loadError?.let { ErrorBanner(it, onRetry = viewModel::retry, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                val floorOrder = com.aarush.cpm.domain.schedule.ResidentialScheduleTemplate.floorOptions(state.project?.numberOfFloors ?: 1)
                when (section) {
                    ScheduleSection.OVERVIEW -> ScheduleOverview(report, onOpenActivity = { detailId = it })
                    ScheduleSection.ACTIVITIES -> ScheduleActivitiesList(report, floorOrder, onOpen = { detailId = it })
                    ScheduleSection.GANTT -> ScheduleGantt(report, onOpen = { detailId = it })
                    ScheduleSection.LOG -> ScheduleSiteLog(report, onDeleteLog = removeLog)
                }
            }
            when {
                section == ScheduleSection.ACTIVITIES -> if (pa.editSchedulePlan) ExtendedFloatingActionButton(
                    onClick = { adding = true }, shape = MaterialTheme.shapes.small,
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Activity") },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                )
                else -> if (pa.logProgress) ExtendedFloatingActionButton(
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
                onDeleteLog = removeLog,
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

internal fun round1(v: Double) = kotlin.math.round(v * 10) / 10.0

// ===================================================================== activities

@Composable
internal fun statusColors(status: ActivityStatus) = when (status) {
    ActivityStatus.COMPLETED -> SiteTheme.colors.good to SiteTheme.colors.goodTint
    ActivityStatus.DELAYED -> SiteTheme.colors.bad to SiteTheme.colors.badTint
    ActivityStatus.IN_PROGRESS -> SiteTheme.colors.accentText to SiteTheme.colors.accentTint
    // Muted text on the soft surface tint (4.9:1) — on the hairline grey it was 4.4:1.
    ActivityStatus.NOT_STARTED -> SiteTheme.colors.muted to MaterialTheme.colorScheme.surfaceVariant
}

/**
 * One compact activity line: floor code + name + status tag, a progress rail (gold done, red
 * when delayed, dark-neutral tick = planned today), then dates and quantities. One "·" at most
 * per line. Whole line is a 48dp+ tap target that opens the activity.
 */
@Composable
internal fun ActivityLine(v: ScheduleActivityView, onClick: () -> Unit, showFloor: Boolean = true, showPhase: Boolean = false) {
    val t = v.tracking
    val (fg, bg) = statusColors(t.status)
    val delayed = t.status == ActivityStatus.DELAYED
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(onClickLabel = "Open activity", onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (v.activity.isMilestone) Icon(Icons.Filled.Flag, contentDescription = "Milestone", tint = SiteTheme.colors.planned,
                modifier = Modifier.size(14.dp).padding(end = 2.dp))
            val code = floorCode(v.activity.floorLabel)
            if (showFloor && code.isNotEmpty()) {
                Text(code, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 6.dp).semantics { contentDescription = v.activity.floorLabel })
            }
            Text(activityBaseName(v.activity.activityName, v.activity.floorLabel), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            StatusPill(if (t.isCritical && t.status != ActivityStatus.COMPLETED) "${t.status.name} · crit" else t.status.name, fg, bg)
        }
        if (showPhase && v.activity.phase.isNotBlank()) {
            Text(v.activity.phase, style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
        }
        Spacer(Modifier.height(6.dp))
        ProgressRail((t.actualPercent / 100).toFloat(), plannedMarker = (t.plannedPercent / 100).toFloat(),
            color = if (delayed) SiteTheme.colors.bad else SiteTheme.colors.accent, height = 5.dp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${formatShortDate(t.forecastStart)} → ${formatShortDate(t.forecastFinish)}",
                style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
            if (t.delayDays > 0) {
                Text("  +${t.delayDays}d late", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.bad, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            Text(
                formatPercent(t.actualPercent) +
                    (if (v.plannedQuantity > 0) " · ${formatQuantity(round1(v.activity.completedQuantity), "")}/${formatQuantity(round1(v.plannedQuantity), v.activity.unit)}" else ""),
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold
            )
        }
    }
}
