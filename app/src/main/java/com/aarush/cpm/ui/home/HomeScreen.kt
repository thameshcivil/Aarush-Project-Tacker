package com.aarush.cpm.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.repository.BOQRepository
import com.aarush.cpm.data.repository.ClientPaymentRepository
import com.aarush.cpm.data.repository.ExpenseRepository
import com.aarush.cpm.data.repository.ProjectRepository
import com.aarush.cpm.data.repository.VendorRepository
import com.aarush.cpm.ui.common.formatCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HomeSummary(
    val boqItemCount: Int = 0,
    val boqTotalValue: Double = 0.0,
    val expenseCount: Int = 0,
    val expenseTotal: Double = 0.0,
    val vendorCount: Int = 0,
    val vendorPayable: Double = 0.0,
    val paymentCount: Int = 0,
    val totalReceived: Double = 0.0,
    val projectCount: Int = 0
)

class HomeViewModel(
    private val projectRepository: ProjectRepository,
    private val boqRepository: BOQRepository,
    private val expenseRepository: ExpenseRepository,
    private val vendorRepository: VendorRepository,
    private val clientPaymentRepository: ClientPaymentRepository
) : ViewModel() {
    private val _summary = MutableStateFlow(HomeSummary())
    val summary: StateFlow<HomeSummary> = _summary.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            val projects = projectRepository.observeAll().first()

            var boqCount = 0; var boqValue = 0.0
            var expCount = 0; var expTotal = 0.0
            var vendorCount = 0; var vendorPayable = 0.0
            var paymentCount = 0; var received = 0.0

            projects.forEach { project ->
                val boq = boqRepository.getForProject(project.id)
                boqCount += boq.size
                boqValue += boq.sumOf { it.amount }

                val expenses = expenseRepository.getForProject(project.id)
                expCount += expenses.size
                expTotal += expenses.sumOf { it.totalAmount }

                val vendors = vendorRepository.getForProject(project.id)
                vendorCount += vendors.size
                vendorPayable += vendors.sumOf { (it.contractValue - it.amountPaid).coerceAtLeast(0.0) }

                val payments = clientPaymentRepository.getForProject(project.id)
                paymentCount += payments.size
                received += payments.sumOf { it.amountReceived }
            }

            _summary.value = HomeSummary(
                boqItemCount = boqCount, boqTotalValue = boqValue,
                expenseCount = expCount, expenseTotal = expTotal,
                vendorCount = vendorCount, vendorPayable = vendorPayable,
                paymentCount = paymentCount, totalReceived = received,
                projectCount = projects.size
            )
            _isLoading.value = false
        }
    }
}

private data class HomeCard(val title: String, val icon: ImageVector, val primaryLine: String, val secondaryLine: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenProjects: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.load() }
    val summary by viewModel.summary.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Home") }) }) { padding ->
        if (isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        val cards = listOf(
            HomeCard("BOQ", Icons.Filled.ListAlt, "${summary.boqItemCount} items", formatCurrency(summary.boqTotalValue)),
            HomeCard("Expenses", Icons.Filled.Receipt, "${summary.expenseCount} entries", formatCurrency(summary.expenseTotal)),
            HomeCard("Vendors", Icons.Filled.Engineering, "${summary.vendorCount} vendors", "${formatCurrency(summary.vendorPayable)} payable"),
            HomeCard("Payments", Icons.Filled.Payments, "${summary.paymentCount} payments", "${formatCurrency(summary.totalReceived)} received")
        )

        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    if (summary.projectCount == 0) "No projects yet." else "Summary across ${summary.projectCount} project${if (summary.projectCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.heightIn(max = 400.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(cards) { card ->
                        ElevatedCard {
                            Column(Modifier.padding(16.dp)) {
                                Icon(card.icon, contentDescription = null)
                                Spacer(Modifier.height(8.dp))
                                Text(card.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(card.primaryLine, style = MaterialTheme.typography.bodyMedium)
                                Text(card.secondaryLine, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (summary.projectCount == 0) {
                item {
                    OutlinedButton(onClick = onOpenProjects, modifier = Modifier.fillMaxWidth()) { Text("Go to Projects") }
                }
            }
        }
    }
}
