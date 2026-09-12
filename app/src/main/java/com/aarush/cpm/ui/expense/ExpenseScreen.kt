package com.aarush.cpm.ui.expense

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
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
import com.aarush.cpm.data.entity.*
import com.aarush.cpm.data.repository.ExpenseRepository
import com.aarush.cpm.data.repository.MaterialRepository
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.ui.common.formatCurrency
import com.aarush.cpm.ui.common.formatDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Fields for a material-purchase style expense: Date, Mode of payment, List of material,
 *  Description, Quantity, Unit, Rate (+ optional Freight & Unloading Charges). */
data class MaterialExpenseInput(
    val date: Long,
    val paymentMode: PaymentMode,
    val materialName: String,
    val description: String,
    val quantity: Double,
    val unit: String,
    val rate: Double,
    val freightAmount: Double? = null
)

/** Fields for a "Received" entry: money in, logged simply — Date, Mode of payment, Amount,
 *  and an optional description (e.g. "Client advance", "Milestone 2"). */
data class ReceivedInput(
    val date: Long,
    val paymentMode: PaymentMode,
    val amount: Double,
    val description: String
)

class ExpenseViewModel(
    private val expenseRepository: ExpenseRepository,
    private val materialRepository: MaterialRepository
) : ViewModel() {
    private val projectIdFlow = MutableStateFlow<Long?>(null)

    val expenses: StateFlow<List<Expense>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else expenseRepository.observeForProject(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val materialRates: StateFlow<List<MaterialRateCard>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else materialRepository.observeMaterialRates(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun init(projectId: Long) {
        projectIdFlow.value = projectId
    }

    /** Vendor id auto-derived from the selected material's link — this is how a vendor
     *  payment gets tagged without a separate vendor field in the simplified form. */
    private fun resolveVendorLink(materialName: String): Pair<ExpenseType, Long?> {
        val linked = materialRates.value.find { it.description == materialName && it.linkedVendorId != null }
        return if (linked != null) ExpenseType.VENDOR to linked.linkedVendorId else ExpenseType.MATERIAL to null
    }

    fun addMaterialExpense(input: MaterialExpenseInput) {
        val currentProjectId = projectIdFlow.value ?: return
        val (type, vendorId) = resolveVendorLink(input.materialName)
        viewModelScope.launch {
            val category = if (type == ExpenseType.MATERIAL) {
                materialRepository.inferCategoryForMaterial(currentProjectId, input.materialName) ?: CostCategory.OTHER_MISC
            } else {
                CostCategory.OTHER_MISC
            }
            expenseRepository.addExpense(
                Expense(
                    projectId = currentProjectId, date = input.date, category = category, type = type,
                    direction = TransactionDirection.EXPENSE, vendorId = vendorId, itemOrMaterialName = input.materialName,
                    quantity = input.quantity, unit = input.unit, rate = input.rate,
                    totalAmount = CalculationEngine.expenseTotal(input.quantity, input.rate), paymentMode = input.paymentMode,
                    paymentStatus = PaymentStatus.PAID, notes = input.description
                )
            )
            val freight = input.freightAmount
            if (freight != null && freight > 0) {
                expenseRepository.addExpense(
                    Expense(
                        projectId = currentProjectId, date = input.date, category = category, type = ExpenseType.OTHER,
                        direction = TransactionDirection.EXPENSE, vendorId = null, itemOrMaterialName = "Freight & Unloading Charges",
                        quantity = 1.0, unit = "LS", rate = freight, totalAmount = freight, paymentMode = input.paymentMode,
                        paymentStatus = PaymentStatus.PAID, notes = "Linked to: ${input.materialName}"
                    )
                )
            }
        }
    }

    fun updateMaterialExpense(original: Expense, input: MaterialExpenseInput) {
        val currentProjectId = projectIdFlow.value ?: return
        val (type, vendorId) = resolveVendorLink(input.materialName)
        viewModelScope.launch {
            val category = if (type == ExpenseType.MATERIAL) {
                materialRepository.inferCategoryForMaterial(currentProjectId, input.materialName) ?: CostCategory.OTHER_MISC
            } else {
                CostCategory.OTHER_MISC
            }
            expenseRepository.updateExpense(
                original,
                original.copy(
                    date = input.date, type = type, category = category, vendorId = vendorId, itemOrMaterialName = input.materialName,
                    quantity = input.quantity, unit = input.unit, rate = input.rate,
                    totalAmount = CalculationEngine.expenseTotal(input.quantity, input.rate),
                    paymentMode = input.paymentMode, notes = input.description
                )
            )
        }
    }

    fun addReceived(input: ReceivedInput) {
        val currentProjectId = projectIdFlow.value ?: return
        viewModelScope.launch {
            expenseRepository.addExpense(
                Expense(
                    projectId = currentProjectId, date = input.date, category = CostCategory.OTHER_MISC, type = ExpenseType.OTHER,
                    direction = TransactionDirection.RECEIVED, vendorId = null,
                    itemOrMaterialName = input.description.ifBlank { "Received from client" },
                    quantity = 1.0, unit = "LS", rate = input.amount, totalAmount = input.amount,
                    paymentMode = input.paymentMode, paymentStatus = PaymentStatus.PAID, notes = input.description
                )
            )
        }
    }

    fun updateReceived(original: Expense, input: ReceivedInput) {
        viewModelScope.launch {
            expenseRepository.updateExpense(
                original,
                original.copy(
                    date = input.date, itemOrMaterialName = input.description.ifBlank { "Received from client" },
                    rate = input.amount, totalAmount = input.amount, paymentMode = input.paymentMode, notes = input.description
                )
            )
        }
    }

    fun deleteExpense(expense: Expense) { viewModelScope.launch { expenseRepository.deleteExpense(expense) } }
}

private enum class GroupMode { MATERIAL, DATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTabContent(projectId: Long, viewModel: ExpenseViewModel) {
    LaunchedEffect(projectId) { viewModel.init(projectId) }
    val allExpenses by viewModel.expenses.collectAsState()
    val materialRates by viewModel.materialRates.collectAsState()
    var activeDirection by remember { mutableStateOf(TransactionDirection.EXPENSE) }
    var groupMode by remember { mutableStateOf(GroupMode.MATERIAL) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }

    val filtered = allExpenses.filter { it.direction == activeDirection }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = activeDirection == TransactionDirection.EXPENSE, onClick = { activeDirection = TransactionDirection.EXPENSE }, label = { Text("Expense") })
                FilterChip(selected = activeDirection == TransactionDirection.RECEIVED, onClick = { activeDirection = TransactionDirection.RECEIVED }, label = { Text("Received") })
            }

            if (activeDirection == TransactionDirection.EXPENSE) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sort by:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = groupMode == GroupMode.MATERIAL, onClick = { groupMode = GroupMode.MATERIAL }, label = { Text("Material") })
                        FilterChip(selected = groupMode == GroupMode.DATE, onClick = { groupMode = GroupMode.DATE }, label = { Text("Date") })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (activeDirection == TransactionDirection.EXPENSE) "No expenses yet. Tap + to add spending." else "No amounts received yet. Tap + to log one.")
                }
            } else if (activeDirection == TransactionDirection.RECEIVED) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered.sortedByDescending { it.date }, key = { it.id }) { e ->
                        ReceivedItemRow(expense = e, onEdit = { editingExpense = e }, onDelete = { viewModel.deleteExpense(e) })
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            } else {
                val grouped = when (groupMode) {
                    GroupMode.MATERIAL -> filtered.groupBy { it.itemOrMaterialName }.toSortedMap()
                    GroupMode.DATE -> filtered
                        .groupBy { it.date / 86_400_000L } // group by calendar day, not the display string
                        .toSortedMap(compareByDescending { it })
                        .mapKeys { (dayBucket, _) -> formatDate(dayBucket * 86_400_000L) }
                }
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    grouped.forEach { (key, groupItems) ->
                        val isExpanded = expandedGroups.contains(key)
                        val groupTotal = groupItems.sumOf { it.totalAmount }
                        item(key = "header-$key") {
                            Card(
                                onClick = { expandedGroups = if (isExpanded) expandedGroups - key else expandedGroups + key },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(key, fontWeight = FontWeight.Bold)
                                        Text("${groupItems.size} entr${if (groupItems.size == 1) "y" else "ies"}  •  ${formatCurrency(groupTotal)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (isExpanded) "Collapse" else "Expand")
                                }
                            }
                        }
                        if (isExpanded) {
                            items(groupItems.sortedByDescending { it.date }, key = { it.id }) { e ->
                                ExpenseItemRow(expense = e, onEdit = { editingExpense = e }, onDelete = { viewModel.deleteExpense(e) })
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
        ) { Icon(Icons.Filled.Add, "Add") }
    }

    if (showAddDialog) {
        if (activeDirection == TransactionDirection.EXPENSE) {
            AddEditMaterialExpenseDialog(
                materialRates = materialRates, existing = null,
                onDismiss = { showAddDialog = false },
                onSubmit = { input -> viewModel.addMaterialExpense(input); showAddDialog = false }
            )
        } else {
            AddEditReceivedDialog(
                existing = null,
                onDismiss = { showAddDialog = false },
                onSubmit = { input -> viewModel.addReceived(input); showAddDialog = false }
            )
        }
    }
    editingExpense?.let { expense ->
        if (expense.direction == TransactionDirection.EXPENSE) {
            AddEditMaterialExpenseDialog(
                materialRates = materialRates, existing = expense,
                onDismiss = { editingExpense = null },
                onSubmit = { input -> viewModel.updateMaterialExpense(expense, input); editingExpense = null }
            )
        } else {
            AddEditReceivedDialog(
                existing = expense,
                onDismiss = { editingExpense = null },
                onSubmit = { input -> viewModel.updateReceived(expense, input); editingExpense = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseItemRow(expense: Expense, onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp).animateContentSize()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(formatDate(expense.date), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${expense.quantity} ${expense.unit} @ ${formatCurrency(expense.rate)}  =  ${formatCurrency(expense.totalAmount)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand")
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Material: ${expense.itemOrMaterialName}", style = MaterialTheme.typography.bodySmall)
                Text("Category: ${expense.category.name.replace("_", " ")}", style = MaterialTheme.typography.bodySmall)
                Text("Description: ${expense.notes.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                Text("Quantity: ${expense.quantity} ${expense.unit}", style = MaterialTheme.typography.bodySmall)
                Text("Rate: ${formatCurrency(expense.rate)}", style = MaterialTheme.typography.bodySmall)
                Text("Mode of payment: ${expense.paymentMode.name}", style = MaterialTheme.typography.bodySmall)
                if (expense.type == ExpenseType.VENDOR) Text("Vendor payment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceivedItemRow(expense: Expense, onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(formatDate(expense.date), fontWeight = FontWeight.SemiBold)
                    Text(formatCurrency(expense.totalAmount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand")
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Mode of payment: ${expense.paymentMode.name}", style = MaterialTheme.typography.bodySmall)
                Text("Description: ${expense.notes.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditMaterialExpenseDialog(
    materialRates: List<MaterialRateCard>,
    existing: Expense?,
    onDismiss: () -> Unit,
    onSubmit: (MaterialExpenseInput) -> Unit
) {
    var dateMillis by remember { mutableStateOf(existing?.date ?: System.currentTimeMillis()) }
    var paymentMode by remember { mutableStateOf(existing?.paymentMode ?: PaymentMode.CASH) }
    var materialName by remember { mutableStateOf(existing?.itemOrMaterialName ?: "") }
    var description by remember { mutableStateOf(existing?.notes ?: "") }
    var quantity by remember { mutableStateOf(existing?.quantity?.toString() ?: "1") }
    var unit by remember { mutableStateOf(existing?.unit ?: "") }
    var rate by remember { mutableStateOf(existing?.rate?.toString() ?: "") }
    var showFreightField by remember { mutableStateOf(false) }
    var freightAmount by remember { mutableStateOf("") }

    var materialMenu by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val qtyD = quantity.toDoubleOrNull() ?: 0.0
    val rateD = rate.toDoubleOrNull() ?: 0.0
    val total = CalculationEngine.expenseTotal(qtyD, rateD)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add expense" else "Edit expense") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Date: ${formatDate(dateMillis)}")
                }
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(onClick = { modeMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Mode of payment: ${paymentMode.name}") }
                    DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                        PaymentMode.values().forEach { m -> DropdownMenuItem(text = { Text(m.name) }, onClick = { paymentMode = m; modeMenu = false }) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (materialRates.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { materialMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(materialName.ifBlank { "List of material" })
                        }
                        DropdownMenu(expanded = materialMenu, onDismissRequest = { materialMenu = false }) {
                            materialRates.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(if (m.linkedVendorId != null) "${m.description} (vendor)" else "${m.description} (${m.unit})") },
                                    onClick = {
                                        materialName = m.description
                                        unit = m.unit
                                        if (m.rate > 0) rate = "%.2f".format(m.rate)
                                        materialMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(rate, { rate = it }, label = { Text("Rate (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text("Total amount: ${formatCurrency(total)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                Spacer(Modifier.height(10.dp))
                if (!showFreightField) {
                    OutlinedButton(onClick = { showFreightField = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Freight & Unloading Charges")
                    }
                } else {
                    OutlinedTextField(
                        freightAmount, { freightAmount = it }, label = { Text("Freight & Unloading Charges (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { showFreightField = false; freightAmount = "" }) { Text("Remove") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (materialName.isNotBlank() && qtyD > 0 && rateD >= 0) {
                    onSubmit(
                        MaterialExpenseInput(
                            date = dateMillis, paymentMode = paymentMode, materialName = materialName,
                            description = description, quantity = qtyD, unit = unit, rate = rateD,
                            freightAmount = freightAmount.toDoubleOrNull()
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditReceivedDialog(
    existing: Expense?,
    onDismiss: () -> Unit,
    onSubmit: (ReceivedInput) -> Unit
) {
    var dateMillis by remember { mutableStateOf(existing?.date ?: System.currentTimeMillis()) }
    var paymentMode by remember { mutableStateOf(existing?.paymentMode ?: PaymentMode.BANK_TRANSFER) }
    var amount by remember { mutableStateOf(existing?.totalAmount?.toString() ?: "") }
    var description by remember { mutableStateOf(existing?.notes ?: "") }
    var modeMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add received amount" else "Edit received amount") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Date: ${formatDate(dateMillis)}")
                }
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(onClick = { modeMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Mode of payment: ${paymentMode.name}") }
                    DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                        PaymentMode.values().forEach { m -> DropdownMenuItem(text = { Text(m.name) }, onClick = { paymentMode = m; modeMenu = false }) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount received (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(description, { description = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.toDoubleOrNull() ?: 0.0
                if (a > 0) onSubmit(ReceivedInput(date = dateMillis, paymentMode = paymentMode, amount = a, description = description))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
