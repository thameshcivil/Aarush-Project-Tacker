package com.aarush.cpm.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aarush.cpm.ui.theme.FigureStyle
import com.aarush.cpm.ui.theme.LabelMono
import com.aarush.cpm.ui.theme.RailShape
import com.aarush.cpm.ui.theme.SiteTheme
import com.aarush.cpm.ui.theme.TagShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt

/*
 * Shared "site ledger" building blocks. Screens use these instead of raw Material cards so
 * the whole app reads as one system: white sheets on paper, 1dp hairlines, no shadows.
 */

/** Hairline-bordered white sheet. Drop-in replacement for Card/ElevatedCard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    colors: CardColors? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = colors ?: CardDefaults.outlinedCardColors(containerColor = SiteTheme.colors.sheet)
    val border = BorderStroke(1.dp, SiteTheme.colors.hairline)
    if (onClick != null) OutlinedCard(onClick = onClick, modifier = modifier, colors = c, border = border, content = content)
    else OutlinedCard(modifier = modifier, colors = c, border = border, content = content)
}

/**
 * Small muted label, shown exactly as written (sentence case): section captions and stat
 * labels. Previously an uppercase monospaced "eyebrow" on every card; the redesign keeps mono
 * caps only for table column headings, see [ColumnLabel]. Pass enum names through
 * [humanizeLabel] first.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = SiteTheme.colors.muted) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier)
}

/** Monospaced capitals for table column headings only (BOQ Nos / L / B / D / Qty, history
 *  columns), where a fixed "spec sheet" voice helps the eye scan down the column. */
@Composable
fun ColumnLabel(text: String, modifier: Modifier = Modifier, color: Color = SiteTheme.colors.muted) {
    Text(text.uppercase(), style = LabelMono, color = color, modifier = modifier, maxLines = 1)
}

/** Heading for a group of content on a screen (sentence case, normal ink), with an optional
 *  trailing action such as a text button. Replaces a caption-plus-box for each section. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp).semantics { heading() }, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

private val TradeAcronyms = setOf("MEP", "RCC", "PCC", "BOQ", "SPI", "CPI", "EV", "GST", "OHT", "CP", "UPVC", "HVAC", "DPC", "AAC")

/** Enum-style text to sentence case: "CIVIL_STRUCTURAL" -> "Civil structural",
 *  "IN_PROGRESS · crit" -> "In progress · crit". Trade acronyms (MEP, RCC, BOQ...) are kept.
 *  Only for app-generated labels (statuses, enum names), never for names the user typed. */
fun humanizeLabel(text: String): String {
    val words = text.replace('_', ' ').split(' ')
    val needs = words.any { w -> val c = w.filter { it.isLetter() }; c.length >= 2 && c.all { it.isUpperCase() } && c !in TradeAcronyms }
    if (!needs) return words.joinToString(" ")
    val joined = words.joinToString(" ") { w ->
        val c = w.filter { it.isLetter() }
        if (c.isNotEmpty() && c.all { it.isUpperCase() } && c !in TradeAcronyms) w.lowercase() else w
    }
    val i = joined.indexOfFirst { it.isLetter() }
    return if (i < 0) joined else joined.substring(0, i) + joined[i].uppercaseChar() + joined.substring(i + 1)
}

/** Label + big tabular figure. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
    caption: String? = null,
    onClick: (() -> Unit)? = null
) {
    AppCard(modifier = modifier, onClick = onClick) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            SectionLabel(label)
            Spacer(Modifier.height(6.dp))
            FitText(value, style = FigureStyle, color = valueColor)
            caption?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted, maxLines = 2)
            }
        }
    }
}

/**
 * Single-line text that shrinks its font until it fits the available width, so figures such
 * as currency amounts are never cut off with "…". If even [minFontSize] does not fit (very
 * narrow screens, huge amounts), it wraps instead of truncating. The text is drawn only once
 * its final size is known, so there is no visible flicker while it measures.
 */
@Composable
fun FitText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    minFontSize: androidx.compose.ui.unit.TextUnit = 11.sp
) {
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    var scaled by remember(text, style, screenWidth) { mutableStateOf(style) }
    var wrap by remember(text, style, screenWidth) { mutableStateOf(false) }
    var ready by remember(text, style, screenWidth) { mutableStateOf(false) }
    Text(
        text,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        style = scaled,
        color = color,
        fontWeight = fontWeight,
        maxLines = if (wrap) 3 else 1,
        softWrap = wrap,
        onTextLayout = { result ->
            if (!wrap && result.didOverflowWidth && scaled.fontSize.isSp) {
                val next = scaled.fontSize * 0.92f
                if (next.value >= minFontSize.value) scaled = scaled.copy(fontSize = next)
                else { wrap = true; ready = true }
            } else ready = true
        }
    )
}

/** One "label ........ value" line of a ledger, with an optional hairline under it. */
@Composable
fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
    emphasize: Boolean = false,
    divider: Boolean = false
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted, modifier = Modifier.weight(1f))
            Text(
                value,
                style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold,
                color = valueColor
            )
        }
        if (divider) HorizontalDivider(color = SiteTheme.colors.hairline, thickness = 1.dp)
    }
}

/**
 * Thin progress rail (2dp corners). [progress] 0..1 fills in [color] (gold = actual, red =
 * delayed / over budget); an optional [plannedMarker] (0..1) draws a dark-neutral tick where the
 * baseline says you should be. The tick has a 1dp light outline so it stays visible on the
 * gold fill, the red fill and the empty track alike. Screen readers hear the percentages.
 */
@Composable
fun ProgressRail(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = SiteTheme.colors.accent,
    plannedMarker: Float? = null,
    height: Dp = 6.dp
) {
    val track = SiteTheme.colors.hairline
    val markerColor = SiteTheme.colors.planned
    val halo = SiteTheme.colors.sheet
    val spoken = "${(progress.coerceIn(0f, 1f) * 100).roundToInt()}% done" +
        (plannedMarker?.let { ", ${(it.coerceIn(0f, 1f) * 100).roundToInt()}% planned" } ?: "")
    BoxWithConstraints(
        modifier.fillMaxWidth().height(height + 6.dp).semantics { contentDescription = spoken },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth().height(height).clip(RailShape).background(track))
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(height).clip(RailShape).background(color))
        plannedMarker?.let { m ->
            val x = maxWidth * m.coerceIn(0f, 1f)
            Box(
                Modifier.offset(x = (x - 2.dp).coerceIn(0.dp, (maxWidth - 4.dp).coerceAtLeast(0.dp)))
                    .width(4.dp).fillMaxHeight().background(halo).padding(horizontal = 1.dp).background(markerColor)
            )
        }
    }
}

/** Compact status tag (4dp corners, sentence case: "In progress", "Delayed · crit"). Colour is
 *  never the only signal; the word itself carries the status. */
@Composable
fun StatusPill(text: String, color: Color, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(TagShape).background(tint).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(humanizeLabel(text), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

// ------------------------------------------------------------------ loading / error states

/** One grey placeholder bar. Static (no shimmer): the app keeps motion to a minimum. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, width: Dp? = null, height: Dp = 14.dp) {
    Box(
        (if (width != null) modifier.width(width) else modifier.fillMaxWidth())
            .height(height).clip(RailShape).background(SiteTheme.colors.hairline)
    )
}

/**
 * Placeholder shown on FIRST load only, shaped like the content that will replace it: an
 * optional headline figure, then [rows] label/value lines grouped into hairline sheets.
 * Announced once as "Loading" to screen readers. When data is being refreshed, screens keep
 * showing the previous data with [RefreshBar] instead.
 */
@Composable
fun SkeletonScreen(modifier: Modifier = Modifier, headline: Boolean = true, sections: Int = 3, rows: Int = 3) {
    Column(
        modifier.fillMaxSize().padding(16.dp).semantics { contentDescription = "Loading" },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (headline) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(width = 120.dp, height = 12.dp)
                SkeletonBlock(width = 200.dp, height = 28.dp)
                SkeletonBlock(width = 160.dp, height = 12.dp)
            }
        }
        repeat(sections) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkeletonBlock(width = 140.dp, height = 12.dp)
                    repeat(rows) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SkeletonBlock(Modifier.weight(1f).padding(end = 48.dp), height = 12.dp)
                            SkeletonBlock(width = 72.dp, height = 12.dp)
                        }
                    }
                }
            }
        }
    }
}

/** Thin bar at the top of a screen while existing data is being refreshed. The data under it
 *  stays visible and usable; nothing is blanked out. */
@Composable
fun RefreshBar(visible: Boolean, modifier: Modifier = Modifier) {
    if (visible) LinearProgressIndicator(modifier.fillMaxWidth().height(2.dp), color = SiteTheme.colors.accent, trackColor = Color.Transparent)
    else Spacer(modifier.height(2.dp))
}

/** Load / save failure with a plain explanation and a Retry button (48dp target). */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Couldn't load this"
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onRetry, shape = MaterialTheme.shapes.small, modifier = Modifier.heightIn(min = 48.dp)) { Text("Retry") }
        }
    }
}

/** Inline version of [ErrorState] for when older data is still on screen: a one-line notice
 *  with Retry, placed above the content instead of replacing it. */
@Composable
fun ErrorBanner(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(SiteTheme.colors.badTint)
            .padding(start = 12.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.bad, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Retry") }
    }
}

/** Centered empty state with an optional action. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            action?.let { Spacer(Modifier.height(14.dp)); it() }
        }
    }
}

/** Row of stat tiles that share the width — give each tile Modifier.weight(1f). */
@Composable
fun StatRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

// ------------------------------------------------------------------ brand

/** The full Aarush logo (emblem, wordmark, tagline). */
@Composable
fun AarushLogo(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.aarush.cpm.R.drawable.aarush_logo),
        contentDescription = "Aarush Construction & Consultant Works",
        modifier = modifier
    )
}

/** Just the emblem (the "A", towers, house and gold swoosh) — for headers. */
@Composable
fun AarushMark(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.aarush.cpm.R.drawable.aarush_logo_mark),
        contentDescription = null,
        modifier = modifier
    )
}

/** Short gold rule: the brand's signature mark under headings (from the logo's gold swoosh). */
@Composable
fun BrandRule(modifier: Modifier = Modifier, width: Dp = 32.dp) {
    Box(modifier.width(width).height(3.dp).clip(RoundedCornerShape(2.dp)).background(SiteTheme.colors.gold))
}

/** Screen header: emblem + small "AARUSH" wordmark + page title + gold rule. */
@Composable
fun BrandHeader(title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AarushMark(Modifier.height(34.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("AARUSH", style = com.aarush.cpm.ui.theme.WordmarkStyle.copy(fontSize = 13.sp),
                color = SiteTheme.colors.maroon)
            Text(title, style = MaterialTheme.typography.headlineMedium)
            BrandRule(Modifier.padding(top = 4.dp))
        }
    }
}
