package com.aarush.cpm.ui.vendor

import androidx.compose.foundation.clickable

import com.aarush.cpm.ui.common.formatQuantity

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
import com.aarush.cpm.data.entity.VendorWork
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

/** Extra work entered in the dialog. [amount] = running feet × rate, or the lump sum. */
data class WorkInput(val label: String, val isLumpSum: Boolean, val quantity: Double, val rate: Double, val amount: Double)

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

    /** Extra work on vendors' contracts (e.g. a compound wall), for the cards. */
    val works: StateFlow<List<VendorWork>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else vendorRepository.observeWorks(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun init(projectId: Long) {
        projectIdFlow.value = projectId
    }

    fun addWork(vendor: Vendor, input: WorkInput) {
        viewModelScope.launch { vendorRepository.addWork(vendor.id, input.label, input.isLumpSum, input.quantity, input.rate, input.amount) }
    }

    fun updateWork(original: VendorWork, input: WorkInput) {
        viewModelScope.launch {
            vendorRepository.updateWork(original, original.copy(label = input.label, isLumpSum = input.isLumpSum,
                quantity = if (input.isLumpSum) 0.0 else input.quantity, rate = if (input.isLumpSum) 0.0 else input.rate, amount = input.amount))
        }
    }

    fun deleteWork(work: VendorWork) { viewModelScope.launch { vendorRepository.deleteWork(work) } }
    fun restoreWork(work: VendorWork) { viewModelScope.launch { vendorRepository.restoreWork(work) } }

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
            // Base contract recalculated from the edited terms, plus any extra work already
            // added (e.g. a compound wall), so editing the vendor never drops that work.
            val newContractValue = CalculationEngine.vendorContractValue(input.rateType, input.rate, input.quantity, project?.projectValue ?: 0.0) +
                vendorRepository.worksTotal(original.id)
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
    val works by viewModel.works.collectAsState()
    var addingWorkFor by remember { mutableStateOf<Vendor?>(null) }
    var editingWork by remember { mutableStateOf<VendorWork?>(null) }
    var pendingDeleteWork by remember { mutableStateOf<VendorWork?>(null) }
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
                    VendorCard(
                        vendor = v, payments = payments, works = works.filter { it.vendorId == v.id },
                        onEdit = { editingVendor = v }, onDelete = { pendingDelete = v },
                        onAddWork = { addingWorkFor = v }, onEditWork = { editingWork = it }
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }

        if (com.aarush.cpm.ui.common.LocalProjectAccess.current.editVendors) FloatingActionButton(
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
    addingWorkFor?.let { vendor ->
        VendorWorkDialog(vendorName = vendor.name, existing = null, onDismiss = { addingWorkFor = null },
            onSave = { input -> viewModel.addWork(vendor, input); addingWorkFor = null }, onDelete = null)
    }
    editingWork?.let { work ->
        VendorWorkDialog(vendorName = vendors.firstOrNull { it.id == work.vendorId }?.name ?: "", existing = work,
            onDismiss = { editingWork = null },
            onSave = { input -> viewModel.updateWork(work, input); editingWork = null },
            onDelete = { editingWork = null; pendingDeleteWork = work })
    }
    pendingDeleteWork?.let { work ->
        ConfirmDeleteDialog(
            itemLabel = "\"${work.label}\" (${formatCurrency(work.amount)} comes off the contract)",
            onConfirm = {
                pendingDeleteWork = null
                viewModel.deleteWork(work)
                scope.showUndoDeleteSnackbar(snackbarHostState, "Work removed") { viewModel.restoreWork(work) }
            },
            onDismiss = { pendingDeleteWork = null }
        )
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
internal fun VendorCard(
    vendor: Vendor, payments: List<Expense>, works: List<VendorWork>,
    onEdit: () -> Unit, onDelete: () -> Unit, onAddWork: () -> Unit, onEditWork: (VendorWork) -> Unit
) {
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

            // Extra work on this contract (e.g. a compound wall); included in "Contract" above.
            val canEdit = com.aarush.cpm.ui.common.LocalProjectAccess.current.editVendors
            if (works.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Extra work (included in contract)")
                works.forEach { w ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = canEdit, onClickLabel = "Edit work") { onEditWork(w) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(w.label, style = MaterialTheme.typography.bodyMedium)
                            Text(if (w.isLumpSum) "Lump sum" else "${formatQuantity(w.quantity, "rft")} × ${formatCurrency(w.rate)}/rft",
                                style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
                        }
                        Text(formatCurrency(w.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (canEdit) TextButton(onClick = onAddWork, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add work (e.g. compound wall)")
            }

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
                if (canEdit) {
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

/**
 * Add / edit extra work on a vendor's contract. Running feet × rate or a lump sum; the amount
 * is added to the vendor's contract value. Label defaults to "Compound wall".
 */
@Composable
private fun VendorWorkDialog(
    vendorName: String,
    existing: VendorWork?,
    onDismiss: () -> Unit,
    onSave: (WorkInput) -> Unit,
    onDelete: (() -> Unit)?
) {
    var label by remember { mutableStateOf(existing?.label ?: "Compound wall") }
    var lumpSum by remember { mutableStateOf(existing?.isLumpSum ?: false) }
    var rft by remember { mutableStateOf(existing?.quantity?.takeIf { it > 0 }?.let { formatQuantity(it, "") } ?: "") }
    var rate by remember { mutableStateOf(existing?.rate?.takeIf { it > 0 }?.let { formatQuantity(it, "") } ?: "") }
    var amountText by remember { mutableStateOf(existing?.takeIf { it.isLumpSum }?.amount?.let { formatQuantity(it, "") } ?: "") }
    val q = rft.toDoubleOrNull() ?: 0.0
    val r = rate.toDoubleOrNull() ?: 0.0
    val amount = if (lumpSum) amountText.toDoubleOrNull() ?: 0.0 else q * r
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add work" else "Edit work") },
        text = {
            Column {
                if (vendorName.isNotBlank()) Text("For $vendorName", style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(label, { label = it }, label = { Text("Work") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !lumpSum, onClick = { lumpSum = false }, label = { Text("Running ft × rate") })
                    FilterChip(selected = lumpSum, onClick = { lumpSum = true }, label = { Text("Lump sum") })
                }
                Spacer(Modifier.height(8.dp))
                if (lumpSum) {
                    OutlinedTextField(amountText, { amountText = it }, label = { Text("Amount (₹)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                } else {
                    Row {
                        OutlinedTextField(rft, { rft = it }, label = { Text("Length (rft)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(rate, { rate = it }, label = { Text("Rate (₹/rft)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Adds ${formatCurrency(amount)} to the contract", fontWeight = FontWeight.SemiBold)
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Delete this work", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(WorkInput(label.trim().ifBlank { "Compound wall" }, lumpSum, q, r, amount)) }, enabled = amount > 0) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
