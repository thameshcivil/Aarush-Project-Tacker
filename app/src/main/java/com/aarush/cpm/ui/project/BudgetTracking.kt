package com.aarush.cpm.ui.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aarush.cpm.data.entity.Project
import com.aarush.cpm.data.repository.BudgetComparison
import com.aarush.cpm.data.repository.CategoryBudgetVsActual
import com.aarush.cpm.data.repository.ProjectSummary
import com.aarush.cpm.ui.common.*
import com.aarush.cpm.ui.theme.FigureStyle
import com.aarush.cpm.ui.theme.SiteTheme

/*
 * Budget tracking, redesigned as ledger tables (redesign Phase 2).
 *
 * Display only: every figure here is read straight from ProjectSummary / BudgetComparison,
 * exactly as the old tiles and cards did. Nothing is recalculated except the bar fill
 * (actual / budget), which the old card also drew.
 *
 * Layout rules: one hairline sheet per section with its title outside the sheet (no boxes in
 * boxes), labels left, money right-aligned in tabular digits, hairline dividers between rows,
 * red only for over-budget / negative, gold only for the "actual" bar fill.
 */

private val FigureCell = FigureStyle.copy(fontSize = 15.sp, letterSpacing = 0.sp)

// ------------------------------------------------------------------ header

/** Project value, shown plainly at the top of the screen. Tap (or the button) edits area & rate. */
@Composable
internal fun ProjectValueHeader(project: Project, onEditAreaRate: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Edit area and rate", onClick = onEditAreaRate)
                .padding(vertical = 4.dp)
        ) {
            SectionLabel("Project value (Budget 1)")
            Spacer(Modifier.height(2.dp))
            FitText(formatCurrency(project.projectValue), style = FigureStyle.copy(fontSize = FigureStyle.fontSize * 1.3f), minFontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatQuantity(project.plinthAreaSqft, "sqft")} @ ${formatCurrency(project.ratePerSqft)}/sqft",
                style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted
            )
            val details = listOf(
                project.projectType,
                "${project.numberOfFloors} floor${if (project.numberOfFloors == 1) "" else "s"}"
            ).filter { it.isNotBlank() }.joinToString(", ")
            val who = listOf(project.clientName, project.location).filter { it.isNotBlank() }.joinToString(", ")
            Text(listOf(details, who).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
        }
        TextButton(onClick = onEditAreaRate, contentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 4.dp, bottom = 8.dp), modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Edit area & rate")
        }
    }
}

// ------------------------------------------------------------------ ledger building blocks

/** One section: title outside, rows inside a single hairline sheet. */
@Composable
internal fun LedgerSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        SectionTitle(title)
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), content = content)
        }
    }
}

/**
 * "Label ........ ₹ value" row. Label (and optional caption) on the left, the figure
 * right-aligned in tabular digits so a column of rows lines up digit for digit. The figure
 * shrinks to fit rather than being cut off. Read by TalkBack as one item.
 */
@Composable
internal fun LedgerRow(
    label: String,
    value: String,
    caption: String? = null,
    valueColor: Color = Color.Unspecified,
    emphasize: Boolean = false,
    divider: Boolean = true
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
            caption?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted) }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            FitText(
                value,
                style = if (emphasize) FigureCell.copy(fontSize = 17.sp) else FigureCell,
                color = valueColor,
                fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold,
                minFontSize = 11.sp
            )
        }
    }
    if (divider) HorizontalDivider(color = SiteTheme.colors.hairline, thickness = 1.dp)
}

/** Red for a negative figure, otherwise the normal ink. */
@Composable
private fun negativeRed(v: Double): Color = if (v < 0) SiteTheme.colors.bad else Color.Unspecified

// ------------------------------------------------------------------ money

/** Received / Spent / Cash balance / Client due / Value − spent, as one aligned table. */
@Composable
internal fun MoneySummaryTable(summary: ProjectSummary, modifier: Modifier = Modifier) {
    LedgerSection("Money", modifier) {
        LedgerRow("Received", formatCurrency(summary.totalReceived))
        LedgerRow("Spent", formatCurrency(summary.totalSpent))
        LedgerRow("Cash balance", formatCurrency(summary.cashBalance), caption = "Received − spent",
            valueColor = negativeRed(summary.cashBalance))
        LedgerRow("Client due", formatCurrency(summary.clientBalance), caption = "Value − received")
        // Same figure as before (project value − total spent); neutral unless negative.
        LedgerRow("Value − spent", formatCurrency(summary.profit), caption = "${formatPercent(summary.profitPercent)} of value",
            valueColor = negativeRed(summary.profit), emphasize = true, divider = false)
    }
}

/** Progress % and days left (previously two of the tiles), in the same table style. */
@Composable
internal fun TimeProgressTable(progressPercent: Double, daysRemaining: Long?, hasSchedule: Boolean, modifier: Modifier = Modifier) {
    LedgerSection("Time & progress", modifier) {
        LedgerRow("Progress", formatPercent(progressPercent))
        LedgerRow(
            "Days left",
            daysRemaining?.let { if (it < 0) "${-it} over" else "$it" } ?: NO_VALUE,
            caption = if (hasSchedule) "to forecast handover" else "to expected completion",
            valueColor = if ((daysRemaining ?: 0) < 0) SiteTheme.colors.bad else Color.Unspecified,
            divider = false
        )
    }
}

// ------------------------------------------------------------------ Budget 1 vs Budget 2

/** Budget 1 vs Budget 2 with Budget 2's build-up and the cost-control result, as one table. */
@Composable
internal fun BudgetComparisonTable(bc: BudgetComparison, modifier: Modifier = Modifier) {
    LedgerSection("Budget 1 vs Budget 2", modifier) {
        LedgerRow("Budget 1", formatCurrency(bc.budget1), caption = "Plinth area × rate", emphasize = true)
        LedgerRow("Budget 2", formatCurrency(bc.budget2), caption = "Measured (built up below)", emphasize = true)
        SubHeading("Budget 2 build-up")
        LedgerRow("Civil material", formatCurrency(bc.civilMaterialCost), caption = "BOQ × coefficients × rates")
        LedgerRow("Civil labour", formatCurrency(bc.civilLabourCost), caption = "Vendor contracts")
        LedgerRow("Other categories", formatCurrency(bc.otherCategoriesTotal), caption = "MEP, painting, joinery, tiling, other (% of Budget 1)")
        SubHeading("Against expense")
        LedgerRow("Expense so far", formatCurrency(bc.actualExpense))
        val over = bc.costControlVariance < 0
        LedgerRow(
            if (over) "Over Budget 2 by" else "Budget 2 remaining",
            formatCurrency(kotlin.math.abs(bc.costControlVariance)),
            valueColor = if (over) SiteTheme.colors.bad else Color.Unspecified
        )
        LedgerRow("Budget 1 − expense", formatCurrency(bc.expectedProfit), caption = "Expected profit",
            valueColor = negativeRed(bc.expectedProfit), emphasize = true, divider = false)
    }
}

@Composable
private fun SubHeading(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = SiteTheme.colors.muted,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp).semantics { heading() })
}

// ------------------------------------------------------------------ categories

/**
 * Budget vs actual by category: Category | Budget | Actual | Variance. Column amounts are
 * short (₹12.40 L); the exact over-budget amount is spelled out in red under the row, and
 * TalkBack reads each row with exact amounts.
 *
 * Narrow phones: when the sheet is under 290dp wide (about a 340dp screen) the category name
 * moves to its own line and Budget / Actual / Variance get the full width beneath it, so no
 * amount ever has to shrink below legibility or be cut.
 */
@Composable
internal fun CategoryBudgetTable(categories: List<CategoryBudgetVsActual>, modifier: Modifier = Modifier) {
    LedgerSection("Budget vs actual by category", modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 290.dp
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!stacked) ColumnLabel("Category", Modifier.weight(CategoryWeight))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { ColumnLabel("Budget") }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { ColumnLabel("Actual") }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { ColumnLabel("Variance") }
                }
                HorizontalDivider(color = SiteTheme.colors.hairline, thickness = 1.dp)
                categories.forEachIndexed { i, cat ->
                    CategoryRow(cat, stacked)
                    if (i < categories.lastIndex) HorizontalDivider(color = SiteTheme.colors.hairline, thickness = 1.dp)
                }
                if (categories.isEmpty()) {
                    Text("No cost distribution set for this project. Set it in Settings, Project.",
                        style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
    }
}

private const val CategoryWeight = 1.25f

@Composable
private fun CategoryRow(cat: CategoryBudgetVsActual, stacked: Boolean) {
    val over = cat.variance < 0
    val name = humanizeLabel(cat.allocation.category.name)
    val share = "${formatPercent(cat.allocation.percentOfProjectValue)} of value"
    val spoken = "$name, $share. Budget ${formatCurrency(cat.budget)}, actual ${formatCurrency(cat.actual)}, " +
        (if (over) "over by ${formatCurrency(-cat.variance)}" else "remaining ${formatCurrency(cat.variance)}")
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) { contentDescription = spoken }) {
        if (stacked) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                Text(share, style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
            }
            Spacer(Modifier.height(4.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            if (!stacked) {
                Column(Modifier.weight(CategoryWeight).padding(end = 6.dp)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text(share, style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
                }
            }
            MoneyCell(formatCurrencyShort(cat.budget), Modifier.weight(1f))
            MoneyCell(formatCurrencyShort(cat.actual), Modifier.weight(1f))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (over) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Over by", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.bad, fontWeight = FontWeight.SemiBold)
                        FitText(formatCurrencyShort(-cat.variance), style = FigureCell, color = SiteTheme.colors.bad,
                            fontWeight = FontWeight.SemiBold, minFontSize = 10.sp)
                    }
                } else {
                    FitText(formatCurrencyShort(cat.variance), style = FigureCell, fontWeight = FontWeight.SemiBold, minFontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Subtle bar: share of this category's budget already spent (gold), red once over.
        ProgressRail(
            progress = if (cat.budget > 0) (cat.actual / cat.budget).toFloat() else 0f,
            color = if (over) SiteTheme.colors.bad else SiteTheme.colors.accent,
            height = 3.dp,
            spokenDescription = if (cat.budget > 0) "${kotlin.math.round(cat.actual / cat.budget * 100).toInt()}% of budget used" else "No budget"
        )
        if (over) {
            Text("Over by ${formatCurrency(-cat.variance)}", style = MaterialTheme.typography.bodySmall,
                color = SiteTheme.colors.bad, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun MoneyCell(text: String, modifier: Modifier) {
    Box(modifier.padding(start = 4.dp), contentAlignment = Alignment.CenterEnd) {
        FitText(text, style = FigureCell, fontWeight = FontWeight.Medium, minFontSize = 10.sp)
    }
}
