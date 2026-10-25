package com.aarush.cpm.ui.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aarush.cpm.data.entity.*
import com.aarush.cpm.data.repository.ActivityInput
import com.aarush.cpm.data.repository.ScheduleActivityView
import com.aarush.cpm.domain.schedule.ResidentialScheduleTemplate
import com.aarush.cpm.ui.common.*
import com.aarush.cpm.ui.theme.SiteTheme
import java.time.LocalDate

// ------------------------------------------------------------------ date field

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, shape = MaterialTheme.shapes.small, modifier = modifier) { Text("$label: ${formatDate(date)}") }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = pickerMillisFromDate(date))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { onChange(dateFromPickerMillis(it)) }; open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        ) { DatePicker(state = st) }
    }
}

// ------------------------------------------------------------------ generate / rebaseline

@Composable
internal fun GenerateScheduleDialog(
    defaultStart: LocalDate,
    defaultFloors: Int,
    sundayOff: Boolean,
    replacesExisting: Boolean,
    onDismiss: () -> Unit,
    onGenerate: (LocalDate, Int, Boolean) -> Unit
) {
    var start by remember { mutableStateOf(defaultStart) }
    var floors by remember { mutableStateOf(defaultFloors.coerceIn(1, 10).toString()) }
    var sunday by remember { mutableStateOf(sundayOff) }
    val n = (floors.toIntOrNull() ?: 0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (replacesExisting) "Regenerate schedule" else "Generate schedule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Builds the full residential sequence for ${if (n in 1..10) "$n floor${if (n == 1) "" else "s"}" else "the building"}: " +
                        "approvals → foundation → frame & masonry per floor → terrace → services → finishes → handover, with " +
                        "BOQ quantities, crew-based durations, budgets from Budget 2 and a critical-path baseline.",
                    style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted
                )
                Spacer(Modifier.height(10.dp))
                DateField("Start", start, { start = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(floors, { floors = it.filter(Char::isDigit).take(2) }, label = { Text("Floors incl. ground (G+1 = 2)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = sunday, onCheckedChange = { sunday = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Sunday is a holiday (6-day week)", style = MaterialTheme.typography.bodySmall)
                }
                if (replacesExisting) {
                    Spacer(Modifier.height(6.dp))
                    Text("This replaces the current schedule, including all logged progress and the material consumption booked from it.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(enabled = n in 1..10, onClick = { onGenerate(start, n, sunday) }) { Text("Generate") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun RebaselineDialog(defaultStart: LocalDate, sundayOff: Boolean, onDismiss: () -> Unit, onConfirm: (LocalDate, Boolean) -> Unit) {
    var start by remember { mutableStateOf(defaultStart) }
    var sunday by remember { mutableStateOf(sundayOff) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Re-baseline") },
        text = {
            Column {
                Text("Re-plans every activity's baseline dates by critical path from the start date, using current durations and links. " +
                    "Progress is kept, but delays are then measured against the new baseline.",
                    style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                Spacer(Modifier.height(10.dp))
                DateField("Start", start, { start = it }, Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = sunday, onCheckedChange = { sunday = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Sunday is a holiday", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(start, sunday) }) { Text("Re-baseline") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ------------------------------------------------------------------ logging progress

@Composable
internal fun PickActivityDialog(activities: List<ScheduleActivityView>, onDismiss: () -> Unit, onPick: (ScheduleActivityView) -> Unit) {
    val today = LocalDate.now()
    // Running and due activities first — that's what a site engineer logs most.
    val sorted = activities.sortedWith(compareBy(
        { if (it.tracking.actualPercent > 0) 0 else if (!it.tracking.forecastStart.isAfter(today)) 1 else 2 },
        { it.tracking.forecastStart }
    ))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log progress for…") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(sorted, key = { it.activity.id }) { v ->
                    TextButton(onClick = { onPick(v) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(v.activity.activityName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${formatPercent(v.tracking.actualPercent)} · ${formatShortDate(v.tracking.forecastStart)} → ${formatShortDate(v.tracking.forecastFinish)}",
                                style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
                        }
                    }
                    HorizontalDivider(color = SiteTheme.colors.hairline)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
internal fun LogProgressDialog(
    view: ScheduleActivityView,
    onDismiss: () -> Unit,
    onSave: (LocalDate, Double, Double?, Int, String) -> Unit
) {
    val a = view.activity
    val byQuantity = view.plannedQuantity > 0
    val remaining = (view.plannedQuantity - a.completedQuantity).coerceAtLeast(0.0)
    var date by remember { mutableStateOf(LocalDate.now()) }
    var qty by remember { mutableStateOf("") }
    var pct by remember { mutableStateOf(if (byQuantity) "" else "%.0f".format(view.tracking.actualPercent)) }
    var complete by remember { mutableStateOf(false) }
    var labour by remember { mutableStateOf(a.requiredLabourCount.takeIf { it > 0 }?.toString() ?: "") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log progress") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(a.activityName, style = MaterialTheme.typography.titleSmall)
                Text("Now ${formatPercent(view.tracking.actualPercent)} complete" +
                    if (byQuantity) " · ${formatQuantity(round1(a.completedQuantity), "")} of ${formatQuantity(round1(view.plannedQuantity), a.unit)}" else "",
                    style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
                Spacer(Modifier.height(10.dp))
                DateField("Date", date, { date = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                if (byQuantity) {
                    OutlinedTextField(qty, { qty = it }, label = { Text("Quantity done on this day (${a.unit})") },
                        supportingText = { Text("Remaining: ${formatQuantity(round1(remaining), a.unit)} · materials are booked out of stock automatically") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = complete, onCheckedChange = { complete = it })
                        Text("Activity is now complete", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    OutlinedTextField(pct, { pct = it }, label = { Text("Total % complete now") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(labour, { labour = it.filter(Char::isDigit) }, label = { Text("Workers on site for this activity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = qty.toDoubleOrNull() ?: 0.0
                val p = pct.toDoubleOrNull()
                error = when {
                    date.isAfter(LocalDate.now()) -> "Progress can't be logged for a future date."
                    byQuantity && q <= 0 && !complete -> "Enter the quantity done, or tick complete."
                    byQuantity && q < 0 -> "Quantity can't be negative."
                    !byQuantity && (p == null || p < 0 || p > 100) -> "Enter a % between 0 and 100."
                    else -> null
                }
                if (error == null) {
                    val percent = if (byQuantity) (if (complete) 100.0 else null) else p
                    onSave(date, if (byQuantity) q else 0.0, percent, labour.toIntOrNull() ?: 0, notes)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ------------------------------------------------------------------ detail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityDetailSheet(
    view: ScheduleActivityView,
    all: List<ScheduleActivityView>,
    onDismiss: () -> Unit,
    onLog: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDeleteLog: (ActivityProgressLog) -> Unit,
    onMarkComplete: () -> Unit
) {
    val a = view.activity
    val t = view.tracking
    val (fg, bg) = statusColors(t.status)
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingLogRemoval by remember { mutableStateOf<ActivityProgressLog?>(null) }
    val names = all.associate { it.activity.id to it.activity.activityName }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SiteTheme.colors.sheet) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            SectionLabel(listOf(a.phase, a.floorLabel).filter { it.isNotBlank() }.joinToString(" · "))
            Text(a.activityName, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(t.status.name.replace('_', ' '), fg, bg)
                if (t.isCritical && t.status != ActivityStatus.COMPLETED) StatusPill("Critical path", SiteTheme.colors.bad, SiteTheme.colors.badTint)
                if (a.isMilestone) StatusPill("Milestone", SiteTheme.colors.accentText, SiteTheme.colors.accentTint)
            }
            Spacer(Modifier.height(12.dp))
            ProgressRail((t.actualPercent / 100).toFloat(), plannedMarker = (t.plannedPercent / 100).toFloat(), height = 8.dp,
                color = if (t.status == ActivityStatus.DELAYED) SiteTheme.colors.bad else SiteTheme.colors.accent)
            Text("${formatPercent(t.actualPercent)} done · ${formatPercent(t.plannedPercent)} planned by today",
                style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
            Spacer(Modifier.height(12.dp))
            val pa = com.aarush.cpm.ui.common.LocalProjectAccess.current
            if (pa.logProgress || pa.editSchedulePlan) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pa.logProgress) Button(onClick = onLog, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f), enabled = t.status != ActivityStatus.COMPLETED) { Text("Log progress") }
                if (pa.editSchedulePlan) OutlinedButton(onClick = onEdit, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) { Text("Edit") }
            }
            if (pa.logProgress && t.status != ActivityStatus.COMPLETED) {
                TextButton(onClick = onMarkComplete) { Text("Mark complete today") }
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("Dates")
            MetricRow("Baseline", "${formatDate(t.baselineStart)} → ${formatDate(t.baselineFinish)}")
            MetricRow("Forecast", "${formatDate(t.forecastStart)} → ${formatDate(t.forecastFinish)}",
                valueColor = if (t.delayDays > 0) SiteTheme.colors.bad else androidx.compose.ui.graphics.Color.Unspecified)
            a.actualStart?.let { MetricRow("Actual start", formatDate(it)) }
            a.actualCompletion?.let { MetricRow("Actual finish", formatDate(it)) }
            MetricRow("Duration", "${a.durationDays} working days" + if (a.isDurationManual) " (fixed)" else if (a.productivityPerDay > 0) " (from quantity)" else "")
            MetricRow("Delay", if (t.delayDays > 0) "${t.delayDays} working days" else "None")
            MetricRow("Float", "${t.totalFloat.coerceAtLeast(0)} working days", divider = true)

            Spacer(Modifier.height(8.dp))
            SectionLabel("Quantity & budget")
            if (a.linkedNotationCode.isNotBlank()) {
                MetricRow("BOQ notation", a.linkedNotationCode)
                MetricRow("Planned quantity", formatQuantity(round1(view.plannedQuantity), a.unit) + if (a.quantityOverride != null) " (manual)" else "")
                MetricRow("Done", formatQuantity(round1(a.completedQuantity), a.unit))
                if (a.productivityPerDay > 0) MetricRow("Crew output", "${formatQuantity(a.productivityPerDay, a.unit)}/day")
            }
            MetricRow("Category", a.category.name.replace('_', ' '))
            MetricRow("Budget", formatCurrency(view.budget) + if (a.budgetOverride != null) " (manual)" else "")
            if (view.materialCost > 0) MetricRow("of which BOQ materials", formatCurrency(view.materialCost))
            MetricRow("Earned value", formatCurrency(view.earnedValue))
            MetricRow("Labour", "${a.requiredLabourCount} × ${a.requiredLabourSkill.ifBlank { NO_VALUE }}", divider = true)

            if (view.predecessors.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Starts after")
                view.predecessors.forEach { d ->
                    Text("• ${names[d.dependsOnActivityId] ?: "#${d.dependsOnActivityId}"}" + if (d.lagDays > 0) " + ${d.lagDays} days" else "",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (a.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp)); SectionLabel("Notes"); Text(a.notes, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(12.dp))
            SectionLabel("Progress history · ${view.logs.size}")
            if (view.logs.isEmpty()) Text("Nothing logged yet.", style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
            view.logs.forEach { log ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(formatDate(log.date), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(listOfNotNull(
                            log.quantityDone.takeIf { it > 0 }?.let { formatQuantity(round1(it), a.unit) },
                            log.percentComplete?.let { "${formatPercent(it)} total" },
                            log.labourCount.takeIf { it > 0 }?.let { "$it workers" },
                            log.notes.takeIf { it.isNotBlank() },
                            com.aarush.cpm.ui.common.enteredByName(log.enteredBy)?.let { "by $it" }
                        ).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
                    }
                    // Previously removed immediately; now confirms first (and the screen offers Undo).
                    if (pa.logProgress) TextButton(onClick = { pendingLogRemoval = log }) { Text("Remove") }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (pa.editSchedulePlan) TextButton(onClick = { confirmDelete = true }) { Text("Delete activity", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(itemLabel = "\"${a.activityName}\" with its links and progress", onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false })
    }
    pendingLogRemoval?.let { log ->
        ConfirmRemoveProgressDialog(
            log = log, activityName = a.activityName, unit = a.unit, booksMaterial = a.linkedNotationCode.isNotBlank(),
            onConfirm = { pendingLogRemoval = null; onDeleteLog(log) },
            onDismiss = { pendingLogRemoval = null }
        )
    }
}

// ------------------------------------------------------------------ editor

@Composable
private fun <T> Dropdown(label: String, selected: String, options: List<T>, text: (T) -> String, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $selected", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(text(o)) }, onClick = { onPick(o); open = false }) }
        }
    }
}

@Composable
internal fun ActivityEditDialog(
    existing: ScheduleActivityView?,
    all: List<ScheduleActivityView>,
    notations: List<BOQNotation>,
    vendors: List<Vendor>,
    floorOptions: List<String>,
    onDismiss: () -> Unit,
    onSave: (ActivityInput) -> Unit
) {
    val a = existing?.activity
    val phases = (listOf(
        ResidentialScheduleTemplate.PHASE_PRE, ResidentialScheduleTemplate.PHASE_SUB, ResidentialScheduleTemplate.PHASE_SUPER,
        ResidentialScheduleTemplate.PHASE_ROOF, ResidentialScheduleTemplate.PHASE_SERVICES, ResidentialScheduleTemplate.PHASE_FINISH,
        ResidentialScheduleTemplate.PHASE_HANDOVER
    ) + all.map { it.activity.phase }).filter { it.isNotBlank() }.distinct()

    var name by remember { mutableStateOf(a?.activityName ?: "") }
    var phase by remember { mutableStateOf(a?.phase ?: ResidentialScheduleTemplate.PHASE_FINISH) }
    var floor by remember { mutableStateOf(a?.floorLabel ?: "") }
    var category by remember { mutableStateOf(a?.category ?: CostCategory.CIVIL_STRUCTURAL) }
    var notation by remember { mutableStateOf(a?.linkedNotationCode ?: "") }
    var share by remember { mutableStateOf(a?.boqSharePercent?.let { "%.1f".format(it) } ?: "100") }
    var qtyOverride by remember { mutableStateOf(a?.quantityOverride?.toString() ?: "") }
    var unit by remember { mutableStateOf(a?.unit ?: "") }
    var duration by remember { mutableStateOf(a?.durationDays?.toString() ?: "3") }
    var lockDuration by remember { mutableStateOf(a?.isDurationManual ?: (a == null)) }
    var productivity by remember { mutableStateOf(a?.productivityPerDay?.takeIf { it > 0 }?.toString() ?: "") }
    var skill by remember { mutableStateOf(a?.requiredLabourSkill ?: "") }
    var crew by remember { mutableStateOf(a?.requiredLabourCount?.toString() ?: "2") }
    var weight by remember { mutableStateOf(a?.budgetWeight?.toString() ?: "1") }
    var budgetOverride by remember { mutableStateOf(a?.budgetOverride?.let { "%.0f".format(it) } ?: "") }
    var vendorId by remember { mutableStateOf(a?.vendorId) }
    var milestone by remember { mutableStateOf(a?.isMilestone ?: false) }
    var notes by remember { mutableStateOf(a?.notes ?: "") }
    val preds = remember { mutableStateMapOf<Long, String>().apply { existing?.predecessors?.forEach { put(it.dependsOnActivityId, it.lagDays.toString()) } } }
    var showPreds by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add activity" else "Edit activity") },
        text = {
            Column(Modifier.heightIn(max = 580.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Activity name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Dropdown("Phase", phase, phases, { it }, { phase = it }, Modifier.fillMaxWidth())
                Dropdown("Floor", floor.ifBlank { "Whole building" }, listOf("") + floorOptions, { it.ifBlank { "Whole building" } }, { floor = it }, Modifier.fillMaxWidth())
                Dropdown("Category", category.name.replace('_', ' '), CostCategory.values().toList(), { it.name.replace('_', ' ') }, { category = it }, Modifier.fillMaxWidth())

                Spacer(Modifier.height(8.dp)); SectionLabel("Quantity from BOQ")
                Dropdown("BOQ notation", notation.ifBlank { "None (fixed duration)" }, listOf<BOQNotation?>(null) + notations,
                    { it?.code ?: "None (fixed duration)" }, { n -> notation = n?.code ?: ""; if (n != null) unit = n.defaultUnit }, Modifier.fillMaxWidth())
                if (notation.isNotBlank()) {
                    Row {
                        OutlinedTextField(share, { share = it }, label = { Text("Share of untagged %") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(qtyOverride, { qtyOverride = it }, label = { Text("Qty override") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                    Text("BOQ entries tagged with this activity's floor count in full; untagged entries are shared by %. Leave override blank to follow the BOQ.",
                        style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
                }
                OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(8.dp)); SectionLabel("Duration & crew")
                Row {
                    OutlinedTextField(duration, { duration = it.filter(Char::isDigit); lockDuration = true }, label = { Text("Working days") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(productivity, { productivity = it }, label = { Text("Crew output/day") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = lockDuration, onCheckedChange = { lockDuration = it })
                    Text("Fix duration (don't recompute from quantity ÷ crew output)", style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    OutlinedTextField(skill, { skill = it }, label = { Text("Labour skill") }, singleLine = true, modifier = Modifier.weight(1.4f))
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(crew, { crew = it.filter(Char::isDigit) }, label = { Text("Workers") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                Dropdown("Vendor", vendors.find { it.id == vendorId }?.name ?: "None", listOf<Vendor?>(null) + vendors,
                    { it?.name ?: "None" }, { vendorId = it?.id }, Modifier.fillMaxWidth())

                Spacer(Modifier.height(8.dp)); SectionLabel("Budget")
                Row {
                    OutlinedTextField(weight, { weight = it }, label = { Text("Weight in category") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(budgetOverride, { budgetOverride = it }, label = { Text("Fixed budget ₹") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Text(if (category == CostCategory.CIVIL_STRUCTURAL) "Civil: BOQ material cost + civil labour contracts shared by man-days."
                    else "Share of the ${category.name.replace('_', ' ')} budget by weight. Leave fixed budget blank to follow the budget.",
                    style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)

                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showPreds = !showPreds }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Text("Starts after: ${if (preds.isEmpty()) "nothing" else "${preds.size} activit${if (preds.size == 1) "y" else "ies"}"}")
                }
                if (showPreds) {
                    all.filter { it.activity.id != a?.id }.forEach { other ->
                        val id = other.activity.id
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = id in preds, onCheckedChange = { c -> if (c) preds[id] = "0" else preds.remove(id) })
                            Text(other.activity.activityName, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (id in preds) {
                                OutlinedTextField(preds[id] ?: "0", { preds[id] = it.filter(Char::isDigit).take(3) }, label = { Text("Lag") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(72.dp))
                            }
                        }
                    }
                    Text("Links that would create a loop are ignored.", style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = milestone, onCheckedChange = { milestone = it })
                    Text("Milestone", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val d = duration.toIntOrNull() ?: 0
                error = when {
                    name.isBlank() -> "Give the activity a name."
                    d < 1 -> "Duration must be at least 1 working day."
                    (share.toDoubleOrNull() ?: -1.0) < 0 -> "Share % can't be negative."
                    else -> null
                }
                if (error == null) onSave(
                    ActivityInput(
                        name = name.trim(), phase = phase, floorLabel = floor, category = category, notationCode = notation,
                        sharePercent = share.toDoubleOrNull() ?: 100.0, quantityOverride = qtyOverride.toDoubleOrNull(), unit = unit,
                        durationDays = d, isDurationManual = lockDuration || (productivity.toDoubleOrNull() ?: 0.0) <= 0,
                        productivityPerDay = productivity.toDoubleOrNull() ?: 0.0, labourSkill = skill.trim(), labourCount = crew.toIntOrNull() ?: 0,
                        budgetWeight = weight.toDoubleOrNull() ?: 1.0, budgetOverride = budgetOverride.toDoubleOrNull(), vendorId = vendorId,
                        isMilestone = milestone, notes = notes, predecessors = preds.map { (id, lag) -> id to (lag.toIntOrNull() ?: 0) }
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ------------------------------------------------------------------ remove progress entry

/**
 * Confirmation before removing a site progress entry, spelling out what else changes:
 * the activity's progress is recalculated and — for BOQ-linked work — the material usage
 * this entry booked (quantity × coefficients) is returned to stock. Used from both the
 * activity sheet and the Site log so the behaviour is identical everywhere.
 */
@Composable
internal fun ConfirmRemoveProgressDialog(
    log: ActivityProgressLog,
    activityName: String,
    unit: String,
    booksMaterial: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val what = listOfNotNull(
        log.quantityDone.takeIf { it > 0 }?.let { formatQuantity(round1(it), unit) + " done" },
        log.percentComplete?.let { "${formatPercent(it)} complete" },
        log.labourCount.takeIf { it > 0 }?.let { "$it workers" }
    ).joinToString(" · ")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove progress entry?") },
        text = {
            Column {
                Text("${formatDate(log.date)}${if (activityName.isNotBlank()) " · $activityName" else ""}", style = MaterialTheme.typography.titleSmall)
                if (what.isNotBlank()) Text(what, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                Spacer(Modifier.height(10.dp))
                Text("• The activity's progress and the project's earned value will be recalculated.", style = MaterialTheme.typography.bodySmall)
                if (booksMaterial && log.quantityDone > 0) {
                    Text("• The materials this entry used (from the BOQ coefficients) will be returned to stock in Material Status.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("• You can Undo straight after.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
