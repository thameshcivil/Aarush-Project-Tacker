package com.aarush.cpm.calculation

import com.aarush.cpm.data.entity.*
import com.aarush.cpm.domain.calculation.CashLedger
import org.junit.Assert.assertEquals
import org.junit.Test

/** Office cash and partners, using the example the partners described. */
class CashLedgerTest {
    private val value = 20_00_000.0
    private val me = ProjectPartner(id = 1, projectId = 1, name = "Thamesh", sharePercent = 10.0)
    private val partner = ProjectPartner(id = 2, projectId = 1, name = "Partner", sharePercent = 10.0)

    private fun received(amount: Double) = Expense(projectId = 1, date = 0, category = CostCategory.OTHER_MISC, type = ExpenseType.OTHER,
        direction = TransactionDirection.RECEIVED, itemOrMaterialName = "Advance", rate = amount, totalAmount = amount,
        paymentMode = PaymentMode.BANK_TRANSFER, paymentStatus = PaymentStatus.PAID)
    private fun spent(amount: Double, by: Long? = null) = Expense(projectId = 1, date = 0, category = CostCategory.CIVIL_STRUCTURAL,
        type = ExpenseType.MATERIAL, itemOrMaterialName = "Cement", rate = amount, totalAmount = amount,
        paymentMode = PaymentMode.CASH, paymentStatus = PaymentStatus.PAID, paidByPartnerId = by)
    private fun entry(p: ProjectPartner, type: CashEntryType, amount: Double) =
        CashEntry(projectId = 1, partnerId = p.id, type = type, amount = amount, date = 0)

    @Test fun advanceMinusProfitIsOfficeCash() {
        // ₹20 L project, 30% mobilisation, each partner takes 10% of the value.
        val pos = CashLedger.position(value, listOf(received(6_00_000.0)), listOf(me, partner),
            listOf(entry(me, CashEntryType.PROFIT_TAKEN, 2_00_000.0), entry(partner, CashEntryType.PROFIT_TAKEN, 2_00_000.0)))
        assertEquals(2_00_000.0, pos.officeCash, 0.001)
        assertEquals(2_00_000.0, pos.partners[0].share, 0.001)
        assertEquals(0.0, pos.partners[0].shareLeft, 0.001)
    }

    @Test fun ownMoneyIsOwedUntilRefilled() {
        val expenses = listOf(received(6_00_000.0), spent(1_50_000.0), spent(80_000.0, by = me.id), spent(30_000.0, by = partner.id))
        val taken = listOf(entry(me, CashEntryType.PROFIT_TAKEN, 2_00_000.0), entry(partner, CashEntryType.PROFIT_TAKEN, 2_00_000.0))
        var pos = CashLedger.position(value, expenses, listOf(me, partner), taken)
        // Office: 6 L − 4 L profit − 1.5 L office spend = 50,000.
        assertEquals(50_000.0, pos.officeCash, 0.001)
        assertEquals(80_000.0, pos.partners[0].toRefill, 0.001)
        assertEquals(30_000.0, pos.partners[1].toRefill, 0.001)
        assertEquals(2_60_000.0, pos.totalSpent, 0.001)
        // Suggestion is limited by the cash available.
        assertEquals(50_000.0, pos.suggestedRefill(pos.partners[0]), 0.001)
        assertEquals(30_000.0, pos.suggestedRefill(pos.partners[1]), 0.001)

        // Next stage payment of ₹6 L arrives; refill both in full.
        pos = CashLedger.position(value, expenses + received(6_00_000.0), listOf(me, partner),
            taken + entry(me, CashEntryType.REFILL, 80_000.0) + entry(partner, CashEntryType.REFILL, 30_000.0))
        assertEquals(0.0, pos.toRefill, 0.001)
        assertEquals(6_50_000.0 - 1_10_000.0, pos.officeCash, 0.001)
    }

    @Test fun expenseOfARemovedPartnerCountsAsOfficeCash() {
        val pos = CashLedger.position(value, listOf(received(1_00_000.0), spent(10_000.0, by = 99)), listOf(me), emptyList())
        assertEquals(90_000.0, pos.officeCash, 0.001)
        assertEquals(0.0, pos.partners[0].spentOwn, 0.001)
    }
}
