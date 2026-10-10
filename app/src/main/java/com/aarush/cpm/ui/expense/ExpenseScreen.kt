package com.aarush.cpm.ui.expense

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
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
import com.aarush.cpm.data.repository.VendorRepository
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.domain.calculation.CategoryRules
import com.aarush.cpm.ui.common.AppCard
import com.aarush.cpm.ui.common.EmptyState
import com.aarush.cpm.ui.common.MetricRow
import com.aarush.cpm.ui.common.SectionLabel
import com.aarush.cpm.ui.common.formatCurrencyShort
import com.aarush.cpm.ui.common.localDayOf
import com.aarush.cpm.ui.common.pickerMillisFromStored
import com.aarush.cpm.ui.common.storedMillisFromPicker
import com.aarush.cpm.ui.theme.SiteTheme
import com.aarush.cpm.ui.common.ConfirmDeleteDialog
import com.aarush.cpm.ui.common.formatCurrency
import com.aarush.cpm.ui.common.formatDate
import com.aarush.cpm.ui.common.showUndoDeleteSnackbar
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
    val freightAmount: Double? = null,
    /** Steel only: the per-diameter breakdown that was summed into quantity/rate above.
     *  Stored in the expense's notes so the detail view can show what made up the total. */
    val rodBreakdown: String = "",
    /** null = infer automatically (BOQ trace → name keywords → Other/Misc). */
    val categoryOverride: CostCategory? = null
)

private const val RODS_PREFIX = "Rods: "
private val rodRegex = Regex("""(\d+mm):\s*([\d.]+)\s*@\s*([\d.]+)""")

/** Notes are stored as "description | Rods: 8mm: 120 @ 70, …". Older entries stored the
 *  breakdown without the prefix — both are recognised. */
internal fun splitExpenseNotes(notes: String): Pair<String, String> {
    val parts = notes.split(" | ")
    val rods = parts.firstOrNull { it.startsWith(RODS_PREFIX) || rodRegex.containsMatchIn(it) }
    val desc = parts.filter { it != rods }.joinToString(" | ")
    return desc to (rods?.removePrefix(RODS_PREFIX) ?: "")
}

internal fun parseRodBreakdown(breakdown: String): Map<String, Pair<String, String>> =
    rodRegex.findAll(breakdown).associate { m -> m.groupValues[1] to (m.groupValues[2] to m.groupValues[3]) }

/** Steel is bought by rod diameter, each at its own rate — so the Expense form offers a row
 *  per size instead of a single qty/rate pair. */
val STEEL_ROD_SIZES = listOf("6mm", "8mm", "10mm", "12mm", "16mm", "20mm", "25mm", "32mm")

/** Materials whose name marks them as steel, triggering the per-diameter entry form. */
fun isSteelMaterial(name: String): Boolean = name.equals("Steel", ignoreCase = true)

/** Fields for a "Received" entry: money in, logged simply — Date, Mode of payment, Amount,
 *  and an optional description (e.g. "Client advance", "Milestone 2"). */
data class ReceivedInput(
    val date: Long,
    val paymentMode: PaymentMode,
    val amount: Double,
    val description: String
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ExpenseViewModel(
    private val expenseRepository: ExpenseRepository,
    private val materialRepository: MaterialRepository,
    private val vendorRepository: VendorRepository
) : ViewModel() {
    private val projectIdFlow = MutableStateFlow<Long?>(null)

    val expenses: StateFlow<List<Expense>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else expenseRepository.observeForProject(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val materialRates: StateFlow<List<MaterialRateCard>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else materialRepository.observeMaterialRates(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val vendors: StateFlow<List<Vendor>> = projectIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else vendorRepository.observeForProject(id) }
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

    /** Category for an expense: explicit choice if given; a vendor payment takes the vendor's
     *  work category (Civil vendor → Civil/Structural); otherwise inferred from the material
     *  (BOQ trace → name keywords → Other/Misc). */
    private suspend fun resolveCategory(projectId: Long, materialName: String, type: ExpenseType, vendorId: Long?, override: CostCategory?): CostCategory {
        override?.let { return it }
        if (type == ExpenseType.VENDOR && vendorId != null) {
            val vendor = vendors.value.find { it.id == vendorId } ?: vendorRepository.getForProject(projectIdFlow.value ?: 0).find { it.id == vendorId }
            if (vendor != null) return CategoryRules.categoryForVendor(vendor.workCategory)
        }
        return materialRepository.resolveCategory(projectId, materialName)
    }

    private fun composeNotes(input: MaterialExpenseInput) =
        listOf(input.description, input.rodBreakdown.takeIf { it.isNotBlank() }?.let { RODS_PREFIX + it } ?: "")
            .filter { it.isNotBlank() }.joinToString(" | ")

    fun addMaterialExpense(input: MaterialExpenseInput) {
        val currentProjectId = projectIdFlow.value ?: return
        val (type, vendorId) = resolveVendorLink(input.materialName)
        viewModelScope.launch {
            val category = resolveCategory(currentProjectId, input.materialName, type, vendorId, input.categoryOverride)
            // Freight & unloading is folded INTO this material's total, not split into its own
            // line: what you actually spent on cement is the goods cost plus the cost of
            // getting it to site, and that whole figure is what should come off the material's
            // budget. Quantity/rate stay as the goods figures so stock tracking (bags bought)
            // and average-rate reporting remain truthful — only totalAmount carries the freight.
            val freight = input.freightAmount?.takeIf { it > 0 } ?: 0.0
            val goodsTotal = CalculationEngine.expenseTotal(input.quantity, input.rate)
            expenseRepository.addExpense(
                Expense(
                    projectId = currentProjectId, date = input.date, category = category, type = type,
                    direction = TransactionDirection.EXPENSE, vendorId = vendorId, itemOrMaterialName = input.materialName,
                    quantity = input.quantity, unit = input.unit, rate = input.rate,
                    totalAmount = goodsTotal + freight, freightAmount = freight, paymentMode = input.paymentMode,
                    paymentStatus = PaymentStatus.PAID,
                    notes = composeNotes(input)
                )
            )
        }
    }

    fun updateMaterialExpense(original: Expense, input: MaterialExpenseInput) {
        val currentProjectId = projectIdFlow.value ?: return
        val (type, vendorId) = resolveVendorLink(input.materialName)
        viewModelScope.launch {
            val category = resolveCategory(currentProjectId, input.materialName, type, vendorId, input.categoryOverride)
            val freight = input.freightAmount?.takeIf { it > 0 } ?: 0.0
            val goodsTotal = CalculationEngine.expenseTotal(input.quantity, input.rate)
            expenseRepository.updateExpense(
                original,
                original.copy(
                    date = input.date, type = type, category = category, vendorId = vendorId, itemOrMaterialName = input.materialName,
                    quantity = input.quantity, unit = input.unit, rate = input.rate,
                    totalAmount = goodsTotal + freight, freightAmount = freight,
                    paymentMode = input.paymentMode,
                    notes = composeNotes(input)
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

    /** Re-inserts a deleted expense for Undo — id dropped so Room assigns a fresh one. The
     *  repository's normal add path runs, so material-purchase and vendor-payable side
     *  effects are re-applied too, exactly as when it was first entered. */
    fun restoreExpense(expense: Expense) {
        viewModelScope.launch { expenseRepository.addExpense(expense.copy(id = 0)) }
    }
}

private enum class GroupMode { MATERIAL, DATE }

@Composable
private fun DateOrderToggle(ascending: Boolean, onToggle: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(selected = !ascending, onClick = { onToggle(false) }, label = { Text("Newest first") })
        FilterChip(selected = ascending, onClick = { onToggle(true) }, label = { Text("Oldest first") })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTabContent(projectId: Long, viewModel: ExpenseViewModel, snackbarHostState: SnackbarHostState) {
    LaunchedEffect(projectId) { viewModel.init(projectId) }
    val allExpenses by viewModel.expenses.collectAsState()
    val materialRates by viewModel.materialRates.collectAsState()
    var activeDirection by remember { mutableStateOf(TransactionDirection.EXPENSE) }
    var groupMode by remember { mutableStateOf(GroupMode.DATE) }
    var dateAscending by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var pendingDelete by remember { mutableStateOf<Expense?>(null) }
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    val filtered = allExpenses.filter { it.direction == activeDirection }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = activeDirection == TransactionDirection.EXPENSE, onClick = { activeDirection = TransactionDirection.EXPENSE }, label = { Text("Expense") })
                FilterChip(selected = activeDirection == TransactionDirection.RECEIVED, onClick = { activeDirection = TransactionDirection.RECEIVED }, label = { Text("Received") })
            }

            if (activeDirection == TransactionDirection.EXPENSE) {
                Row(Modifier.padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sort by:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = groupMode == GroupMode.MATERIAL, onClick = { groupMode = GroupMode.MATERIAL }, label = { Text("Material") })
                        FilterChip(selected = groupMode == GroupMode.DATE, onClick = { groupMode = GroupMode.DATE }, label = { Text("Date") })
                    }
                    if (groupMode == GroupMode.DATE) {
                        Spacer(Modifier.width(8.dp))
                        DateOrderToggle(ascending = dateAscending, onToggle = { dateAscending = it })
                    }
                }
                Spacer(Modifier.height(8.dp))
            } else {
                Row(Modifier.padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sort by date:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    DateOrderToggle(ascending = dateAscending, onToggle = { dateAscending = it })
                }
                Spacer(Modifier.height(8.dp))
            }

            if (filtered.isNotEmpty()) {
                AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(if (activeDirection == TransactionDirection.EXPENSE) "Total spent" else "Total received", modifier = Modifier.weight(1f))
                        com.aarush.cpm.ui.common.FitText(formatCurrency(filtered.sumOf { it.totalAmount }), style = com.aarush.cpm.ui.theme.FigureStyle)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (filtered.isEmpty()) {
                EmptyState(
                    if (activeDirection == TransactionDirection.EXPENSE) "No expenses yet" else "No receipts yet",
                    if (activeDirection == TransactionDirection.EXPENSE) "Tap + to log a purchase or a vendor payment. Stock, vendor balances and budgets update automatically."
                    else "Tap + to log money received from the client."
                )
            } else if (activeDirection == TransactionDirection.RECEIVED) {
                val sortedReceived = if (dateAscending) filtered.sortedBy { it.date } else filtered.sortedByDescending { it.date }
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sortedReceived, key = { it.id }) { e ->
                        ReceivedItemRow(expense = e, onEdit = { editingExpense = e }, onDelete = { pendingDelete = e })
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            } else {
                val grouped = when (groupMode) {
                    GroupMode.MATERIAL -> filtered.groupBy { it.itemOrMaterialName }.toSortedMap()
                    GroupMode.DATE -> filtered
                        .groupBy { localDayOf(it.date) } // the person's local calendar day (not UTC)
                        .toSortedMap(if (dateAscending) compareBy { it } else compareByDescending { it })
                        .mapKeys { (day, _) -> com.aarush.cpm.ui.common.formatDate(day) }
                }
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    grouped.forEach { (key, groupItems) ->
                        val isExpanded = expandedGroups.contains(key)
                        val groupTotal = groupItems.sumOf { it.totalAmount }
                        item(key = "header-$key") {
                            AppCard(
                                onClick = { expandedGroups = if (isExpanded) expandedGroups - key else expandedGroups + key },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(key, style = MaterialTheme.typography.titleSmall)
                                        SectionLabel("${groupItems.size} entr${if (groupItems.size == 1) "y" else "ies"} · ${formatCurrency(groupTotal)}")
                                    }
                                    Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (isExpanded) "Collapse" else "Expand")
                                }
                            }
                        }
                        if (isExpanded) {
                            val sortedGroupItems = if (dateAscending) groupItems.sortedBy { it.date } else groupItems.sortedByDescending { it.date }
                            items(sortedGroupItems, key = { it.id }) { e ->
                                ExpenseItemRow(expense = e, onEdit = { editingExpense = e }, onDelete = { pendingDelete = e })
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
    pendingDelete?.let { expense ->
        ConfirmDeleteDialog(
            itemLabel = "\"${expense.itemOrMaterialName}\"",
            onConfirm = {
                pendingDelete = null
                viewModel.deleteExpense(expense)
                scope.showUndoDeleteSnackbar(snackbarHostState, "Entry deleted") { viewModel.restoreExpense(expense) }
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseItemRow(expense: Expense, onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    AppCard(
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
                val (descText, rodsText) = splitExpenseNotes(expense.notes)
                Text("Description: ${descText.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                if (rodsText.isNotBlank()) Text("Rod sizes: $rodsText", style = MaterialTheme.typography.bodySmall)
                Text("Quantity: ${com.aarush.cpm.ui.common.formatQuantity(expense.quantity, expense.unit)}", style = MaterialTheme.typography.bodySmall)
                Text("Rate: ${formatCurrency(expense.rate)}", style = MaterialTheme.typography.bodySmall)
                if (expense.freightAmount > 0) {
                    Text(
                        "Includes freight & unloading: ${formatCurrency(expense.freightAmount)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
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

    AppCard(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(formatDate(expense.date), fontWeight = FontWeight.SemiBold)
                    com.aarush.cpm.ui.common.FitText(formatCurrency(expense.totalAmount), style = com.aarush.cpm.ui.theme.FigureStyle, color = SiteTheme.colors.good)
                    if (expense.itemOrMaterialName.isNotBlank()) Text(expense.itemOrMaterialName, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
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
    var dateMillis by remember { mutableStateOf(existing?.date ?: com.aarush.cpm.domain.schedule.WorkCalendar.toEpochMillis(java.time.LocalDate.now())) }
    var paymentMode by remember { mutableStateOf(existing?.paymentMode ?: PaymentMode.CASH) }
    var materialName by remember { mutableStateOf(existing?.itemOrMaterialName ?: "") }
    val existingNotes = remember(existing) { splitExpenseNotes(existing?.notes ?: "") }
    var description by remember { mutableStateOf(existingNotes.first) }
    var categoryOverride by remember { mutableStateOf<CostCategory?>(null) }
    var categoryMenu by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf(existing?.quantity?.toString() ?: "1") }
    var unit by remember { mutableStateOf(existing?.unit ?: "") }
    var rate by remember { mutableStateOf(existing?.rate?.toString() ?: "") }
    var vendorAmount by remember { mutableStateOf(existing?.totalAmount?.toString() ?: "") }
    // Editing a steel purchase restores its rod-wise rows from the saved breakdown (they used
    // to come back empty, so a steel expense could not be saved without re-typing everything).
    val savedRods = remember(existing) { parseRodBreakdown(existingNotes.second) }
    val rodQty = remember { mutableStateMapOf<String, String>().apply { savedRods.forEach { (k, v) -> put(k, v.first) } } }
    val rodRate = remember { mutableStateMapOf<String, String>().apply { savedRods.forEach { (k, v) -> put(k, v.second) } } }
    // Legacy steel entries without a breakdown are edited as a plain qty × rate line.
    var steelByRod by remember { mutableStateOf(existing == null || savedRods.isNotEmpty()) }
    var showFreightField by remember { mutableStateOf(existing != null && existing.freightAmount > 0) }
    var freightAmount by remember { mutableStateOf(existing?.freightAmount?.takeIf { it > 0 }?.toString() ?: "") }

    var materialMenu by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val selectedCard = materialRates.find { it.description == materialName }
    val isVendorLine = selectedCard?.linkedVendorId != null
    val isSteel = isSteelMaterial(materialName) && steelByRod

    val freightD = freightAmount.toDoubleOrNull() ?: 0.0
    // Steel totals roll up every rod size; a vendor line is a plain amount; everything else
    // is the usual qty x rate. Freight is added on top in all cases (it's part of what the
    // material actually cost, not a separate expense).
    val steelTotal = STEEL_ROD_SIZES.sumOf { size ->
        (rodQty[size]?.toDoubleOrNull() ?: 0.0) * (rodRate[size]?.toDoubleOrNull() ?: 0.0)
    }
    val steelQty = STEEL_ROD_SIZES.sumOf { rodQty[it]?.toDoubleOrNull() ?: 0.0 }
    val goodsTotal = when {
        isVendorLine -> vendorAmount.toDoubleOrNull() ?: 0.0
        isSteel -> steelTotal
        else -> CalculationEngine.expenseTotal(quantity.toDoubleOrNull() ?: 0.0, rate.toDoubleOrNull() ?: 0.0)
    }
    val grandTotal = goodsTotal + freightD

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
                if (!isVendorLine) {
                    Box {
                        OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Category: ${categoryOverride?.name?.replace('_', ' ') ?: "Auto" + (existing?.let { " (${it.category.name.replace('_', ' ')})" } ?: "")}")
                        }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            DropdownMenuItem(text = { Text("Auto — from BOQ / material name") }, onClick = { categoryOverride = null; categoryMenu = false })
                            CostCategory.values().forEach { c ->
                                DropdownMenuItem(text = { Text(c.name.replace('_', ' ')) }, onClick = { categoryOverride = c; categoryMenu = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (isSteelMaterial(materialName)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = steelByRod, onCheckedChange = { steelByRod = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Enter by rod diameter", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(6.dp))
                }

                when {
                    // A vendor payment is just "how much did I hand over" — no qty/rate/unit.
                    isVendorLine -> {
                        OutlinedTextField(
                            vendorAmount, { vendorAmount = it }, label = { Text("Amount (Rs.)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // Steel: a qty + rate pair per rod diameter.
                    isSteel -> {
                        Text("Rod-wise quantity & rate", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        STEEL_ROD_SIZES.forEach { size ->
                            val q = rodQty[size] ?: ""
                            val r = rodRate[size] ?: ""
                            val lineTotal = (q.toDoubleOrNull() ?: 0.0) * (r.toDoubleOrNull() ?: 0.0)
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(size, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(46.dp))
                                OutlinedTextField(
                                    q, { rodQty[size] = it }, label = { Text("Qty") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(4.dp))
                                OutlinedTextField(
                                    r, { rodRate[size] = it }, label = { Text("Rate") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
                                )
                            }
                            if (lineTotal > 0) {
                                Text(
                                    "  ${formatCurrency(lineTotal)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(start = 46.dp, bottom = 2.dp)
                                )
                            }
                        }
                    }
                    else -> {
                        OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(rate, { rate = it }, label = { Text("Rate (Rs.)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    }
                }

                if (!isVendorLine) {
                    Spacer(Modifier.height(10.dp))
                    if (!showFreightField) {
                        OutlinedButton(onClick = { showFreightField = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add Freight & Unloading Charges")
                        }
                    } else {
                        OutlinedTextField(
                            freightAmount, { freightAmount = it }, label = { Text("Freight & Unloading Charges (Rs.)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { showFreightField = false; freightAmount = "" }) { Text("Remove") }
                    }
                }

                Spacer(Modifier.height(10.dp))
                if (freightD > 0) {
                    Text("Goods: ${formatCurrency(goodsTotal)}  +  Freight: ${formatCurrency(freightD)}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Total amount: ${formatCurrency(grandTotal)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (materialName.isBlank() || grandTotal <= 0) return@TextButton
                val breakdown = if (isSteel) {
                    STEEL_ROD_SIZES.filter { (rodQty[it]?.toDoubleOrNull() ?: 0.0) > 0 }
                        .joinToString(", ") { "$it: ${rodQty[it]} @ ${rodRate[it] ?: "0"}" }
                } else ""
                val finalQty = when {
                    isVendorLine -> 1.0
                    isSteel -> steelQty
                    else -> quantity.toDoubleOrNull() ?: 0.0
                }
                val finalRate = when {
                    isVendorLine -> vendorAmount.toDoubleOrNull() ?: 0.0
                    isSteel -> if (steelQty > 0) steelTotal / steelQty else 0.0
                    else -> rate.toDoubleOrNull() ?: 0.0
                }
                val finalUnit = when {
                    isVendorLine -> "LS"
                    isSteel -> "kg"
                    else -> unit
                }
                onSubmit(
                    MaterialExpenseInput(
                        date = dateMillis, paymentMode = paymentMode, materialName = materialName,
                        description = description, quantity = finalQty, unit = finalUnit, rate = finalRate,
                        freightAmount = freightAmount.toDoubleOrNull(), rodBreakdown = breakdown,
                        categoryOverride = if (isVendorLine) null else categoryOverride ?: existing?.takeIf { it.itemOrMaterialName == materialName }?.category
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = pickerMillisFromStored(dateMillis))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMillis = storedMillisFromPicker(it) }
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
    var dateMillis by remember { mutableStateOf(existing?.date ?: com.aarush.cpm.domain.schedule.WorkCalendar.toEpochMillis(java.time.LocalDate.now())) }
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
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = pickerMillisFromStored(dateMillis))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMillis = storedMillisFromPicker(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
