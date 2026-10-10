package com.aarush.cpm.ui.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aarush.cpm.data.entity.ActivityProgressLog
import com.aarush.cpm.data.entity.ActivityStatus
import com.aarush.cpm.data.repository.ScheduleActivityView
import com.aarush.cpm.data.repository.ScheduleReport
import com.aarush.cpm.ui.common.*
import com.aarush.cpm.ui.project.AlertsCard
import com.aarush.cpm.ui.project.LedgerRow
import com.aarush.cpm.ui.project.LedgerSection
import com.aarush.cpm.ui.theme.SiteTheme
import java.time.LocalDate

/*
 * Schedule screen views (redesign Phase 4). Display only: every figure comes from the
 * ScheduleReport the repository builds (earned value, SPI/CPI, forecast, critical path,
 * tracking per activity, logs); nothing here recalculates schedule data.
 *
 * Colour roles: gold = actual / done, dark neutral = planned (baseline), red = delayed or
 * overdue, maroon = primary actions only.
 */

internal enum class ScheduleSection(val label: String) { OVERVIEW("Overview"), ACTIVITIES("Activities"), GANTT("Gantt"), LOG("Site log") }

internal const val NOT_AVAILABLE = "Not available"

// ===================================================================== top row + tools menu

/**
 * Section selector plus the schedule tools in a ⋮ menu (previously five large buttons at the
 * bottom of the Overview). Every tool keeps its own dialog / confirmation.
 */
@Composable
internal fun ScheduleTopRow(
    section: ScheduleSection,
    onSection: (ScheduleSection) -> Unit,
    toolsEnabled: Boolean,
    onSync: () -> Unit,
    onRebaseline: () -> Unit,
    onExport: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            ScheduleSection.values().forEach { s ->
                FilterChip(selected = section == s, onClick = { onSection(s) }, label = { Text(s.label) }, colors = selectedChipColors())
            }
        }
        var open by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { open = true }, enabled = toolsEnabled) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Schedule tools")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                ToolItem("Update quantities from BOQ", "Pull the latest BOQ measurements and recompute durations", Icons.Filled.Sync) { open = false; onSync() }
                ToolItem("Re-baseline", "Re-plan baseline dates from a new start date", Icons.Filled.Update) { open = false; onRebaseline() }
                ToolItem("Export to CSV", "Open the schedule in Excel or Sheets", Icons.Filled.FileDownload) { open = false; onExport() }
                HorizontalDivider()
                ToolItem("Regenerate from template", "Start again. Progress logs are cleared.", Icons.Filled.Refresh) { open = false; onRegenerate() }
                DropdownMenuItem(
                    text = { Text("Delete schedule", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { open = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun ToolItem(title: String, hint: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(title)
                Text(hint, style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
            }
        },
        leadingIcon = { Icon(icon, null) },
        onClick = onClick
    )
}

// ===================================================================== legend keys

/** Selected chips use the maroon tint (primary action colour); gold is reserved for "actual". */
@Composable
internal fun selectedChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
)

internal enum class KeyKind { ACTUAL, PLANNED_TICK, PLANNED_OUTLINE, PLANNED_LINE, ACTUAL_LINE, FORECAST, DELAYED, TODAY, CRITICAL }

/** Small drawn key matching exactly how the chart draws that thing. */
@Composable
internal fun ChartKey(kind: KeyKind, modifier: Modifier = Modifier) {
    val c = SiteTheme.colors
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.size(width = 18.dp, height = 12.dp)) {
        val h = size.height; val w = size.width
        when (kind) {
            KeyKind.ACTUAL -> drawRoundRect(c.accent, Offset(0f, h * 0.2f), Size(w, h * 0.6f), CornerRadius(2f))
            KeyKind.FORECAST -> drawRoundRect(c.accent.copy(alpha = 0.3f), Offset(0f, h * 0.2f), Size(w, h * 0.6f), CornerRadius(2f))
            KeyKind.DELAYED -> drawRoundRect(c.bad, Offset(0f, h * 0.2f), Size(w, h * 0.6f), CornerRadius(2f))
            KeyKind.PLANNED_OUTLINE -> drawRoundRect(c.planned, Offset(1f, h * 0.2f), Size(w - 2f, h * 0.6f), CornerRadius(2f), style = Stroke(1.5.dp.toPx()))
            KeyKind.PLANNED_TICK -> drawRect(c.planned, Offset(w / 2 - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), h))
            KeyKind.TODAY -> drawLine(ink, Offset(w / 2, 0f), Offset(w / 2, h), strokeWidth = 2.dp.toPx())
            KeyKind.PLANNED_LINE -> drawLine(c.planned, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 2.dp.toPx())
            KeyKind.ACTUAL_LINE -> drawLine(c.accent, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 2.5.dp.toPx())
            KeyKind.CRITICAL -> drawRoundRect(c.planned, Offset(1f, h * 0.2f), Size(w - 2f, h * 0.6f), CornerRadius(2f), style = Stroke(2.5.dp.toPx()))
        }
    }
}

@Composable
internal fun KeyItem(kind: KeyKind, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 14.dp, bottom = 4.dp)) {
        ChartKey(kind)
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
    }
}

// ===================================================================== overview

@Composable
private fun indexColor(v: Double?) = when {
    v == null -> SiteTheme.colors.muted
    v < 0.9 -> SiteTheme.colors.bad
    v < 1.0 -> SiteTheme.colors.warn
    else -> SiteTheme.colors.good
}

@Composable
private fun signColor(v: Double) = if (v < 0) SiteTheme.colors.bad else SiteTheme.colors.good

/** Overview: key figures table first, then S-curve and critical path, then the rest. */
@Composable
internal fun ScheduleOverview(report: ScheduleReport, onOpenActivity: (Long) -> Unit) {
    val ev = report.earnedValue
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            LedgerSection("Key figures") {
                if (ev != null) {
                    Spacer(Modifier.height(12.dp))
                    ProgressRail((ev.actualPercent / 100).toFloat(), plannedMarker = (ev.plannedPercent / 100).toFloat(), height = 10.dp)
                    Spacer(Modifier.height(4.dp))
                }
                LedgerRow("Actual progress", ev?.let { formatPercent(it.actualPercent) } ?: NOT_AVAILABLE,
                    caption = "Budget-weighted work done", leading = { ChartKey(KeyKind.ACTUAL) },
                    valueColor = if (ev == null) SiteTheme.colors.muted else Color.Unspecified)
                LedgerRow("Planned progress", ev?.let { formatPercent(it.plannedPercent) } ?: NOT_AVAILABLE,
                    caption = "Where the baseline says you should be today", leading = { ChartKey(KeyKind.PLANNED_TICK) },
                    valueColor = if (ev == null) SiteTheme.colors.muted else Color.Unspecified)
                LedgerRow("SPI", ev?.spi?.let { "%.2f".format(it) } ?: NOT_AVAILABLE,
                    caption = if (ev?.spi == null) "No planned value yet" else "Schedule: earned ÷ planned",
                    valueColor = indexColor(ev?.spi))
                LedgerRow("CPI", ev?.cpi?.let { "%.2f".format(it) } ?: NOT_AVAILABLE,
                    caption = if (ev?.cpi == null) "No cost of work booked yet" else "Cost: earned ÷ cost of work",
                    valueColor = indexColor(ev?.cpi))
                LedgerRow("Forecast finish", report.forecastFinish?.let { formatDate(it) } ?: NOT_AVAILABLE,
                    caption = report.baselineFinish?.let { "Baseline ${formatDate(it)}" },
                    valueColor = if (report.forecastFinish == null) SiteTheme.colors.muted else Color.Unspecified)
                LedgerRow(
                    "Delay",
                    when {
                        report.forecastFinish == null -> NOT_AVAILABLE
                        report.projectDelayDays > 0 -> "${report.projectDelayDays} days late"
                        report.projectDelayDays < 0 -> "${-report.projectDelayDays} days early"
                        else -> "On baseline"
                    },
                    caption = "Working days, forecast against baseline",
                    valueColor = when {
                        report.forecastFinish == null -> SiteTheme.colors.muted
                        report.projectDelayDays > 0 -> SiteTheme.colors.bad
                        report.projectDelayDays < 0 -> SiteTheme.colors.good
                        else -> Color.Unspecified
                    }
                )
                LedgerRow(
                    "Activities", "${report.activities.size}",
                    caption = "${report.completedCount} done, ${report.inProgressCount} running, ${report.delayedCount} delayed, ${report.notStartedCount} to start",
                    divider = false
                )
            }
        }
        item {
            LedgerSection("S-curve") {
                Spacer(Modifier.height(12.dp))
                SCurveChart(report.sCurve, report.today, Modifier.fillMaxWidth().height(180.dp))
                Spacer(Modifier.height(8.dp))
                FlowKeys { KeyItem(KeyKind.PLANNED_LINE, "Planned (baseline)"); KeyItem(KeyKind.ACTUAL_LINE, "Actual"); KeyItem(KeyKind.TODAY, "Today") }
                Spacer(Modifier.height(4.dp))
            }
        }
        if (report.criticalPath.isNotEmpty()) item {
            LedgerSection("Critical path") {
                Text("${report.criticalPath.size} remaining. Any delay to these moves the handover date.",
                    style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted, modifier = Modifier.padding(top = 10.dp))
                val shown = report.criticalPath.take(8)
                shown.forEachIndexed { i, v ->
                    ActivityLine(v, onClick = { onOpenActivity(v.activity.id) })
                    if (i < shown.lastIndex) HorizontalDivider(color = SiteTheme.colors.hairline)
                }
                if (report.criticalPath.size > 8) {
                    Text("+${report.criticalPath.size - 8} more under Activities, Critical", style = MaterialTheme.typography.labelSmall,
                        color = SiteTheme.colors.muted, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
        if (report.alerts.isNotEmpty()) item { AlertsCard(report.alerts, max = 8) }
        item { LookaheadSection(report, onOpenActivity) }
        ev?.let { e ->
            item {
                LedgerSection("Earned value") {
                    LedgerRow("Budget at completion", formatCurrency(e.budgetAtCompletion), caption = "Scheduled")
                    LedgerRow("Planned value to date", formatCurrency(e.plannedValue))
                    LedgerRow("Earned value", formatCurrency(e.earnedValue), caption = "Work done")
                    LedgerRow("Actual cost of work done", formatCurrency(e.actualCost),
                        caption = "Spent ${formatCurrency(report.totalSpent)} − in store ${formatCurrency(report.stockValue)}")
                    LedgerRow("Schedule variance", formatCurrency(e.scheduleVariance), valueColor = signColor(e.scheduleVariance))
                    LedgerRow("Cost variance", formatCurrency(e.costVariance), valueColor = signColor(e.costVariance))
                    LedgerRow("Estimate at completion", formatCurrency(e.estimateAtCompletion), emphasize = true)
                    LedgerRow("Variance at completion", formatCurrency(e.varianceAtCompletion), valueColor = signColor(e.varianceAtCompletion))
                    LedgerRow("Budget 2 (measured)", formatCurrency(report.budget2))
                    LedgerRow("Assigned to activities", formatCurrency(report.scheduledBudget), divider = report.unscheduledCategories.isNotEmpty())
                    if (report.unscheduledCategories.isNotEmpty()) {
                        Text("No activities yet for: ${report.unscheduledCategories.joinToString { humanizeLabel(it.name) }}. Their budget isn't tracked in earned value.",
                            style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.warn, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
        if (report.phases.isNotEmpty()) item {
            LedgerSection("Phases") {
                report.phases.forEachIndexed { i, ph ->
                    Column(Modifier.padding(vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ph.phase, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(formatPercent(ph.actualPercent), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                        ProgressRail((ph.actualPercent / 100).toFloat(), plannedMarker = (ph.plannedPercent / 100).toFloat())
                        Text("${formatShortDate(ph.start)} → ${formatShortDate(ph.finish)}, ${ph.completedCount}/${ph.activityCount} done, ${formatCurrencyShort(ph.budget)}",
                            style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
                        if (ph.forecastFinish.isAfter(ph.finish)) {
                            Text("Forecast finish ${formatShortDate(ph.forecastFinish)}", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.bad)
                        }
                    }
                    if (i < report.phases.lastIndex) HorizontalDivider(color = SiteTheme.colors.hairline)
                }
            }
        }
    }
}

/** Keys laid out in a wrapping row so they never get cut on narrow screens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlowKeys(content: @Composable () -> Unit) {
    FlowRow(Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun LookaheadSection(report: ScheduleReport, onOpenActivity: (Long) -> Unit) {
    LedgerSection("Next two weeks") {
        Text("${formatShortDate(report.lookaheadStart)} to ${formatShortDate(report.lookaheadEnd)}",
            style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted, modifier = Modifier.padding(top = 10.dp))
        if (report.upcoming.isEmpty()) {
            Text("Nothing scheduled in the next two weeks.", style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted,
                modifier = Modifier.padding(vertical = 10.dp))
        } else {
            val shown = report.upcoming.take(10)
            shown.forEachIndexed { i, v ->
                ActivityLine(v, onClick = { onOpenActivity(v.activity.id) })
                if (i < shown.lastIndex) HorizontalDivider(color = SiteTheme.colors.hairline)
            }
            if (report.upcoming.size > 10) Text("+${report.upcoming.size - 10} more", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
        }
        if (report.labourNeeds.isNotEmpty()) {
            Text("Labour to arrange", style = MaterialTheme.typography.labelMedium, color = SiteTheme.colors.muted,
                modifier = Modifier.padding(top = 14.dp).semantics { heading() })
            report.labourNeeds.forEachIndexed { i, it ->
                LedgerRow("${it.skill}", "${it.workers} workers",
                    caption = "${it.activities} activit${if (it.activities == 1) "y" else "ies"}", divider = i < report.labourNeeds.lastIndex)
            }
        }
        if (report.materialNeeds.isNotEmpty()) {
            Text("Materials needed vs stock", style = MaterialTheme.typography.labelMedium, color = SiteTheme.colors.muted,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp).semantics { heading() })
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                ColumnLabel("Material", Modifier.weight(1.3f))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { ColumnLabel("Need") }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { ColumnLabel("Stock") }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { ColumnLabel("Buy") }
            }
            report.materialNeeds.forEach { m ->
                HorizontalDivider(color = SiteTheme.colors.hairline)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(m.material, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.3f))
                    NumText(formatQuantity(round1(m.required), m.unit), Modifier.weight(1f))
                    NumText(formatQuantity(round1(m.inStock), m.unit), Modifier.weight(1f))
                    NumText(if (m.shortage > 0) formatQuantity(round1(m.shortage), m.unit) else NO_VALUE, Modifier.weight(1f),
                        color = if (m.shortage > 0) SiteTheme.colors.bad else SiteTheme.colors.muted, bold = true)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun NumText(text: String, modifier: Modifier, color: Color = Color.Unspecified, bold: Boolean = false) {
    Box(modifier.padding(start = 4.dp), contentAlignment = Alignment.CenterEnd) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = if (bold) FontWeight.SemiBold else null, maxLines = 2)
    }
}

// ===================================================================== activities by floor

internal const val WHOLE_BUILDING = "Whole building"

/**
 * Activities grouped by floor tag, floors in building order (whole-building work first, then
 * Foundation, Plinth, GF, 1F... Terrace, then any custom tags). Inside a floor the schedule's
 * own order is kept. Every activity appears exactly once. Display grouping only.
 */
internal fun activitiesByFloor(acts: List<ScheduleActivityView>, floorOrder: List<String>): List<Pair<String, List<ScheduleActivityView>>> {
    val groups = acts.groupBy { it.activity.floorLabel.trim() }
    fun rank(f: String) = when {
        f.isEmpty() -> -1
        f in floorOrder -> floorOrder.indexOf(f)
        else -> floorOrder.size
    }
    return groups.entries.sortedWith(compareBy({ rank(it.key) }, { it.key })).map { (floor, list) ->
        (if (floor.isEmpty()) WHOLE_BUILDING else floor) to list
    }
}

internal enum class ActivityFilter(val label: String) { ALL("All"), CRITICAL("Critical"), DELAYED("Delayed"), RUNNING("In progress"), UPCOMING("Next 14 days"), DONE("Done") }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ScheduleActivitiesList(report: ScheduleReport, floorOrder: List<String>, onOpen: (Long) -> Unit) {
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
            ActivityFilter.values().forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }, colors = selectedChipColors()) }
        }
        if (list.isEmpty()) {
            EmptyState("Nothing here", "No activities match this filter.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                activitiesByFloor(list, floorOrder).forEach { (floor, acts) ->
                    stickyHeader(key = "floor-$floor") { FloorHeading(floor, acts) }
                    acts.forEachIndexed { i, v ->
                        item(key = v.activity.id) {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                ActivityLine(v, onClick = { onOpen(v.activity.id) }, showFloor = false, showPhase = true)
                                if (i < acts.lastIndex) HorizontalDivider(color = SiteTheme.colors.hairline)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloorHeading(floor: String, acts: List<ScheduleActivityView>) {
    val code = if (floor == WHOLE_BUILDING) "" else floorCode(floor)
    val delayed = acts.count { it.tracking.status == ActivityStatus.DELAYED }
    val done = acts.count { it.tracking.status == ActivityStatus.COMPLETED }
    // Opaque: stays pinned over the floor's activities while they scroll.
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        HorizontalDivider(color = SiteTheme.colors.hairline)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically) {
            Text(floor, style = MaterialTheme.typography.titleSmall)
            if (code.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(code, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SiteTheme.colors.muted)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${acts.size} activit${if (acts.size == 1) "y" else "ies"}, $done done",
                style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted
            )
            if (delayed > 0) {
                Text(", $delayed delayed", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.bad, fontWeight = FontWeight.SemiBold)
            }
        }
        HorizontalDivider(color = SiteTheme.colors.hairline)
    }
}

// ===================================================================== gantt

private enum class GanttScale(val label: String, val dayWidth: Float) { MONTHS("Months", 4f), WEEKS("Weeks", 7f), DAYS("Days", 14f) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleGantt(report: ScheduleReport, onOpen: (Long) -> Unit) {
    var scale by remember { mutableStateOf(GanttScale.WEEKS) }
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            val all = GanttScale.values()
            all.forEachIndexed { i, s ->
                SegmentedButton(
                    selected = scale == s, onClick = { scale = s },
                    shape = SegmentedButtonDefaults.itemShape(i, all.size, baseShape = MaterialTheme.shapes.small),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) { Text(s.label, maxLines = 1) }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            FlowKeys {
                KeyItem(KeyKind.PLANNED_OUTLINE, "Planned")
                KeyItem(KeyKind.ACTUAL, "Done")
                KeyItem(KeyKind.FORECAST, "Remaining")
                KeyItem(KeyKind.DELAYED, "Delayed")
                KeyItem(KeyKind.TODAY, "Today")
                KeyItem(KeyKind.CRITICAL, "Critical")
            }
        }
        HorizontalDivider(color = SiteTheme.colors.hairline)
        GanttChart(report = report, dayWidthDp = scale.dayWidth, onRowClick = onOpen, modifier = Modifier.fillMaxSize())
    }
}

// ===================================================================== site log

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ScheduleSiteLog(report: ScheduleReport, onDeleteLog: (ActivityProgressLog) -> Unit) {
    var pendingDelete by remember { mutableStateOf<ActivityProgressLog?>(null) }
    if (report.recentLogs.isEmpty()) {
        EmptyState("No site progress logged yet", "Use \"Log progress\" each day to record quantities done and labour on site. Each entry updates progress, stock consumption and earned value.")
        return
    }
    val byId = remember(report) { report.activities.associateBy { it.activity.id } }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Text("Latest ${report.recentLogs.size} entries", style = MaterialTheme.typography.labelMedium, color = SiteTheme.colors.muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        report.recentLogs.groupBy { localDayOf(it.first.date) }.forEach { (day, entries) ->
            stickyHeader(key = "d-$day") { LogDayHeading(day, entries.size) }
            entries.forEachIndexed { i, (log, name) ->
                item(key = log.id) {
                    val act = byId[log.activityId]?.activity
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        LogEntryRow(log, name, act?.floorLabel ?: "", act?.unit ?: "", onRemove = { pendingDelete = log })
                        if (i < entries.lastIndex) HorizontalDivider(color = SiteTheme.colors.hairline)
                    }
                }
            }
        }
    }
    pendingDelete?.let { log ->
        val view = byId[log.activityId]
        ConfirmRemoveProgressDialog(
            log = log,
            activityName = view?.activity?.activityName ?: "",
            unit = view?.activity?.unit ?: "",
            booksMaterial = view?.activity?.linkedNotationCode?.isNotBlank() == true,
            onConfirm = { pendingDelete = null; onDeleteLog(log) },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun LogDayHeading(day: LocalDate, count: Int) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        HorizontalDivider(color = SiteTheme.colors.hairline)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically) {
            Text(formatDate(day), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("$count entr${if (count == 1) "y" else "ies"}", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
        }
        HorizontalDivider(color = SiteTheme.colors.hairline)
    }
}

@Composable
private fun LogEntryRow(log: ActivityProgressLog, name: String, floorLabel: String, unit: String, onRemove: () -> Unit) {
    val code = floorCode(floorLabel)
    val what = listOfNotNull(
        log.quantityDone.takeIf { it > 0 }?.let { "${formatQuantity(round1(it), unit)} done" },
        log.percentComplete?.let { "${formatPercent(it)} complete" },
        log.labourCount.takeIf { it > 0 }?.let { "$it workers" }
    ).joinToString(", ")
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (code.isNotEmpty()) {
                    Text(code, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 6.dp).semantics { contentDescription = floorLabel })
                }
                Text(activityBaseName(name, floorLabel), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (what.isNotBlank()) Text(what, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            if (log.notes.isNotBlank()) Text(log.notes, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
        }
        TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove") }
    }
}
