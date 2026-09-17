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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.repository.MaterialRepository
import com.aarush.cpm.data.repository.MaterialStatusRow
import com.aarush.cpm.ui.common.formatCurrency
import com.aarush.cpm.ui.common.formatDate
import com.aarush.cpm.ui.common.formatQuantity
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
            _isLoading.value = true
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

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No materials tracked yet — add BOQ items linked to a notation with coefficients to see this.")
        }
        return
    }

    var detailRow by remember { mutableStateOf<MaterialStatusRow?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rows, key = { it.materialName }) { row ->
            MaterialStatusCard(row, onClick = { detailRow = row })
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    detailRow?.let { row -> PurchaseHistoryDialog(row = row, onDismiss = { detailRow = null }) }
}

/** Tapping a material opens its purchase history — every dated buy with quantity and spend,
 *  so the single "Expensed amount" figure on the card can be traced back to what made it up. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseHistoryDialog(row: MaterialStatusRow, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(row.materialName, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                if (row.purchaseHistory.isEmpty()) {
                    Text("No purchases recorded yet for this material.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Date", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("Qty", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("Spend", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(row.purchaseHistory.size) { index ->
                            val entry = row.purchaseHistory[index]
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatDate(entry.date), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (row.isVendorLinked) "-" else formatQuantity(entry.quantity, row.unit),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(formatCurrency(entry.amount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(formatCurrency(row.expensedAmount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialStatusCard(row: MaterialStatusRow, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(Modifier.padding(12.dp)) {
            Text(row.materialName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (row.isVendorLinked) {
                // A labour-cost line has no meaningful quantity/stock — just budget vs spend.
                StatRow("Required budget", formatCurrency(row.requiredBudget))
                StatRow("Expensed amount", formatCurrency(row.expensedAmount))
                StatRow(
                    "Balance holding amount", formatCurrency(row.balanceHoldingAmount),
                    color = if (row.balanceHoldingAmount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            } else {
                StatRow("Required qty", formatQuantity(row.requiredQuantity, row.unit))
                StatRow("Required budget", formatCurrency(row.requiredBudget))
                StatRow("Purchased qty", formatQuantity(row.purchasedQuantity, row.unit))
                StatRow("Remaining qty", formatQuantity(row.balanceToPurchase, row.unit))
                StatRow("Stock", formatQuantity(row.currentStock, row.unit))
                StatRow("Expensed amount", formatCurrency(row.expensedAmount))
                StatRow(
                    "Balance holding amount", formatCurrency(row.balanceHoldingAmount),
                    color = if (row.balanceHoldingAmount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = color)
    }
}
