package com.aarush.cpm.ui.cash

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.entity.CashEntry
import com.aarush.cpm.data.entity.CashEntryType
import com.aarush.cpm.data.entity.ProjectPartner
import com.aarush.cpm.data.entity.TeamRole
import com.aarush.cpm.data.repository.CashRepository
import com.aarush.cpm.domain.calculation.CashPosition
import com.aarush.cpm.domain.calculation.PartnerCash
import com.aarush.cpm.ui.common.*
import com.aarush.cpm.ui.project.LedgerRow
import com.aarush.cpm.ui.project.LedgerSection
import com.aarush.cpm.ui.theme.FigureStyle
import com.aarush.cpm.ui.theme.SiteTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/*
 * Expenses > Cash (v12): office cash and the partners' money.
 *   Office cash = received − profit taken − refills − expenses paid from office cash.
 *   Each partner: profit share (their % of the project value), taken, still to take; spent
 *   from own money, refilled, still to refill. Refill and Take profit are recorded here.
 */

@OptIn(ExperimentalCoroutinesApi::class)
class CashViewModel(private val repo: CashRepository) : ViewModel() {
    private val projectId = MutableStateFlow<Long?>(null)

    val position: StateFlow<CashPosition?> = projectId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repo.observePosition(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val entries: StateFlow<List<CashEntry>> = projectId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeEntries(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var message by mutableStateOf<String?>(null)
        private set

    fun init(id: Long) { projectId.value = id }

    fun savePartners(rows: List<ProjectPartner>, removed: List<ProjectPartner>, onDone: () -> Unit) {
        val id = projectId.value ?: return
        viewModelScope.launch {
            for (p in removed) {
                val r = repo.deletePartner(p)
                if (r.isFailure) { message = r.exceptionOrNull()?.message; return@launch }
            }
            for (p in rows) {
                val r = repo.savePartner(p.copy(projectId = id))
                if (r.isFailure) { message = r.exceptionOrNull()?.message; return@launch }
            }
            message = null
            onDone()
        }
    }

    fun record(partnerId: Long, type: CashEntryType, amount: Double, notes: String, onDone: () -> Unit) {
        val id = projectId.value ?: return
        viewModelScope.launch {
            repo.record(id, partnerId, type, amount, System.currentTimeMillis(), notes)
                .onSuccess { message = null; onDone() }
                .onFailure { message = it.message }
        }
    }

    fun delete(entry: CashEntry, snackbar: SnackbarHostState?) {
        viewModelScope.launch {
            repo.deleteEntry(entry)
            snackbar?.let { sb -> showUndoDeleteSnackbar(sb, "Entry deleted") { viewModelScope.launch { repo.restoreEntry(entry) } } }
        }
    }

    fun dismissMessage() { message = null }
}

/** Owner and Partners record refills and profit; only the Owner sets up the partners. */
@Composable
private fun canManageCash() = LocalAccess.current.role.let { it == TeamRole.OWNER || it == TeamRole.PARTNER }

@Composable
fun CashTabContent(projectId: Long, viewModel: CashViewModel, snackbarHostState: SnackbarHostState?) {
    LaunchedEffect(projectId) { viewModel.init(projectId) }
    val pos = viewModel.position.collectAsState().value
    val entries by viewModel.entries.collectAsState()
    val isOwner = LocalAccess.current.role == TeamRole.OWNER
    val canManage = canManageCash()
    var editPartners by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<Pair<PartnerCash, CashEntryType>?>(null) }
    var pendingDelete by remember { mutableStateOf<CashEntry?>(null) }

    if (pos == null) { SkeletonScreen(Modifier.padding(16.dp), headline = true, sections = 2, rows = 4); return }

    CashContent(
        pos = pos, entries = entries, isOwner = isOwner, canManage = canManage, message = viewModel.message,
        onDismissMessage = viewModel::dismissMessage,
        onEditPartners = { editPartners = true },
        onRefill = { action = it to CashEntryType.REFILL },
        onTakeProfit = { action = it to CashEntryType.PROFIT_TAKEN },
        onDelete = { pendingDelete = it }
    )

    if (editPartners) {
        PartnersDialog(pos.partners.map { it.partner }, onDismiss = { editPartners = false }) { rows, removed ->
            viewModel.savePartners(rows, removed) { editPartners = false }
        }
    }
    action?.let { (pc, type) ->
        CashEntryDialog(
            partner = pc, type = type,
            suggested = if (type == CashEntryType.REFILL) pos.suggestedRefill(pc) else minOf(pc.shareLeft, pos.officeCash).coerceAtLeast(0.0),
            officeCash = pos.officeCash,
            onDismiss = { action = null },
            onSave = { amount, notes -> viewModel.record(pc.partner.id, type, amount, notes) { action = null } }
        )
    }
    pendingDelete?.let { e ->
        ConfirmDeleteDialog(
            itemLabel = "this ${formatCurrency(e.amount)} entry from ${formatDate(e.date)}",
            onConfirm = { viewModel.delete(e, snackbarHostState); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }
}

/** Stateless body (screenshot tests render it directly). */
@Composable
internal fun CashContent(
    pos: CashPosition, entries: List<CashEntry>, isOwner: Boolean, canManage: Boolean, message: String? = null,
    onDismissMessage: () -> Unit = {}, onEditPartners: () -> Unit = {},
    onRefill: (PartnerCash) -> Unit = {}, onTakeProfit: (PartnerCash) -> Unit = {}, onDelete: (CashEntry) -> Unit = {}
) {
    val names = pos.partners.associate { it.partner.id to it.partner.name }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        message?.let { m ->
            item {
                AppCard(Modifier.fillMaxWidth(), onClick = onDismissMessage,
                    colors = CardDefaults.outlinedCardColors(containerColor = SiteTheme.colors.badTint)) {
                    Text(m, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }
        }
        item { OfficeCash(pos) }
        if (pos.partners.isEmpty()) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("No partners set up", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (isOwner) "Add the partners and their profit share (e.g. 10% each). Then every expense can say who paid: " +
                                "office cash or a partner's own money, which the office refills later."
                            else "The Owner sets up the partners for this project.",
                            style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted
                        )
                        if (isOwner) {
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = onEditPartners, shape = MaterialTheme.shapes.small) { Text("Set up partners") }
                        }
                    }
                }
            }
        }
        items(pos.partners, key = { it.partner.id }) { pc ->
            PartnerSection(pc, pos, canManage, onRefill = { onRefill(pc) }, onTakeProfit = { onTakeProfit(pc) })
        }
        if (pos.partners.isNotEmpty()) {
            item {
                LedgerSection("Whole project") {
                    LedgerRow("Spent from office cash", formatCurrency(pos.spentOffice))
                    LedgerRow("Spent from partners' money", formatCurrency(pos.spentByPartners))
                    LedgerRow("Total spent", formatCurrency(pos.totalSpent), emphasize = true, divider = false)
                }
                if (isOwner) {
                    TextButton(onClick = onEditPartners, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit partners") }
                }
            }
        }
        if (entries.isNotEmpty()) {
            item { SectionTitle("Profit taken & refills") }
            items(entries, key = { it.id }) { e ->
                EntryRow(e, names[e.partnerId] ?: "Removed partner", canManage, onDelete = { onDelete(e) })
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun OfficeCash(pos: CashPosition) {
    Column(Modifier.fillMaxWidth()) {
        ColumnLabel("Office cash")
        FitText(formatCurrency(pos.officeCash), style = FigureStyle.copy(fontSize = 28.sp),
            color = if (pos.officeCash < 0) SiteTheme.colors.bad else Color.Unspecified, minFontSize = 16.sp)
        if (pos.officeCash < 0) {
            Text("More has gone out than came in. Record the client payment, or check the entries.",
                style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.bad)
        }
        Spacer(Modifier.height(8.dp))
        LedgerSection("How it's worked out") {
            LedgerRow("Received from client", formatCurrency(pos.received))
            LedgerRow("Profit taken by partners", "− " + formatCurrency(pos.profitTaken))
            LedgerRow("Refilled to partners", "− " + formatCurrency(pos.refilled))
            LedgerRow("Spent from office cash", "− " + formatCurrency(pos.spentOffice), divider = false)
        }
        if (pos.toRefill > 0) {
            Spacer(Modifier.height(6.dp))
            Text("Still to refill to partners: ${formatCurrency(pos.toRefill)}", style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold, color = SiteTheme.colors.warn)
        }
    }
}

@Composable
private fun PartnerSection(pc: PartnerCash, pos: CashPosition, canManage: Boolean, onRefill: () -> Unit, onTakeProfit: () -> Unit) {
    LedgerSection("${pc.partner.name} · ${formatPercent(pc.partner.sharePercent)} share") {
        LedgerRow("Profit share", formatCurrency(pc.share), caption = "${formatPercent(pc.partner.sharePercent)} of project value")
        LedgerRow("Profit taken", formatCurrency(pc.profitTaken))
        LedgerRow("Still to take", formatCurrency(pc.shareLeft),
            valueColor = if (pc.shareLeft < 0) SiteTheme.colors.bad else Color.Unspecified)
        LedgerRow("Spent from own money", formatCurrency(pc.spentOwn))
        LedgerRow("Refilled", formatCurrency(pc.refilled))
        LedgerRow("To refill", formatCurrency(pc.toRefill), emphasize = true, divider = false,
            valueColor = if (pc.toRefill > 0) SiteTheme.colors.warn else Color.Unspecified)
        if (canManage) {
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val suggested = pos.suggestedRefill(pc)
                Button(onClick = onRefill, enabled = pc.toRefill > 0, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) {
                    Text(if (suggested > 0) "Refill ${formatCurrencyShort(suggested)}" else "Refill", maxLines = 1)
                }
                OutlinedButton(onClick = onTakeProfit, enabled = pc.shareLeft > 0, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) {
                    Text("Take profit", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun EntryRow(e: CashEntry, partnerName: String, canDelete: Boolean, onDelete: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (e.type == CashEntryType.REFILL) "Refill to $partnerName" else "Profit taken by $partnerName",
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val by = enteredByName(e.enteredBy)
                Text(formatDateTime(e.date) + (by?.let { " · by $it" } ?: "") + (e.notes.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(formatCurrency(e.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (canDelete) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete entry", tint = SiteTheme.colors.muted) }
            } else Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun CashEntryDialog(
    partner: PartnerCash, type: CashEntryType, suggested: Double, officeCash: Double,
    onDismiss: () -> Unit, onSave: (Double, String) -> Unit
) {
    var amount by remember { mutableStateOf(if (suggested > 0) "%.0f".format(suggested) else "") }
    var notes by remember { mutableStateOf("") }
    val value = amount.toDoubleOrNull() ?: 0.0
    val refill = type == CashEntryType.REFILL
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (refill) "Refill ${partner.partner.name}" else "${partner.partner.name} takes profit") },
        text = {
            Column {
                Text(
                    if (refill) "Owed for expenses paid from own money: ${formatCurrency(partner.toRefill)}"
                    else "Profit share still to take: ${formatCurrency(partner.shareLeft)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Office cash now: ${formatCurrency(officeCash)}", style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount (₹)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Note (optional)") }, singleLine = true,
                    shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                val warning = when {
                    value > officeCash -> "This is more than the office cash; office cash will go negative."
                    refill && value > partner.toRefill -> "This is more than ${partner.partner.name} is owed."
                    !refill && value > partner.shareLeft -> "This is more than the profit share left."
                    else -> null
                }
                warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.warn, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value, notes) }, enabled = value > 0) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private data class PartnerRow(val key: Int, val original: ProjectPartner?, val name: String, val share: String, val deviceId: String?)

@Composable
private fun PartnersDialog(existing: List<ProjectPartner>, onDismiss: () -> Unit, onSave: (List<ProjectPartner>, List<ProjectPartner>) -> Unit) {
    val devices = LocalTeam.current?.devices.orEmpty()
    var nextKey by remember { mutableStateOf(100) }
    val rows = remember {
        mutableStateListOf<PartnerRow>().apply {
            if (existing.isEmpty()) {
                add(PartnerRow(1, null, "", "10", null)); add(PartnerRow(2, null, "", "10", null))
            } else existing.forEachIndexed { i, p -> add(PartnerRow(i + 1, p, p.name, "%.4f".format(p.sharePercent).trimEnd('0').trimEnd('.'), p.deviceId)) }
        }
    }
    val removed = remember { mutableStateListOf<ProjectPartner>() }
    val total = rows.sumOf { it.share.toDoubleOrNull() ?: 0.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Partners") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Each partner's profit share is a % of the project value. It doesn't change the category budgets.",
                    style = MaterialTheme.typography.bodySmall, color = SiteTheme.colors.muted)
                rows.forEachIndexed { i, r ->
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(r.name, { v -> rows[i] = r.copy(name = v.take(30)) }, label = { Text("Name") }, singleLine = true,
                            shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(r.share, { v -> rows[i] = r.copy(share = v.filter { c -> c.isDigit() || c == '.' }) }, label = { Text("Share %") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = MaterialTheme.shapes.small, modifier = Modifier.width(96.dp))
                        IconButton(onClick = { r.original?.let { removed += it }; rows.removeAt(i) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove ${r.name}")
                        }
                    }
                    if (devices.isNotEmpty()) {
                        var menu by remember(r.key) { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { menu = true }, contentPadding = PaddingValues(0.dp)) {
                                Text("Phone: " + (devices.firstOrNull { it.deviceId == r.deviceId }?.name ?: "none"),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("None") }, onClick = { rows[i] = r.copy(deviceId = null); menu = false })
                                devices.forEach { d ->
                                    DropdownMenuItem(text = { Text(d.name) }, onClick = { rows[i] = r.copy(deviceId = d.deviceId); menu = false })
                                }
                            }
                        }
                    }
                }
                if (rows.size < 4) {
                    TextButton(onClick = { rows += PartnerRow(nextKey++, null, "", "10", null) }) { Text("Add partner") }
                }
                Text("Total profit share: ${formatPercent(total)} of project value", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp))
                Text("The phone you pick makes \"Paid by\" default to that partner for expenses entered on it.",
                    style = MaterialTheme.typography.labelSmall, color = SiteTheme.colors.muted)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(rows.map { r ->
                        (r.original ?: ProjectPartner(projectId = 0, name = "", sharePercent = 0.0))
                            .copy(name = r.name, sharePercent = r.share.toDoubleOrNull() ?: 0.0, deviceId = r.deviceId)
                    }, removed.toList())
                },
                enabled = rows.all { it.name.isNotBlank() && it.share.toDoubleOrNull() != null }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
