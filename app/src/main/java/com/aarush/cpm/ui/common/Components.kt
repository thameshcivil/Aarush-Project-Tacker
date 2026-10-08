package com.aarush.cpm.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aarush.cpm.ui.theme.FigureStyle
import com.aarush.cpm.ui.theme.LabelMono
import com.aarush.cpm.ui.theme.SiteTheme

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

/** Small uppercase monospaced label — section headers and stat captions. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = SiteTheme.colors.muted) {
    Text(text.uppercase(), style = LabelMono, color = color, modifier = modifier)
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
            Text(value, style = FigureStyle, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            caption?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted, maxLines = 2)
            }
        }
    }
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
 * Thin progress rail. [progress] 0..1 fills in [color]; an optional [plannedMarker] (0..1)
 * draws a blueprint tick where the baseline says you should be — planned vs actual in one line.
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
    BoxWithConstraints(modifier.fillMaxWidth().height(height + 4.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(track))
        Box(
            Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(height)
                .clip(RoundedCornerShape(height / 2)).background(color)
        )
        plannedMarker?.let { m ->
            val x = maxWidth * m.coerceIn(0f, 1f)
            Box(Modifier.offset(x = (x - 1.dp).coerceAtLeast(0.dp)).width(2.dp).fillMaxHeight().background(markerColor))
        }
    }
}

/** Compact status chip. */
@Composable
fun StatusPill(text: String, color: Color, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(tint).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text.uppercase(), style = LabelMono, color = color, maxLines = 1)
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

/** Short gold rule — the brand's signature mark under headings (from the logo's gold swoosh). */
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
