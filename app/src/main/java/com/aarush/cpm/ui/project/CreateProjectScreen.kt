package com.aarush.cpm.ui.project

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.repository.ProjectRepository
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.ui.common.formatCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class CreateProjectUiState(
    val name: String = "",
    val clientName: String = "",
    val clientContact: String = "",
    val location: String = "",
    val projectType: String = "",
    val numberOfFloors: String = "1",
    val plinthArea: String = "",
    val ratePerSqft: String = "",
    val manualOverrideValue: String = "",
    val useManualOverride: Boolean = false,
    val notes: String = "",
    val error: String? = null,
    val isSaving: Boolean = false
) {
    val computedValue: Double
        get() {
            val area = plinthArea.toDoubleOrNull() ?: 0.0
            val rate = ratePerSqft.toDoubleOrNull() ?: 0.0
            return CalculationEngine.projectValue(area, rate)
        }
    val finalValue: Double
        get() = if (useManualOverride) (manualOverrideValue.toDoubleOrNull() ?: computedValue) else computedValue
}

class CreateProjectViewModel(private val repository: ProjectRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateProjectUiState())
    val uiState: StateFlow<CreateProjectUiState> = _uiState.asStateFlow()

    fun update(transform: (CreateProjectUiState) -> CreateProjectUiState) {
        _uiState.value = transform(_uiState.value).copy(error = null)
    }

    fun save(onSaved: (Long) -> Unit) {
        val s = _uiState.value
        val area = s.plinthArea.toDoubleOrNull()
        val rate = s.ratePerSqft.toDoubleOrNull()
        if (s.name.isBlank()) { _uiState.value = s.copy(error = "Project name is required."); return }
        if (area == null || area <= 0) { _uiState.value = s.copy(error = "Enter a valid plinth area (> 0)."); return }
        if (rate == null || rate <= 0) { _uiState.value = s.copy(error = "Enter a valid rate per sqft (> 0)."); return }

        viewModelScope.launch {
            _uiState.value = s.copy(isSaving = true)
            val now = System.currentTimeMillis()
            val expected = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.MONTH, 6) }.timeInMillis
            val id = repository.createProject(
                name = s.name, clientName = s.clientName, clientContact = s.clientContact,
                location = s.location, projectType = s.projectType.ifBlank { "G+1" },
                numberOfFloors = s.numberOfFloors.toIntOrNull() ?: 1,
                plinthAreaSqft = area, ratePerSqft = rate,
                manualProjectValue = if (s.useManualOverride) s.manualOverrideValue.toDoubleOrNull() else null,
                startDate = now, expectedCompletionDate = expected, notes = s.notes
            )
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
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(s.plinthArea, { v -> viewModel.update { it.copy(plinthArea = v) } }, label = { Text("Plinth / built-up area (sqft)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(s.ratePerSqft, { v -> viewModel.update { it.copy(ratePerSqft = v) } }, label = { Text("Rate per sqft (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(12.dp))
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Calculated project value", style = MaterialTheme.typography.labelMedium)
                    Text(formatCurrency(s.computedValue), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
