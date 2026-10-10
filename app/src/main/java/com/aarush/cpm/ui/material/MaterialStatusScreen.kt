package com.aarush.cpm.ui.material

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.repository.MaterialRepository
import com.aarush.cpm.data.repository.MaterialStatusRow
import com.aarush.cpm.ui.common.*
import com.aarush.cpm.ui.theme.SiteTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MaterialStatusViewModel(private val materialRepository: MaterialRepository) : ViewModel() {
    private val _rows = MutableStateFlow<List<MaterialStatusRow>>(emptyList())
    val rows: StateFlow<List<MaterialStatusRow>> = _rows.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun load(projectId: Long) {
        viewModelScope.launch {
            _isLoading.value = _rows.value.isEmpty()
            _rows.value = materialRepository.materialStatus(projectId)
            _isLoading.value = false
        }
    }
}

@Composable
fun MaterialStatusTabContent(projectId: Long, viewModel: MaterialStatusViewModel) {
    LaunchedEffect(projectId) { viewModel.load(projectId) }
    val rows by viewModel.rows.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var detailRow by remember { mutableStateOf<MaterialStatusRow?>(null) }

    when {
        isLoading -> SkeletonScreen(headline = false, sections = 3, rows = 3)
        rows.isEmpty() -> EmptyState(
            "No materials tracked yet",
            "Add BOQ items linked to a notation with coefficients, or log purchases in Expenses, to see requirements, stock and spend here."
        )
        else -> {
            val tracked = rows.filter { it.isStockTracked && !it.isVendorLinked }
            val vendors = rows.filter { it.isVendorLinked }
            val others = rows.filter { !it.isStockTracked && !it.isVendorLinked }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tracked.isNotEmpty()) {
                    item { SectionLabel("Stock materials · from BOQ") }
                    items(tracked, key = { "t-" + it.materialName }) { row -> MaterialStatusCard(row, onClick = { detailRow = row }) }
                }
                if (vendors.isNotEmpty()) {
                    item { SectionLabel("Labour contracts", modifier = Modifier.padding(top = 8.dp)) }
                    items(vendors, key = { "v-" + it.materialName }) { row -> MaterialStatusCard(row, onClick = { detailRow = row }) }
                }
                if (others.isNotEmpty()) {
                    item { SectionLabel("Other purchases", modifier = Modifier.padding(top = 8.dp)) }
                    items(others, key = { "o-" + it.materialName }) { row -> MaterialStatusCard(row, onClick = { detailRow = row }) }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    detailRow?.let { row -> PurchaseHistoryDialog(row = row, onDismiss = { detailRow = null }) }
}

/** Tapping a material opens its purchase history — every dated buy with quantity and spend. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseHistoryDialog(row: MaterialStatusRow, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(row.materialName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                if (row.purchaseHistory.isEmpty()) {
                    Text(if (row.isVendorLinked) "No payments recorded yet." else "No purchases recorded yet for this material.",
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        ColumnLabel("Date", modifier = Modifier.weight(1f))
                        ColumnLabel("Qty", modifier = Modifier.weight(1f))
                        ColumnLabel(if (row.isVendorLinked) "Paid" else "Spend")
                    }
                    HorizontalDivider(color = SiteTheme.colors.hairline)
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(row.purchaseHistory.size) { index ->
                            val entry = row.purchaseHistory[index]
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Text(formatDate(entry.date), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(if (row.isVendorLinked) NO_VALUE else formatQuantity(entry.quantity, row.unit),
                                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(formatCurrency(entry.amount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    HorizontalDivider(color = SiteTheme.colors.hairline)
                    MetricRow("Total", formatCurrency(row.expensedAmount), emphasize = true)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun MaterialStatusCard(row: MaterialStatusRow, onClick: () -> Unit) {
    val balanceColor = if (row.balanceHoldingAmount < 0) SiteTheme.colors.bad else SiteTheme.colors.good
    AppCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.materialName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (row.isStockTracked && row.currentStock < 0) StatusPill("Stock < 0", SiteTheme.colors.bad, SiteTheme.colors.badTint)
            }
            Spacer(Modifier.height(8.dp))
            when {
                row.isVendorLinked -> {
                    ProgressRail(progress = if (row.requiredBudget > 0) (row.expensedAmount / row.requiredBudget).toFloat() else 0f,
                        color = if (row.balanceHoldingAmount < 0) SiteTheme.colors.bad else SiteTheme.colors.accent)
                    Spacer(Modifier.height(6.dp))
                    MetricRow("Contract value", formatCurrency(row.requiredBudget))
                    MetricRow("Paid", formatCurrency(row.expensedAmount))
                    MetricRow(if (row.balanceHoldingAmount < 0) "Overpaid" else "Balance to pay",
                        formatCurrency(kotlin.math.abs(row.balanceHoldingAmount)), valueColor = balanceColor)
                }
                row.isStockTracked -> {
                    // Purchased vs required, with the used share shown by the stock figure.
                    ProgressRail(progress = if (row.requiredQuantity > 0) (row.purchasedQuantity / row.requiredQuantity).toFloat() else 0f)
                    Spacer(Modifier.height(6.dp))
                    MetricRow("Required (BOQ + waste)", formatQuantity(row.requiredQuantity, row.unit))
                    MetricRow("Purchased", formatQuantity(row.purchasedQuantity, row.unit))
                    MetricRow("Used on site (from progress)", formatQuantity(row.usedQuantity, row.unit))
                    MetricRow("In stock", formatQuantity(row.currentStock, row.unit),
                        valueColor = if (row.currentStock < 0) SiteTheme.colors.bad else androidx.compose.ui.graphics.Color.Unspecified)
                    MetricRow("Still to buy", formatQuantity(row.balanceToPurchase, row.unit), divider = true)
                    MetricRow("Required budget", formatCurrency(row.requiredBudget))
                    MetricRow("Spent", formatCurrency(row.expensedAmount))
                    MetricRow("Est. cost to finish buying", formatCurrency(row.estimatedRemainingCost))
                    MetricRow(if (row.balanceHoldingAmount < 0) "Overspent" else "Budget left",
                        formatCurrency(kotlin.math.abs(row.balanceHoldingAmount)), valueColor = balanceColor, emphasize = true)
                }
                else -> {
                    MetricRow("Purchased", formatQuantity(row.purchasedQuantity, row.unit))
                    MetricRow("Spent", formatCurrency(row.expensedAmount), emphasize = true)
                    Text("Not measured in the BOQ. Budgeted under its cost category.", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
                }
            }
        }
    }
}
