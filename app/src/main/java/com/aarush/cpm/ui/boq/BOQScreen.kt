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
import androidx.compose.material.icons.filled.Delete
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
import com.aarush.cpm.data.repository.BOQRepository
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.ui.common.formatQuantity
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
    val remark: String
) {
    val computedQuantity: Double
        get() = CalculationEngine.boqDimensionalQuantity(members, perMember, length, breadth, depth)
}

class BOQViewModel(private val repository: BOQRepository) : ViewModel() {
    private val projectIdFlow = MutableStateFlow<Long?>(null)

    val items: StateFlow<List<BOQItem>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeForProject(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notations: StateFlow<List<BOQNotation>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeNotations(id) }
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
                    length = input.length, breadth = input.breadth, depth = input.depth, notes = input.remark
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
                    breadth = input.breadth, depth = input.depth, notes = input.remark
                )
            )
        }
    }

    fun deleteItem(item: BOQItem) { viewModelScope.launch { repository.deleteItem(item) } }
}

private enum class BOQSortMode(val label: String) { NOTATIONS("Notations"), DESCRIPTION("Description") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BOQTabContent(projectId: Long, viewModel: BOQViewModel) {
    LaunchedEffect(projectId) { viewModel.init(projectId) }
    val boqItems by viewModel.items.collectAsState()
    val notations by viewModel.notations.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<BOQItem?>(null) }
    var sortMode by remember { mutableStateOf(BOQSortMode.NOTATIONS) }
    var expandedMain by remember { mutableStateOf(setOf<String>()) }
    var expandedSub by remember { mutableStateOf(setOf<String>()) }

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

            if (boqItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No BOQ items yet. Tap + to add one.")
                }
            } else {
                val mainGrouped = boqItems.groupBy { mainKeyOf(it) }.toSortedMap()
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    mainGrouped.forEach { (mainKey, mainItems) ->
                        val mainExpanded = expandedMain.contains(mainKey)
                        item(key = "main-$mainKey") {
                            Card(
                                onClick = { expandedMain = if (mainExpanded) expandedMain - mainKey else expandedMain + mainKey },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(mainKey, fontWeight = FontWeight.Bold)
                                        Text("${mainItems.size} entr${if (mainItems.size == 1) "y" else "ies"}", style = MaterialTheme.typography.bodySmall)
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
                                    Card(
                                        onClick = { expandedSub = if (subExpanded) expandedSub - subFullKey else expandedSub + subFullKey },
                                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp)
                                    ) {
                                        Row(Modifier.padding(10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(subKey, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                                Text("${subItems.size} entr${if (subItems.size == 1) "y" else "ies"}", style = MaterialTheme.typography.labelSmall)
                                            }
                                            Icon(if (subExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (subExpanded) "Collapse" else "Expand", modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                                if (subExpanded) {
                                    items(subItems, key = { it.id }) { boqItem ->
                                        BOQItemCompactRow(item = boqItem, onEdit = { editingItem = boqItem }, onDelete = { viewModel.deleteItem(boqItem) })
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
            existing = null,
            onDismiss = { showAddDialog = false },
            onSubmit = { input -> viewModel.addItem(input); showAddDialog = false }
        )
    }
    editingItem?.let { item ->
        AddEditBOQItemDialog(
            notations = notations,
            existing = item,
            onDismiss = { editingItem = null },
            onSubmit = { input -> viewModel.updateItem(item, input); editingItem = null }
        )
    }
}

/** One BOQ entry's compact row — per request, only Members / Per Member / L / B / D / Qty,
 *  no item code, description, category, waste, or remark repeated here (they're already the
 *  group headers this row sits under). Tap the row to edit; the trailing icon deletes. */
@Composable
private fun BOQItemCompactRow(item: BOQItem, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth().padding(start = 28.dp)) {
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
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CompactStat(label: String, value: Double, unit: String = "") {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(formatQuantity(value, unit), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditBOQItemDialog(
    notations: List<BOQNotation>,
    existing: BOQItem?,
    onDismiss: () -> Unit,
    onSubmit: (BOQItemInput) -> Unit
) {
    var code by remember { mutableStateOf(existing?.itemCode ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: CostCategory.CIVIL_STRUCTURAL) }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var unit by remember { mutableStateOf(existing?.unit ?: BOQUnits.OPTIONS.first()) }
    var members by remember { mutableStateOf(existing?.numberOfMembers?.takeIf { it != 1.0 }?.toString() ?: "") }
    var perMember by remember { mutableStateOf(existing?.perMember?.takeIf { it != 1.0 }?.toString() ?: "") }
    var length by remember { mutableStateOf(existing?.length?.takeIf { it != 0.0 }?.toString() ?: "") }
    var breadth by remember { mutableStateOf(existing?.breadth?.takeIf { it != 0.0 }?.toString() ?: "") }
    var depth by remember { mutableStateOf(existing?.depth?.takeIf { it != 0.0 }?.toString() ?: "") }
    var waste by remember { mutableStateOf(existing?.wastePercent?.toString() ?: "3") }
    var materialType by remember { mutableStateOf(existing?.materialType ?: "") }
    var remark by remember { mutableStateOf(existing?.notes ?: "") }
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
                                        notationMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedTextField(code, { code = it }, label = { Text("Item code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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

                Spacer(Modifier.height(10.dp))
                Text("Quantity", style = MaterialTheme.typography.labelMedium)
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

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(waste, { waste = it }, label = { Text("Waste %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(remark, { remark = it }, label = { Text("Remark (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = waste.toDoubleOrNull() ?: 0.0
                if (code.isNotBlank() && computedQty > 0) {
                    onSubmit(
                        BOQItemInput(
                            itemCode = code, category = category, description = description, unit = unit,
                            members = members.toDoubleOrNull() ?: 0.0, perMember = perMember.toDoubleOrNull() ?: 0.0,
                            length = length.toDoubleOrNull() ?: 0.0, breadth = breadth.toDoubleOrNull() ?: 0.0,
                            depth = depth.toDoubleOrNull() ?: 0.0,
                            materialType = materialType.ifBlank { code }, wastePercent = w, remark = remark
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
