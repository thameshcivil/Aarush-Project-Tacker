package com.aarush.cpm.ui.vendor

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
import com.aarush.cpm.data.entity.Vendor
import com.aarush.cpm.data.entity.VendorRateType
import com.aarush.cpm.data.entity.VendorWorkCategory
import com.aarush.cpm.data.repository.ProjectRepository
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

data class VendorInput(
    val name: String, val contact: String, val workCategory: VendorWorkCategory, val rateType: VendorRateType,
    val rate: Double, val quantity: Double, val paymentTerms: String, val notes: String
)

class VendorViewModel(
    private val vendorRepository: VendorRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {
    private val projectIdFlow = MutableStateFlow<Long?>(null)

    val vendors: StateFlow<List<Vendor>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else vendorRepository.observeForProject(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun init(projectId: Long) {
        projectIdFlow.value = projectId
    }

    fun addVendor(input: VendorInput) {
        val currentProjectId = projectIdFlow.value ?: return
        viewModelScope.launch {
            val project = projectRepository.getById(currentProjectId)
            vendorRepository.addVendor(
                projectId = currentProjectId, name = input.name, contact = input.contact, workCategory = input.workCategory,
                rateType = input.rateType, rate = input.rate, quantity = input.quantity, projectValue = project?.projectValue ?: 0.0,
                startDate = System.currentTimeMillis(), endDate = System.currentTimeMillis(),
                paymentTerms = input.paymentTerms, notes = input.notes
            )
        }
    }

    fun updateVendor(original: Vendor, input: VendorInput) {
        val currentProjectId = projectIdFlow.value ?: return
        viewModelScope.launch {
            val project = projectRepository.getById(currentProjectId)
            val newContractValue = CalculationEngine.vendorContractValue(input.rateType, input.rate, input.quantity, project?.projectValue ?: 0.0)
            vendorRepository.updateVendor(
                original.copy(
                    name = input.name, contact = input.contact, workCategory = input.workCategory, rateType = input.rateType,
                    rate = input.rate, quantity = input.quantity, contractValue = newContractValue,
                    paymentTerms = input.paymentTerms, notes = input.notes
                )
            )
        }
    }

    fun deleteVendor(vendor: Vendor) { viewModelScope.launch { vendorRepository.deleteVendor(vendor) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorScreen(projectId: Long, viewModel: VendorViewModel, onBack: () -> Unit) {
    LaunchedEffect(projectId) { viewModel.init(projectId) }
    val vendors by viewModel.vendors.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingVendor by remember { mutableStateOf<Vendor?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Vendors") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Filled.Add, "Add vendor") } }
    ) { padding ->
        if (vendors.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No vendors yet. Tap + to add one.")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vendors, key = { it.id }) { v ->
                    val balance = (v.contractValue - v.amountPaid).coerceAtLeast(0.0)
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(v.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Row {
                                    TextButton(onClick = { editingVendor = v }) {
                                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Edit")
                                    }
                                    TextButton(onClick = { viewModel.deleteVendor(v) }) { Text("Delete") }
                                }
                            }
                            Text("${v.workCategory.name} • ${v.rateType.name.replace("_", " ")}")
                            Text("Contract: ${formatCurrency(v.contractValue)}  •  Paid: ${formatCurrency(v.amountPaid)}  •  Balance: ${formatCurrency(balance)}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showAddDialog) {
        AddEditVendorDialog(existing = null, onDismiss = { showAddDialog = false }, onSubmit = { input ->
            viewModel.addVendor(input)
            showAddDialog = false
        })
    }
    editingVendor?.let { vendor ->
        AddEditVendorDialog(existing = vendor, onDismiss = { editingVendor = null }, onSubmit = { input ->
            viewModel.updateVendor(vendor, input)
            editingVendor = null
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditVendorDialog(
    existing: Vendor?,
    onDismiss: () -> Unit,
    onSubmit: (VendorInput) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var contact by remember { mutableStateOf(existing?.contact ?: "") }
    var workCategory by remember { mutableStateOf(existing?.workCategory ?: VendorWorkCategory.CIVIL) }
    var rateType by remember { mutableStateOf(existing?.rateType ?: VendorRateType.PER_SQFT) }
    var rate by remember { mutableStateOf(existing?.rate?.takeIf { it != 0.0 }?.toString() ?: "") }
    var quantity by remember { mutableStateOf(existing?.quantity?.takeIf { it != 0.0 }?.toString() ?: "") }
    var terms by remember { mutableStateOf(existing?.paymentTerms ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var wcMenu by remember { mutableStateOf(false) }
    var rtMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add vendor" else "Edit vendor") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Vendor name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(contact, { contact = it }, label = { Text("Contact") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(onClick = { wcMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Category: ${workCategory.name}") }
                    DropdownMenu(expanded = wcMenu, onDismissRequest = { wcMenu = false }) {
                        VendorWorkCategory.values().forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { workCategory = c; wcMenu = false }) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(onClick = { rtMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Rate type: ${rateType.name.replace("_", " ")}") }
                    DropdownMenu(expanded = rtMenu, onDismissRequest = { rtMenu = false }) {
                        VendorRateType.values().forEach { r -> DropdownMenuItem(text = { Text(r.name.replace("_", " ")) }, onClick = { rateType = r; rtMenu = false }) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(rate, { rate = it }, label = { Text("Rate (₹/sqft, lump sum, or %)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity (if applicable)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(terms, { terms = it }, label = { Text("Payment terms") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val r = rate.toDoubleOrNull() ?: 0.0
                val q = quantity.toDoubleOrNull() ?: 0.0
                if (name.isNotBlank()) onSubmit(VendorInput(name, contact, workCategory, rateType, r, q, terms, notes))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
