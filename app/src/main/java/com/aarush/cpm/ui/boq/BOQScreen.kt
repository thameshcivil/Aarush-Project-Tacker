package com.aarush.cpm.ui.boq

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.entity.BOQItem
import com.aarush.cpm.data.entity.BOQNotation
import com.aarush.cpm.data.entity.BOQUnits
import com.aarush.cpm.data.entity.CostCategory
import com.aarush.cpm.data.entity.MaterialCoefficient
import com.aarush.cpm.data.repository.BOQRepository
import com.aarush.cpm.data.repository.MaterialRepository
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.ui.common.ConfirmDeleteDialog
import com.aarush.cpm.ui.common.formatQuantity
import com.aarush.cpm.ui.common.showUndoDeleteSnackbar
import com.aarush.cpm.data.repository.ProjectRepository
import com.aarush.cpm.domain.calculation.CategoryRules
import com.aarush.cpm.domain.schedule.ResidentialScheduleTemplate
import com.aarush.cpm.ui.common.AppCard
import com.aarush.cpm.ui.common.EmptyState
import com.aarush.cpm.ui.common.SectionLabel
import com.aarush.cpm.ui.theme.SiteTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the Add/Edit dialog needs to submit — kept as one bundle so the same dialog
 *  composable works for both add and edit. No cost fields: BOQ is purely a measurement/
 *  quantity tool now — costing comes from material coefficients × rate card, and vendor
 *  contracts, not from a BOQ rate. */
data class BOQItemInput(
    val itemCode: String,
    val category: CostCategory,
    val description: String,
    val unit: String,
    val members: Double,
    val perMember: Double,
    val length: Double,
    val breadth: Double,
    val depth: Double,
    val materialType: String,
    val wastePercent: Double,
    val remark: String,
    val floor: String = ""
) {
    val computedQuantity: Double
        get() = CalculationEngine.boqDimensionalQuantity(members, perMember, length, breadth, depth)
}

@OptIn(ExperimentalCoroutinesApi::class)
class BOQViewModel(
    private val repository: BOQRepository,
    private val materialRepository: MaterialRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {
    private val projectIdFlow = MutableStateFlow<Long?>(null)

    /** Floor/location tags offered on the form — built from the project's floor count. */
    val floorOptions: StateFlow<List<String>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(null) else projectRepository.observeById(id) }
        .map { p -> ResidentialScheduleTemplate.floorOptions(p?.numberOfFloors ?: 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ResidentialScheduleTemplate.floorOptions(1))

    val items: StateFlow<List<BOQItem>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeForProject(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notations: StateFlow<List<BOQNotation>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeNotations(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Coefficients drive the per-group material totals shown on group headers (cement bags,
     *  sand cft, etc.) instead of a bare entry count. */
    val coefficients: StateFlow<List<MaterialCoefficient>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else materialRepository.observeCoefficients(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun init(projectId: Long) {
        projectIdFlow.value = projectId
    }

    fun addItem(input: BOQItemInput) {
        val currentProjectId = projectIdFlow.value ?: return
        val qty = input.computedQuantity
        viewModelScope.launch {
            repository.addItem(
                BOQItem(
                    projectId = currentProjectId, itemCode = input.itemCode, category = input.category,
                    description = input.description, unit = input.unit, quantity = qty,
                    materialType = input.materialType, wastePercent = input.wastePercent,
                    numberOfMembers = input.members, perMember = input.perMember,
                    length = input.length, breadth = input.breadth, depth = input.depth, notes = input.remark,
                    floor = input.floor
                )
            )
        }
    }

    fun updateItem(original: BOQItem, input: BOQItemInput) {
        val qty = input.computedQuantity
        viewModelScope.launch {
            repository.updateItem(
                original.copy(
                    itemCode = input.itemCode, category = input.category, description = input.description,
                    unit = input.unit, quantity = qty, materialType = input.materialType, wastePercent = input.wastePercent,
                    numberOfMembers = input.members, perMember = input.perMember, length = input.length,
                    breadth = input.breadth, depth = input.depth, notes = input.remark, floor = input.floor
                )
            )
        }
    }

    fun deleteItem(item: BOQItem) { viewModelScope.launch { repository.deleteItem(item) } }

    /** Re-inserts a previously deleted item for Undo. The original row id is dropped (0 =
     *  autogenerate) since that id is gone from the table — the restored row is identical in
     *  every other respect. */
    fun restoreItem(item: BOQItem) {
        viewModelScope.launch { repository.addItem(item.copy(id = 0)) }
    }
}

private enum class BOQSortMode(val label: String) { NOTATIONS("Notations"), DESCRIPTION("Description") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BOQTabContent(projectId: Long, viewModel: BOQViewModel, snackbarHostState: SnackbarHostState) {
    LaunchedEffect(projectId) { viewModel.init(projectId) }
    val boqItems by viewModel.items.collectAsState()
    val notations by viewModel.notations.collectAsState()
    val coefficients by viewModel.coefficients.collectAsState()
    val floorOptions by viewModel.floorOptions.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<BOQItem?>(null) }
    var pendingDelete by remember { mutableStateOf<BOQItem?>(null) }
    /** The "clipboard": measurements copied from one row, offered as the starting values for
     *  the next Add. Holds the whole item so the Add dialog can prefill nos/L/B/D (and unit)
     *  — you then change only what differs, e.g. the depth. */
    var copiedItem by remember { mutableStateOf<BOQItem?>(null) }
    var sortMode by remember { mutableStateOf(BOQSortMode.NOTATIONS) }
    var expandedMain by remember { mutableStateOf(setOf<String>()) }
    var expandedSub by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    fun mainKeyOf(item: BOQItem): String = if (sortMode == BOQSortMode.NOTATIONS) item.itemCode else item.description.ifBlank { item.itemCode }
    fun subKeyOf(item: BOQItem): String = if (sortMode == BOQSortMode.NOTATIONS) item.description.ifBlank { item.itemCode } else item.itemCode

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sort by:", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BOQSortMode.values().forEach { mode ->
                        FilterChip(selected = sortMode == mode, onClick = { sortMode = mode }, label = { Text(mode.label) })
                    }
                }
            }

            copiedItem?.let { copied ->
                AppCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = SiteTheme.colors.accentTint)
                ) {
                    Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Copied measurements", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${copied.itemCode} — M ${formatQuantity(copied.numberOfMembers, "")} • L ${copied.length} • B ${copied.breadth} • D ${copied.depth}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        TextButton(onClick = { showAddDialog = true }) { Text("Paste") }
                        IconButton(onClick = { copiedItem = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear copied measurements", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            if (boqItems.isEmpty()) {
                EmptyState(
                    "No BOQ measurements yet",
                    "Tap + to measure work by notation (excavation, PCC, RCC, brickwork…). Tag each entry with its floor so the schedule gets exact quantities per floor."
                )
            } else {
                val mainGrouped = boqItems.groupBy { mainKeyOf(it) }.toSortedMap()
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    mainGrouped.forEach { (mainKey, mainItems) ->
                        val mainExpanded = expandedMain.contains(mainKey)
                        item(key = "main-$mainKey") {
                            AppCard(
                                onClick = { expandedMain = if (mainExpanded) expandedMain - mainKey else expandedMain + mainKey },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(mainKey, style = MaterialTheme.typography.titleSmall)
                                        SectionLabel("${mainItems.size} entr${if (mainItems.size == 1) "y" else "ies"} · ${quantityTotals(mainItems)}")
                                        Text(materialSummary(mainItems, coefficients), style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                                    }
                                    if (mainExpanded) {
                                        IconButton(onClick = { copiedItem = mainItems.first() }) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy measurements from this group", modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Icon(if (mainExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (mainExpanded) "Collapse" else "Expand")
                                }
                            }
                        }
                        if (mainExpanded) {
                            val subGrouped = mainItems.groupBy { subKeyOf(it) }.toSortedMap()
                            subGrouped.forEach { (subKey, subItems) ->
                                val subFullKey = "$mainKey|$subKey"
                                val subExpanded = expandedSub.contains(subFullKey)
                                item(key = "sub-$subFullKey") {
                                    AppCard(
                                        onClick = { expandedSub = if (subExpanded) expandedSub - subFullKey else expandedSub + subFullKey },
                                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp)
                                    ) {
                                        Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(subKey, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                                Text("${quantityTotals(subItems)} · ${materialSummary(subItems, coefficients)}", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
                                            }
                                            if (subExpanded) {
                                                IconButton(onClick = { copiedItem = subItems.first() }) {
                                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy measurements from this group", modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            Icon(if (subExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (subExpanded) "Collapse" else "Expand", modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                                if (subExpanded) {
                                    items(subItems, key = { it.id }) { boqItem ->
                                        BOQItemCompactRow(
                                            item = boqItem,
                                            onEdit = { editingItem = boqItem },
                                            onCopy = { copiedItem = boqItem },
                                            onDelete = { pendingDelete = boqItem }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Filled.Add, "Add BOQ item") }
    }

    if (showAddDialog) {
        AddEditBOQItemDialog(
            notations = notations,
            floorOptions = floorOptions,
            existing = null,
            template = copiedItem,
            onDismiss = { showAddDialog = false },
            onSubmit = { input -> viewModel.addItem(input); showAddDialog = false }
        )
    }
    editingItem?.let { item ->
        AddEditBOQItemDialog(
            notations = notations,
            floorOptions = floorOptions,
            existing = item,
            template = null,
            onDismiss = { editingItem = null },
            onSubmit = { input -> viewModel.updateItem(item, input); editingItem = null }
        )
    }
    pendingDelete?.let { item ->
        ConfirmDeleteDialog(
            itemLabel = "this ${item.itemCode} entry",
            onConfirm = {
                pendingDelete = null
                viewModel.deleteItem(item)
                scope.showUndoDeleteSnackbar(snackbarHostState, "BOQ entry deleted") { viewModel.restoreItem(item) }
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

/** "1,240 Cft" (or several units joined) — total measured quantity of a group. */
private fun quantityTotals(items: List<BOQItem>): String =
    items.groupBy { it.unit }.entries.joinToString(" + ") { (unit, list) -> formatQuantity(list.sumOf { it.quantity }, unit) }

/** Rolls a set of BOQ entries up into their derived material quantities (cement bags, sand
 *  cft, steel kg...) via the coefficient engine — what a group header shows instead of a
 *  bare "3 entries" count, since the quantities are the actually useful figure. */
private fun materialSummary(items: List<BOQItem>, coefficients: List<MaterialCoefficient>): String {
    val totals = mutableMapOf<String, Double>()
    items.forEach { boq ->
        coefficients.filter { it.workItemKey == boq.materialType && it.coefficientPerUnit > 0 }.forEach { coeff ->
            val base = CalculationEngine.requiredMaterialQuantity(boq.quantity, coeff.coefficientPerUnit)
            val withWaste = CalculationEngine.totalRequiredQuantity(base, coeff.wastePercentOverride ?: boq.wastePercent)
            totals[coeff.materialName] = (totals[coeff.materialName] ?: 0.0) + withWaste
        }
    }
    if (totals.isEmpty()) return "No material coefficients for this item"
    return totals.entries.sortedBy { it.key }.joinToString("  •  ") { (name, qty) ->
        "$name ${formatQuantity(qty, "")}"
    }
}

/** One BOQ entry's compact row — only Members / Per Member / L / B / D / Qty (item code,
 *  description etc. are the group headers above it). Tap the row to edit. Actions sit in a
 *  single 48dp ⋮ menu where Delete is separated from Edit/Copy by a divider — previously Copy
 *  and Delete were adjacent 32dp icons, easy to mis-tap. Delete still asks to confirm and
 *  offers Undo. */
@Composable
private fun BOQItemCompactRow(item: BOQItem, onEdit: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    AppCard(onClick = onEdit, modifier = Modifier.fillMaxWidth().padding(start = 28.dp)) {
        if (item.floor.isNotBlank()) {
            SectionLabel(item.floor, modifier = Modifier.padding(start = 10.dp, top = 8.dp), color = SiteTheme.colors.planned)
        }
        Row(Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CompactStat("M", item.numberOfMembers)
                CompactStat("PM", item.perMember)
                CompactStat("L", item.length)
                CompactStat("B", item.breadth)
                CompactStat("D", item.depth)
                CompactStat("Qty", item.quantity, item.unit)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More actions for this ${item.itemCode} entry")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy measurements") },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                        onClick = { menuOpen = false; onCopy() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete entry", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactStat(label: String, value: Double, unit: String = "") {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SectionLabel(label)
        Text(formatQuantity(value, unit), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditBOQItemDialog(
    notations: List<BOQNotation>,
    floorOptions: List<String>,
    existing: BOQItem?,
    template: BOQItem?,
    onDismiss: () -> Unit,
    onSubmit: (BOQItemInput) -> Unit
) {
    // `existing` = editing that row. `template` = pasting copied measurements into a NEW row:
    // the numbers prefill so you only change what differs (e.g. the depth), but the item code
    // and description are left blank since you're usually pasting into a *different* work item.
    val prefill = existing ?: template
    var code by remember { mutableStateOf(existing?.itemCode ?: "") }
    var category by remember { mutableStateOf(prefill?.category ?: CostCategory.CIVIL_STRUCTURAL) }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var unit by remember { mutableStateOf(prefill?.unit ?: BOQUnits.OPTIONS.first()) }
    var members by remember { mutableStateOf(prefill?.numberOfMembers?.takeIf { it != 1.0 }?.toString() ?: "") }
    var perMember by remember { mutableStateOf(prefill?.perMember?.takeIf { it != 1.0 }?.toString() ?: "") }
    var length by remember { mutableStateOf(prefill?.length?.takeIf { it != 0.0 }?.toString() ?: "") }
    var breadth by remember { mutableStateOf(prefill?.breadth?.takeIf { it != 0.0 }?.toString() ?: "") }
    var depth by remember { mutableStateOf(prefill?.depth?.takeIf { it != 0.0 }?.toString() ?: "") }
    var waste by remember { mutableStateOf(prefill?.wastePercent?.toString() ?: "3") }
    var materialType by remember { mutableStateOf(existing?.materialType ?: "") }
    var remark by remember { mutableStateOf(existing?.notes ?: "") }
    var floor by remember { mutableStateOf(prefill?.floor ?: "") }
    var floorMenuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var notationMenuExpanded by remember { mutableStateOf(false) }
    var unitMenuExpanded by remember { mutableStateOf(false) }

    val computedQty = CalculationEngine.boqDimensionalQuantity(
        members.toDoubleOrNull() ?: 0.0, perMember.toDoubleOrNull() ?: 0.0,
        length.toDoubleOrNull() ?: 0.0, breadth.toDoubleOrNull() ?: 0.0, depth.toDoubleOrNull() ?: 0.0
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add BOQ item" else "Edit BOQ item") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                if (notations.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { notationMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(code.ifBlank { "Item code (pick a notation)" })
                        }
                        DropdownMenu(expanded = notationMenuExpanded, onDismissRequest = { notationMenuExpanded = false }) {
                            notations.forEach { n ->
                                DropdownMenuItem(
                                    text = { Text(n.code) },
                                    onClick = {
                                        code = n.code
                                        materialType = n.code
                                        if (BOQUnits.OPTIONS.contains(n.defaultUnit)) unit = n.defaultUnit
                                        // Sensible default category for the notation (Tile → Tiling…),
                                        // only for new entries — never overrides an edit.
                                        if (existing == null) category = CategoryRules.categoryForNotation(n.code + " " + n.description)
                                        notationMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedTextField(code, { code = it; materialType = it }, label = { Text("Item code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(onClick = { categoryMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(category.name.replace("_", " "))
                    }
                    DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                        CostCategory.values().forEach { c ->
                            DropdownMenuItem(text = { Text(c.name.replace("_", " ")) }, onClick = { category = c; categoryMenuExpanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(onClick = { unitMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Unit: $unit") }
                    DropdownMenu(expanded = unitMenuExpanded, onDismissRequest = { unitMenuExpanded = false }) {
                        BOQUnits.OPTIONS.forEach { u ->
                            DropdownMenuItem(text = { Text(u) }, onClick = { unit = u; unitMenuExpanded = false })
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(onClick = { floorMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Floor / location: ${floor.ifBlank { "Whole building" }}")
                    }
                    DropdownMenu(expanded = floorMenuExpanded, onDismissRequest = { floorMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Whole building / untagged") }, onClick = { floor = ""; floorMenuExpanded = false })
                        (floorOptions + listOfNotNull(floor.takeIf { it.isNotBlank() && it !in floorOptions })).forEach { f ->
                            DropdownMenuItem(text = { Text(f) }, onClick = { floor = f; floorMenuExpanded = false })
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                SectionLabel("Quantity")
                Spacer(Modifier.height(4.dp))
                Row {
                    OutlinedTextField(members, { members = it }, label = { Text("No. of Member") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(perMember, { perMember = it }, label = { Text("No. Per Member") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    OutlinedTextField(length, { length = it }, label = { Text("Length") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(breadth, { breadth = it }, label = { Text("Breadth") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(depth, { depth = it }, label = { Text("Depth") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                Text("Leave any field blank to treat it as 1 — e.g. just \"No. of Member\" for a plain count.", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                Text("Calculated quantity: ${formatQuantity(computedQty, unit)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(waste, { waste = it }, label = { Text("Waste %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(remark, { remark = it }, label = { Text("Remark (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = waste.toDoubleOrNull() ?: 0.0
                val anyDimension = listOf(members, perMember, length, breadth, depth).any { (it.toDoubleOrNull() ?: 0.0) > 0 }
                error = when {
                    code.isBlank() -> "Pick or type an item code."
                    !anyDimension -> "Enter at least one measurement (members, length, breadth or depth)."
                    w < 0 || w > 100 -> "Waste % must be between 0 and 100."
                    else -> null
                }
                if (error == null && computedQty > 0) {
                    onSubmit(
                        BOQItemInput(
                            itemCode = code, category = category, description = description, unit = unit,
                            members = members.toDoubleOrNull() ?: 0.0, perMember = perMember.toDoubleOrNull() ?: 0.0,
                            length = length.toDoubleOrNull() ?: 0.0, breadth = breadth.toDoubleOrNull() ?: 0.0,
                            depth = depth.toDoubleOrNull() ?: 0.0,
                            materialType = materialType.ifBlank { code }, wastePercent = w, remark = remark, floor = floor
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
