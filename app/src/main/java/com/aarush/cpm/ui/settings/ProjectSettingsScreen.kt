package com.aarush.cpm.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.entity.BOQNotation
import com.aarush.cpm.data.entity.MaterialCoefficient
import com.aarush.cpm.data.entity.MaterialRateCard
import com.aarush.cpm.data.repository.BOQRepository
import com.aarush.cpm.data.repository.MaterialRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Editable in-memory row for one notation + its five material coefficients — the wide-table
 *  UI shape the user works with, backed by one BOQNotation + up to five MaterialCoefficient
 *  rows in the database. `key` is a stable Compose identity, independent of any DB id (a new
 *  row has no DB id yet). */
data class CoefficientEditRow(
    val key: Int,
    var code: String = "",
    var description: String = "",
    var unit: String = "Cft",
    var cement: String = "0",
    var pSand: String = "0",
    var mSand: String = "0",
    var bricks: String = "0",
    var aggregate20mm: String = "0"
)

data class RateEditRow(
    val key: Int,
    var description: String = "",
    var unit: String = "",
    var rate: String = "0"
)

data class ProjectSettingsUiState(
    val coefficientRows: List<CoefficientEditRow> = emptyList(),
    val rateRows: List<RateEditRow> = emptyList(),
    val nextKey: Int = 0,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val savedMessage: String? = null
)

class ProjectSettingsViewModel(
    private val boqRepository: BOQRepository,
    private val materialRepository: MaterialRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectSettingsUiState())
    val uiState: StateFlow<ProjectSettingsUiState> = _uiState.asStateFlow()
    private var projectId: Long = 0L

    fun load(projectId: Long) {
        this.projectId = projectId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val notations = boqRepository.getNotations(projectId)
            val allCoefficients = materialRepository.getCoefficients(projectId)
            // Map of (notation code -> material name -> coefficient value), so each wide
            // table row can pull its five cells from the normalized MaterialCoefficient rows.
            val coefficientLookup: Map<String, Map<String, Double>> = allCoefficients
                .groupBy { it.workItemKey }
                .mapValues { (_, rows) -> rows.associate { it.materialName to it.coefficientPerUnit } }

            val coefficientRows = notations.mapIndexed { index, n ->
                val cells = coefficientLookup[n.code].orEmpty()
                CoefficientEditRow(
                    key = index,
                    code = n.code,
                    description = n.description,
                    unit = n.defaultUnit,
                    cement = fmt(cells["Cement"]),
                    pSand = fmt(cells["P.Sand"]),
                    mSand = fmt(cells["M.Sand"]),
                    bricks = fmt(cells["Bricks"]),
                    aggregate20mm = fmt(cells["20mm Aggregate"])
                )
            }

            val ratesFromDb = materialRepository.getMaterialRates(projectId)
            val rateRows = ratesFromDb.mapIndexed { index, r ->
                RateEditRow(key = index, description = r.description, unit = r.unit, rate = if (r.rate == 0.0) "0" else "%.2f".format(r.rate))
            }

            _uiState.value = ProjectSettingsUiState(
                coefficientRows = coefficientRows,
                rateRows = rateRows,
                nextKey = maxOf(coefficientRows.size, rateRows.size) + 1,
                isLoading = false
            )
        }
    }

    private fun fmt(v: Double?): String = if (v == null || v == 0.0) "0" else "%.4f".format(v).trimEnd('0').trimEnd('.')

    fun updateCoefficientRow(key: Int, transform: (CoefficientEditRow) -> CoefficientEditRow) {
        _uiState.value = _uiState.value.let { s ->
            s.copy(coefficientRows = s.coefficientRows.map { if (it.key == key) transform(it) else it })
        }
    }

    fun addCoefficientRow() {
        _uiState.value = _uiState.value.let { s ->
            s.copy(coefficientRows = s.coefficientRows + CoefficientEditRow(key = s.nextKey), nextKey = s.nextKey + 1)
        }
    }

    fun removeCoefficientRow(key: Int) {
        _uiState.value = _uiState.value.let { s -> s.copy(coefficientRows = s.coefficientRows.filterNot { it.key == key }) }
    }

    fun updateRateRow(key: Int, transform: (RateEditRow) -> RateEditRow) {
        _uiState.value = _uiState.value.let { s ->
            s.copy(rateRows = s.rateRows.map { if (it.key == key) transform(it) else it })
        }
    }

    fun addRateRow() {
        _uiState.value = _uiState.value.let { s ->
            s.copy(rateRows = s.rateRows + RateEditRow(key = s.nextKey), nextKey = s.nextKey + 1)
        }
    }

    fun removeRateRow(key: Int) {
        _uiState.value = _uiState.value.let { s -> s.copy(rateRows = s.rateRows.filterNot { it.key == key }) }
    }

    fun saveAll() {
        val s = _uiState.value
        viewModelScope.launch {
            _uiState.value = s.copy(isSaving = true, savedMessage = null)

            val notations = s.coefficientRows.filter { it.code.isNotBlank() }.map {
                BOQNotation(projectId = projectId, code = it.code, description = it.description.ifBlank { it.code }, defaultUnit = it.unit.ifBlank { "Cft" })
            }
            val coefficients = mutableListOf<MaterialCoefficient>()
            s.coefficientRows.filter { it.code.isNotBlank() }.forEach { row ->
                coefficients += MaterialCoefficient(projectId = projectId, workItemKey = row.code, materialName = "Cement", coefficientPerUnit = row.cement.toDoubleOrNull() ?: 0.0)
                coefficients += MaterialCoefficient(projectId = projectId, workItemKey = row.code, materialName = "P.Sand", coefficientPerUnit = row.pSand.toDoubleOrNull() ?: 0.0)
                coefficients += MaterialCoefficient(projectId = projectId, workItemKey = row.code, materialName = "M.Sand", coefficientPerUnit = row.mSand.toDoubleOrNull() ?: 0.0)
                coefficients += MaterialCoefficient(projectId = projectId, workItemKey = row.code, materialName = "Bricks", coefficientPerUnit = row.bricks.toDoubleOrNull() ?: 0.0)
                coefficients += MaterialCoefficient(projectId = projectId, workItemKey = row.code, materialName = "20mm Aggregate", coefficientPerUnit = row.aggregate20mm.toDoubleOrNull() ?: 0.0)
            }
            boqRepository.replaceNotationsAndCoefficients(projectId, notations, coefficients)

            val rates = s.rateRows.filter { it.description.isNotBlank() }.map {
                MaterialRateCard(projectId = projectId, description = it.description, unit = it.unit.ifBlank { "Units" }, rate = it.rate.toDoubleOrNull() ?: 0.0)
            }
            materialRepository.replaceMaterialRates(projectId, rates)

            _uiState.value = _uiState.value.copy(isSaving = false, savedMessage = "Saved")
            load(projectId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSettingsTabContent(projectId: Long, viewModel: ProjectSettingsViewModel) {
    LaunchedEffect(projectId) { viewModel.load(projectId) }
    val state by viewModel.uiState.collectAsState()
    var tab by remember { mutableStateOf(0) }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Coefficients") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Material Rates") })
            }
            state.savedMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
            }
            if (tab == 0) CoefficientsTab(state = state, viewModel = viewModel)
            else RatesTab(state = state, viewModel = viewModel)
        }

        ExtendedFloatingActionButton(
            onClick = { viewModel.saveAll() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (state.isSaving) "Saving…" else "Save all")
        }
    }
}

@Composable
private fun CoefficientsTab(state: ProjectSettingsUiState, viewModel: ProjectSettingsViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "Coefficient = quantity of material per 1 unit of the notation's BOQ quantity. " +
                    "Picking a notation as a BOQ item's code will use these values automatically.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        items(state.coefficientRows, key = { it.key }) { row ->
            CoefficientRowCard(row = row, onChange = { transform -> viewModel.updateCoefficientRow(row.key, transform) }, onDelete = { viewModel.removeCoefficientRow(row.key) })
        }
        item {
            OutlinedButton(onClick = { viewModel.addCoefficientRow() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add notation")
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun CoefficientRowCard(row: CoefficientEditRow, onChange: ((CoefficientEditRow) -> CoefficientEditRow) -> Unit, onDelete: () -> Unit) {
    Card {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(row.code, { v -> onChange { it.copy(code = v) } }, label = { Text("Code") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(row.unit, { v -> onChange { it.copy(unit = v) } }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(row.description, { v -> onChange { it.copy(description = v) } }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text("Coefficients per unit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Row {
                CoeffField("Cement", row.cement, Modifier.weight(1f)) { v -> onChange { it.copy(cement = v) } }
                Spacer(Modifier.width(4.dp))
                CoeffField("P.Sand", row.pSand, Modifier.weight(1f)) { v -> onChange { it.copy(pSand = v) } }
            }
            Spacer(Modifier.height(4.dp))
            Row {
                CoeffField("M.Sand", row.mSand, Modifier.weight(1f)) { v -> onChange { it.copy(mSand = v) } }
                Spacer(Modifier.width(4.dp))
                CoeffField("Bricks", row.bricks, Modifier.weight(1f)) { v -> onChange { it.copy(bricks = v) } }
            }
            Spacer(Modifier.height(4.dp))
            CoeffField("20mm Aggregate", row.aggregate20mm, Modifier.fillMaxWidth()) { v -> onChange { it.copy(aggregate20mm = v) } }
        }
    }
}

@Composable
private fun CoeffField(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value, onValueChange, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = modifier
    )
}

@Composable
private fun RatesTab(state: ProjectSettingsUiState, viewModel: ProjectSettingsViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "A reference rate card for tracking budget — used for estimating material costs. " +
                    "Lump-sum items (Door, Window, Electrical, etc.) can be left at rate 0 and tracked as a lump sum expense instead.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        items(state.rateRows, key = { it.key }) { row ->
            Card {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(row.description, { v -> viewModel.updateRateRow(row.key) { it.copy(description = v) } }, label = { Text("Description") }, singleLine = true, modifier = Modifier.weight(2f))
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(row.unit, { v -> viewModel.updateRateRow(row.key) { it.copy(unit = v) } }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(
                        row.rate, { v -> viewModel.updateRateRow(row.key) { it.copy(rate = v) } }, label = { Text("Rate") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.removeRateRow(row.key) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                }
            }
        }
        item {
            OutlinedButton(onClick = { viewModel.addRateRow() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add material rate")
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}
