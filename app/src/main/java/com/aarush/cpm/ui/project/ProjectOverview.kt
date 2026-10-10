package com.aarush.cpm.ui.project

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aarush.cpm.data.entity.Project
import com.aarush.cpm.data.entity.ProjectStatus
import com.aarush.cpm.data.repository.ProjectSummary
import com.aarush.cpm.data.repository.ScheduleReport
import com.aarush.cpm.ui.common.*
import com.aarush.cpm.ui.schedule.ChartKey
import com.aarush.cpm.ui.schedule.KeyKind
import com.aarush.cpm.ui.theme.FigureStyle
import com.aarush.cpm.ui.theme.SiteTheme

/*
 * Project Overview (redesign Phase 5) and the pieces the project list cards share with it.
 * Display only: every figure is read from ProjectSummary / ScheduleReport as before.
 * Budget tables are NOT repeated here; the Overview links to Expenses > Budget instead.
 */

/** Status tag colours for a project (moved from the dashboard so both screens match). */
@Composable
internal fun projectStatusColors(status: ProjectStatus) = when (status) {
    ProjectStatus.COMPLETED -> SiteTheme.colors.good to SiteTheme.colors.goodTint
    ProjectStatus.ON_HOLD -> SiteTheme.colors.bad to SiteTheme.colors.badTint
    ProjectStatus.IN_PROGRESS, ProjectStatus.STARTED -> SiteTheme.colors.accentText to SiteTheme.colors.accentTint
    ProjectStatus.PLANNING -> SiteTheme.colors.planned to SiteTheme.colors.plannedTint
}

/** Name and client prominently, status tag on the right, optional trailing slot (⋮ menu). */
@Composable
internal fun ProjectIdentity(project: Project, large: Boolean, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(top = if (trailing != null) 10.dp else 0.dp)) {
            Text(project.name, style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            val who = listOf(project.clientName, project.location).filter { it.isNotBlank() }.joinToString(", ")
            if (who.isNotBlank()) {
                Text(who, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        val (fg, bg) = projectStatusColors(project.status)
        StatusPill(project.status.name, fg, bg, Modifier.padding(top = if (trailing != null) 14.dp else 4.dp))
        trailing?.invoke()
    }
}

/**
 * Overall progress: gold bar = actual, dark-neutral tick = planned (when a schedule exists).
 * Lateness is stated in red text ("3 days late"); the bar itself stays gold because it shows
 * actual work done. Labels spell out both numbers so colour is never the only cue.
 */
@Composable
internal fun ProgressSummary(actualPercent: Double, plannedPercent: Double?, delayDays: Int?, modifier: Modifier = Modifier, barHeight: Dp = 8.dp) {
    Column(modifier.fillMaxWidth()) {
        ProgressRail(
            progress = (actualPercent / 100).toFloat(),
            plannedMarker = plannedPercent?.let { (it / 100).toFloat() },
            color = SiteTheme.colors.accent,
            height = barHeight
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChartKey(KeyKind.ACTUAL)
            Spacer(Modifier.width(4.dp))
            Text("${formatPercent(actualPercent)} done", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (plannedPercent != null) {
                Spacer(Modifier.width(12.dp))
                ChartKey(KeyKind.PLANNED_TICK)
                Spacer(Modifier.width(4.dp))
                Text("${formatPercent(plannedPercent)} planned", style = MaterialTheme.typography.labelMedium, color = SiteTheme.colors.muted)
            }
            Spacer(Modifier.weight(1f))
            when {
                delayDays == null -> {}
                delayDays > 0 -> Text("$delayDays days late", style = MaterialTheme.typography.labelMedium, color = SiteTheme.colors.bad, fontWeight = FontWeight.SemiBold)
                delayDays < 0 -> Text("${-delayDays} days early", style = MaterialTheme.typography.labelMedium, color = SiteTheme.colors.good)
                else -> Text("On baseline", style = MaterialTheme.typography.labelMedium, color = SiteTheme.colors.muted)
            }
        }
    }
}

internal data class Figure(val label: String, val value: String, val color: Color = Color.Unspecified)

/**
 * Key figures as a responsive grid without boxes: [wideColumns] columns when there is room,
 * two (or one) on narrow screens. Label above, value right-aligned in tabular digits; values
 * shrink to fit rather than being cut. Each cell reads as "label, value" to TalkBack.
 */
@Composable
internal fun FigureGrid(figures: List<Figure>, wideColumns: Int, modifier: Modifier = Modifier, valueSize: Float = 17f) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val minCell = if (wideColumns >= 4) 84.dp else 150.dp
        val cols = when {
            maxWidth >= minCell * wideColumns -> wideColumns
            maxWidth >= minCell * 2 -> 2
            else -> 1
        }
        Column {
            figures.chunked(cols).forEachIndexed { r, row ->
                if (r > 0) HorizontalDivider(color = SiteTheme.colors.hairline, modifier = Modifier.padding(vertical = 2.dp))
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    row.forEachIndexed { i, f ->
                        if (i > 0) VerticalDivider(color = SiteTheme.colors.hairline, modifier = Modifier.padding(horizontal = 8.dp))
                        Column(Modifier.weight(1f).padding(vertical = 6.dp).semantics(mergeDescendants = true) {}) {
                            Text(f.label, style = MaterialTheme.typography.labelMedium, color = SiteTheme.colors.muted, maxLines = 1)
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                FitText(f.value, style = FigureStyle.copy(fontSize = valueSize.sp, letterSpacing = 0.sp), color = f.color,
                                    fontWeight = FontWeight.SemiBold, minFontSize = 11.sp)
                            }
                        }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Overview tab sections

/** Identity, progress and key figures in one sheet at the top of the Overview. */
@Composable
internal fun OverviewHeader(summary: ProjectSummary, report: ScheduleReport?) {
    val p = summary.project
    val ev = report?.earnedValue?.takeIf { report.hasSchedule }
    Column(Modifier.fillMaxWidth()) {
        ProjectIdentity(p, large = true)
        Spacer(Modifier.height(16.dp))
        // Live schedule figures when a schedule exists (same numbers as Schedule > Key figures);
        // otherwise the project's last recorded progress, as before.
        ProgressSummary(
            actualPercent = ev?.actualPercent ?: summary.progressPercent,
            plannedPercent = ev?.plannedPercent,
            delayDays = report?.takeIf { it.hasSchedule && it.forecastFinish != null }?.projectDelayDays
        )
        Spacer(Modifier.height(16.dp))
        AppCard(Modifier.fillMaxWidth()) {
            FigureGrid(
                listOf(
                    Figure("Project value", formatCurrency(p.projectValue)),
                    Figure("Received", formatCurrency(summary.totalReceived)),
                    Figure("Spent", formatCurrency(summary.totalSpent)),
                    Figure("Client due", formatCurrency(summary.clientBalance))
                ),
                wideColumns = 2, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

/** Schedule at a glance; the full picture is on the Schedule tab. */
@Composable
internal fun OverviewSchedule(report: ScheduleReport?, daysRemaining: Long?, onOpenSchedule: () -> Unit) {
    val ev = report?.earnedValue
    LedgerSection("Schedule") {
        if (report == null || !report.hasSchedule || ev == null) {
            Text("No construction schedule yet", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text("Generate the residential schedule from your BOQ and budget to track progress, delays and earned value.",
                style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
            Spacer(Modifier.height(10.dp))
            Button(onClick = onOpenSchedule, shape = MaterialTheme.shapes.small, modifier = Modifier.padding(bottom = 12.dp)) { Text("Open Schedule") }
        } else {
            LedgerRow("Forecast handover", report.forecastFinish?.let { formatDate(it) } ?: NOT_AVAILABLE_TEXT,
                caption = daysRemaining?.let { if (it < 0) "${-it} days past" else "$it days left" },
                valueColor = if (report.forecastFinish == null) SiteTheme.colors.muted else Color.Unspecified)
            LedgerRow("SPI", ev.spi?.let { "%.2f".format(it) } ?: NOT_AVAILABLE_TEXT, caption = "Schedule: earned ÷ planned",
                valueColor = indexColor(ev.spi))
            LedgerRow("CPI", ev.cpi?.let { "%.2f".format(it) } ?: NOT_AVAILABLE_TEXT, caption = "Cost: earned ÷ cost of work",
                valueColor = indexColor(ev.cpi))
            LedgerRow("Activities", "${report.activities.size}",
                caption = "${report.completedCount} done, ${report.inProgressCount} running, ${report.delayedCount} delayed",
                divider = false)
            TextButton(onClick = onOpenSchedule, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open Schedule") }
        }
    }
}

/** Budget at a glance; links to the Budget tracking screen under Expenses. */
@Composable
internal fun OverviewBudgetLink(summary: ProjectSummary, onOpenBudget: () -> Unit) {
    val over = summary.categories.filter { it.variance < 0 }
    LedgerSection("Budget") {
        LedgerRow("Value − spent", formatCurrency(summary.profit), caption = "${formatPercent(summary.profitPercent)} of value",
            valueColor = if (summary.profit < 0) SiteTheme.colors.bad else Color.Unspecified)
        LedgerRow(
            "Categories over budget",
            if (over.isEmpty()) "None" else "${over.size} of ${summary.categories.size}",
            caption = over.takeIf { it.isNotEmpty() }?.joinToString { humanizeLabel(it.allocation.category.name) },
            valueColor = if (over.isNotEmpty()) SiteTheme.colors.bad else Color.Unspecified,
            divider = false
        )
        TextButton(onClick = onOpenBudget, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open Budget tracking") }
    }
}

internal const val NOT_AVAILABLE_TEXT = "Not available"

/** Same SPI / CPI colouring as Schedule > Key figures. */
@Composable
private fun indexColor(v: Double?) = when {
    v == null -> SiteTheme.colors.muted
    v < 0.9 -> SiteTheme.colors.bad
    v < 1.0 -> SiteTheme.colors.warn
    else -> SiteTheme.colors.good
}
