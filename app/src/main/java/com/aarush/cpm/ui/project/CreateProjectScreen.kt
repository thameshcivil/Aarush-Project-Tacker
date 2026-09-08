package com.aarush.cpm.ui.project

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.repository.AreaRateComponent
import com.aarush.cpm.data.repository.BuiltInDefaultsSeeder
import com.aarush.cpm.data.repository.ProjectRepository
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.ui.common.formatCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/** One editable "area & rate" row in the Create Project form — e.g. "Residence" at one
 *  ₹/sqft rate and "Staircase" at another. `id` is a stable Compose key, unrelated to any
 *  database id (this row doesn't exist in Room until the project is saved). */
data class AreaRateRow(
    val id: Int,
    val label: String = "",
    val area: String = "",
    val rate: String = ""
) {
    val subtotal: Double
        get() = CalculationEngine.projectValue(area.toDoubleOrNull() ?: 0.0, rate.toDoubleOrNull() ?: 0.0)
}

data class CreateProjectUiState(
    val name: String = "",
    val clientName: String = "",
    val clientContact: String = "",
    val location: String = "",
    val projectType: String = "",
    val numberOfFloors: String = "1",
    val areaRows: List<AreaRateRow> = listOf(AreaRateRow(id = 0, label = "Main structure")),
    val nextRowId: Int = 1,
    val manualOverrideValue: String = "",
    val useManualOverride: Boolean = false,
    val notes: String = "",
    val error: String? = null,
    val isSaving: Boolean = false
) {
    val computedValue: Double get() = areaRows.sumOf { it.subtotal }
    val totalArea: Double get() = areaRows.sumOf { it.area.toDoubleOrNull() ?: 0.0 }
    val finalValue: Double
        get() = if (useManualOverride) (manualOverrideValue.toDoubleOrNull() ?: computedValue) else computedValue
}

class CreateProjectViewModel(
    private val repository: ProjectRepository,
    private val builtInDefaultsSeeder: BuiltInDefaultsSeeder
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateProjectUiState())
    val uiState: StateFlow<CreateProjectUiState> = _uiState.asStateFlow()

    fun update(transform: (CreateProjectUiState) -> CreateProjectUiState) {
        _uiState.value = transform(_uiState.value).copy(error = null)
    }

    fun addAreaRow() {
        _uiState.value = _uiState.value.let { s ->
            s.copy(areaRows = s.areaRows + AreaRateRow(id = s.nextRowId), nextRowId = s.nextRowId + 1, error = null)
        }
    }

    fun removeAreaRow(rowId: Int) {
        _uiState.value = _uiState.value.let { s ->
            if (s.areaRows.size <= 1) s else s.copy(areaRows = s.areaRows.filterNot { it.id == rowId })
        }
    }

    fun updateAreaRow(rowId: Int, label: String? = null, area: String? = null, rate: String? = null) {
        _uiState.value = _uiState.value.let { s ->
            s.copy(areaRows = s.areaRows.map { row ->
                if (row.id != rowId) row else row.copy(
                    label = label ?: row.label,
                    area = area ?: row.area,
                    rate = rate ?: row.rate
                )
            }, error = null)
        }
    }

    fun save(onSaved: (Long) -> Unit) {
        val s = _uiState.value
        if (s.name.isBlank()) { _uiState.value = s.copy(error = "Project name is required."); return }

        val validRows = s.areaRows.filter { row ->
            (row.area.toDoubleOrNull() ?: 0.0) > 0 && (row.rate.toDoubleOrNull() ?: 0.0) > 0
        }
        if (validRows.isEmpty()) {
            _uiState.value = s.copy(error = "Add at least one area/rate row with area and rate both greater than 0.")
            return
        }

        viewModelScope.launch {
            _uiState.value = s.copy(isSaving = true)
            val now = System.currentTimeMillis()
            val expected = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.MONTH, 6) }.timeInMillis
            val components = validRows.mapIndexed { index, row ->
                AreaRateComponent(
                    label = row.label.ifBlank { "Area ${index + 1}" },
                    areaSqft = row.area.toDouble(),
                    ratePerSqft = row.rate.toDouble()
                )
            }
            val id = repository.createProject(
                name = s.name, clientName = s.clientName, clientContact = s.clientContact,
                location = s.location, projectType = s.projectType.ifBlank { "G+1" },
                numberOfFloors = s.numberOfFloors.toIntOrNull() ?: 1,
                areaComponents = components,
                manualProjectValue = if (s.useManualOverride) s.manualOverrideValue.toDoubleOrNull() else null,
                startDate = now, expectedCompletionDate = expected, notes = s.notes
            )
            builtInDefaultsSeeder.seedBuiltInDefaults(id)
            _uiState.value = _uiState.value.copy(isSaving = false)
            onSaved(id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(viewModel: CreateProjectViewModel, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val s by viewModel.uiState.collectAsState()

    Scaffold(topBar = {
        TopAppBar(title = { Text("New Project") }, navigationIcon = { TextButton(onClick = onBack) { Text("Cancel") } })
    }) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            OutlinedTextField(s.name, { v -> viewModel.update { it.copy(name = v) } }, label = { Text("Project name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(s.clientName, { v -> viewModel.update { it.copy(clientName = v) } }, label = { Text("Client name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(s.clientContact, { v -> viewModel.update { it.copy(clientContact = v) } }, label = { Text("Client contact") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(s.location, { v -> viewModel.update { it.copy(location = v) } }, label = { Text("Project location") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(s.projectType, { v -> viewModel.update { it.copy(projectType = v) } }, label = { Text("Project type (e.g. G+1, G+Terrace)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(s.numberOfFloors, { v -> viewModel.update { it.copy(numberOfFloors = v) } }, label = { Text("Number of floors") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(20.dp))
            Text("Area & rate breakdown", style = MaterialTheme.typography.titleMedium)
            Text(
                "Add a row for each part of the build with its own rate — e.g. Residence, Staircase, Portico.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))

            s.areaRows.forEach { row ->
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                row.label,
                                { v -> viewModel.updateAreaRow(row.id, label = v) },
                                label = { Text("Label") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (s.areaRows.size > 1) {
                                IconButton(onClick = { viewModel.removeAreaRow(row.id) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove row")
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row {
                            OutlinedTextField(
                                row.area,
                                { v -> viewModel.updateAreaRow(row.id, area = v) },
                                label = { Text("Area (sqft)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))
                            OutlinedTextField(
                                row.rate,
                                { v -> viewModel.updateAreaRow(row.id, rate = v) },
                                label = { Text("Rate (₹/sqft)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.subtotal > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text("Subtotal: ${formatCurrency(row.subtotal)}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            OutlinedButton(onClick = { viewModel.addAreaRow() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add another area / rate")
            }

            Spacer(Modifier.height(12.dp))
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Calculated project value", style = MaterialTheme.typography.labelMedium)
                    Text(formatCurrency(s.computedValue), style = MaterialTheme.typography.titleLarge)
                    Text("Total area: ${"%.0f".format(s.totalArea)} sqft", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = s.useManualOverride, onCheckedChange = { v -> viewModel.update { it.copy(useManualOverride = v) } })
                        Text("Override project value manually")
                    }
                    if (s.useManualOverride) {
                        OutlinedTextField(
                            s.manualOverrideValue, { v -> viewModel.update { it.copy(manualOverrideValue = v) } },
                            label = { Text("Manual project value (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(s.notes, { v -> viewModel.update { it.copy(notes = v) } }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            s.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = { viewModel.save(onSaved) }, enabled = !s.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(if (s.isSaving) "Saving…" else "Create Project")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
