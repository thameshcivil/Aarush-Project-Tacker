package com.aarush.cpm.ui.expense

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import com.aarush.cpm.data.repository.VendorRepository
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

data class ExpenseInput(
    val category: CostCategory, val type: ExpenseType, val vendorId: Long?, val itemName: String,
    val quantity: Double, val unit: String, val rate: Double, val paymentMode: PaymentMode,
    val paymentStatus: PaymentStatus, val invoiceNumber: String, val notes: String
)

class ExpenseViewModel(
    private val expenseRepository: ExpenseRepository,
    private val vendorRepository: VendorRepository
) : ViewModel() {
    private val projectIdFlow = MutableStateFlow<Long?>(null)

    val expenses: StateFlow<List<Expense>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else expenseRepository.observeForProject(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vendors: StateFlow<List<Vendor>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else vendorRepository.observeForProject(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun init(projectId: Long) {
        projectIdFlow.value = projectId
    }

    fun addExpense(input: ExpenseInput) {
        val currentProjectId = projectIdFlow.value ?: return
        viewModelScope.launch {
            expenseRepository.addExpense(
                Expense(
                    projectId = currentProjectId, date = System.currentTimeMillis(), category = input.category, type = input.type,
                    vendorId = input.vendorId, itemOrMaterialName = input.itemName, quantity = input.quantity, unit = input.unit, rate = input.rate,
                    totalAmount = CalculationEngine.expenseTotal(input.quantity, input.rate), paymentMode = input.paymentMode,
                    paymentStatus = input.paymentStatus, invoiceNumber = input.invoiceNumber, notes = input.notes
                )
            )
        }
    }

    fun updateExpense(original: Expense, input: ExpenseInput) {
        viewModelScope.launch {
            expenseRepository.updateExpense(
                original,
                original.copy(
                    category = input.category, type = input.type, vendorId = input.vendorId, itemOrMaterialName = input.itemName,
                    quantity = input.quantity, unit = input.unit, rate = input.rate,
                    totalAmount = CalculationEngine.expenseTotal(input.quantity, input.rate), paymentMode = input.paymentMode,
                    paymentStatus = input.paymentStatus, invoiceNumber = input.invoiceNumber, notes = input.notes
                )
            )
        }
    }

    fun deleteExpense(expense: Expense) { viewModelScope.launch { expenseRepository.deleteExpense(expense) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTabContent(projectId: Long, viewModel: ExpenseViewModel) {
    LaunchedEffect(projectId) { viewModel.init(projectId) }
    val expenseList by viewModel.expenses.collectAsState()
    val vendorList by viewModel.vendors.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }

    Box(Modifier.fillMaxSize()) {
        if (expenseList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No expenses yet. Tap + to add spending.")
            }
        } else {
            val grouped = expenseList.groupBy { it.itemOrMaterialName }.toSortedMap()
            LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grouped.forEach { (name, groupItems) ->
                    val isExpanded = expandedGroups.contains(name)
                    val groupTotal = groupItems.sumOf { it.totalAmount }
                    item(key = "header-$name") {
                        Card(
                            onClick = { expandedGroups = if (isExpanded) expandedGroups - name else expandedGroups + name },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontWeight = FontWeight.Bold)
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

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Filled.Add, "Add expense") }
    }

    if (showAddDialog) {
        AddEditExpenseDialog(vendors = vendorList, existing = null, onDismiss = { showAddDialog = false }, onSubmit = { input ->
            viewModel.addExpense(input)
            showAddDialog = false
        })
    }
    editingExpense?.let { expense ->
        AddEditExpenseDialog(vendors = vendorList, existing = expense, onDismiss = { editingExpense = null }, onSubmit = { input ->
            viewModel.updateExpense(expense, input)
            editingExpense = null
        })
    }
}

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
                Text("Date: ${formatDate(expense.date)}", style = MaterialTheme.typography.bodySmall)
                Text("Description: ${expense.itemOrMaterialName}", style = MaterialTheme.typography.bodySmall)
                Text("Quantity: ${expense.quantity} ${expense.unit}", style = MaterialTheme.typography.bodySmall)
                Text("Type: ${expense.type.name}  •  Category: ${expense.category.name.replace("_", " ")}  •  ${expense.paymentStatus.name}", style = MaterialTheme.typography.bodySmall)
                if (expense.invoiceNumber.isNotBlank()) Text("Invoice: ${expense.invoiceNumber}", style = MaterialTheme.typography.bodySmall)
                Text("Remark: ${expense.notes.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
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
private fun AddEditExpenseDialog(
    vendors: List<Vendor>,
    existing: Expense?,
    onDismiss: () -> Unit,
    onSubmit: (ExpenseInput) -> Unit
) {
    var category by remember { mutableStateOf(existing?.category ?: CostCategory.CIVIL_STRUCTURAL) }
    var type by remember { mutableStateOf(existing?.type ?: ExpenseType.MATERIAL) }
    var vendorId by remember { mutableStateOf(existing?.vendorId) }
    var itemName by remember { mutableStateOf(existing?.itemOrMaterialName ?: "") }
    var quantity by remember { mutableStateOf(existing?.quantity?.toString() ?: "1") }
    var unit by remember { mutableStateOf(existing?.unit ?: "") }
    var rate by remember { mutableStateOf(existing?.rate?.toString() ?: "") }
    var paymentMode by remember { mutableStateOf(existing?.paymentMode ?: PaymentMode.CASH) }
    var paymentStatus by remember { mutableStateOf(existing?.paymentStatus ?: PaymentStatus.PAID) }
    var invoice by remember { mutableStateOf(existing?.invoiceNumber ?: "") }
    var remark by remember { mutableStateOf(existing?.notes ?: "") }

    var catMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var vendorMenu by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }

    val qtyD = quantity.toDoubleOrNull() ?: 0.0
    val rateD = rate.toDoubleOrNull() ?: 0.0
    val total = CalculationEngine.expenseTotal(qtyD, rateD)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add expense" else "Edit expense") },
        text = {
            Column(Modifier.heightIn(max = 560.dp)) {
                if (existing != null) {
                    Text("Date: ${formatDate(existing.date)}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                }
                Box {
                    OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Type: ${type.name}") }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        ExpenseType.values().forEach { t -> DropdownMenuItem(text = { Text(t.name) }, onClick = { type = t; typeMenu = false }) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(onClick = { catMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Category: ${category.name.replace("_", " ")}") }
                    DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                        CostCategory.values().forEach { c -> DropdownMenuItem(text = { Text(c.name.replace("_", " ")) }, onClick = { category = c; catMenu = false }) }
                    }
                }
                if (type == ExpenseType.VENDOR && vendors.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Box {
                        OutlinedButton(onClick = { vendorMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(vendors.find { it.id == vendorId }?.name ?: "Select vendor")
                        }
                        DropdownMenu(expanded = vendorMenu, onDismissRequest = { vendorMenu = false }) {
                            vendors.forEach { v -> DropdownMenuItem(text = { Text(v.name) }, onClick = { vendorId = v.id; vendorMenu = false }) }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(itemName, { itemName = it }, label = { Text("Item / material / labour description") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row {
                    OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(rate, { rate = it }, label = { Text("Rate (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text("Total: ${formatCurrency(total)}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(onClick = { modeMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Payment mode: ${paymentMode.name}") }
                    DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                        PaymentMode.values().forEach { m -> DropdownMenuItem(text = { Text(m.name) }, onClick = { paymentMode = m; modeMenu = false }) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(onClick = { statusMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Status: ${paymentStatus.name}") }
                    DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                        PaymentStatus.values().forEach { s -> DropdownMenuItem(text = { Text(s.name) }, onClick = { paymentStatus = s; statusMenu = false }) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(invoice, { invoice = it }, label = { Text("Invoice number (optional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(remark, { remark = it }, label = { Text("Remark (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (itemName.isNotBlank() && qtyD > 0 && rateD >= 0) {
                    onSubmit(
                        ExpenseInput(
                            category = category, type = type, vendorId = if (type == ExpenseType.VENDOR) vendorId else null,
                            itemName = itemName, quantity = qtyD, unit = unit, rate = rateD, paymentMode = paymentMode,
                            paymentStatus = paymentStatus, invoiceNumber = invoice, notes = remark
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
