package com.aarush.cpm.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.entity.*
import com.aarush.cpm.data.repository.BOQRepository
import com.aarush.cpm.data.repository.MaterialRepository
import com.aarush.cpm.data.repository.ProjectRepository
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.aarush.cpm.ui.common.*
import com.aarush.cpm.ui.theme.SiteTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The built-in coefficient materials, always shown as columns (others are added as found). */
private val DEFAULT_COEFF_MATERIALS = listOf("Cement", "P.Sand", "M.Sand", "Bricks", "20mm Aggregate", "Steel")

/** Editable in-memory row for one notation and its coefficients per material. [originalCode]
 *  lets a rename be propagated to existing BOQ items and schedule activities on save. */
data class CoefficientEditRow(
    val key: Int,
    val originalCode: String = "",
    val code: String = "",
    val description: String = "",
    val unit: String = "Cft",
    /** material name → coefficient text */
    val coefficients: Map<String, String> = emptyMap(),
    /** Optional waste % for this notation's materials; blank = use each BOQ item's waste %. */
    val wasteOverride: String = ""
)

/** [linkedVendorId] rows are vendor labour lines: shown read-only and never rebuilt here. */
data class RateEditRow(
    val key: Int,
    val originalDescription: String = "",
    val description: String = "",
    val unit: String = "",
    val rate: String = "0",
    val linkedVendorId: Long? = null
)

data class AllocationEditRow(
    val category: CostCategory,
    val percent: String,
    val materialPercent: String
)

data class ProjectEdit(
    val name: String = "",
    val clientName: String = "",
    val clientContact: String = "",
    val location: String = "",
    val projectType: String = "",
    val numberOfFloors: String = "1",
    val status: ProjectStatus = ProjectStatus.PLANNING,
    val startDate: Long = System.currentTimeMillis(),
    val expectedCompletionDate: Long = System.currentTimeMillis(),
    val useManualValue: Boolean = false,
    val manualValue: String = "",
    val notes: String = ""
)

data class ProjectSettingsUiState(
    val project: Project? = null,
    val projectEdit: ProjectEdit = ProjectEdit(),
    val allocations: List<AllocationEditRow> = emptyList(),
    val materialColumns: List<String> = DEFAULT_COEFF_MATERIALS,
    val coefficientRows: List<CoefficientEditRow> = emptyList(),
    val rateRows: List<RateEditRow> = emptyList(),
    val nextKey: Int = 0,
    val isLoading: Boolean = true,
    /** Compound wall part of the project value, for the budget preview (Civil and Painting only). */
    val compoundWallValue: Double = 0.0,
    val isSaving: Boolean = false,
    val savedMessage: String? = null,
    val error: String? = null
) {
    val allocationTotal: Double get() = allocations.sumOf { it.percent.toDoubleOrNull() ?: 0.0 }
}

class ProjectSettingsViewModel(
    private val boqRepository: BOQRepository,
    private val materialRepository: MaterialRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectSettingsUiState())
    val uiState: StateFlow<ProjectSettingsUiState> = _uiState.asStateFlow()
    private var projectId: Long = 0L

    fun load(projectId: Long) {
        this.projectId = projectId
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        run {
            _uiState.value = _uiState.value.copy(isLoading = _uiState.value.project == null)
            val project = projectRepository.getById(projectId)
            val notations = boqRepository.getNotations(projectId)
            val allCoefficients = materialRepository.getCoefficients(projectId)
            val byCode = allCoefficients.groupBy { it.workItemKey }
            // Columns: the six defaults plus any other material a coefficient refers to — the
            // previous fixed six-column table silently DROPPED every other material on save.
            val columns = (DEFAULT_COEFF_MATERIALS + allCoefficients.map { it.materialName }).distinct()

            val coefficientRows = notations.mapIndexed { index, n ->
                val rows = byCode[n.code].orEmpty()
                CoefficientEditRow(
                    key = index, originalCode = n.code, code = n.code, description = n.description, unit = n.defaultUnit,
                    coefficients = rows.associate { it.materialName to fmt(it.coefficientPerUnit) },
                    wasteOverride = rows.firstNotNullOfOrNull { it.wastePercentOverride }?.let { fmt(it) } ?: ""
                )
            }
            val rateRows = materialRepository.getMaterialRates(projectId).mapIndexed { index, r ->
                RateEditRow(
                    key = 10_000 + index, originalDescription = r.description, description = r.description, unit = r.unit,
                    rate = if (r.rate == 0.0) "0" else "%.2f".format(r.rate), linkedVendorId = r.linkedVendorId
                )
            }
            val allocations = projectRepository.getCostAllocations(projectId)
            val allocationRows = CostCategory.values().map { cat ->
                val a = allocations.find { it.category == cat }
                AllocationEditRow(cat, fmt(a?.percentOfProjectValue ?: 0.0), fmt(a?.materialPercent ?: 65.0))
            }

            val wallValue = com.aarush.cpm.domain.calculation.CompoundWallBudget.wallValue(projectRepository.getAreaComponents(projectId))
            _uiState.value = ProjectSettingsUiState(
                project = project,
                compoundWallValue = wallValue,
                projectEdit = project?.let {
                    ProjectEdit(
                        name = it.name, clientName = it.clientName, clientContact = it.clientContact, location = it.location,
                        projectType = it.projectType, numberOfFloors = it.numberOfFloors.toString(), status = it.status,
                        startDate = it.startDate, expectedCompletionDate = it.expectedCompletionDate,
                        useManualValue = it.isProjectValueManuallyOverridden,
                        manualValue = if (it.isProjectValueManuallyOverridden) "%.0f".format(it.projectValue) else "",
                        notes = it.notes
                    )
                } ?: ProjectEdit(),
                allocations = allocationRows,
                materialColumns = columns,
                coefficientRows = coefficientRows,
                rateRows = rateRows,
                nextKey = 20_000 + coefficientRows.size + rateRows.size,
                isLoading = false
            )
        }
    }

    private fun fmt(v: Double?): String = if (v == null || v == 0.0) "0" else "%.4f".format(v).trimEnd('0').trimEnd('.')

    fun editProject(transform: (ProjectEdit) -> ProjectEdit) {
        _uiState.value = _uiState.value.copy(projectEdit = transform(_uiState.value.projectEdit), savedMessage = null, error = null)
    }

    fun editAllocation(category: CostCategory, transform: (AllocationEditRow) -> AllocationEditRow) {
        _uiState.value = _uiState.value.let { s ->
            s.copy(allocations = s.allocations.map { if (it.category == category) transform(it) else it }, savedMessage = null, error = null)
        }
    }

    fun updateCoefficientRow(key: Int, transform: (CoefficientEditRow) -> CoefficientEditRow) {
        _uiState.value = _uiState.value.let { s ->
            s.copy(coefficientRows = s.coefficientRows.map { if (it.key == key) transform(it) else it }, savedMessage = null)
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

    fun addMaterialColumn(name: String) {
        val n = name.trim()
        if (n.isBlank()) return
        _uiState.value = _uiState.value.let { s -> if (n in s.materialColumns) s else s.copy(materialColumns = s.materialColumns + n) }
    }

    fun updateRateRow(key: Int, transform: (RateEditRow) -> RateEditRow) {
        _uiState.value = _uiState.value.let { s ->
            s.copy(rateRows = s.rateRows.map { if (it.key == key && it.linkedVendorId == null) transform(it) else it }, savedMessage = null)
        }
    }

    fun addRateRow() {
        _uiState.value = _uiState.value.let { s ->
            s.copy(rateRows = s.rateRows + RateEditRow(key = s.nextKey), nextKey = s.nextKey + 1)
        }
    }

    fun removeRateRow(key: Int) {
        _uiState.value = _uiState.value.let { s -> s.copy(rateRows = s.rateRows.filterNot { it.key == key && it.linkedVendorId == null }) }
    }

    /** Saves project details + cost distribution (validated to 100%). */
    fun saveProject() {
        val s = _uiState.value
        val project = s.project ?: return
        val e = s.projectEdit
        val error = when {
            e.name.isBlank() -> "Project name is required."
            kotlin.math.abs(s.allocationTotal - 100.0) > 0.01 -> "Cost distribution must add up to 100% (now ${"%.2f".format(s.allocationTotal)}%)."
            s.allocations.any { (it.materialPercent.toDoubleOrNull() ?: -1.0) !in 0.0..100.0 } -> "Material share must be between 0 and 100%."
            e.useManualValue && (e.manualValue.toDoubleOrNull() ?: 0.0) <= 0 -> "Enter the manual project value or untick the override."
            e.expectedCompletionDate < e.startDate -> "Expected completion can't be before the start date."
            else -> null
        }
        if (error != null) { _uiState.value = s.copy(error = error); return }
        viewModelScope.launch {
            _uiState.value = s.copy(isSaving = true)
            projectRepository.updateProjectDetails(
                project.copy(
                    name = e.name.trim(), clientName = e.clientName.trim(), clientContact = e.clientContact.trim(),
                    location = e.location.trim(), projectType = e.projectType.trim(),
                    numberOfFloors = (e.numberOfFloors.toIntOrNull() ?: project.numberOfFloors).coerceIn(1, 10),
                    status = e.status, startDate = e.startDate, expectedCompletionDate = e.expectedCompletionDate, notes = e.notes
                ),
                manualValue = if (e.useManualValue) e.manualValue.toDoubleOrNull() else null
            )
            projectRepository.saveCostAllocations(projectId, s.allocations.map {
                val mat = it.materialPercent.toDoubleOrNull() ?: 65.0
                CostAllocation(projectId = projectId, category = it.category, percentOfProjectValue = it.percent.toDoubleOrNull() ?: 0.0,
                    materialPercent = mat, labourPercent = 100.0 - mat)
            })
            reload()
            _uiState.value = _uiState.value.copy(isSaving = false, savedMessage = "Project saved")
        }
    }

    /** Saves the coefficient table and the editable rate card. */
    fun saveTables() {
        val s = _uiState.value
        val codes = s.coefficientRows.filter { it.code.isNotBlank() }.map { it.code.trim() }
        val dupCode = codes.groupBy { it }.filter { it.value.size > 1 }.keys.firstOrNull()
        val names = s.rateRows.filter { it.description.isNotBlank() }.map { it.description.trim() }
        val dupName = names.groupBy { it }.filter { it.value.size > 1 }.keys.firstOrNull()
        if (dupCode != null) { _uiState.value = s.copy(error = "Notation \"$dupCode\" appears twice."); return }
        if (dupName != null) { _uiState.value = s.copy(error = "Material \"$dupName\" appears twice in the rate card."); return }

        viewModelScope.launch {
            _uiState.value = s.copy(isSaving = true, savedMessage = null, error = null)
            val rows = s.coefficientRows.filter { it.code.isNotBlank() }
            val notations = rows.map {
                BOQNotation(projectId = projectId, code = it.code.trim(), description = it.description.ifBlank { it.code }.trim(), defaultUnit = it.unit.ifBlank { "Cft" })
            }
            val coefficients = rows.flatMap { row ->
                val waste = row.wasteOverride.toDoubleOrNull()
                s.materialColumns.mapNotNull { material ->
                    val v = row.coefficients[material]?.toDoubleOrNull() ?: 0.0
                    if (v <= 0.0 && material !in DEFAULT_COEFF_MATERIALS) null
                    else MaterialCoefficient(projectId = projectId, workItemKey = row.code.trim(), materialName = material,
                        coefficientPerUnit = v, wastePercentOverride = waste)
                }
            }
            val codeRenames = rows.filter { it.originalCode.isNotBlank() && it.originalCode != it.code.trim() }
                .associate { it.originalCode to it.code.trim() }
            boqRepository.replaceNotationsAndCoefficients(projectId, notations, coefficients, codeRenames)

            val editable = s.rateRows.filter { it.linkedVendorId == null && it.description.isNotBlank() }
            val nameRenames = editable.filter { it.originalDescription.isNotBlank() && it.originalDescription != it.description.trim() }
                .associate { it.originalDescription to it.description.trim() }
            materialRepository.replaceMaterialRates(
                projectId,
                editable.map { MaterialRateCard(projectId = projectId, description = it.description.trim(), unit = it.unit.ifBlank { "Units" }, rate = it.rate.toDoubleOrNull() ?: 0.0) },
                nameRenames
            )
            reload()
            _uiState.value = _uiState.value.copy(isSaving = false, savedMessage = "Saved. BOQ, schedule and expenses keep their links.")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSettingsTabContent(projectId: Long, viewModel: ProjectSettingsViewModel, snackbarHostState: SnackbarHostState? = null) {
    LaunchedEffect(projectId) { viewModel.load(projectId) }
    val state by viewModel.uiState.collectAsState()
    var tab by remember { mutableStateOf(0) }

    if (state.isLoading) {
        com.aarush.cpm.ui.common.SkeletonScreen(headline = false, sections = 2, rows = 5)
        return
    }
    LaunchedEffect(state.savedMessage) { state.savedMessage?.let { snackbarHostState?.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab]), color = MaterialTheme.colorScheme.primary)
                }) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Project") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Coefficients") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Rates") })
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
            when (tab) {
                0 -> ProjectTab(state, viewModel)
                1 -> CoefficientsTab(state = state, viewModel = viewModel)
                else -> RatesTab(state = state, viewModel = viewModel)
            }
        }

        ExtendedFloatingActionButton(
            onClick = { if (tab == 0) viewModel.saveProject() else viewModel.saveTables() },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (state.isSaving) "Saving…" else if (tab == 0) "Save project" else "Save tables")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectTab(state: ProjectSettingsUiState, viewModel: ProjectSettingsViewModel) {
    val e = state.projectEdit
    var statusMenu by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionLabel("Project details")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(e.name, { v -> viewModel.editProject { it.copy(name = v) } }, label = { Text("Project name") }, modifier = Modifier.fillMaxWidth())
        Row {
            OutlinedTextField(e.clientName, { v -> viewModel.editProject { it.copy(clientName = v) } }, label = { Text("Client") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(e.clientContact, { v -> viewModel.editProject { it.copy(clientContact = v) } }, label = { Text("Contact") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.weight(1f))
        }
        OutlinedTextField(e.location, { v -> viewModel.editProject { it.copy(location = v) } }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
        Row {
            OutlinedTextField(e.projectType, { v -> viewModel.editProject { it.copy(projectType = v) } }, label = { Text("Type (G+1…)") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(e.numberOfFloors, { v -> viewModel.editProject { it.copy(numberOfFloors = v.filter(Char::isDigit).take(2)) } },
                label = { Text("Floors") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(onClick = { statusMenu = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                Text("Status: ${e.status.name.replace('_', ' ')}")
            }
            DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                ProjectStatus.values().forEach { st ->
                    DropdownMenuItem(text = { Text(st.name.replace('_', ' ')) }, onClick = { viewModel.editProject { it.copy(status = st) }; statusMenu = false })
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { pickStart = true }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) { Text("Start ${formatDate(e.startDate)}") }
            OutlinedButton(onClick = { pickEnd = true }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) { Text("Due ${formatDate(e.expectedCompletionDate)}") }
        }
        Text("Generating or re-baselining the schedule updates these dates automatically.", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = e.useManualValue, onCheckedChange = { v -> viewModel.editProject { it.copy(useManualValue = v) } })
            Text("Override project value (Budget 1) manually")
        }
        if (e.useManualValue) {
            OutlinedTextField(e.manualValue, { v -> viewModel.editProject { it.copy(manualValue = v) } }, label = { Text("Project value (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        } else {
            Text("Value = sum of area × rate rows (edit them from the Home tab header).", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
        }
        OutlinedTextField(e.notes, { v -> viewModel.editProject { it.copy(notes = v) } }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Cost distribution · thumb rule", modifier = Modifier.weight(1f))
            val ok = kotlin.math.abs(state.allocationTotal - 100.0) <= 0.01
            StatusPill("Total ${"%.1f".format(state.allocationTotal)}%",
                if (ok) SiteTheme.colors.good else SiteTheme.colors.bad, if (ok) SiteTheme.colors.goodTint else SiteTheme.colors.badTint)
        }
        Spacer(Modifier.height(6.dp))
        val value = state.project?.projectValue ?: 0.0
        // Preview with the percentages as currently typed; the compound wall (if any) goes to
        // Civil and Painting only, exactly as the saved budgets will.
        val previewAllocations = state.allocations.map {
            com.aarush.cpm.data.entity.CostAllocation(projectId = 0, category = it.category, percentOfProjectValue = it.percent.toDoubleOrNull() ?: 0.0)
        }
        if (state.compoundWallValue > 0) {
            Text("Includes compound wall ${formatCurrency(state.compoundWallValue)}: shared by Civil and Painting only; other categories use the building value.",
                style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
            Spacer(Modifier.height(6.dp))
        }
        state.allocations.forEach { a ->
            AppCard(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.category.name.replace('_', ' '), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(formatCurrencyShort(com.aarush.cpm.domain.calculation.CompoundWallBudget.categoryBudget(
                            a.category, a.percent.toDoubleOrNull() ?: 0.0, value, state.compoundWallValue, previewAllocations)),
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Row {
                        OutlinedTextField(a.percent, { v -> viewModel.editAllocation(a.category) { it.copy(percent = v) } },
                            label = { Text("% of value") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(a.materialPercent, { v -> viewModel.editAllocation(a.category) { it.copy(materialPercent = v) } },
                            label = { Text("Material %") }, singleLine = true,
                            supportingText = { Text("Labour ${"%.0f".format(100 - (a.materialPercent.toDoubleOrNull() ?: 0.0))}%") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(88.dp))
    }

    if (pickStart || pickEnd) {
        val initial = if (pickStart) e.startDate else e.expectedCompletionDate
        val dpState = rememberDatePickerState(initialSelectedDateMillis = pickerMillisFromStored(initial))
        DatePickerDialog(
            onDismissRequest = { pickStart = false; pickEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { ms ->
                        val stored = storedMillisFromPicker(ms)
                        if (pickStart) viewModel.editProject { it.copy(startDate = stored) } else viewModel.editProject { it.copy(expectedCompletionDate = stored) }
                    }
                    pickStart = false; pickEnd = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickStart = false; pickEnd = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }
}

@Composable
private fun CoefficientsTab(state: ProjectSettingsUiState, viewModel: ProjectSettingsViewModel) {
    var newMaterial by remember { mutableStateOf("") }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "Coefficient = quantity of material per 1 unit of the notation's BOQ quantity. Renaming a code here also renames it on existing BOQ entries and schedule activities.",
                style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted
            )
        }
        items(state.coefficientRows, key = { it.key }) { row ->
            CoefficientRowCard(row = row, materials = state.materialColumns,
                onChange = { transform -> viewModel.updateCoefficientRow(row.key, transform) },
                onDelete = { viewModel.removeCoefficientRow(row.key) })
        }
        item {
            OutlinedButton(onClick = { viewModel.addCoefficientRow() }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add notation")
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    SectionLabel("Add a material column")
                    Text("e.g. \"Tile adhesive\" or \"40mm Aggregate\" Use the same name as in the rate card so it gets priced.",
                        style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(newMaterial, { newMaterial = it }, label = { Text("Material name") }, singleLine = true, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.addMaterialColumn(newMaterial); newMaterial = "" }) { Text("Add") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun CoefficientRowCard(
    row: CoefficientEditRow,
    materials: List<String>,
    onChange: ((CoefficientEditRow) -> CoefficientEditRow) -> Unit,
    onDelete: () -> Unit
) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(row.code, { v -> onChange { it.copy(code = v) } }, label = { Text("Code") }, singleLine = true, modifier = Modifier.weight(1.4f))
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(row.unit, { v -> onChange { it.copy(unit = v) } }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.weight(0.8f))
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(row.description, { v -> onChange { it.copy(description = v) } }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            SectionLabel("Coefficients per unit")
            Spacer(Modifier.height(4.dp))
            materials.chunked(2).forEach { pair ->
                Row {
                    pair.forEach { m ->
                        OutlinedTextField(
                            row.coefficients[m] ?: "0", { v -> onChange { it.copy(coefficients = it.coefficients + (m to v)) } },
                            label = { Text(m) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
                        )
                        if (pair.size == 2 && m == pair[0]) Spacer(Modifier.width(4.dp))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
            }
            OutlinedTextField(row.wasteOverride, { v -> onChange { it.copy(wasteOverride = v) } },
                label = { Text("Waste % override (blank = BOQ item's waste)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RatesTab(state: ProjectSettingsUiState, viewModel: ProjectSettingsViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "Reference rate card used for Budget 2 and the Expense form. Lump-sum items can stay at rate 0. Renaming a material keeps its purchases, stock and expenses attached. Vendor labour lines are managed from the Vendors tab.",
                style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted
            )
        }
        items(state.rateRows, key = { it.key }) { row ->
            AppCard(Modifier.fillMaxWidth()) {
                if (row.linkedVendorId != null) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = SiteTheme.colors.muted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.description, style = MaterialTheme.typography.bodyMedium)
                            SectionLabel("Vendor line · ${row.unit}")
                        }
                        Text(row.rate, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(row.description, { v -> viewModel.updateRateRow(row.key) { it.copy(description = v) } }, label = { Text("Material") }, singleLine = true, modifier = Modifier.weight(2f))
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
        }
        item {
            OutlinedButton(onClick = { viewModel.addRateRow() }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add material rate")
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}
