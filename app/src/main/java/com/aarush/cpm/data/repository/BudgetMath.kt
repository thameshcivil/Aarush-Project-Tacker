package com.aarush.cpm.data.repository

import com.aarush.cpm.data.entity.BOQItem
import com.aarush.cpm.data.entity.CostCategory
import com.aarush.cpm.data.entity.MaterialCoefficient
import com.aarush.cpm.data.entity.MaterialRateCard
import com.aarush.cpm.data.entity.Vendor
import com.aarush.cpm.data.entity.VendorWorkCategory
import com.aarush.cpm.domain.calculation.CalculationEngine

/** Shared Budget-2 maths used by both the Home budget card and the schedule's activity
 *  budgets, so the two can never disagree. */
internal object BudgetMath {

    /** Vendor categories counted as "civil labour" for Budget 2's civil total. */
    val civilLabourCategories = setOf(
        VendorWorkCategory.CIVIL, VendorWorkCategory.STRUCTURAL, VendorWorkCategory.MASON, VendorWorkCategory.CARPENTER
    )

    /**
     * Material cost of CIVIL_STRUCTURAL BOQ items per notation code: quantity × coefficient
     * × (1 + waste%) × rate-card rate. BOQ items of other categories (tiling, painting…) are
     * deliberately excluded — their cost is already inside that category's % of Budget 1,
     * so counting them here too would double-count (a bug in the previous version).
     */
    fun civilMaterialCostByNotation(
        boqItems: List<BOQItem>,
        coefficients: List<MaterialCoefficient>,
        rates: List<MaterialRateCard>
    ): Map<String, Double> {
        val rateByName = rates.associateBy { it.description }
        val result = mutableMapOf<String, Double>()
        for (boq in boqItems.filter { it.category == CostCategory.CIVIL_STRUCTURAL }) {
            val cost = coefficients.filter { it.workItemKey == boq.materialType && it.coefficientPerUnit > 0 }.sumOf { coeff ->
                val required = CalculationEngine.requiredMaterialQuantity(boq.quantity, coeff.coefficientPerUnit)
                val total = CalculationEngine.totalRequiredQuantity(required, coeff.wastePercentOverride ?: boq.wastePercent)
                total * (rateByName[coeff.materialName]?.rate ?: 0.0)
            }
            result[boq.materialType] = (result[boq.materialType] ?: 0.0) + cost
        }
        return result
    }

    fun civilLabourPool(vendors: List<Vendor>): Double =
        vendors.filter { it.workCategory in civilLabourCategories }.sumOf { it.contractValue }
}
