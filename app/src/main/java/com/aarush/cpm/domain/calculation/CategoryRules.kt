package com.aarush.cpm.domain.calculation

import com.aarush.cpm.data.entity.CostCategory
import com.aarush.cpm.data.entity.VendorWorkCategory

/**
 * Pure rules mapping things to the project's cost categories, so Budget-vs-Actual stays
 * meaningful without asking the user for a category on every expense.
 */
object CategoryRules {

    /** A vendor's payments count against the category of the work it does. */
    fun categoryForVendor(workCategory: VendorWorkCategory): CostCategory = when (workCategory) {
        VendorWorkCategory.CIVIL, VendorWorkCategory.STRUCTURAL, VendorWorkCategory.MASON,
        VendorWorkCategory.CARPENTER -> CostCategory.CIVIL_STRUCTURAL
        VendorWorkCategory.ELECTRICIAN, VendorWorkCategory.PLUMBING, VendorWorkCategory.HVAC -> CostCategory.MEP
        VendorWorkCategory.PAINTING -> CostCategory.PAINTING
        VendorWorkCategory.FLOORING -> CostCategory.TILING
        VendorWorkCategory.ALUMINIUM, VendorWorkCategory.FABRICATION -> CostCategory.JOINERY
        VendorWorkCategory.OTHER -> CostCategory.OTHER_MISC
    }

    private val keywordRules: List<Pair<List<String>, CostCategory>> = listOf(
        listOf("electric", "wire", "wiring", "switch", "light", "fan", "plumb", "pipe", "sanitary", "cp fitting",
            "tap", "hvac", "ac ", "motor", "pump", "tank") to CostCategory.MEP,
        listOf("paint", "putty", "primer", "emulsion", "texture") to CostCategory.PAINTING,
        listOf("tile", "tiling", "skirting", "dado", "granite", "marble", "flooring") to CostCategory.TILING,
        listOf("door", "window", "grill", "railing", "aluminium", "aluminum", "fabrication", "wood", "glass",
            "upvc", "shutter", "frame", "joinery") to CostCategory.JOINERY,
        listOf("cement", "sand", "brick", "block", "aggregate", "jelly", "steel", "tmt", "rod", "gravel",
            "concrete", "rmc", "shuttering", "binding wire", "labour", "labor", "machinar", "excavat", "mason") to CostCategory.CIVIL_STRUCTURAL
    )

    /** Fallback when a material isn't traceable through the BOQ coefficient engine. */
    fun categoryForMaterialName(name: String): CostCategory? {
        val n = " ${name.lowercase()} "
        return keywordRules.firstOrNull { (words, _) -> words.any { n.contains(it) } }?.second
    }

    /** Default BOQ category for a notation code/description picked on the BOQ form. */
    fun categoryForNotation(codeOrDescription: String): CostCategory {
        val n = codeOrDescription.lowercase()
        return when {
            listOf("tile", "dado", "skirting", "granite", "marble", "flooring").any { n.contains(it) } -> CostCategory.TILING
            listOf("paint", "putty").any { n.contains(it) } -> CostCategory.PAINTING
            listOf("electric", "plumb", "sanitary").any { n.contains(it) } -> CostCategory.MEP
            listOf("door", "window", "grill").any { n.contains(it) } -> CostCategory.JOINERY
            else -> CostCategory.CIVIL_STRUCTURAL
        }
    }
}
