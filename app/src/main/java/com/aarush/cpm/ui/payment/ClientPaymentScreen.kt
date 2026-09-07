package com.aarush.cpm.ui.payment

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
import com.aarush.cpm.data.entity.ClientPayment
import com.aarush.cpm.data.entity.PaymentMode
import com.aarush.cpm.data.entity.Project
import com.aarush.cpm.data.repository.ClientPaymentRepository
import com.aarush.cpm.data.repository.ProjectRepository
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.ui.common.formatCurrency
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ClientPaymentViewModel(
    private val paymentRepository: ClientPaymentRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {
    private val projectIdFlow = MutableStateFlow<Long?>(null)

    val payments: StateFlow<List<ClientPayment>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else paymentRepository.observeForProject(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalReceived: StateFlow<Double> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(0.0) else paymentRepository.observeTotalReceived(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    fun init(projectId: Long) {
        projectIdFlow.value = projectId
        viewModelScope.launch { _project.value = projectRepository.getById(projectId) }
    }

    fun addPayment(amount: Double, mode: PaymentMode, reference: String, notes: String) {
        val currentProjectId = projectIdFlow.value ?: return
        viewModelScope.launch {
            paymentRepository.addPayment(
                ClientPayment(projectId = currentProjectId, date = System.currentTimeMillis(), amountReceived = amount, paymentMode = mode, referenceNumber = reference, notes = notes)
            )
        }
    }

    fun deletePayment(payment: ClientPayment) { viewModelScope.launch { paymentRepository.deletePayment(payment) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientPaymentScreen(projectId: Long, viewModel: ClientPaymentViewModel, onBack: () -> Unit) {
    LaunchedEffect(projectId) { viewModel.init(projectId) }
    val payments by viewModel.payments.collectAsState()
    val totalReceived by viewModel.totalReceived.collectAsState()
    val project by viewModel.project.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Client Payments") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Filled.Add, "Add payment") } }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            project?.let { p ->
                val balance = CalculationEngine.clientBalance(p.projectValue, totalReceived)
                Card(Modifier.padding(12.dp).fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Project Value: ${formatCurrency(p.projectValue)}")
                        Text("Total Received: ${formatCurrency(totalReceived)}")
                        Text("Yet to Receive: ${formatCurrency(balance)}", fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (payments.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("No payments recorded yet.") }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(payments, key = { it.id }) { pay ->
                        Card {
                            Column(Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(formatCurrency(pay.amountReceived), fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { viewModel.deletePayment(pay) }) { Text("Delete") }
                                }
                                Text("${pay.paymentMode.name} • Ref: ${pay.referenceNumber.ifBlank { "-" }}")
                            }
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddPaymentDialog(onDismiss = { showAddDialog = false }, onSave = { amount, mode, ref, notes ->
            viewModel.addPayment(amount, mode, ref, notes)
            showAddDialog = false
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPaymentDialog(onDismiss: () -> Unit, onSave: (Double, PaymentMode, String, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(PaymentMode.BANK_TRANSFER) }
    var reference by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var modeMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add client payment") },
        text = {
            Column {
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount received (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(onClick = { modeMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Mode: ${mode.name}") }
                    DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                        PaymentMode.values().forEach { m -> DropdownMenuItem(text = { Text(m.name) }, onClick = { mode = m; modeMenu = false }) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(reference, { reference = it }, label = { Text("Reference number") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.toDoubleOrNull() ?: 0.0
                if (a > 0) onSave(a, mode, reference, notes)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
