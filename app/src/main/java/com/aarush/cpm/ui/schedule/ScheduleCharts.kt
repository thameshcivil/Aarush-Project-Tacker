package com.aarush.cpm.ui.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aarush.cpm.data.entity.ActivityStatus
import com.aarush.cpm.data.repository.ScheduleReport
import com.aarush.cpm.domain.schedule.SCurvePoint
import com.aarush.cpm.ui.theme.SiteTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Gantt chart drawn on one Canvas (fast for 100+ activities). Each row shows the baseline as
 * a thin blueprint line and the forecast as a bar — the done share solid orange, the rest
 * tinted; delayed activities in red; milestones as diamonds; a vertical line marks today.
 * The activity-name column and the month header stay pinned while scrolling.
 * Tap a row to open the activity.
 */
@Composable
fun GanttChart(report: ScheduleReport, dayWidthDp: Float, onRowClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    val acts = report.activities
    if (acts.isEmpty()) return
    val colors = SiteTheme.colors
    val ink = MaterialTheme.colorScheme.onSurface
    val sheet = colors.sheet
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    val start = (listOfNotNull(report.baselineStart) + acts.map { it.tracking.forecastStart }).min().minusDays(3)
    val end = (listOfNotNull(report.baselineFinish, report.forecastFinish) + acts.map { it.tracking.forecastFinish }).max().plusDays(10)
    val days = ChronoUnit.DAYS.between(start, end).toInt() + 1

    // Wider than before so a floor code plus the activity name fit (e.g. "1F  Beams & roof slab").
    val labelW = 172.dp
    val rowH = 30.dp
    val headerH = 36.dp
    val dayW = dayWidthDp.dp
    val totalW = labelW + dayW * days
    val totalH = headerH + rowH * acts.size

    val labelWpx = with(density) { labelW.toPx() }
    val nameStyle = TextStyle(fontSize = 11.sp, color = ink)
    val smallStyle = TextStyle(fontSize = 10.sp, color = colors.muted)
    // Floor code in bold ink (maroon is reserved for brand and primary actions).
    val floorStyle = SpanStyle(color = ink, fontWeight = FontWeight.Bold)
    val names = remember(acts, ink, floorStyle) {
        acts.map {
            // Floor code first (bold, brand maroon) so rows for different floors are told apart
            // even when the end of a long name is cut off; then the activity name without the
            // repeated " — <floor>" suffix. Display only — stored names are unchanged.
            val code = floorCode(it.activity.floorLabel)
            val label = buildAnnotatedString {
                if (code.isNotEmpty()) { withStyle(floorStyle) { append(code) }; append("  ") }
                append(activityBaseName(it.activity.activityName, it.activity.floorLabel))
            }
            measurer.measure(
                label, nameStyle, overflow = TextOverflow.Ellipsis, maxLines = 1,
                constraints = Constraints(maxWidth = (labelWpx - with(density) { 18.dp.toPx() }).toInt().coerceAtLeast(1))
            )
        }
    }
    val monthFmt = remember { DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault()) }
    val dayFmt = remember { DateTimeFormatter.ofPattern("d", Locale.getDefault()) }
    val h = rememberScrollState()
    val v = rememberScrollState()

    Box(modifier.verticalScroll(v).horizontalScroll(h)) {
        Canvas(
            Modifier.size(totalW, totalH).pointerInput(acts) {
                detectTapGestures { pos ->
                    val row = ((pos.y - headerH.toPx()) / rowH.toPx()).toInt()
                    if (pos.y > headerH.toPx() && row in acts.indices) onRowClick(acts[row].activity.id)
                }
            }
        ) {
            val dw = dayW.toPx(); val rh = rowH.toPx(); val hh = headerH.toPx()
            fun xOf(d: LocalDate) = labelWpx + ChronoUnit.DAYS.between(start, d) * dw
            val scrollX = h.value.toFloat()
            val scrollY = v.value.toFloat()

            // Week grid (Mondays) and Sunday shading.
            var d = start
            while (!d.isAfter(end)) {
                if (d.dayOfWeek == DayOfWeek.SUNDAY && report.sundayOff) {
                    drawRect(colors.hairline.copy(alpha = 0.35f), topLeft = Offset(xOf(d), hh), size = Size(dw, size.height - hh))
                }
                if (d.dayOfWeek == DayOfWeek.MONDAY) {
                    drawLine(colors.hairline, Offset(xOf(d), hh), Offset(xOf(d), size.height), strokeWidth = 1f)
                }
                d = d.plusDays(1)
            }

            // Rows
            acts.forEachIndexed { i, view ->
                val y = hh + i * rh
                val t = view.tracking
                drawLine(colors.hairline.copy(alpha = 0.6f), Offset(labelWpx, y + rh), Offset(size.width, y + rh), strokeWidth = 1f)
                // baseline
                val bx1 = xOf(t.baselineStart); val bx2 = xOf(t.baselineFinish) + dw
                drawRoundRect(colors.planned, Offset(bx1, y + rh * 0.18f), Size((bx2 - bx1).coerceAtLeast(2f), rh * 0.12f), CornerRadius(2f))
                // forecast
                val fx1 = xOf(t.forecastStart); val fx2 = xOf(t.forecastFinish) + dw
                val barTop = y + rh * 0.40f; val barH = rh * 0.38f
                val base = if (t.status == ActivityStatus.DELAYED) colors.bad else colors.accent
                if (view.activity.isMilestone) {
                    val cx = fx2 - dw / 2; val cy = barTop + barH / 2; val r = barH * 0.75f
                    val path = Path().apply { moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close() }
                    drawPath(path, if (t.actualPercent >= 100) colors.good else base)
                } else {
                    val w = (fx2 - fx1).coerceAtLeast(2f)
                    drawRoundRect(base.copy(alpha = 0.28f), Offset(fx1, barTop), Size(w, barH), CornerRadius(3f))
                    val doneW = w * (t.actualPercent / 100.0).toFloat()
                    if (doneW > 0) drawRoundRect(if (t.actualPercent >= 100) colors.good else base, Offset(fx1, barTop), Size(doneW, barH), CornerRadius(3f))
                    if (t.isCritical && t.status != ActivityStatus.COMPLETED) {
                        drawRoundRect(colors.bad, Offset(fx1, barTop), Size(w, barH), CornerRadius(3f), style = Stroke(width = 1.5f))
                    }
                }
            }

            // Today
            val tx = xOf(report.today) + dw / 2
            drawLine(colors.accent, Offset(tx, hh), Offset(tx, size.height), strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))

            // Pinned name column
            drawRect(sheet, topLeft = Offset(scrollX, hh), size = Size(labelWpx, size.height - hh))
            drawLine(colors.hairline, Offset(scrollX + labelWpx, hh), Offset(scrollX + labelWpx, size.height), strokeWidth = 1f)
            acts.forEachIndexed { i, view ->
                val y = hh + i * rh
                val layout = names[i]
                if (view.tracking.isCritical && view.tracking.status != ActivityStatus.COMPLETED) {
                    drawCircle(colors.bad, radius = 3.dp.toPx(), center = Offset(scrollX + 7.dp.toPx(), y + rh / 2))
                }
                drawText(layout, topLeft = Offset(scrollX + 14.dp.toPx(), y + (rh - layout.size.height) / 2))
            }

            // Pinned month header
            drawRect(sheet, topLeft = Offset(scrollX, scrollY), size = Size(size.width - scrollX, hh))
            drawLine(colors.hairline, Offset(scrollX, scrollY + hh), Offset(size.width, scrollY + hh), strokeWidth = 1f)
            var m = start.withDayOfMonth(1)
            while (!m.isAfter(end)) {
                val x = xOf(maxOf(m, start))
                if (x + 40 > scrollX + labelWpx) {
                    drawText(measurer, m.format(monthFmt).uppercase(), Offset(maxOf(x + 4, scrollX + labelWpx + 4), scrollY + 4.dp.toPx()), smallStyle)
                }
                drawLine(colors.hairline, Offset(x, scrollY), Offset(x, scrollY + hh), strokeWidth = 1f)
                m = m.plusMonths(1)
            }
            if (dw >= 10.dp.toPx() * 0.7f) {
                var wd = start
                while (!wd.isAfter(end)) {
                    if (wd.dayOfWeek == DayOfWeek.MONDAY && xOf(wd) > scrollX + labelWpx) {
                        drawText(measurer, wd.format(dayFmt), Offset(xOf(wd) + 2, scrollY + hh - 15.dp.toPx()), smallStyle)
                    }
                    wd = wd.plusDays(1)
                }
            }
            drawText(measurer, "ACTIVITY", Offset(scrollX + 14.dp.toPx(), scrollY + 4.dp.toPx()), smallStyle)
        }
    }
}

/** Cumulative planned (blueprint) vs actual (orange) % complete, weekly. */
@Composable
fun SCurveChart(points: List<SCurvePoint>, today: LocalDate, modifier: Modifier = Modifier) {
    if (points.size < 2) {
        Text("The S-curve appears once the schedule spans more than a week.", style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
        return
    }
    val colors = SiteTheme.colors
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 9.sp, color = colors.muted)
    val fmt = remember { DateTimeFormatter.ofPattern("MMM", Locale.getDefault()) }
    Canvas(modifier) {
        val left = 30.dp.toPx(); val bottom = size.height - 16.dp.toPx(); val top = 6.dp.toPx(); val right = size.width - 4.dp.toPx()
        val first = points.first().date; val last = points.last().date
        val span = ChronoUnit.DAYS.between(first, last).coerceAtLeast(1).toFloat()
        fun x(d: LocalDate) = left + (right - left) * (ChronoUnit.DAYS.between(first, d) / span)
        fun y(p: Double) = bottom - (bottom - top) * (p.coerceIn(0.0, 100.0) / 100.0).toFloat()

        listOf(0, 25, 50, 75, 100).forEach { g ->
            drawLine(colors.hairline, Offset(left, y(g.toDouble())), Offset(right, y(g.toDouble())), strokeWidth = 1f)
            drawText(measurer, "$g%", Offset(0f, y(g.toDouble()) - 6.dp.toPx()), axisStyle)
        }
        var lastMonth = -1
        points.forEach { p ->
            if (p.date.monthValue != lastMonth) {
                lastMonth = p.date.monthValue
                drawText(measurer, p.date.format(fmt), Offset(x(p.date), bottom + 3.dp.toPx()), axisStyle)
            }
        }
        val planned = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.date), y(p.plannedPercent)) else lineTo(x(p.date), y(p.plannedPercent)) }
        }
        drawPath(planned, colors.planned, style = Stroke(width = 2.dp.toPx()))
        val actualPts = points.filter { it.actualPercent != null }
        if (actualPts.isNotEmpty()) {
            val actual = Path().apply {
                actualPts.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.date), y(p.actualPercent!!)) else lineTo(x(p.date), y(p.actualPercent!!)) }
            }
            drawPath(actual, colors.accent, style = Stroke(width = 2.5f.dp.toPx()))
            val lp = actualPts.last()
            drawCircle(colors.accent, 4.dp.toPx(), Offset(x(lp.date), y(lp.actualPercent!!)))
        }
        if (!today.isBefore(first) && !today.isAfter(last)) {
            drawLine(colors.muted, Offset(x(today), top), Offset(x(today), bottom), strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
    }
}

// ------------------------------------------------------------------ floor labelling (display only)

/** Short code for a floor tag: Ground Floor → GF, First Floor → 1F, Foundation → FDN, Terrace → TER.
 *  Unknown custom tags fall back to their first letters; blank stays blank. */
internal fun floorCode(floorLabel: String): String {
    val f = floorLabel.trim()
    if (f.isEmpty()) return ""
    val ordinals = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth")
    val lower = f.lowercase()
    return when {
        lower == "ground floor" || lower == "ground" -> "GF"
        lower == "foundation" -> "FDN"
        lower == "plinth" -> "PLN"
        lower == "terrace" -> "TER"
        lower.endsWith(" floor") && lower.removeSuffix(" floor") in ordinals ->
            "${ordinals.indexOf(lower.removeSuffix(" floor")) + 1}F"
        lower.startsWith("floor ") && lower.removePrefix("floor ").toIntOrNull() != null ->
            "${lower.removePrefix("floor ").toInt()}F"
        else -> f.split(" ").filter { it.isNotBlank() }.joinToString("") { it.take(1) }.uppercase().take(4)
    }
}

/** Activity name without a trailing " — <floor>" (or " - <floor>") when the floor is shown separately. */
internal fun activityBaseName(name: String, floorLabel: String): String {
    val f = floorLabel.trim()
    if (f.isEmpty()) return name
    for (sep in listOf(" — ", " – ", " - ")) {
        if (name.endsWith(sep + f, ignoreCase = true)) return name.dropLast(sep.length + f.length).ifBlank { name }
    }
    return name
}
