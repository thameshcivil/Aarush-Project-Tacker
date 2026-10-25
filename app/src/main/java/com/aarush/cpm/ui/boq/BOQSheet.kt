package com.aarush.cpm.ui.boq

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aarush.cpm.data.entity.BOQItem
import com.aarush.cpm.data.entity.MaterialCoefficient
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.ui.common.*
import com.aarush.cpm.ui.schedule.floorCode
import com.aarush.cpm.ui.theme.SiteTheme

/*
 * The BOQ measurement sheet (redesign Phase 3). Display only: grouping keys, quantities,
 * group totals and material roll-ups are the same as before; every action calls the same
 * callbacks the old list did (edit dialog, copy, delete with confirm + Undo).
 *
 *  - Group headings (notation, or description) stay pinned while their entries scroll.
 *  - Sub-group headings are divider headings with the group's total quantity and material
 *    roll-up.
 *  - Entries are a table: Floor | Nos | L | B | D | Qty, tabular digits, numbers right-aligned,
 *    hairlines between rows, no box per row. Tap a row to edit; the 48dp ⋮ menu has Edit,
 *    Copy measurements and (after a divider) Delete entry.
 *  - Narrow phones (< ~355dp): each entry becomes two lines (Floor + Qty + ⋮, then Nos/L/B/D)
 *    so Qty stays large and on screen; nothing scrolls sideways.
 */

internal enum class BOQSortMode(val label: String) { NOTATIONS("Notations"), DESCRIPTION("Description") }

internal fun mainKeyOf(item: BOQItem, mode: BOQSortMode): String =
    if (mode == BOQSortMode.NOTATIONS) item.itemCode else item.description.ifBlank { item.itemCode }

internal fun subKeyOf(item: BOQItem, mode: BOQSortMode): String =
    if (mode == BOQSortMode.NOTATIONS) item.description.ifBlank { item.itemCode } else item.itemCode

/** "1240 Cft" (or several units joined) — total measured quantity of a group. Unchanged. */
internal fun quantityTotals(items: List<BOQItem>): String =
    items.groupBy { it.unit }.entries.joinToString(" + ") { (unit, list) -> formatQuantity(list.sumOf { it.quantity }, unit) }

/** Rolls a set of BOQ entries up into their derived material quantities (cement bags, sand
 *  cft, steel kg...) via the coefficient engine. Same arithmetic as before; only the joining
 *  separator changed from "  •  " to ", " (copy rule). */
internal fun materialSummary(items: List<BOQItem>, coefficients: List<MaterialCoefficient>): String {
    val totals = mutableMapOf<String, Double>()
    items.forEach { boq ->
        coefficients.filter { it.workItemKey == boq.materialType && it.coefficientPerUnit > 0 }.forEach { coeff ->
            val base = CalculationEngine.requiredMaterialQuantity(boq.quantity, coeff.coefficientPerUnit)
            val withWaste = CalculationEngine.totalRequiredQuantity(base, coeff.wastePercentOverride ?: boq.wastePercent)
            totals[coeff.materialName] = (totals[coeff.materialName] ?: 0.0) + withWaste
        }
    }
    if (totals.isEmpty()) return "No material coefficients for this item"
    return totals.entries.sortedBy { it.key }.joinToString(", ") { (name, qty) -> "$name ${formatQuantity(qty, "")}" }
}

/** A stored dimension of 0 means "left blank" (the engine treats it as 1), so it is shown as
 *  "-" rather than a misleading 0. Values are formatted exactly as before. */
internal fun dimText(v: Double): String = if (v <= 0.0) NO_VALUE else formatQuantity(v, "")

/** No. of Member, with No. per Member when it isn't 1: "16" or "16×4". */
internal fun nosText(item: BOQItem): String {
    val m = dimText(item.numberOfMembers)
    return if (item.perMember > 0.0 && item.perMember != 1.0) "$m×${formatQuantity(item.perMember, "")}" else m
}

/** Entries in building order (whole-building first, then Foundation, Plinth, GF, 1F...);
 *  stable, so entries on the same floor keep their stored order. Display order only. */
internal fun inFloorOrder(items: List<BOQItem>, floorOrder: List<String>): List<BOQItem> =
    items.sortedBy { if (it.floor.isBlank()) -1 else floorOrder.indexOf(it.floor).let { i -> if (i < 0) floorOrder.size else i } }

private fun floorShort(floor: String): String = floorCode(floor).ifBlank { "All" }
private fun floorSpoken(floor: String): String = floor.ifBlank { "Whole building" }

// ------------------------------------------------------------------ the sheet

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BOQSheet(
    items: List<BOQItem>,
    coefficients: List<MaterialCoefficient>,
    floorOrder: List<String>,
    sortMode: BOQSortMode,
    onSortMode: (BOQSortMode) -> Unit,
    expandedMain: Set<String>,
    onToggleMain: (String) -> Unit,
    expandedSub: Set<String>,
    onToggleSub: (String) -> Unit,
    copiedItem: BOQItem?,
    onCopy: (BOQItem) -> Unit,
    onPaste: () -> Unit,
    onClearCopy: () -> Unit,
    onEdit: (BOQItem) -> Unit,
    onDelete: (BOQItem) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            GroupByToggle(sortMode, onSortMode)
            if (items.isEmpty()) {
                EmptyState(
                    "No BOQ measurements yet",
                    "Tap + to measure work by notation (excavation, PCC, RCC, brickwork…). Tag each entry with its floor so the schedule gets exact quantities per floor."
                )
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val stacked = maxWidth < 355.dp
                    val mainGrouped = items.groupBy { mainKeyOf(it, sortMode) }.toSortedMap()
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = if (copiedItem != null) 152.dp else 88.dp)
                    ) {
                        mainGrouped.forEach { (mainKey, mainItems) ->
                            val mainExpanded = mainKey in expandedMain
                            stickyHeader(key = "main-$mainKey") {
                                GroupHeading(
                                    title = mainKey,
                                    subtitle = "${mainItems.size} entr${if (mainItems.size == 1) "y" else "ies"}, ${quantityTotals(mainItems)}",
                                    materials = materialSummary(mainItems, coefficients),
                                    expanded = mainExpanded,
                                    onToggle = { onToggleMain(mainKey) },
                                    onCopy = { onCopy(mainItems.first()) }
                                )
                            }
                            if (mainExpanded) {
                                val subGrouped = mainItems.groupBy { subKeyOf(it, sortMode) }.toSortedMap()
                                subGrouped.forEach { (subKey, subItems) ->
                                    val subFullKey = "$mainKey|$subKey"
                                    val subExpanded = subFullKey in expandedSub
                                    item(key = "sub-$subFullKey") {
                                        SubGroupHeading(
                                            title = subKey,
                                            totals = "${subItems.size} entr${if (subItems.size == 1) "y" else "ies"}, ${quantityTotals(subItems)}",
                                            materials = materialSummary(subItems, coefficients),
                                            expanded = subExpanded,
                                            onToggle = { onToggleSub(subFullKey) },
                                            onCopy = { onCopy(subItems.first()) }
                                        )
                                    }
                                    if (subExpanded) {
                                        item(key = "cols-$subFullKey") { TableColumnsHeader(stacked) }
                                        val rows = inFloorOrder(subItems, floorOrder)
                                        rows.forEachIndexed { i, row ->
                                            item(key = row.id) {
                                                MeasurementRow(row, stacked, onEdit = { onEdit(row) }, onCopy = { onCopy(row) }, onDelete = { onDelete(row) })
                                                if (i < rows.lastIndex) HorizontalDivider(Modifier.padding(start = 16.dp), color = SiteTheme.colors.hairline)
                                            }
                                        }
                                        item(key = "end-$subFullKey") { Spacer(Modifier.height(8.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val canEdit = com.aarush.cpm.ui.common.LocalProjectAccess.current.editMeasurements
        copiedItem?.takeIf { canEdit }?.let { copied ->
            CopiedBar(copied, onPaste = onPaste, onClear = onClearCopy,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 84.dp))
        }
        if (canEdit) FloatingActionButton(
            onClick = onAdd,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Filled.Add, "Add BOQ item") }
    }
}

// ------------------------------------------------------------------ group by

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupByToggle(mode: BOQSortMode, onMode: (BOQSortMode) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Group by", style = MaterialTheme.typography.labelMedium, color = SiteTheme.colors.muted)
        Spacer(Modifier.width(12.dp))
        val modes = BOQSortMode.values()
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            modes.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { onMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size, baseShape = MaterialTheme.shapes.small),
                    // Selected = maroon tint (primary action colour); gold is reserved for "actual".
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) { Text(m.label, maxLines = 1) }
            }
        }
    }
}

// ------------------------------------------------------------------ headings

@Composable
private fun GroupHeading(title: String, subtitle: String, materials: String, expanded: Boolean, onToggle: () -> Unit, onCopy: () -> Unit) {
    // Opaque background: this heading stays pinned over its entries while they scroll.
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        HorizontalDivider(color = SiteTheme.colors.hairline)
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "Collapse" else "Expand", onClick = onToggle)
                .semantics { heading(); stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text(materials, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
            }
            if (expanded && com.aarush.cpm.ui.common.LocalProjectAccess.current.editMeasurements) {
                IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, "Copy measurements from $title", Modifier.size(20.dp)) }
            }
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null,
                modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun SubGroupHeading(title: String, totals: String, materials: String, expanded: Boolean, onToggle: () -> Unit, onCopy: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(start = 16.dp), color = SiteTheme.colors.hairline)
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "Collapse" else "Expand", onClick = onToggle)
                .semantics { heading(); stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Short neutral rule marks the sub-level without another box.
            Box(Modifier.width(3.dp).height(32.dp).background(SiteTheme.colors.planned))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(totals, style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
                Text(materials, style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
            }
            if (expanded && com.aarush.cpm.ui.common.LocalProjectAccess.current.editMeasurements) {
                IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, "Copy measurements from $title", Modifier.size(20.dp)) }
            }
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null,
                modifier = Modifier.padding(12.dp).size(20.dp))
        }
    }
}

// ------------------------------------------------------------------ table

private val FloorW: Dp = 44.dp
private val MenuW: Dp = 48.dp
private const val QtyWeight = 1.6f

@Composable
private fun TableColumnsHeader(stacked: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(SiteTheme.colors.sheet).padding(start = 16.dp, top = 6.dp, bottom = 6.dp)
            .clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically
    ) {
        ColumnLabel("Floor", Modifier.width(FloorW))
        if (stacked) {
            Spacer(Modifier.weight(1f))
            ColumnLabel("Qty", Modifier.padding(end = 4.dp))
        } else {
            NumHeader("Nos", 1.1f); NumHeader("L", 1f); NumHeader("B", 1f); NumHeader("D", 1f)
            NumHeader("Qty", QtyWeight)
        }
        Spacer(Modifier.width(MenuW))
    }
}

@Composable
private fun RowScope.NumHeader(text: String, weight: Float) {
    Box(Modifier.weight(weight), contentAlignment = Alignment.CenterEnd) { ColumnLabel(text) }
}

@Composable
private fun MeasurementRow(item: BOQItem, stacked: Boolean, onEdit: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    val qty = formatQuantity(item.quantity, "")
    val spoken = "${floorSpoken(item.floor)}. Nos ${nosText(item)}, L ${dimText(item.length)}, B ${dimText(item.breadth)}, " +
        "D ${dimText(item.depth)}. Quantity ${formatQuantity(item.quantity, item.unit)}"
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(onClickLabel = "Edit entry", onClick = onEdit)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = spoken }) {
            if (stacked) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FloorTag(item.floor, Modifier.width(FloorW))
                        Spacer(Modifier.weight(1f))
                        QtyText(qty, item.unit, large = true)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth().padding(start = FloorW)) {
                        DimPair("Nos", nosText(item)); DimPair("L", dimText(item.length))
                        DimPair("B", dimText(item.breadth)); DimPair("D", dimText(item.depth))
                    }
                }
            } else {
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FloorTag(item.floor, Modifier.width(FloorW))
                    NumCell(nosText(item), 1.1f); NumCell(dimText(item.length), 1f)
                    NumCell(dimText(item.breadth), 1f); NumCell(dimText(item.depth), 1f)
                    Box(Modifier.weight(QtyWeight), contentAlignment = Alignment.CenterEnd) { QtyText(qty, item.unit, large = false) }
                }
            }
        }
        // Read-only phones: same width kept so the columns still line up with the headings.
        if (com.aarush.cpm.ui.common.LocalProjectAccess.current.editMeasurements) RowMenu(item, onEdit, onCopy, onDelete)
        else Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun FloorTag(floor: String, modifier: Modifier) {
    Text(
        floorShort(floor), modifier = modifier,
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        color = if (floor.isBlank()) SiteTheme.colors.muted else MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun RowScope.NumCell(text: String, weight: Float) {
    Box(Modifier.weight(weight).padding(start = 4.dp), contentAlignment = Alignment.CenterEnd) {
        FitText(text, style = MaterialTheme.typography.bodyMedium, minFontSize = 12.sp)
    }
}

@Composable
private fun RowScope.DimPair(label: String, value: String) {
    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

/** Quantity in bold with its unit in a smaller muted size, never cut. */
@Composable
private fun QtyText(qty: String, unit: String, large: Boolean) {
    Row(verticalAlignment = Alignment.Bottom) {
        FitText(qty, style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold, minFontSize = 12.sp)
        if (unit.isNotBlank()) {
            Spacer(Modifier.width(3.dp))
            Text(unit, style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted, maxLines = 1,
                modifier = Modifier.padding(bottom = 1.dp))
        }
    }
}

@Composable
private fun RowMenu(item: BOQItem, onEdit: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More actions for this ${item.itemCode} entry")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { open = false; onEdit() })
            DropdownMenuItem(text = { Text("Copy measurements") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = { open = false; onCopy() })
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Delete entry", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete() }
            )
        }
    }
}

// ------------------------------------------------------------------ copied bar

/** Compact bar just above the + button while measurements are on the clipboard. Paste opens
 *  Add with them prefilled (same as before); × clears them. Announced politely. */
@Composable
private fun CopiedBar(copied: BOQItem, onPaste: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val dims = "M ${nosText(copied)}   L ${dimText(copied.length)}   B ${dimText(copied.breadth)}   D ${dimText(copied.depth)}"
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, SiteTheme.colors.hairline, MaterialTheme.shapes.small)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp), tint = SiteTheme.colors.muted)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text("Copied: ${copied.itemCode}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(dims, style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onPaste, modifier = Modifier.heightIn(min = 48.dp)) { Text("Paste") }
        IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Clear copied measurements", modifier = Modifier.size(20.dp)) }
    }
}
