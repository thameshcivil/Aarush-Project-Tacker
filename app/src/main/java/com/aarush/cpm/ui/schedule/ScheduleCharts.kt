package com.aarush.cpm.ui.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.aarush.cpm.ui.common.formatPercent
import com.aarush.cpm.ui.common.formatShortDate
import com.aarush.cpm.ui.theme.SiteTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Gantt chart drawn on one Canvas (fast for 100+ activities). Redesign Phase 4 colour rules:
 *  - Planned (baseline) dates: dark-neutral OUTLINE bar in the upper lane of each row, so it
 *    stays visible whatever the forecast bar below looks like. Critical activities get a
 *    heavier outline (and a heavy neutral mark in the name column).
 *  - Forecast bar in the lower lane: the done share solid GOLD, the remainder light gold;
 *    DELAYED activities show the remainder in red with a red edge (done share stays gold).
 *  - Today: a solid dark line across the chart, labelled in the header.
 *  - Milestones: diamonds (gold when reached, red when delayed, otherwise outlined).
 * The name column (floor code + name, then dates and %) and the month header stay pinned while
 * scrolling. On narrow phones the name column takes ~46% of the width instead of a fixed 172dp.
 * Tap a row to open the activity. Display only: all dates and percentages come from tracking.
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
    val delayedCount = acts.count { it.tracking.status == ActivityStatus.DELAYED }

    BoxWithConstraints(modifier.semantics {
        contentDescription = "Gantt chart, ${acts.size} activities, $delayedCount delayed. Tap a row to open the activity."
    }) {
        val labelW = minOf(180.dp, maxWidth * 0.46f)
        val rowH = 44.dp
        val headerH = 36.dp
        val dayW = dayWidthDp.dp
        val totalW = labelW + dayW * days
        val totalH = headerH + rowH * acts.size

        val labelWpx = with(density) { labelW.toPx() }
        val nameStyle = TextStyle(fontSize = 12.sp, color = ink)
        val metaStyle = TextStyle(fontSize = 10.5.sp, color = colors.muted)
        val lateStyle = SpanStyle(color = colors.bad, fontWeight = FontWeight.SemiBold)
        val smallStyle = TextStyle(fontSize = 10.sp, color = colors.muted)
        val todayStyle = TextStyle(fontSize = 10.sp, color = sheet, fontWeight = FontWeight.Bold)
        // Floor code in bold ink (maroon is reserved for brand and primary actions).
        val floorStyle = SpanStyle(color = ink, fontWeight = FontWeight.Bold)
        val textMax = (labelWpx - with(density) { 20.dp.toPx() }).toInt().coerceAtLeast(1)
        val names = remember(acts, ink, labelWpx) {
            acts.map {
                // Floor code first so rows for different floors are told apart even when the
                // end of a long name is cut off; the repeated " — <floor>" suffix is dropped
                // (display only — stored names are unchanged).
                val code = floorCode(it.activity.floorLabel)
                val label = buildAnnotatedString {
                    if (code.isNotEmpty()) { withStyle(floorStyle) { append(code) }; append("  ") }
                    append(activityBaseName(it.activity.activityName, it.activity.floorLabel))
                }
                measurer.measure(label, nameStyle, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = textMax))
            }
        }
        val metas = remember(acts, labelWpx) {
            acts.map {
                val t = it.tracking
                val meta = buildAnnotatedString {
                    append("${formatShortDate(t.forecastStart)} → ${formatShortDate(t.forecastFinish)}, ${formatPercent(t.actualPercent)}")
                    if (t.delayDays > 0) withStyle(lateStyle) { append("  +${t.delayDays}d") }
                }
                measurer.measure(meta, metaStyle, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = textMax))
            }
        }
        val monthFmt = remember { DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault()) }
        val dayFmt = remember { DateTimeFormatter.ofPattern("d", Locale.getDefault()) }
        // Open scrolled so "today" is in view (a week of context to its left) instead of at
        // the schedule's first day, which on a phone is usually months away.
        val todayStartPx = with(density) {
            (ChronoUnit.DAYS.between(start, report.today) - 7).coerceAtLeast(0) * dayW.toPx()
        }.toInt()
        val h = remember(dayWidthDp) { ScrollState(todayStartPx) }
        val v = rememberScrollState()

        Box(Modifier.verticalScroll(v).horizontalScroll(h)) {
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
                val thin = 1.5.dp.toPx(); val heavy = 2.5.dp.toPx()

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
                    val delayed = t.status == ActivityStatus.DELAYED
                    val critical = t.isCritical && t.status != ActivityStatus.COMPLETED
                    drawLine(colors.hairline.copy(alpha = 0.6f), Offset(labelWpx, y + rh), Offset(size.width, y + rh), strokeWidth = 1f)

                    // Planned (baseline): neutral outline, upper lane.
                    val bx1 = xOf(t.baselineStart); val bx2 = xOf(t.baselineFinish) + dw
                    val pTop = y + rh * 0.16f; val pH = rh * 0.22f
                    drawRoundRect(colors.planned, Offset(bx1, pTop), Size((bx2 - bx1).coerceAtLeast(2f), pH), CornerRadius(2f),
                        style = Stroke(width = if (critical) heavy else thin))

                    // Forecast / actual: lower lane.
                    val fx1 = xOf(t.forecastStart); val fx2 = xOf(t.forecastFinish) + dw
                    val barTop = y + rh * 0.50f; val barH = rh * 0.30f
                    if (view.activity.isMilestone) {
                        val cx = fx2 - dw / 2; val cy = barTop + barH / 2; val r = barH * 0.8f
                        val path = Path().apply { moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close() }
                        when {
                            t.actualPercent >= 100 -> drawPath(path, colors.accent)
                            delayed -> drawPath(path, colors.bad)
                            else -> drawPath(path, colors.planned, style = Stroke(width = thin))
                        }
                    } else {
                        val w = (fx2 - fx1).coerceAtLeast(2f)
                        val rest = if (delayed) colors.bad.copy(alpha = 0.35f) else colors.accent.copy(alpha = 0.30f)
                        drawRoundRect(rest, Offset(fx1, barTop), Size(w, barH), CornerRadius(3f))
                        val doneW = w * (t.actualPercent / 100.0).toFloat().coerceIn(0f, 1f)
                        if (doneW > 0) drawRoundRect(colors.accent, Offset(fx1, barTop), Size(doneW, barH), CornerRadius(3f))
                        if (delayed) drawRoundRect(colors.bad, Offset(fx1, barTop), Size(w, barH), CornerRadius(3f), style = Stroke(width = thin))
                    }
                }

                // Today: solid dark line, labelled in the header below.
                val tx = xOf(report.today) + dw / 2
                drawLine(ink, Offset(tx, hh), Offset(tx, size.height), strokeWidth = 2.dp.toPx())

                // Pinned name column
                drawRect(sheet, topLeft = Offset(scrollX, hh), size = Size(labelWpx, size.height - hh))
                drawLine(colors.hairline, Offset(scrollX + labelWpx, hh), Offset(scrollX + labelWpx, size.height), strokeWidth = 1f)
                acts.forEachIndexed { i, view ->
                    val y = hh + i * rh
                    if (i > 0) drawLine(colors.hairline.copy(alpha = 0.6f), Offset(scrollX, y), Offset(scrollX + labelWpx, y), strokeWidth = 1f)
                    if (view.tracking.isCritical && view.tracking.status != ActivityStatus.COMPLETED) {
                        drawRect(colors.planned, topLeft = Offset(scrollX, y + rh * 0.2f), size = Size(3.dp.toPx(), rh * 0.6f))
                    }
                    val name = names[i]; val meta = metas[i]
                    val blockH = name.size.height + meta.size.height
                    val top = y + (rh - blockH) / 2
                    drawText(name, topLeft = Offset(scrollX + 10.dp.toPx(), top))
                    drawText(meta, topLeft = Offset(scrollX + 10.dp.toPx(), top + name.size.height))
                }

                // Pinned month header
                drawRect(sheet, topLeft = Offset(scrollX, scrollY), size = Size(size.width - scrollX, hh))
                drawLine(colors.hairline, Offset(scrollX, scrollY + hh), Offset(size.width, scrollY + hh), strokeWidth = 1f)
                var m = start.withDayOfMonth(1)
                while (!m.isAfter(end)) {
                    val x = xOf(maxOf(m, start))
                    if (x + 40 > scrollX + labelWpx) {
                        drawText(measurer, m.format(monthFmt), Offset(maxOf(x + 4, scrollX + labelWpx + 4), scrollY + 4.dp.toPx()), smallStyle)
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
                // "Today" tag on the header, where the line is.
                if (tx > scrollX + labelWpx) {
                    val tag = measurer.measure("Today", todayStyle)
                    val pad = 4.dp.toPx()
                    val tagW = tag.size.width + pad * 2; val tagH = tag.size.height + pad
                    val tagX = (tx - tagW / 2).coerceAtLeast(scrollX + labelWpx + 2)
                    val tagY = scrollY + hh - tagH - 2.dp.toPx()
                    drawRoundRect(ink, Offset(tagX, tagY), Size(tagW, tagH), CornerRadius(4.dp.toPx()))
                    drawText(tag, topLeft = Offset(tagX + pad, tagY + pad / 2))
                    drawLine(ink, Offset(tx, tagY + tagH), Offset(tx, scrollY + hh), strokeWidth = 2.dp.toPx())
                }
                drawText(measurer, "Activity", Offset(scrollX + 10.dp.toPx(), scrollY + 4.dp.toPx()), smallStyle)
            }
        }
    }
}

/** Cumulative planned (dark neutral) vs actual (gold) % complete, weekly; dashed line = today. */
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
