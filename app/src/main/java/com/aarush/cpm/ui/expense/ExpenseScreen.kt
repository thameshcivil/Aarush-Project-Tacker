package com.aarush.cpm.ui.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun ExpenseScreen(projectId: Long, viewModel: ExpenseViewModel, onBack: () -> Unit) {
    LaunchedEffect(projectId) { viewModel.init(projectId) }
    val expenseList by viewModel.expenses.collectAsState()
    val vendorList by viewModel.vendors.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Expenses") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Filled.Add, "Add expense") } }
    ) { padding ->
        if (expenseList.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No expenses yet. Tap + to add spending.")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(expenseList, key = { it.id }) { e ->
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(e.itemOrMaterialName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Row {
                                    TextButton(onClick = { editingExpense = e }) {
                                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Edit")
                                    }
                                    TextButton(onClick = { viewModel.deleteExpense(e) }) { Text("Delete") }
                                }
                            }
                            Text("${e.type.name} • ${e.category.name.replace("_", " ")} • ${e.paymentStatus.name}")
                            Text("${e.quantity} ${e.unit} @ ${formatCurrency(e.rate)} = ${formatCurrency(e.totalAmount)}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
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
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

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
            Column {
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
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (itemName.isNotBlank() && qtyD > 0 && rateD >= 0) {
                    onSubmit(
                        ExpenseInput(
                            category = category, type = type, vendorId = if (type == ExpenseType.VENDOR) vendorId else null,
                            itemName = itemName, quantity = qtyD, unit = unit, rate = rateD, paymentMode = paymentMode,
                            paymentStatus = paymentStatus, invoiceNumber = invoice, notes = notes
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
