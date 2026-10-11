package com.aarush.cpm.screenshots

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.aarush.cpm.data.entity.*
import com.aarush.cpm.data.repository.CategoryBudgetVsActual
import com.aarush.cpm.domain.calculation.CompoundWallBudget
import com.aarush.cpm.ui.project.CategoryBudgetTable
import com.aarush.cpm.ui.project.ProjectValueHeader
import com.aarush.cpm.ui.theme.AarushCPMTheme
import com.aarush.cpm.ui.vendor.VendorCard
import org.junit.Rule
import org.junit.Test

/*
 * Review screenshots for the compound wall. Illustrative example (not the demo project):
 * the demo building (2,000 sqft x Rs 2,500) plus a compound wall of 180 rft x Rs 1,800.
 * Category budgets come from the app's own CompoundWallBudget rule; actuals are the demo's.
 */
private const val WALL = 180.0 * 1_800.0
private val wallProject = DemoBudget.project.copy(projectValue = DemoBudget.project.projectValue + WALL)
private val wallCategories: List<CategoryBudgetVsActual> = DemoBudget.categories.map { c ->
    val allocs = DemoBudget.categories.map { it.allocation }
    val budget = CompoundWallBudget.categoryBudget(c.allocation.category, c.allocation.percentOfProjectValue, wallProject.projectValue, WALL, allocs)
    c.copy(budget = budget, remaining = (budget - c.actual).coerceAtLeast(0.0), variance = budget - c.actual)
}
private val vendor = Vendor(id = 1, projectId = 1, name = "Sri Murugan Constructions", contact = "", workCategory = VendorWorkCategory.CIVIL,
    rateType = VendorRateType.PER_SQFT, rate = 450.0, quantity = 2000.0, contractValue = 900_000.0 + 108_000.0,
    startDate = 0, endDate = 0, amountPaid = 200_000.0)
private val work = VendorWork(id = 1, projectId = 1, vendorId = 1, label = "Compound wall", quantity = 180.0, rate = 600.0, amount = 108_000.0)

@Composable
private fun Screen() {
    AarushCPMTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProjectValueHeader(wallProject, onEditAreaRate = {}, compoundWallValue = WALL)
                CategoryBudgetTable(wallCategories, compoundWallValue = WALL)
                VendorCard(vendor, payments = emptyList(), works = listOf(work), onEdit = {}, onDelete = {}, onAddWork = {}, onEditWork = {})
            }
        }
    }
}

class CompoundWallScreenshots {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 590, screenHeight = 2000, xdpi = 240, ydpi = 240, density = Density.HIGH),
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test fun compound_wall_budget_and_vendor() = paparazzi.snapshot { Screen() }
}
