package com.aarush.cpm.ui.boq

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.entity.BOQItem
import com.aarush.cpm.data.entity.CostCategory
import com.aarush.cpm.data.repository.BOQRepository
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.ui.common.formatCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BOQViewModel(private val repository: BOQRepository) : ViewModel() {
    private val projectIdFlow = MutableStateFlow<Long?>(null)

    // Eagerly initialized (never lateinit) so the very first composition always has a safe,
    // empty value to collect — no crash if the UI reads this before init() has run.
    val items: StateFlow<List<BOQItem>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeForProject(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun init(projectId: Long) {
        projectIdFlow.value = projectId
    }

    fun addItem(
        itemCode: String, category: CostCategory, description: String, unit: String,
        quantity: Double, rate: Double, materialType: String, wastePercent: Double
    ) {
        val currentProjectId = projectIdFlow.value ?: return
        viewModelScope.launch {
            repository.addItem(
                BOQItem(
                    projectId = currentProjectId, itemCode = itemCode, category = category, description = description,
                    unit = unit, quantity = quantity, rate = rate, amount = CalculationEngine.boqAmount(quantity, rate),
                    materialType = materialType, wastePercent = wastePercent
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
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("BOQ") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } })
        },
        floatingActionButton = { FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Filled.Add, "Add BOQ item") } }
    ) { padding ->
        if (boqItems.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No BOQ items yet. Tap + to add one.")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(boqItems, key = { it.id }) { item ->
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${item.itemCode} — ${item.description}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.deleteItem(item) }) { Text("Delete") }
                            }
                            Text("${item.category.name.replace("_", " ")} • ${item.quantity} ${item.unit} @ ${formatCurrency(item.rate)}")
                            Text("Amount: ${formatCurrency(item.amount)}", fontWeight = FontWeight.Bold)
                            if (item.materialType.isNotBlank()) Text("Work item: ${item.materialType} • Waste ${item.wastePercent}%", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showAddDialog) {
        AddBOQItemDialog(onDismiss = { showAddDialog = false }, onSave = { code, cat, desc, unit, qty, rate, workItem, waste ->
            viewModel.addItem(code, cat, desc, unit, qty, rate, workItem, waste)
            showAddDialog = false
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBOQItemDialog(
    onDismiss: () -> Unit,
    onSave: (String, CostCategory, String, String, Double, Double, String, Double) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CostCategory.CIVIL_STRUCTURAL) }
    var description by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var workItem by remember { mutableStateOf("") }
    var waste by remember { mutableStateOf("3") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add BOQ item") },
        text = {
            Column {
                OutlinedTextField(code, { code = it }, label = { Text("Item code") }, modifier = Modifier.fillMaxWidth())
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
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row {
                    OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(rate, { rate = it }, label = { Text("Rate (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(workItem, { workItem = it }, label = { Text("Work item key (for material coefficients, e.g. \"Concrete M20\")") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(waste, { waste = it }, label = { Text("Waste %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = quantity.toDoubleOrNull() ?: 0.0
                val r = rate.toDoubleOrNull() ?: 0.0
                val w = waste.toDoubleOrNull() ?: 0.0
                if (code.isNotBlank() && description.isNotBlank() && q > 0 && r >= 0) {
                    onSave(code, category, description, unit, q, r, workItem, w)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
