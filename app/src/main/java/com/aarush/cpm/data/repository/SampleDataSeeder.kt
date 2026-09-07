package com.aarush.cpm.data.repository

import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.entity.*
import java.util.Calendar

/** Creates the demo "Residential Building" project described in the spec (section 33).
 *  All rows are flagged isDemoData = true so they can be found and deleted as a group. */
class SampleDataSeeder(
    private val db: AppDatabase,
    private val projectRepository: ProjectRepository
) {
    private fun daysFromNow(days: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, days)
        return cal.timeInMillis
    }

    /** Idempotent-ish: relies on the caller (Settings screen) to only invoke this once;
     *  Room's autoGenerate ids mean calling it twice simply creates a second demo project,
     *  which the "delete demo data" action removes just as easily since it's flagged. */
    suspend fun seedDemoProject() {
        val projectId = projectRepository.createProject(
            name = "Residential Building - Demo",
            clientName = "Demo Client",
            clientContact = "9999999999",
            location = "Sample Layout, Demo City",
            projectType = "G+1",
            numberOfFloors = 2,
            areaComponents = listOf(
                com.aarush.cpm.data.repository.AreaRateComponent(label = "Main structure", areaSqft = 2000.0, ratePerSqft = 2500.0)
            ),
            startDate = daysFromNow(-30),
            expectedCompletionDate = daysFromNow(150),
            notes = "Demo/sample project for testing. Safe to delete.",
            isDemoData = true
        )

        // Materials + coefficients
        val cement = Material(projectId = projectId, name = "Cement", unit = "bag")
        val sand = Material(projectId = projectId, name = "River Sand", unit = "m3")
        val aggregate = Material(projectId = projectId, name = "Aggregate", unit = "m3")
        val steel = Material(projectId = projectId, name = "Steel", unit = "kg")
        listOf(cement, sand, aggregate, steel).forEach { db.materialDao().insert(it) }

        db.materialCoefficientDao().insertAll(
            listOf(
                MaterialCoefficient(projectId = projectId, workItemKey = "Concrete M20", materialName = "Cement", coefficientPerUnit = 6.0),
                MaterialCoefficient(projectId = projectId, workItemKey = "Concrete M20", materialName = "River Sand", coefficientPerUnit = 0.45),
                MaterialCoefficient(projectId = projectId, workItemKey = "Concrete M20", materialName = "Aggregate", coefficientPerUnit = 0.9),
                MaterialCoefficient(projectId = projectId, workItemKey = "Brick Masonry", materialName = "Cement", coefficientPerUnit = 1.3),
                MaterialCoefficient(projectId = projectId, workItemKey = "Brick Masonry", materialName = "River Sand", coefficientPerUnit = 0.3)
            )
        )

        // Sample BOQ
        db.boqDao().insertAll(
            listOf(
                BOQItem(projectId = projectId, itemCode = "C-01", category = CostCategory.CIVIL_STRUCTURAL, description = "RCC Footing (M20)", unit = "m3", quantity = 40.0, rate = 8500.0, amount = 40.0 * 8500.0, materialType = "Concrete M20", wastePercent = 3.0),
                BOQItem(projectId = projectId, itemCode = "C-02", category = CostCategory.CIVIL_STRUCTURAL, description = "Brick masonry - superstructure", unit = "m3", quantity = 60.0, rate = 4200.0, amount = 60.0 * 4200.0, materialType = "Brick Masonry", wastePercent = 5.0)
            )
        )

        // Sample civil vendor
        val vendorId = db.vendorDao().insert(
            Vendor(
                projectId = projectId, name = "Ramesh Civil Contractors", contact = "9876500000",
                workCategory = VendorWorkCategory.CIVIL, rateType = VendorRateType.PER_SQFT,
                rate = 850.0, quantity = 2000.0, contractValue = 850.0 * 2000.0,
                startDate = daysFromNow(-30), endDate = daysFromNow(120), paymentTerms = "30% advance, monthly RA bills"
            )
        )

        // Sample expenses (both material + labour)
        db.expenseDao().insert(
            Expense(
                projectId = projectId, date = daysFromNow(-10), category = CostCategory.CIVIL_STRUCTURAL,
                type = ExpenseType.MATERIAL, itemOrMaterialName = "Cement", quantity = 100.0, unit = "bag",
                rate = 420.0, totalAmount = 42_000.0, paymentMode = PaymentMode.BANK_TRANSFER,
                paymentStatus = PaymentStatus.PAID, isDemoData = true
            )
        )
        db.expenseDao().insert(
            Expense(
                projectId = projectId, date = daysFromNow(-8), category = CostCategory.CIVIL_STRUCTURAL,
                type = ExpenseType.LABOUR, itemOrMaterialName = "Mason wages", quantity = 10.0, unit = "workers",
                rate = 900.0, totalAmount = 9_000.0, paymentMode = PaymentMode.CASH,
                paymentStatus = PaymentStatus.PAID, isDemoData = true
            )
        )
        db.expenseDao().insert(
            Expense(
                projectId = projectId, date = daysFromNow(-5), category = CostCategory.CIVIL_STRUCTURAL,
                type = ExpenseType.VENDOR, vendorId = vendorId, itemOrMaterialName = "Civil vendor - advance",
                quantity = 1.0, unit = "lump", rate = 3_00_000.0, totalAmount = 3_00_000.0,
                paymentMode = PaymentMode.BANK_TRANSFER, paymentStatus = PaymentStatus.PAID, isDemoData = true
            )
        )

        // Sample client payment
        db.clientPaymentDao().insert(
            ClientPayment(projectId = projectId, date = daysFromNow(-15), amountReceived = 15_00_000.0, paymentMode = PaymentMode.BANK_TRANSFER, referenceNumber = "UTR-DEMO-001")
        )

        // Sample progress + schedule
        db.projectProgressDao().insert(ProjectProgress(projectId = projectId, date = daysFromNow(0), overallProgressPercent = 18.0))
        db.scheduleActivityDao().insert(
            ScheduleActivity(
                projectId = projectId, activityName = "Footing", plannedStart = daysFromNow(-20), plannedEnd = daysFromNow(-15),
                plannedQuantity = 20.0, unit = "footings", requiredLabourSkill = "Mason", requiredLabourCount = 4,
                requiredMaterialName = "Cement", vendorId = vendorId, status = ActivityStatus.COMPLETED,
                actualStart = daysFromNow(-20), actualCompletion = daysFromNow(-14), progressPercent = 100.0
            )
        )
    }
}
