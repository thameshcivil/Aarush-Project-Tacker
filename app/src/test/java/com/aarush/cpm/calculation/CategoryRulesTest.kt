package com.aarush.cpm.calculation

import com.aarush.cpm.data.entity.CostCategory
import com.aarush.cpm.data.entity.VendorWorkCategory
import com.aarush.cpm.domain.calculation.CategoryRules
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryRulesTest {
    private fun cat(name: String) = CategoryRules.categoryForMaterialName(name)

    @Test fun doorAndWindowHardwareIsJoinery() {
        listOf("Hinges", "Door lock", "Lock", "Door handle", "Tower bolt", "Aldrop", "Latch", "Door closer", "Magnetic catch", "Drawer channel")
            .forEach { assertEquals(it, CostCategory.JOINERY, cat(it)) }
    }

    @Test fun sanitaryAndCpFittingsAreMep() {
        listOf("Closet", "EWC", "Wash basin", "Kitchen sink", "Pillar cock", "Angle cock", "Health faucet", "Shower", "Flush tank", "Floor trap", "Geyser")
            .forEach { assertEquals(it, CostCategory.MEP, cat(it)) }
    }

    @Test fun blockIsNotALock() {
        assertEquals(CostCategory.CIVIL_STRUCTURAL, cat("Solid block"))
        assertEquals(CostCategory.CIVIL_STRUCTURAL, cat("AAC block"))
    }

    @Test fun joineryCarpenterVendorIsJoinery() {
        assertEquals(CostCategory.JOINERY, CategoryRules.categoryForVendor(VendorWorkCategory.JOINERY_CARPENTER))
        assertEquals(CostCategory.CIVIL_STRUCTURAL, CategoryRules.categoryForVendor(VendorWorkCategory.CARPENTER))
    }
}
