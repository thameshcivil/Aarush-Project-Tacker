package com.aarush.cpm.data.repository

import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.entity.BOQNotation
import com.aarush.cpm.data.entity.Material
import com.aarush.cpm.data.entity.MaterialCoefficient
import com.aarush.cpm.data.entity.MaterialRateCard

/**
 * Seeds every new project with a starting set of BOQ notations, material coefficients
 * (per notation), a material rate card, and the underlying Material rows they reference —
 * all editable afterward from the project's Settings tab (see ProjectSettingsScreen).
 * Nothing here is hard-coded as mandatory; it's just a sensible starting point.
 */
class BuiltInDefaultsSeeder(private val db: AppDatabase) {

    private data class NotationRow(
        val code: String,
        val description: String,
        val unit: String,
        val cement: Double,
        val pSand: Double,
        val mSand: Double,
        val bricks: Double,
        val aggregate20mm: Double
    )

    private val notationRows = listOf(
        NotationRow("001-Excavation", "Excavation", "Cft", 0.0, 0.0, 0.0, 0.0, 0.0),
        NotationRow("002-Plain Cement Concrete", "Plain Cement Concrete", "Cft", 0.123, 0.0, 0.462, 0.0, 0.924),
        NotationRow("003-Reinforced Cement Concrete", "Reinforced Cement Concrete", "Cft", 0.246, 0.0, 0.462, 0.0, 0.924),
        NotationRow("004-9\" Brick work", "9\" Brick work", "Cft", 0.03, 0.0, 0.225, 13.5, 0.0),
        NotationRow("005-4.5\" Brick work", "4.5\" Brick work", "Sqft", 0.012, 0.0, 0.09, 5.5, 0.0),
        NotationRow("006-Back Filling", "Back Filling", "Cft", 0.0, 0.0, 0.0, 0.0, 0.0),
        NotationRow("007-Tile Laying", "Tile Laying", "Sqft", 0.003, 0.0, 0.02, 0.0, 0.0),
        NotationRow("008-Wall Dado Laying", "Wall Dado Laying", "Sqft", 0.003, 0.0, 0.02, 0.0, 0.0),
        NotationRow("009-Skirting Laying", "Skirting Laying", "Sqft", 0.003, 0.0, 0.02, 0.0, 0.0),
        NotationRow("010-Granite Laying", "Granite Laying", "Sqft", 0.004, 0.0, 0.027, 0.0, 0.0),
        NotationRow("011-Internal Plastering", "Internal Plastering", "Sqft", 0.0045, 0.034, 0.0, 0.0, 0.0),
        NotationRow("012-External Plastering", "External Plastering", "Sqft", 0.0078, 0.039, 0.0, 0.0, 0.0)
    )

    private data class RateRow(val description: String, val unit: String, val rate: Double)

    private val rateRows = listOf(
        RateRow("Cement", "Bags", 325.0),
        RateRow("P.Sand", "Units", 6000.0),
        RateRow("M.Sand", "Units", 6000.0),
        RateRow("Bricks", "Nos", 10.0),
        RateRow("20mm Aggregate", "Units", 6000.0),
        RateRow("40mm Aggregate", "Units", 7000.0),
        RateRow("Steel", "MT", 66.0),
        RateRow("Floor Tile", "Sqft", 75.0),
        RateRow("Skirting", "Sqft", 75.0),
        RateRow("Wall Dado", "Sqft", 65.0),
        RateRow("Granite", "Sqft", 180.0),
        RateRow("Internal Painting", "Sqft", 30.0),
        RateRow("External Painting", "Sqft", 30.0),
        RateRow("Gravel", "Units", 20000.0),
        RateRow("Door", "LS", 0.0),
        RateRow("Window", "LS", 0.0),
        RateRow("Electrical", "LS", 0.0),
        RateRow("Plumbing", "LS", 0.0),
        RateRow("Extra Labours & Machinaries", "LS", 0.0),
        RateRow("Labour Cost for Civil Vendor", "Sqft", 0.0)
    )

    /** The distinct raw materials referenced by the coefficient table above, with the unit
     *  used for stock tracking (Material Status). Rate-card-only items (tiles, painting,
     *  LS items) aren't consumable "materials" tracked by stock, so they're excluded here. */
    private val materialRows = listOf(
        "Cement" to "bag",
        "P.Sand" to "cft",
        "M.Sand" to "cft",
        "Bricks" to "nos",
        "20mm Aggregate" to "cft"
    )

    suspend fun seedBuiltInDefaults(projectId: Long) {
        db.boqNotationDao().insertAll(
            notationRows.map { BOQNotation(projectId = projectId, code = it.code, description = it.description, defaultUnit = it.unit) }
        )

        val coefficients = mutableListOf<MaterialCoefficient>()
        notationRows.forEach { row ->
            coefficients += MaterialCoefficient(projectId = projectId, workItemKey = row.code, materialName = "Cement", coefficientPerUnit = row.cement)
            coefficients += MaterialCoefficient(projectId = projectId, workItemKey = row.code, materialName = "P.Sand", coefficientPerUnit = row.pSand)
            coefficients += MaterialCoefficient(projectId = projectId, workItemKey = row.code, materialName = "M.Sand", coefficientPerUnit = row.mSand)
            coefficients += MaterialCoefficient(projectId = projectId, workItemKey = row.code, materialName = "Bricks", coefficientPerUnit = row.bricks)
            coefficients += MaterialCoefficient(projectId = projectId, workItemKey = row.code, materialName = "20mm Aggregate", coefficientPerUnit = row.aggregate20mm)
        }
        db.materialCoefficientDao().insertAll(coefficients)

        db.materialRateCardDao().insertAll(
            rateRows.map { MaterialRateCard(projectId = projectId, description = it.description, unit = it.unit, rate = it.rate) }
        )

        materialRows.forEach { (name, unit) ->
            db.materialDao().insert(Material(projectId = projectId, name = name, unit = unit))
        }
    }
}
