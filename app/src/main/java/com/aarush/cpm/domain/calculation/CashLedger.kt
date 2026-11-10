package com.aarush.cpm.domain.calculation

import com.aarush.cpm.data.entity.CashEntry
import com.aarush.cpm.data.entity.CashEntryType
import com.aarush.cpm.data.entity.Expense
import com.aarush.cpm.data.entity.ProjectPartner
import com.aarush.cpm.data.entity.TransactionDirection

/** One partner's position in a project. */
data class PartnerCash(
    val partner: ProjectPartner,
    /** Profit share in rupees: project value × share %. */
    val share: Double,
    val profitTaken: Double,
    /** Expenses this partner paid from their own pocket. */
    val spentOwn: Double,
    val refilled: Double
) {
    val shareLeft: Double get() = share - profitTaken
    /** What the office still owes this partner for expenses they paid. */
    val toRefill: Double get() = (spentOwn - refilled).coerceAtLeast(0.0)
}

data class CashPosition(
    val received: Double,
    /** Expenses paid from office cash. */
    val spentOffice: Double,
    val partners: List<PartnerCash>
) {
    val profitTaken: Double get() = partners.sumOf { it.profitTaken }
    val refilled: Double get() = partners.sumOf { it.refilled }
    val spentByPartners: Double get() = partners.sumOf { it.spentOwn }
    /** Money in hand: received − profit taken − refills paid − expenses paid from office cash. */
    val officeCash: Double get() = received - profitTaken - refilled - spentOffice
    val toRefill: Double get() = partners.sumOf { it.toRefill }
    /** All project spending, whoever paid. */
    val totalSpent: Double get() = spentOffice + spentByPartners

    /** Suggested refill for [p]: what they're owed, limited to the office cash available. */
    fun suggestedRefill(p: PartnerCash): Double = minOf(p.toRefill, officeCash.coerceAtLeast(0.0))
}

/**
 * The office-cash and partner ledger. Pure, so the rules are unit-tested.
 *
 * Example: value ₹20,00,000; ₹6,00,000 advance received; each partner (10%) takes ₹2,00,000
 * → office cash ₹2,00,000. If a partner then pays ₹50,000 of expenses themselves, they are
 * owed ₹50,000 until the office refills them.
 */
object CashLedger {
    fun position(projectValue: Double, expenses: List<Expense>, partners: List<ProjectPartner>, entries: List<CashEntry>): CashPosition {
        val received = expenses.filter { it.direction == TransactionDirection.RECEIVED }.sumOf { it.totalAmount }
        val spending = expenses.filter { it.direction == TransactionDirection.EXPENSE }
        val known = partners.map { it.id }.toSet()
        // An expense whose partner was removed counts as office cash, so nothing goes missing.
        val spentOffice = spending.filter { it.paidByPartnerId == null || it.paidByPartnerId !in known }.sumOf { it.totalAmount }
        return CashPosition(
            received = received,
            spentOffice = spentOffice,
            partners = partners.map { p ->
                PartnerCash(
                    partner = p,
                    share = projectValue * p.sharePercent / 100.0,
                    profitTaken = entries.filter { it.partnerId == p.id && it.type == CashEntryType.PROFIT_TAKEN }.sumOf { it.amount },
                    spentOwn = spending.filter { it.paidByPartnerId == p.id }.sumOf { it.totalAmount },
                    refilled = entries.filter { it.partnerId == p.id && it.type == CashEntryType.REFILL }.sumOf { it.amount }
                )
            }
        )
    }
}
