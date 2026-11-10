package com.aarush.cpm.ui.cash

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.aarush.cpm.data.entity.*
import com.aarush.cpm.domain.calculation.CashLedger
import com.aarush.cpm.ui.theme.AarushCPMTheme
import org.junit.Rule
import org.junit.Test

/* Review screenshot: Expenses > Cash for the partners' example (₹20 L project, ₹6 L advance,
 * ₹2 L profit each, some expenses from office cash and from own money). */
class CashScreenshots {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 590, screenHeight = 2600, xdpi = 240, ydpi = 240, density = Density.HIGH),
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    private val me = ProjectPartner(id = 1, projectId = 1, name = "Thamesh", sharePercent = 10.0)
    private val partner = ProjectPartner(id = 2, projectId = 1, name = "Partner", sharePercent = 10.0)
    private fun e(amount: Double, dir: TransactionDirection = TransactionDirection.EXPENSE, by: Long? = null) =
        Expense(projectId = 1, date = 0, category = CostCategory.CIVIL_STRUCTURAL, type = ExpenseType.MATERIAL, direction = dir,
            itemOrMaterialName = "x", rate = amount, totalAmount = amount, paymentMode = PaymentMode.CASH,
            paymentStatus = PaymentStatus.PAID, paidByPartnerId = by)
    private val entries = listOf(
        CashEntry(id = 1, projectId = 1, partnerId = 1, type = CashEntryType.PROFIT_TAKEN, amount = 2_00_000.0, date = 1_762_700_000_000),
        CashEntry(id = 2, projectId = 1, partnerId = 2, type = CashEntryType.PROFIT_TAKEN, amount = 2_00_000.0, date = 1_762_700_000_000)
    )
    private val pos = CashLedger.position(20_00_000.0,
        listOf(e(6_00_000.0, TransactionDirection.RECEIVED), e(1_50_000.0), e(80_000.0, by = 1), e(30_000.0, by = 2)),
        listOf(me, partner), entries)

    @Test fun cash_owner_view() = paparazzi.snapshot {
        AarushCPMTheme(darkTheme = false) {
            Surface(color = MaterialTheme.colorScheme.background) { CashContent(pos, entries, isOwner = true, canManage = true) }
        }
    }
}
