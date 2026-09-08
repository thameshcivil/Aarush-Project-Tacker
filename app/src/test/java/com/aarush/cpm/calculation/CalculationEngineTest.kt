package com.aarush.cpm.calculation

import com.aarush.cpm.data.entity.CostAllocation
import com.aarush.cpm.data.entity.CostCategory
import com.aarush.cpm.data.entity.VendorRateType
import com.aarush.cpm.domain.calculation.CalculationEngine
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CalculationEngineTest {

    @Test
    fun `project value equals plinth area times rate`() {
        val value = CalculationEngine.projectValue(2000.0, 2500.0)
        assertThat(value).isEqualTo(50_00_000.0)
    }

    @Test
    fun `category budget is percent of project value`() {
        val budget = CalculationEngine.categoryBudget(50_00_000.0, 50.0)
        assertThat(budget).isEqualTo(25_00_000.0)
    }

    @Test
    fun `allocation of exactly 100 percent is valid`() {
        val allocations = listOf(
            CostAllocation(projectId = 1, category = CostCategory.CIVIL_STRUCTURAL, percentOfProjectValue = 50.0),
            CostAllocation(projectId = 1, category = CostCategory.MEP, percentOfProjectValue = 15.0),
            CostAllocation(projectId = 1, category = CostCategory.PAINTING, percentOfProjectValue = 8.0),
            CostAllocation(projectId = 1, category = CostCategory.JOINERY, percentOfProjectValue = 12.0),
            CostAllocation(projectId = 1, category = CostCategory.OTHER_MISC, percentOfProjectValue = 15.0)
        )
        assertThat(CalculationEngine.isAllocationValid(allocations)).isTrue()
    }

    @Test
    fun `allocation not summing to 100 percent is invalid`() {
        val allocations = listOf(
            CostAllocation(projectId = 1, category = CostCategory.CIVIL_STRUCTURAL, percentOfProjectValue = 50.0),
            CostAllocation(projectId = 1, category = CostCategory.MEP, percentOfProjectValue = 15.0)
        )
        assertThat(CalculationEngine.isAllocationValid(allocations)).isFalse()
    }

    @Test
    fun `material and labour budget split within category`() {
        val categoryBudget = 25_00_000.0
        assertThat(CalculationEngine.materialBudget(categoryBudget, 65.0)).isEqualTo(16_25_000.0)
        assertThat(CalculationEngine.labourBudget(categoryBudget, 35.0)).isEqualTo(8_75_000.0)
    }

    @Test
    fun `vendor contract value per sqft`() {
        val value = CalculationEngine.vendorContractValue(
            VendorRateType.PER_SQFT, rate = 850.0, quantity = 2000.0, projectValue = 0.0
        )
        assertThat(value).isEqualTo(17_00_000.0)
    }

    @Test
    fun `vendor contract value percentage of project`() {
        val value = CalculationEngine.vendorContractValue(
            VendorRateType.PERCENTAGE, rate = 10.0, quantity = 0.0, projectValue = 50_00_000.0
        )
        assertThat(value).isEqualTo(5_00_000.0)
    }

    @Test
    fun `required material quantity from BOQ and coefficient`() {
        val required = CalculationEngine.requiredMaterialQuantity(boqQuantity = 100.0, coefficientPerUnit = 6.0)
        assertThat(required).isEqualTo(600.0)
    }

    @Test
    fun `waste and total required quantity`() {
        val required = 600.0
        val waste = CalculationEngine.wasteQuantity(required, 5.0)
        assertThat(waste).isEqualTo(30.0)
        assertThat(CalculationEngine.totalRequiredQuantity(required, 5.0)).isEqualTo(630.0)
    }

    @Test
    fun `current stock is purchased minus used`() {
        assertThat(CalculationEngine.currentStock(purchasedQuantity = 600.0, usedQuantity = 450.0)).isEqualTo(150.0)
    }

    @Test
    fun `balance to purchase never goes negative`() {
        assertThat(CalculationEngine.balanceToPurchase(requiredQuantity = 1000.0, purchasedQuantity = 600.0)).isEqualTo(400.0)
        assertThat(CalculationEngine.balanceToPurchase(requiredQuantity = 500.0, purchasedQuantity = 600.0)).isEqualTo(0.0)
    }

    @Test
    fun `expense total is quantity times rate`() {
        assertThat(CalculationEngine.expenseTotal(100.0, 420.0)).isEqualTo(42_000.0)
        assertThat(CalculationEngine.expenseTotal(10.0, 900.0)).isEqualTo(9_000.0)
    }

    @Test
    fun `client balance is project value minus received`() {
        assertThat(CalculationEngine.clientBalance(50_00_000.0, 20_00_000.0)).isEqualTo(30_00_000.0)
    }

    @Test
    fun `gross profit and profit percent`() {
        val profit = CalculationEngine.grossProfit(50_00_000.0, 40_00_000.0)
        assertThat(profit).isEqualTo(10_00_000.0)
        assertThat(CalculationEngine.profitPercent(profit, 50_00_000.0)).isEqualTo(20.0)
    }

    @Test
    fun `cash balance is received minus spent`() {
        assertThat(CalculationEngine.cashBalance(20_00_000.0, 15_00_000.0)).isEqualTo(5_00_000.0)
    }

    @Test
    fun `required teams rounds up`() {
        // 500 sqm remaining, 10 sqm per team per day -> 50 team-days worth; with 2 teams' combined rate...
        assertThat(CalculationEngine.requiredTeams(remainingQuantity = 500.0, productivityPerTeamPerDay = 10.0)).isEqualTo(50)
        assertThat(CalculationEngine.requiredTeams(remainingQuantity = 21.0, productivityPerTeamPerDay = 10.0)).isEqualTo(3)
    }

    @Test
    fun `material shortage is clamped at zero`() {
        assertThat(CalculationEngine.materialShortage(requiredInWindow = 150.0, currentStock = 70.0)).isEqualTo(80.0)
        assertThat(CalculationEngine.materialShortage(requiredInWindow = 50.0, currentStock = 70.0)).isEqualTo(0.0)
    }

    @Test
    fun `labour shortage is clamped at zero`() {
        assertThat(CalculationEngine.labourShortage(requiredCount = 12, availableCount = 5)).isEqualTo(7)
        assertThat(CalculationEngine.labourShortage(requiredCount = 5, availableCount = 12)).isEqualTo(0)
    }

    @Test
    fun `cost overrun and savings`() {
        assertThat(CalculationEngine.costOverrun(budget = 25_00_000.0, actual = 25_45_000.0)).isEqualTo(45_000.0)
        assertThat(CalculationEngine.costOverrun(budget = 25_00_000.0, actual = 20_00_000.0)).isEqualTo(0.0)
        assertThat(CalculationEngine.savings(budget = 25_00_000.0, actual = 20_00_000.0)).isEqualTo(5_00_000.0)
    }

    @Test
    fun `predicted completion days remaining from linear progress`() {
        // 25% done in 30 days -> total ~120 days -> ~90 remaining
        val remaining = CalculationEngine.predictedCompletionDaysRemaining(progressPercent = 25.0, daysElapsed = 30)
        assertThat(remaining).isEqualTo(90)
    }

    @Test
    fun `lbd quantity multiplies sets by dimensions`() {
        // 20 footings, 3ft x 3ft x 1.5ft each
        val qty = CalculationEngine.lbdQuantity(sets = 20.0, length = 3.0, breadth = 3.0, depth = 1.5)
        assertThat(qty).isEqualTo(270.0)
    }

    @Test
    fun `lbd quantity treats missing breadth or depth as area or length only`() {
        // Plastering entered as length x breadth only (no depth) -> area
        val area = CalculationEngine.lbdQuantity(sets = 1.0, length = 10.0, breadth = 8.0, depth = 0.0)
        assertThat(area).isEqualTo(80.0)
        // A single length-only entry -> just the length
        val length = CalculationEngine.lbdQuantity(sets = 1.0, length = 12.0, breadth = 0.0, depth = 0.0)
        assertThat(length).isEqualTo(12.0)
    }
}
