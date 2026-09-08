package com.aarush.cpm.ui.boq

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import com.aarush.cpm.data.entity.BOQQuantityMode
import com.aarush.cpm.data.entity.CostCategory
import com.aarush.cpm.data.repository.BOQRepository
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.ui.common.formatCurrency
import com.aarush.cpm.ui.common.formatQuantity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    fun addItem(
        itemCode: String, category: CostCategory, description: String, unit: String,
        quantityMode: BOQQuantityMode, nosQuantity: Double, sets: Double, length: Double, breadth: Double, depth: Double,
        rate: Double, materialType: String, wastePercent: Double
    ) {
        val currentProjectId = projectIdFlow.value ?: return
        val finalQuantity = if (quantityMode == BOQQuantityMode.LBD)
            CalculationEngine.lbdQuantity(sets, length, breadth, depth)
        else nosQuantity

        viewModelScope.launch {
            repository.addItem(
                BOQItem(
                    projectId = currentProjectId, itemCode = itemCode, category = category, description = description,
                    unit = unit, quantity = finalQuantity, rate = rate, amount = CalculationEngine.boqAmount(finalQuantity, rate),
                    materialType = materialType, wastePercent = wastePercent, quantityMode = quantityMode,
                    sets = sets, length = length, breadth = breadth, depth = depth
                )
            )
        }
    }

    fun deleteItem(item: BOQItem) { viewModelScope.launch { repository.deleteItem(item) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BOQScreen(projectId: Long, viewModel: BOQViewModel, onBack: () -> Unit) {
    LaunchedEffect(projectId) { viewModel.init(projectId) }
    val boqItems by viewModel.items.collectAsState()
    val notations by viewModel.notations.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("BOQ") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } })
        },
        floatingActionButton = { FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Filled.Add, "Add BOQ item") } }
    ) { padding ->
        if (boqItems.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No BOQ items yet. Tap + to add one.")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(boqItems, key = { it.id }) { item ->
                    BOQItemCard(item = item, onDelete = { viewModel.deleteItem(item) })
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showAddDialog) {
        AddBOQItemDialog(
            notations = notations,
            onDismiss = { showAddDialog = false },
            onSave = { code, cat, desc, unit, mode, nos, sets, length, breadth, depth, rate, workItem, waste ->
                viewModel.addItem(code, cat, desc, unit, mode, nos, sets, length, breadth, depth, rate, workItem, waste)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun BOQItemCard(item: BOQItem, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${item.itemCode} — ${item.description}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatQuantity(item.quantity, item.unit)} @ ${formatCurrency(item.rate)}  =  ${formatCurrency(item.amount)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand")
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Category: ${item.category.name.replace("_", " ")}", style = MaterialTheme.typography.bodySmall)
                if (item.quantityMode == BOQQuantityMode.LBD) {
                    Text(
                        "Quantity mode: L×B×D  •  Sets ${formatQuantity(item.sets, "")}  •  L ${item.length}  •  B ${item.breadth}  •  D ${item.depth}",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text("Quantity mode: Nos", style = MaterialTheme.typography.bodySmall)
                }
                if (item.materialType.isNotBlank()) {
                    Text("Work item: ${item.materialType}  •  Waste ${item.wastePercent}%", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBOQItemDialog(
    notations: List<BOQNotation>,
    onDismiss: () -> Unit,
    onSave: (String, CostCategory, String, String, BOQQuantityMode, Double, Double, Double, Double, Double, Double, String, Double) -> Unit
) {
    var selectedNotation by remember { mutableStateOf<BOQNotation?>(null) }
    var code by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CostCategory.CIVIL_STRUCTURAL) }
    var description by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var quantityMode by remember { mutableStateOf(BOQQuantityMode.NOS) }
    var nosQuantity by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf("1") }
    var length by remember { mutableStateOf("") }
    var breadth by remember { mutableStateOf("") }
    var depth by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var waste by remember { mutableStateOf("3") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var notationMenuExpanded by remember { mutableStateOf(false) }

    val computedQty = if (quantityMode == BOQQuantityMode.LBD)
        CalculationEngine.lbdQuantity(
            sets.toDoubleOrNull() ?: 1.0, length.toDoubleOrNull() ?: 0.0,
            breadth.toDoubleOrNull() ?: 0.0, depth.toDoubleOrNull() ?: 0.0
        )
    else nosQuantity.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add BOQ item") },
        text = {
            Column(Modifier.heightIn(max = 500.dp)) {
                if (notations.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { notationMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedNotation?.let { "${it.code} — ${it.description}" } ?: "Item code (pick a notation)")
                        }
                        DropdownMenu(expanded = notationMenuExpanded, onDismissRequest = { notationMenuExpanded = false }) {
                            notations.forEach { n ->
                                DropdownMenuItem(
                                    text = { Text("${n.code} — ${n.description}") },
                                    onClick = {
                                        selectedNotation = n
                                        code = n.code
                                        description = n.description
                                        unit = n.defaultUnit
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
                OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(10.dp))
                Text("Quantity", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = quantityMode == BOQQuantityMode.NOS, onClick = { quantityMode = BOQQuantityMode.NOS }, label = { Text("Nos") })
                    FilterChip(selected = quantityMode == BOQQuantityMode.LBD, onClick = { quantityMode = BOQQuantityMode.LBD }, label = { Text("L × B × D") })
                }
                Spacer(Modifier.height(6.dp))
                if (quantityMode == BOQQuantityMode.NOS) {
                    OutlinedTextField(
                        nosQuantity, { nosQuantity = it }, label = { Text("Quantity (Nos)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        sets, { sets = it }, label = { Text("Sets (count, e.g. 20 footings)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Row {
                        OutlinedTextField(length, { length = it }, label = { Text("Length") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(4.dp))
                        OutlinedTextField(breadth, { breadth = it }, label = { Text("Breadth") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(4.dp))
                        OutlinedTextField(depth, { depth = it }, label = { Text("Depth") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Leave breadth/depth blank for a pure length or area item.", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(6.dp))
                Text("Calculated quantity: ${formatQuantity(computedQty, unit)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(rate, { rate = it }, label = { Text("Rate (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(waste, { waste = it }, label = { Text("Waste %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (computedQty > 0 && (rate.toDoubleOrNull() ?: 0.0) > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text("Amount: ${formatCurrency(computedQty * (rate.toDoubleOrNull() ?: 0.0))}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val r = rate.toDoubleOrNull() ?: 0.0
                val w = waste.toDoubleOrNull() ?: 0.0
                if (code.isNotBlank() && description.isNotBlank() && computedQty > 0 && r >= 0) {
                    onSave(
                        code, category, description, unit, quantityMode,
                        nosQuantity.toDoubleOrNull() ?: 0.0,
                        sets.toDoubleOrNull() ?: 1.0, length.toDoubleOrNull() ?: 0.0,
                        breadth.toDoubleOrNull() ?: 0.0, depth.toDoubleOrNull() ?: 0.0,
                        r, code, w
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
