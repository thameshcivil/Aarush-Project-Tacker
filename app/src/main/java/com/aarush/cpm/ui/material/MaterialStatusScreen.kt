package com.aarush.cpm.ui.material

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rows, key = { it.materialName }) { row -> MaterialStatusCard(row) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun MaterialStatusCard(row: MaterialStatusRow) {
    Card {
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
