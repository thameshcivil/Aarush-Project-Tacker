package com.aarush.cpm.ui.project

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

    fun load(projectId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val summary = summaryRepository.buildSummary(projectId)
            val materials = materialRepository.materialStatus(projectId)
            val areaComponents = projectRepository.getAreaComponents(projectId)
            val daysRemaining = summary?.project?.expectedCompletionDate?.let {
                TimeUnit.MILLISECONDS.toDays(it - System.currentTimeMillis())
            }
            _uiState.value = ProjectDetailUiState(
                summary = summary, materialStatus = materials, areaComponents = areaComponents,
                daysRemaining = daysRemaining, isLoading = false
            )
        }
    }
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
    onOpenClientPayments: () -> Unit
) {
    LaunchedEffect(projectId) { viewModel.load(projectId) }
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(state.summary?.project?.name ?: "Project") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
        )
    }) { padding ->
        if (state.isLoading || state.summary == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val summary = state.summary!!
        val p = summary.project

        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Client: ${p.clientName}", style = MaterialTheme.typography.bodyMedium)
                        Text("Location: ${p.location}", style = MaterialTheme.typography.bodyMedium)
                        Text("Area: ${formatQuantity(p.plinthAreaSqft, "sqft")}  •  Rate: ${formatCurrency(p.ratePerSqft)}/sqft", style = MaterialTheme.typography.bodyMedium)
                        Text("Project Value: ${formatCurrency(p.projectValue)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                LazyRowStats(summary = summary, daysRemaining = state.daysRemaining)
            }
            if (state.areaComponents.size > 1) {
                item { Text("Area & Rate Breakdown", style = MaterialTheme.typography.titleSmall) }
                items(state.areaComponents) { comp ->
                    Card {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(comp.label, fontWeight = FontWeight.SemiBold)
                            Text("${formatQuantity(comp.areaSqft, "sqft")} @ ${formatCurrency(comp.ratePerSqft)} = ${formatCurrency(comp.areaSqft * comp.ratePerSqft)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { Text("Quick actions", style = MaterialTheme.typography.titleSmall) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenBOQ) { Text("BOQ") }
                    OutlinedButton(onClick = onOpenExpenses) { Text("Expenses") }
                    OutlinedButton(onClick = onOpenVendors) { Text("Vendors") }
                    OutlinedButton(onClick = onOpenClientPayments) { Text("Payments") }
                }
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
            item { Spacer(Modifier.height(48.dp)) }
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
