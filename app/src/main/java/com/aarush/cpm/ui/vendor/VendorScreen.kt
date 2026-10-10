package com.aarush.cpm.ui.vendor

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
import com.aarush.cpm.data.entity.Expense
import com.aarush.cpm.data.entity.Vendor
import com.aarush.cpm.data.entity.VendorRateType
import com.aarush.cpm.data.entity.VendorWorkCategory
import com.aarush.cpm.data.repository.ExpenseRepository
import com.aarush.cpm.data.repository.ProjectRepository
import com.aarush.cpm.data.repository.VendorRepository
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.ui.common.ConfirmDeleteDialog
import com.aarush.cpm.ui.common.formatCurrency
import com.aarush.cpm.ui.common.formatDate
import com.aarush.cpm.ui.common.showUndoDeleteSnackbar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.aarush.cpm.ui.common.AppCard
import com.aarush.cpm.ui.common.EmptyState
import com.aarush.cpm.ui.common.MetricRow
import com.aarush.cpm.ui.common.ProgressRail
import com.aarush.cpm.ui.common.SectionLabel
import com.aarush.cpm.ui.common.StatusPill
import com.aarush.cpm.ui.common.humanizeLabel
import com.aarush.cpm.ui.theme.SiteTheme
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VendorInput(
    val name: String, val contact: String, val workCategory: VendorWorkCategory, val rateType: VendorRateType,
    val rate: Double, val quantity: Double, val paymentTerms: String, val notes: String
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VendorViewModel(
    private val vendorRepository: VendorRepository,
    private val projectRepository: ProjectRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {
    private val projectIdFlow = MutableStateFlow<Long?>(null)

    val vendors: StateFlow<List<Vendor>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else vendorRepository.observeForProject(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** All project expenses, so each vendor card can show its own payment history (date +
     *  amount) by filtering on vendorId — payments are logged from the Expense tab via the
     *  vendor's auto-linked material, not entered here directly. */
    val expenses: StateFlow<List<Expense>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else expenseRepository.observeForProject(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Project value — needed so the "percentage of project value" contract preview in the
     *  dialog matches exactly what gets saved. */
    val projectValue: StateFlow<Double> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(null) else projectRepository.observeById(id) }
        .map { it?.projectValue ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

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

    /** Undo for delete: restores the vendor with its original id, so its past payments and
     *  its "Labour Cost for <Vendor>" line reconnect exactly as before. */
    fun restoreVendor(vendor: Vendor) {
        viewModelScope.launch { vendorRepository.restoreVendor(vendor) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorTabContent(projectId: Long, viewModel: VendorViewModel, snackbarHostState: SnackbarHostState) {
    LaunchedEffect(projectId) { viewModel.init(projectId) }
    val vendors by viewModel.vendors.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val projectValue by viewModel.projectValue.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingVendor by remember { mutableStateOf<Vendor?>(null) }
    var pendingDelete by remember { mutableStateOf<Vendor?>(null) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        if (vendors.isEmpty()) {
            EmptyState("No vendors yet", "Add your civil contractor, electrician, plumber, painter… Each vendor gets a \"Labour Cost for <name>\" line in the Expense form. That's how payments are logged and tracked against the contract.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            SectionLabel("All contracts")
                            MetricRow("Contract value", formatCurrency(vendors.sumOf { it.contractValue }))
                            MetricRow("Paid", formatCurrency(vendors.sumOf { it.amountPaid }))
                            MetricRow("Balance payable", formatCurrency(vendors.sumOf { (it.contractValue - it.amountPaid).coerceAtLeast(0.0) }), emphasize = true)
                        }
                    }
                }
                items(vendors, key = { it.id }) { v ->
                    val payments = expenses.filter { it.vendorId == v.id }.sortedByDescending { it.date }
                    VendorCard(vendor = v, payments = payments, onEdit = { editingVendor = v }, onDelete = { pendingDelete = v })
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Filled.Add, "Add vendor") }
    }

    if (showAddDialog) {
        AddEditVendorDialog(existing = null, projectValue = projectValue, onDismiss = { showAddDialog = false }, onSubmit = { input ->
            viewModel.addVendor(input)
            showAddDialog = false
        })
    }
    editingVendor?.let { vendor ->
        AddEditVendorDialog(existing = vendor, projectValue = projectValue, onDismiss = { editingVendor = null }, onSubmit = { input ->
            viewModel.updateVendor(vendor, input)
            editingVendor = null
        })
    }
    pendingDelete?.let { vendor ->
        ConfirmDeleteDialog(
            itemLabel = "vendor \"${vendor.name}\"",
            onConfirm = {
                pendingDelete = null
                viewModel.deleteVendor(vendor)
                scope.showUndoDeleteSnackbar(snackbarHostState, "Vendor deleted") { viewModel.restoreVendor(vendor) }
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VendorCard(vendor: Vendor, payments: List<Expense>, onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val balance = vendor.contractValue - vendor.amountPaid

    AppCard(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel(humanizeLabel("${vendor.workCategory.name} · ${vendor.rateType.name}"))
                    Text(vendor.name, style = MaterialTheme.typography.titleSmall)
                    if (vendor.contact.isNotBlank()) Text(vendor.contact, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                }
                if (balance < 0) StatusPill("Overpaid", SiteTheme.colors.bad, SiteTheme.colors.badTint)
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand")
            }
            Spacer(Modifier.height(8.dp))
            ProgressRail(progress = if (vendor.contractValue > 0) (vendor.amountPaid / vendor.contractValue).toFloat() else 0f,
                color = if (balance < 0) SiteTheme.colors.bad else SiteTheme.colors.accent)
            Spacer(Modifier.height(4.dp))
            MetricRow("Contract", formatCurrency(vendor.contractValue))
            MetricRow("Paid", formatCurrency(vendor.amountPaid))
            MetricRow(if (balance < 0) "Overpaid by" else "Balance", formatCurrency(kotlin.math.abs(balance)),
                valueColor = if (balance < 0) SiteTheme.colors.bad else androidx.compose.ui.graphics.Color.Unspecified, emphasize = true)
            if (vendor.paymentTerms.isNotBlank()) Text("Terms: ${vendor.paymentTerms}", style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                SectionLabel("Payment history")
                Spacer(Modifier.height(4.dp))
                if (payments.isEmpty()) {
                    Text(
                        "No payments logged yet. Pick \"Labour Cost for ${vendor.name}\" in the Expense tab's material list to log one.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    payments.forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatDate(p.date), style = MaterialTheme.typography.bodySmall)
                            Text(formatCurrency(p.totalAmount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
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
private fun AddEditVendorDialog(
    existing: Vendor?,
    projectValue: Double,
    onDismiss: () -> Unit,
    onSubmit: (VendorInput) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var contact by remember { mutableStateOf(existing?.contact ?: "") }
    var workCategory by remember { mutableStateOf(existing?.workCategory ?: VendorWorkCategory.CIVIL) }
    var rateType by remember { mutableStateOf(existing?.rateType ?: VendorRateType.PER_SQFT) }
    var rate by remember { mutableStateOf(existing?.rate?.takeIf { it != 0.0 }?.toString() ?: "") }
    var quantity by remember { mutableStateOf(existing?.quantity?.takeIf { it != 0.0 }?.toString() ?: "") }
    var lumpSum by remember { mutableStateOf(existing?.takeIf { it.rateType == VendorRateType.LUMP_SUM }?.rate?.toString() ?: "") }
    var terms by remember { mutableStateOf(existing?.paymentTerms ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var wcMenu by remember { mutableStateOf(false) }
    var rtMenu by remember { mutableStateOf(false) }

    // Exactly the inputs that will be saved, through the same CalculationEngine formula the
    // repository uses — the preview previously disagreed for PERCENTAGE / CUSTOM / blank qty.
    val finalRateValue = if (rateType == VendorRateType.LUMP_SUM) (lumpSum.toDoubleOrNull() ?: 0.0) else (rate.toDoubleOrNull() ?: 0.0)
    val finalQuantityValue = when (rateType) {
        VendorRateType.PER_SQFT, VendorRateType.QUANTITY_X_RATE -> quantity.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
    val computedContractValue = CalculationEngine.vendorContractValue(rateType, finalRateValue, finalQuantityValue, projectValue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add vendor" else "Edit vendor") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Vendor name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(contact, { contact = it }, label = { Text("Contact") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { wcMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Category: ${workCategory.name}") }
                    DropdownMenu(expanded = wcMenu, onDismissRequest = { wcMenu = false }) {
                        VendorWorkCategory.values().forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { workCategory = c; wcMenu = false }) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Work basis", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = rateType == VendorRateType.PER_SQFT, onClick = { rateType = VendorRateType.PER_SQFT }, label = { Text("Rate work (plinth area)") })
                    FilterChip(selected = rateType == VendorRateType.LUMP_SUM, onClick = { rateType = VendorRateType.LUMP_SUM }, label = { Text("Lumpsum") })
                }
                Spacer(Modifier.height(4.dp))
                Box {
                    OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { rtMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Other basis: ${rateType.name.replace("_", " ")}") }
                    DropdownMenu(expanded = rtMenu, onDismissRequest = { rtMenu = false }) {
                        VendorRateType.values().forEach { r -> DropdownMenuItem(text = { Text(r.name.replace("_", " ")) }, onClick = { rateType = r; rtMenu = false }) }
                    }
                }
                Spacer(Modifier.height(8.dp))

                when (rateType) {
                    VendorRateType.PER_SQFT -> {
                        OutlinedTextField(rate, { rate = it }, label = { Text("Rate (₹/sqft)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(quantity, { quantity = it }, label = { Text("Area (sqft)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    }
                    VendorRateType.LUMP_SUM -> {
                        OutlinedTextField(lumpSum, { lumpSum = it }, label = { Text("Lump sum amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    }
                    VendorRateType.QUANTITY_X_RATE -> {
                        OutlinedTextField(rate, { rate = it }, label = { Text("Rate per unit") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    }
                    VendorRateType.PERCENTAGE -> {
                        OutlinedTextField(rate, { rate = it }, label = { Text("Percentage of project value (%)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        Text("Project value: ${formatCurrency(projectValue)}", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
                    }
                    VendorRateType.CUSTOM -> {
                        OutlinedTextField(rate, { rate = it }, label = { Text("Contract amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Contract value: ${formatCurrency(computedContractValue)}", fontWeight = FontWeight.Bold)

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(terms, { terms = it }, label = { Text("Payment terms") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onSubmit(VendorInput(name.trim(), contact, workCategory, rateType, finalRateValue, finalQuantityValue, terms, notes))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
