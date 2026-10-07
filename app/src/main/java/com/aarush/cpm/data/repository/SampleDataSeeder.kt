package com.aarush.cpm.data.repository

import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.entity.*
import com.aarush.cpm.domain.schedule.ResidentialScheduleTemplate as T
import com.aarush.cpm.domain.schedule.WorkCalendar
import java.time.LocalDate

/**
 * Creates the demo "Residential Building" project (G+1, 2000 sqft @ ₹2,500/sqft) through the
 * SAME repository paths real data uses — built-in notations/rates, BOQ measurements tagged by
 * floor, a civil vendor (with its linked labour line), expenses (so purchases, stock and
 * vendor paid all update), a client receipt, a generated schedule and some site progress.
 * The project is flagged isDemoData so Settings → "Remove demo data" deletes it completely.
 */
class SampleDataSeeder(
    private val db: AppDatabase,
    private val projectRepository: ProjectRepository,
    private val builtInDefaultsSeeder: BuiltInDefaultsSeeder,
    private val vendorRepository: VendorRepository,
    private val expenseRepository: ExpenseRepository,
    private val scheduleRepository: ScheduleRepository
) {
    private fun millis(date: LocalDate) = WorkCalendar.toEpochMillis(date)

    suspend fun seedDemoProject(): Long {
        val today = LocalDate.now()
        val start = today.minusDays(45)
        val projectId = projectRepository.createProject(
            name = "Residential Building - Demo",
            clientName = "Demo Client",
            clientContact = "9999999999",
            location = "Sample Layout, Demo City",
            projectType = "G+1",
            numberOfFloors = 2,
            areaComponents = listOf(AreaRateComponent(label = "Main structure", areaSqft = 2000.0, ratePerSqft = 2500.0)),
            startDate = millis(start),
            expectedCompletionDate = millis(start.plusMonths(8)),
            notes = "Demo/sample project for testing. Safe to delete.",
            isDemoData = true,
            afterCreate = { id -> builtInDefaultsSeeder.seedBuiltInDefaults(id) }
        )

        fun boq(code: String, cat: CostCategory, desc: String, unit: String, floor: String,
                m: Double, l: Double, b: Double, d: Double) = BOQItem(
            projectId = projectId, itemCode = code, category = cat, description = desc, unit = unit,
            quantity = m * l * b * d, materialType = code, wastePercent = 3.0,
            numberOfMembers = m, perMember = 1.0, length = l, breadth = b, depth = d, floor = floor
        )
        val civil = CostCategory.CIVIL_STRUCTURAL
        db.boqDao().insertAll(listOf(
            boq(T.EXCAVATION, civil, "Footing pits", "Cft", T.FLOOR_FOUNDATION, 16.0, 6.0, 6.0, 5.0),
            boq(T.PCC, civil, "PCC below footings", "Cft", T.FLOOR_FOUNDATION, 16.0, 5.0, 5.0, 0.33),
            boq(T.RCC, civil, "Isolated footings", "Cft", T.FLOOR_FOUNDATION, 16.0, 4.0, 4.0, 1.25),
            boq(T.RCC, civil, "Columns to plinth", "Cft", T.FLOOR_FOUNDATION, 16.0, 0.75, 0.75, 5.0),
            boq(T.RCC, civil, "Plinth beam", "Cft", T.FLOOR_PLINTH, 1.0, 260.0, 0.75, 1.0),
            boq(T.RCC, civil, "Columns", "Cft", "Ground Floor", 16.0, 0.75, 0.75, 10.0),
            boq(T.RCC, civil, "Beams & slab", "Cft", "Ground Floor", 1.0, 1000.0, 1.0, 0.42),
            boq(T.RCC, civil, "Columns", "Cft", "First Floor", 16.0, 0.75, 0.75, 10.0),
            boq(T.RCC, civil, "Beams & slab", "Cft", "First Floor", 1.0, 1000.0, 1.0, 0.42),
            boq(T.BRICK_9, civil, "Basement masonry", "Cft", T.FLOOR_PLINTH, 1.0, 260.0, 0.75, 2.0),
            boq(T.BRICK_9, civil, "External walls", "Cft", "Ground Floor", 1.0, 180.0, 0.75, 10.0),
            boq(T.BRICK_9, civil, "External walls", "Cft", "First Floor", 1.0, 180.0, 0.75, 10.0),
            boq(T.BRICK_4_5, civil, "Partitions", "Sqft", "Ground Floor", 1.0, 120.0, 10.0, 1.0),
            boq(T.BRICK_4_5, civil, "Partitions", "Sqft", "First Floor", 1.0, 120.0, 10.0, 1.0),
            boq(T.BACKFILL, civil, "Backfilling", "Cft", T.FLOOR_PLINTH, 1.0, 1000.0, 1.0, 1.5),
            boq(T.INT_PLASTER, civil, "Walls & ceiling", "Sqft", "Ground Floor", 1.0, 4200.0, 1.0, 1.0),
            boq(T.INT_PLASTER, civil, "Walls & ceiling", "Sqft", "First Floor", 1.0, 4200.0, 1.0, 1.0),
            boq(T.EXT_PLASTER, civil, "External faces", "Sqft", "", 1.0, 3600.0, 1.0, 1.0),
            boq(T.TILE, CostCategory.TILING, "Vitrified flooring", "Sqft", "Ground Floor", 1.0, 900.0, 1.0, 1.0),
            boq(T.TILE, CostCategory.TILING, "Vitrified flooring", "Sqft", "First Floor", 1.0, 900.0, 1.0, 1.0)
        ))

        val vendorId = vendorRepository.addVendor(
            projectId = projectId, name = "Ramesh Civil Contractors", contact = "9876500000",
            workCategory = VendorWorkCategory.CIVIL, rateType = VendorRateType.PER_SQFT, rate = 450.0, quantity = 2000.0,
            projectValue = 5_000_000.0, startDate = millis(start), endDate = millis(start.plusMonths(7)),
            paymentTerms = "10% advance, fortnightly RA bills", notes = ""
        )

        suspend fun buy(daysAgo: Long, name: String, qty: Double, unit: String, rate: Double, cat: CostCategory) =
            expenseRepository.addExpense(Expense(
                projectId = projectId, date = millis(today.minusDays(daysAgo)), category = cat, type = ExpenseType.MATERIAL,
                itemOrMaterialName = name, quantity = qty, unit = unit, rate = rate, totalAmount = qty * rate,
                paymentMode = PaymentMode.BANK_TRANSFER, paymentStatus = PaymentStatus.PAID, isDemoData = true
            ))
        buy(38, "Cement", 250.0, "Bags", 330.0, civil)
        buy(37, "M.Sand", 700.0, "Cft", 60.0, civil)
        buy(37, "20mm Aggregate", 900.0, "Cft", 58.0, civil)
        buy(30, "Steel", 3200.0, "kg", 68.0, civil)
        buy(12, "Bricks", 9000.0, "Nos", 10.0, civil)
        expenseRepository.addExpense(Expense(
            projectId = projectId, date = millis(today.minusDays(20)), category = civil, type = ExpenseType.VENDOR, vendorId = vendorId,
            itemOrMaterialName = vendorRepository.linkedLineName("Ramesh Civil Contractors"), quantity = 1.0, unit = "LS",
            rate = 200_000.0, totalAmount = 200_000.0, paymentMode = PaymentMode.BANK_TRANSFER, paymentStatus = PaymentStatus.PAID, isDemoData = true
        ))
        expenseRepository.addExpense(Expense(
            projectId = projectId, date = millis(today.minusDays(44)), category = CostCategory.OTHER_MISC, type = ExpenseType.OTHER,
            direction = TransactionDirection.RECEIVED, itemOrMaterialName = "Client advance", quantity = 1.0, unit = "LS",
            rate = 1_000_000.0, totalAmount = 1_000_000.0, paymentMode = PaymentMode.BANK_TRANSFER, paymentStatus = PaymentStatus.PAID,
            notes = "Client advance", isDemoData = true
        ))

        // Schedule from the project start, then record what's been done on site so far.
        scheduleRepository.generateFromTemplate(projectId, start, numberOfFloors = 2, sundayOff = true)
        val acts = db.scheduleActivityDao().getForProject(projectId).associateBy { it.templateKey }
        suspend fun done(key: String, daysAgo: Long, qtyFraction: Double? = null, percent: Double? = null, labour: Int = 6) {
            val a = acts[key] ?: return
            scheduleRepository.logProgress(
                a.id, today.minusDays(daysAgo),
                quantityDone = qtyFraction?.let { a.plannedQuantity * it } ?: 0.0,
                percentComplete = percent, labourCount = labour, notes = "Demo"
            )
        }
        done("PRE-APPROVAL", 40, percent = 100.0, labour = 1)
        done("PRE-CLEAR", 33, percent = 100.0, labour = 4)
        done("PRE-TEMP", 32, percent = 100.0, labour = 3)
        done("SUB-EXC", 30, qtyFraction = 1.0)
        done("SUB-ATT", 28, percent = 100.0, labour = 2)
        done("SUB-PCC", 27, qtyFraction = 1.0)
        done("SUB-FTG", 22, qtyFraction = 0.6, labour = 10)
        done("SUB-FTG", 18, qtyFraction = 0.4, labour = 10)
        done("SUB-COL", 12, qtyFraction = 1.0, labour = 8)
        done("SUB-BMAS", 6, qtyFraction = 0.5, labour = 6)
        return projectId
    }
}
