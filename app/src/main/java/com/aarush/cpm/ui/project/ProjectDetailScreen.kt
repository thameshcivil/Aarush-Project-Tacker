package com.aarush.cpm.ui.project

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.entity.MaterialUsage
import com.aarush.cpm.data.entity.ProjectAreaComponent
import com.aarush.cpm.data.repository.*
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.ui.common.formatCurrency
import com.aarush.cpm.ui.common.formatPercent
import com.aarush.cpm.ui.common.formatQuantity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class ProjectDetailUiState(
    val summary: ProjectSummary? = null,
    val materialStatus: List<MaterialStatusRow> = emptyList(),
    val areaComponents: List<ProjectAreaComponent> = emptyList(),
    val daysRemaining: Long? = null,
    val isLoading: Boolean = true
)

class ProjectDetailViewModel(
    private val projectRepository: ProjectRepository,
    private val summaryRepository: ProjectSummaryRepository,
    private val expenseRepository: ExpenseRepository,
    private val materialRepository: MaterialRepository,
    private val scheduleRepository: ScheduleRepository,
    private val clientPaymentRepository: ClientPaymentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectDetailUiState())
    val uiState: StateFlow<ProjectDetailUiState> = _uiState.asStateFlow()
    private var currentProjectId: Long = 0L

    fun load(projectId: Long) {
        currentProjectId = projectId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            refresh()
        }
    }

    private suspend fun refresh() {
        val summary = summaryRepository.buildSummary(currentProjectId)
        val materials = materialRepository.materialStatus(currentProjectId)
        val areaComponents = projectRepository.getAreaComponents(currentProjectId)
        val daysRemaining = summary?.project?.expectedCompletionDate?.let {
            TimeUnit.MILLISECONDS.toDays(it - System.currentTimeMillis())
        }
        _uiState.value = ProjectDetailUiState(
            summary = summary, materialStatus = materials, areaComponents = areaComponents,
            daysRemaining = daysRemaining, isLoading = false
        )
    }

    fun addAreaComponent(label: String, area: Double, rate: Double) {
        viewModelScope.launch {
            projectRepository.addAreaComponent(currentProjectId, label, area, rate)
            refresh()
        }
    }

    fun updateAreaComponent(component: ProjectAreaComponent, label: String, area: Double, rate: Double) {
        viewModelScope.launch {
            projectRepository.updateAreaComponent(component.copy(label = label, areaSqft = area, ratePerSqft = rate))
            refresh()
        }
    }

    fun deleteAreaComponent(component: ProjectAreaComponent) {
        viewModelScope.launch {
            projectRepository.deleteAreaComponent(component)
            refresh()
        }
    }
}

private sealed class ProjectTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object BOQ : ProjectTab("BOQ", Icons.Filled.ListAlt)
    data object Expenses : ProjectTab("Expenses", Icons.Filled.Receipt)
    data object Vendors : ProjectTab("Vendors", Icons.Filled.Engineering)
    data object Payments : ProjectTab("Payments", Icons.Filled.Payments)
    data object Settings : ProjectTab("Settings", Icons.Filled.Tune)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: Long,
    viewModel: ProjectDetailViewModel,
    onBack: () -> Unit,
    onOpenBOQ: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenVendors: () -> Unit,
    onOpenClientPayments: () -> Unit,
    onOpenSettings: () -> Unit
) {
    LaunchedEffect(projectId) { viewModel.load(projectId) }
    val state by viewModel.uiState.collectAsState()
    var showAreaDialog by remember { mutableStateOf(false) }

    val tabs = listOf(ProjectTab.BOQ, ProjectTab.Expenses, ProjectTab.Vendors, ProjectTab.Payments, ProjectTab.Settings)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.summary?.project?.name ?: "Project") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = false,
                        onClick = {
                            when (tab) {
                                ProjectTab.BOQ -> onOpenBOQ()
                                ProjectTab.Expenses -> onOpenExpenses()
                                ProjectTab.Vendors -> onOpenVendors()
                                ProjectTab.Payments -> onOpenClientPayments()
                                ProjectTab.Settings -> onOpenSettings()
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        if (state.isLoading || state.summary == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val summary = state.summary!!
        val p = summary.project

        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(onClick = { showAreaDialog = true }) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Client: ${p.clientName}", style = MaterialTheme.typography.bodyMedium)
                        Text("Location: ${p.location}", style = MaterialTheme.typography.bodyMedium)
                        Text("Area: ${formatQuantity(p.plinthAreaSqft, "sqft")}  •  Rate: ${formatCurrency(p.ratePerSqft)}/sqft", style = MaterialTheme.typography.bodyMedium)
                        Text("Project Value: ${formatCurrency(p.projectValue)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap to view / edit area & rate breakdown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                LazyRowStats(summary = summary, daysRemaining = state.daysRemaining)
            }
            item { Text("Budget vs Actual", style = MaterialTheme.typography.titleSmall) }
            items(summary.categories) { cat ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cat.allocation.category.name.replace("_", " "), fontWeight = FontWeight.SemiBold)
                            Text(formatPercent(cat.allocation.percentOfProjectValue))
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = if (cat.budget > 0) (cat.actual / cat.budget).toFloat().coerceIn(0f, 1f) else 0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Budget ${formatCurrency(cat.budget)}  •  Actual ${formatCurrency(cat.actual)}  •  Remaining ${formatCurrency(cat.remaining)}", style = MaterialTheme.typography.bodySmall)
                        if (cat.variance < 0) {
                            Text("⚠ Over budget by ${formatCurrency(-cat.variance)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { Text("Material Status", style = MaterialTheme.typography.titleSmall) }
            if (state.materialStatus.isEmpty()) {
                item { Text("No materials tracked yet — add BOQ items with a material type to see requirements.", style = MaterialTheme.typography.bodySmall) }
            }
            items(state.materialStatus) { m ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.materialName, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Required ${formatQuantity(m.requiredQuantity, m.unit)}  •  Purchased ${formatQuantity(m.purchasedQuantity, m.unit)}  •  Stock ${formatQuantity(m.currentStock, m.unit)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (m.balanceToPurchase > 0) {
                            Text(
                                "⚠ Balance to purchase: ${formatQuantity(m.balanceToPurchase, m.unit)} (~${formatCurrency(m.estimatedRemainingCost)})",
                                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showAreaDialog) {
        AreaRateBreakdownDialog(
            components = state.areaComponents,
            onDismiss = { showAreaDialog = false },
            onAdd = { label, area, rate -> viewModel.addAreaComponent(label, area, rate) },
            onUpdate = { comp, label, area, rate -> viewModel.updateAreaComponent(comp, label, area, rate) },
            onDelete = { comp -> viewModel.deleteAreaComponent(comp) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AreaRateBreakdownDialog(
    components: List<ProjectAreaComponent>,
    onDismiss: () -> Unit,
    onAdd: (String, Double, Double) -> Unit,
    onUpdate: (ProjectAreaComponent, String, Double, Double) -> Unit,
    onDelete: (ProjectAreaComponent) -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("") }
    var newArea by remember { mutableStateOf("") }
    var newRate by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Area & Rate Breakdown") },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = false)) {
                    items(components, key = { it.id }) { comp ->
                        AreaRateEditableRow(component = comp, onUpdate = onUpdate, onDelete = onDelete)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (!showAddForm) {
                    OutlinedButton(onClick = { showAddForm = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add item")
                    }
                } else {
                    Text("New item", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(newLabel, { newLabel = it }, label = { Text("Label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Row {
                        OutlinedTextField(newArea, { newArea = it }, label = { Text("Area (sqft)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(newRate, { newRate = it }, label = { Text("Rate (₹/sqft)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showAddForm = false; newLabel = ""; newArea = ""; newRate = "" }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(
                            onClick = {
                                val area = newArea.toDoubleOrNull() ?: 0.0
                                val rate = newRate.toDoubleOrNull() ?: 0.0
                                if (area > 0 && rate > 0) {
                                    onAdd(newLabel.ifBlank { "Area" }, area, rate)
                                    newLabel = ""; newArea = ""; newRate = ""
                                    showAddForm = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Add") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun AreaRateEditableRow(
    component: ProjectAreaComponent,
    onUpdate: (ProjectAreaComponent, String, Double, Double) -> Unit,
    onDelete: (ProjectAreaComponent) -> Unit
) {
    var label by remember(component.id) { mutableStateOf(component.label) }
    var area by remember(component.id) { mutableStateOf(if (component.areaSqft == 0.0) "" else "%.2f".format(component.areaSqft)) }
    var rate by remember(component.id) { mutableStateOf(if (component.ratePerSqft == 0.0) "" else "%.2f".format(component.ratePerSqft)) }

    Card {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    label, { label = it }, label = { Text("Label") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onDelete(component) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
            Spacer(Modifier.height(4.dp))
            Row {
                OutlinedTextField(
                    area, { area = it }, label = { Text("Area (sqft)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    rate, { rate = it }, label = { Text("Rate (₹/sqft)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val a = area.toDoubleOrNull() ?: 0.0
                val r = rate.toDoubleOrNull() ?: 0.0
                Text("Subtotal: ${formatCurrency(CalculationEngine.projectValue(a, r))}", style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { onUpdate(component, label, a, r) }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun LazyRowStats(summary: ProjectSummary, daysRemaining: Long?) {
    val stats = listOf(
        "Received" to formatCurrency(summary.totalReceived),
        "Spent" to formatCurrency(summary.totalSpent),
        "Balance" to formatCurrency(summary.cashBalance),
        "Profit" to formatCurrency(summary.profit),
        "Profit %" to formatPercent(summary.profitPercent),
        "Progress" to formatPercent(summary.progressPercent),
        "Days left" to (daysRemaining?.toString() ?: "-")
    )
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(stats) { (label, value) ->
            ElevatedCard {
                Column(Modifier.padding(12.dp).widthIn(min = 96.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                    Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
