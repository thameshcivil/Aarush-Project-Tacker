package com.aarush.cpm.domain.schedule

import com.aarush.cpm.data.entity.CostCategory

/** One predecessor link inside a template: finish-to-start with an optional lag. */
data class TemplatePredecessor(val key: String, val lagDays: Int = 0)

/**
 * One activity in the standard residential construction sequence. [notationCode] links it
 * to a BOQ notation (planned quantity + material budget come from the BOQ); activities
 * without a notation are budgeted from their cost category's share of the project budget,
 * weighted by [budgetWeight].
 */
data class TemplateActivity(
    val key: String,
    val name: String,
    val phase: String,
    val category: CostCategory,
    val floorLabel: String = "",
    val notationCode: String = "",
    val sharePercent: Double = 100.0,
    val defaultDurationDays: Int,
    /** Whole-crew output per working day in the notation's unit; 0 = fixed duration. */
    val productivityPerDay: Double = 0.0,
    val labourSkill: String,
    val labourCount: Int,
    val budgetWeight: Double = 1.0,
    val isMilestone: Boolean = false,
    val predecessors: List<TemplatePredecessor> = emptyList()
)

/**
 * The complete work breakdown of a typical Indian RCC-framed residential building
 * (G, G+1, G+2 … up to 10 floors), from approvals to handover, in execution order:
 *
 *  1. Pre-construction — approvals/soil test, site clearing & setting out, temporary works
 *  2. Substructure — excavation, anti-termite, PCC, footings, columns to plinth, basement
 *     masonry, plinth beam, backfilling, DPC, floor-bed PCC
 *  3. Superstructure (per floor) — columns, beams & slab, curing/de-shuttering, 9" and
 *     4.5" brickwork, lintels/sunshades/lofts, electrical conduits, plumbing rough-in
 *  4. Terrace — parapet & headroom, waterproofing/weathering course
 *  5. Services — septic tank/sump/OHT, wiring & fixtures, sanitary & CP fittings
 *  6. Finishes (per floor) — door/window frames, internal plastering, flooring, dado,
 *     skirting, putty & primer; then external plaster, granite, shutters & grills,
 *     internal and external painting
 *  7. External works & handover — compound wall/gate/paving, cleaning, snag list, handover
 *
 * Notation codes match BuiltInDefaultsSeeder's built-in notations. Where one notation is
 * spread over several activities (e.g. RCC across footings, plinth beam and each floor's
 * slab), [TemplateActivity.sharePercent] gives each activity its share of the untagged BOQ
 * quantity; BOQ items tagged with a floor go to that floor's activity exactly.
 */
object ResidentialScheduleTemplate {

    const val EXCAVATION = "001-Excavation"
    const val PCC = "002-Plain Cement Concrete"
    const val RCC = "003-Reinforced Cement Concrete"
    const val BRICK_9 = "004-9\" Brick work"
    const val BRICK_4_5 = "005-4.5\" Brick work"
    const val BACKFILL = "006-Back Filling"
    const val TILE = "007-Tile Laying"
    const val DADO = "008-Wall Dado Laying"
    const val SKIRTING = "009-Skirting Laying"
    const val GRANITE = "010-Granite Laying"
    const val INT_PLASTER = "011-Internal Plastering"
    const val EXT_PLASTER = "012-External Plastering"

    const val FLOOR_FOUNDATION = "Foundation"
    const val FLOOR_PLINTH = "Plinth"
    const val FLOOR_TERRACE = "Terrace"

    const val PHASE_PRE = "1. Pre-construction"
    const val PHASE_SUB = "2. Substructure"
    const val PHASE_SUPER = "3. Superstructure"
    const val PHASE_ROOF = "4. Terrace & roof"
    const val PHASE_SERVICES = "5. Services (MEP)"
    const val PHASE_FINISH = "6. Finishes"
    const val PHASE_HANDOVER = "7. External works & handover"

    fun floorName(index: Int): String = when (index) {
        0 -> "Ground Floor"
        1 -> "First Floor"
        2 -> "Second Floor"
        3 -> "Third Floor"
        4 -> "Fourth Floor"
        5 -> "Fifth Floor"
        else -> "Floor ${index}"
    }

    /** Floor/location tags offered on the BOQ form, in building order. */
    fun floorOptions(numberOfFloors: Int): List<String> =
        listOf(FLOOR_FOUNDATION, FLOOR_PLINTH) + (0 until numberOfFloors.coerceIn(1, 10)).map { floorName(it) } + FLOOR_TERRACE

    private fun p(key: String, lag: Int = 0) = TemplatePredecessor(key, lag)

    fun build(numberOfFloors: Int): List<TemplateActivity> {
        val n = numberOfFloors.coerceIn(1, 10)
        val list = mutableListOf<TemplateActivity>()
        val civil = CostCategory.CIVIL_STRUCTURAL

        // ---------- 1. Pre-construction ----------
        list += TemplateActivity("PRE-APPROVAL", "Soil test, survey & plan approvals", PHASE_PRE, CostCategory.OTHER_MISC,
            defaultDurationDays = 10, labourSkill = "Engineer", labourCount = 1, budgetWeight = 1.0, isMilestone = false)
        list += TemplateActivity("PRE-CLEAR", "Site clearing & setting out", PHASE_PRE, civil,
            defaultDurationDays = 2, labourSkill = "Helper", labourCount = 4, predecessors = listOf(p("PRE-APPROVAL")))
        list += TemplateActivity("PRE-TEMP", "Temporary works: water, power, store shed", PHASE_PRE, CostCategory.OTHER_MISC,
            defaultDurationDays = 3, labourSkill = "Helper", labourCount = 3, budgetWeight = 1.0, predecessors = listOf(p("PRE-APPROVAL")))

        // ---------- 2. Substructure ----------
        list += TemplateActivity("SUB-EXC", "Earthwork excavation for foundation", PHASE_SUB, civil, FLOOR_FOUNDATION,
            EXCAVATION, 100.0, defaultDurationDays = 5, productivityPerDay = 600.0, labourSkill = "Helper", labourCount = 6,
            predecessors = listOf(p("PRE-CLEAR"), p("PRE-TEMP")))
        list += TemplateActivity("SUB-ATT", "Anti-termite treatment", PHASE_SUB, CostCategory.OTHER_MISC, FLOOR_FOUNDATION,
            defaultDurationDays = 1, labourSkill = "Helper", labourCount = 2, budgetWeight = 0.3, predecessors = listOf(p("SUB-EXC")))
        list += TemplateActivity("SUB-PCC", "PCC below footings", PHASE_SUB, civil, FLOOR_FOUNDATION,
            PCC, 60.0, defaultDurationDays = 2, productivityPerDay = 250.0, labourSkill = "Mason", labourCount = 6,
            predecessors = listOf(p("SUB-ATT")))
        list += TemplateActivity("SUB-FTG", "Footings: reinforcement, formwork & concreting", PHASE_SUB, civil, FLOOR_FOUNDATION,
            RCC, 15.0, defaultDurationDays = 8, productivityPerDay = 50.0, labourSkill = "Bar bender / Carpenter / Mason", labourCount = 10,
            predecessors = listOf(p("SUB-PCC", 1)))
        list += TemplateActivity("SUB-COL", "Columns up to plinth", PHASE_SUB, civil, FLOOR_FOUNDATION,
            RCC, 5.0, defaultDurationDays = 4, productivityPerDay = 20.0, labourSkill = "Bar bender / Carpenter / Mason", labourCount = 8,
            predecessors = listOf(p("SUB-FTG", 2)))
        list += TemplateActivity("SUB-BMAS", "Basement masonry up to plinth", PHASE_SUB, civil, FLOOR_PLINTH,
            BRICK_9, 10.0, defaultDurationDays = 4, productivityPerDay = 100.0, labourSkill = "Mason", labourCount = 6,
            predecessors = listOf(p("SUB-COL")))
        list += TemplateActivity("SUB-PB", "Plinth beam", PHASE_SUB, civil, FLOOR_PLINTH,
            RCC, 8.0, defaultDurationDays = 6, productivityPerDay = 35.0, labourSkill = "Bar bender / Carpenter / Mason", labourCount = 10,
            predecessors = listOf(p("SUB-BMAS")))
        list += TemplateActivity("SUB-BF", "Backfilling & compaction", PHASE_SUB, civil, FLOOR_PLINTH,
            BACKFILL, 100.0, defaultDurationDays = 3, productivityPerDay = 800.0, labourSkill = "Helper", labourCount = 6,
            predecessors = listOf(p("SUB-PB", 3)))
        list += TemplateActivity("SUB-DPC", "Damp-proof course (DPC)", PHASE_SUB, civil, FLOOR_PLINTH,
            defaultDurationDays = 2, labourSkill = "Mason", labourCount = 3, predecessors = listOf(p("SUB-PB", 3)))
        list += TemplateActivity("SUB-BED", "Floor-bed PCC (ground floor)", PHASE_SUB, civil, FLOOR_PLINTH,
            PCC, 40.0, defaultDurationDays = 2, productivityPerDay = 250.0, labourSkill = "Mason", labourCount = 6,
            predecessors = listOf(p("SUB-BF")))

        // ---------- 3. Superstructure, per floor ----------
        val rccFloorShare = 72.0 / n
        var previousSlab = "SUB-DPC"
        for (f in 0 until n) {
            val floor = floorName(f)
            val k = "F$f"
            list += TemplateActivity("$k-COL", "Columns — $floor", PHASE_SUPER, civil, floor,
                RCC, rccFloorShare * 0.3, defaultDurationDays = 5, productivityPerDay = 20.0,
                labourSkill = "Bar bender / Carpenter / Mason", labourCount = 8,
                predecessors = listOf(p(previousSlab, if (f == 0) 0 else 0)))
            list += TemplateActivity("$k-SLAB", "Beams & roof slab — $floor", PHASE_SUPER, civil, floor,
                RCC, rccFloorShare * 0.7, defaultDurationDays = 12, productivityPerDay = 45.0,
                labourSkill = "Bar bender / Carpenter / Mason", labourCount = 14,
                predecessors = listOf(p("$k-COL", 1)))
            list += TemplateActivity("$k-CURE", "Slab curing & de-shuttering — $floor", PHASE_SUPER, civil, floor,
                defaultDurationDays = 14, labourSkill = "Helper", labourCount = 2, budgetWeight = 0.5,
                predecessors = listOf(p("$k-SLAB")))
            list += TemplateActivity("$k-B9", "9\" external brickwork — $floor", PHASE_SUPER, civil, floor,
                BRICK_9, 85.0 / n, defaultDurationDays = 8, productivityPerDay = 100.0, labourSkill = "Mason", labourCount = 8,
                predecessors = listOf(p("$k-CURE")))
            list += TemplateActivity("$k-B45", "4.5\" partition brickwork — $floor", PHASE_SUPER, civil, floor,
                BRICK_4_5, 100.0 / n, defaultDurationDays = 5, productivityPerDay = 240.0, labourSkill = "Mason", labourCount = 6,
                predecessors = listOf(p("$k-B9")))
            list += TemplateActivity("$k-LINT", "Lintels, sunshades & lofts — $floor", PHASE_SUPER, civil, floor,
                defaultDurationDays = 4, labourSkill = "Bar bender / Carpenter / Mason", labourCount = 6,
                predecessors = listOf(p("$k-B9")))
            list += TemplateActivity("$k-ELC", "Electrical conduits & boxes — $floor", PHASE_SERVICES, CostCategory.MEP, floor,
                defaultDurationDays = 4, labourSkill = "Electrician", labourCount = 2, budgetWeight = 1.0,
                predecessors = listOf(p("$k-B45")))
            list += TemplateActivity("$k-PLB", "Concealed plumbing & sanitary rough-in — $floor", PHASE_SERVICES, CostCategory.MEP, floor,
                defaultDurationDays = 4, labourSkill = "Plumber", labourCount = 2, budgetWeight = 1.0,
                predecessors = listOf(p("$k-B45")))
            previousSlab = "$k-CURE"
        }
        val topCure = "F${n - 1}-CURE"
        val lastFloorBrick = "F${n - 1}-B9"

        // ---------- 4. Terrace ----------
        list += TemplateActivity("ROOF-PAR", "Parapet wall & staircase headroom", PHASE_ROOF, civil, FLOOR_TERRACE,
            BRICK_9, 5.0, defaultDurationDays = 5, productivityPerDay = 80.0, labourSkill = "Mason", labourCount = 4,
            predecessors = listOf(p(topCure)))
        list += TemplateActivity("ROOF-WP", "Terrace waterproofing & weathering course", PHASE_ROOF, CostCategory.OTHER_MISC, FLOOR_TERRACE,
            defaultDurationDays = 4, labourSkill = "Waterproofing applicator", labourCount = 3, budgetWeight = 1.5,
            predecessors = listOf(p("ROOF-PAR")))

        // ---------- 5. Services (whole building) ----------
        list += TemplateActivity("SRV-SUMP", "Septic tank, sump & overhead tank", PHASE_SERVICES, CostCategory.MEP,
            defaultDurationDays = 10, labourSkill = "Mason", labourCount = 4, budgetWeight = 2.0,
            predecessors = listOf(p("SUB-BF")))

        // ---------- 6. Finishes, per floor ----------
        list += TemplateActivity("FIN-FRM", "Door & window frames fixing", PHASE_FINISH, CostCategory.JOINERY,
            defaultDurationDays = 4, labourSkill = "Carpenter", labourCount = 2, budgetWeight = 1.0,
            predecessors = listOf(p(lastFloorBrick)))
        for (f in 0 until n) {
            val floor = floorName(f)
            val k = "F$f"
            list += TemplateActivity("$k-IPL", "Internal plastering (walls & ceiling) — $floor", PHASE_FINISH, civil, floor,
                INT_PLASTER, 100.0 / n, defaultDurationDays = 10, productivityPerDay = 400.0, labourSkill = "Mason", labourCount = 8,
                predecessors = listOf(p("$k-ELC"), p("$k-PLB"), p("$k-LINT"), p("FIN-FRM")))
            list += TemplateActivity("$k-TILE", "Floor tiling — $floor", PHASE_FINISH, CostCategory.TILING, floor,
                TILE, 100.0 / n, defaultDurationDays = 6, productivityPerDay = 300.0, labourSkill = "Tile mason", labourCount = 4,
                budgetWeight = 2.0, predecessors = listOf(p("$k-IPL")) + if (f == 0) listOf(p("SUB-BED")) else emptyList())
            list += TemplateActivity("$k-DADO", "Toilet & kitchen wall dado — $floor", PHASE_FINISH, CostCategory.TILING, floor,
                DADO, 100.0 / n, defaultDurationDays = 4, productivityPerDay = 200.0, labourSkill = "Tile mason", labourCount = 3,
                budgetWeight = 1.0, predecessors = listOf(p("$k-IPL")))
            list += TemplateActivity("$k-SKRT", "Skirting — $floor", PHASE_FINISH, CostCategory.TILING, floor,
                SKIRTING, 100.0 / n, defaultDurationDays = 2, productivityPerDay = 150.0, labourSkill = "Tile mason", labourCount = 2,
                budgetWeight = 0.4, predecessors = listOf(p("$k-TILE")))
            list += TemplateActivity("$k-PUTTY", "Wall putty & primer — $floor", PHASE_FINISH, CostCategory.PAINTING, floor,
                defaultDurationDays = 7, labourSkill = "Painter", labourCount = 4, budgetWeight = 1.5,
                predecessors = listOf(p("$k-SKRT"), p("$k-DADO")))
        }
        val allPutty = (0 until n).map { p("F$it-PUTTY") }
        val allTiles = (0 until n).map { p("F$it-TILE") }
        list += TemplateActivity("FIN-EPL", "External plastering", PHASE_FINISH, civil, "",
            EXT_PLASTER, 100.0, defaultDurationDays = 12, productivityPerDay = 300.0, labourSkill = "Mason", labourCount = 8,
            predecessors = listOf(p("ROOF-PAR"), p(lastFloorBrick)))
        list += TemplateActivity("FIN-GRN", "Granite: kitchen platform, staircase & sills", PHASE_FINISH, CostCategory.TILING, "",
            GRANITE, 100.0, defaultDurationDays = 5, productivityPerDay = 60.0, labourSkill = "Tile mason", labourCount = 3,
            budgetWeight = 1.0, predecessors = allTiles)
        list += TemplateActivity("SRV-WIRE", "Electrical wiring, DB & fixtures", PHASE_SERVICES, CostCategory.MEP,
            defaultDurationDays = 10, labourSkill = "Electrician", labourCount = 3, budgetWeight = 3.0,
            predecessors = allPutty)
        list += TemplateActivity("FIN-SHUT", "Door shutters, windows, grills & railings", PHASE_FINISH, CostCategory.JOINERY,
            defaultDurationDays = 7, labourSkill = "Carpenter / Fabricator", labourCount = 3, budgetWeight = 3.0,
            predecessors = allPutty)
        list += TemplateActivity("SRV-SAN", "Sanitary ware & CP fittings", PHASE_SERVICES, CostCategory.MEP,
            defaultDurationDays = 7, labourSkill = "Plumber", labourCount = 2, budgetWeight = 2.5,
            predecessors = (0 until n).map { p("F$it-DADO") } + p("SRV-SUMP"))
        list += TemplateActivity("FIN-IPNT", "Internal painting (final coats)", PHASE_FINISH, CostCategory.PAINTING,
            defaultDurationDays = 12, labourSkill = "Painter", labourCount = 5, budgetWeight = 3.0,
            predecessors = listOf(p("SRV-WIRE"), p("FIN-SHUT"), p("FIN-GRN")))
        list += TemplateActivity("FIN-EPNT", "External painting", PHASE_FINISH, CostCategory.PAINTING,
            defaultDurationDays = 10, labourSkill = "Painter", labourCount = 4, budgetWeight = 2.0,
            predecessors = listOf(p("FIN-EPL"), p("ROOF-WP")))

        // ---------- 7. External works & handover ----------
        list += TemplateActivity("EXT-CMP", "Compound wall, gate & external paving", PHASE_HANDOVER, CostCategory.OTHER_MISC,
            defaultDurationDays = 10, labourSkill = "Mason", labourCount = 5, budgetWeight = 3.0,
            predecessors = listOf(p("FIN-EPL")))
        list += TemplateActivity("EXT-HAND", "Cleaning, snag list & handover", PHASE_HANDOVER, CostCategory.OTHER_MISC,
            defaultDurationDays = 3, labourSkill = "Helper", labourCount = 4, budgetWeight = 0.5, isMilestone = true,
            predecessors = listOf(p("FIN-IPNT"), p("FIN-EPNT"), p("EXT-CMP"), p("SRV-SAN")))
        return list
    }
}
