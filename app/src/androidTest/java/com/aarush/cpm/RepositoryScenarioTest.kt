package com.aarush.cpm

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.entity.*
import com.aarush.cpm.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * End-to-end checks of the repositories against a real (in-memory) Room database — this
 * also validates every DAO query against the schema. Covers the bug fixes (vendor links
 * surviving a Settings save, purchases following expense edits/deletes, vendor undo,
 * renames, cascading project delete) and the schedule module (generation from BOQ,
 * progress → stock consumption, earned value reconciling with Budget 2).
 */
@RunWith(AndroidJUnit4::class)
class RepositoryScenarioTest {
    private lateinit var db: AppDatabase
    private lateinit var projects: ProjectRepository
    private lateinit var boq: BOQRepository
    private lateinit var materials: MaterialRepository
    private lateinit var expenses: ExpenseRepository
    private lateinit var vendors: VendorRepository
    private lateinit var schedule: ScheduleRepository
    private lateinit var summary: ProjectSummaryRepository
    private lateinit var seeder: SampleDataSeeder

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        projects = ProjectRepository(db); boq = BOQRepository(db); materials = MaterialRepository(db)
        expenses = ExpenseRepository(db); vendors = VendorRepository(db); schedule = ScheduleRepository(db)
        summary = ProjectSummaryRepository(db)
        seeder = SampleDataSeeder(db, projects, BuiltInDefaultsSeeder(db), vendors, expenses, schedule)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun demoProject_scheduleAndBudgetsReconcile() = runBlocking {
        val pid = seeder.seedDemoProject()
        val acts = db.scheduleActivityDao().getForProject(pid)
        assertEquals(52, acts.size)
        val vendor = db.vendorDao().getForProject(pid).single()
        assertEquals(200_000.0, vendor.amountPaid, 0.01)
        assertTrue(db.materialUsageDao().getForProject(pid).isNotEmpty())

        val r = schedule.buildReport(pid)
        val ev = r.earnedValue!!
        assertTrue(ev.actualPercent in 0.1..99.9)
        assertEquals(summary.buildBudgetComparison(pid)!!.budget2, r.budget2, 1.0)
        assertEquals(r.budget2, r.scheduledBudget, 1.0)
        assertEquals(ev.actualPercent, summary.buildSummary(pid)!!.progressPercent, 0.01)

        val rccBoq = db.boqDao().getForProject(pid).filter { it.materialType == "003-Reinforced Cement Concrete" }.sumOf { it.quantity }
        val rccActs = r.activities.filter { it.activity.linkedNotationCode == "003-Reinforced Cement Concrete" }.sumOf { it.plannedQuantity }
        assertEquals(rccBoq, rccActs, 0.01)
    }

    @Test
    fun settingsSave_keepsVendorLinks_andRenamesPropagate() = runBlocking {
        val pid = seeder.seedDemoProject()
        val vendorId = db.vendorDao().getForProject(pid).single().id
        val editable = db.materialRateCardDao().getForProject(pid).filter { it.linkedVendorId == null }
        materials.replaceMaterialRates(pid, editable.map { if (it.description == "Cement") it.copy(description = "OPC Cement") else it }, mapOf("Cement" to "OPC Cement"))
        assertEquals(1, db.materialRateCardDao().getForVendor(vendorId).size)
        assertTrue(db.materialPurchaseDao().getForProject(pid).any { it.materialName == "OPC Cement" })
        assertTrue(db.materialCoefficientDao().getForProject(pid).none { it.materialName == "Cement" })

        val notations = db.boqNotationDao().getForProject(pid)
        boq.replaceNotationsAndCoefficients(pid,
            notations.map { (if (it.code == "003-Reinforced Cement Concrete") it.copy(code = "003-RCC") else it).copy(id = 0) },
            db.materialCoefficientDao().getForProject(pid).map { (if (it.workItemKey == "003-Reinforced Cement Concrete") it.copy(workItemKey = "003-RCC") else it).copy(id = 0) },
            mapOf("003-Reinforced Cement Concrete" to "003-RCC"))
        assertTrue(db.boqDao().getForProject(pid).none { it.materialType == "003-Reinforced Cement Concrete" })
        assertTrue(db.scheduleActivityDao().getForProject(pid).any { it.linkedNotationCode == "003-RCC" })
    }

    @Test
    fun expenseEditDeleteUndo_keepStockAndVendorInStep() = runBlocking {
        val pid = seeder.seedDemoProject()
        val cement = db.expenseDao().getForProject(pid).first { it.itemOrMaterialName == "Cement" }
        expenses.updateExpense(cement, cement.copy(quantity = 300.0, totalAmount = 99_000.0))
        assertEquals(300.0, db.materialPurchaseDao().getForMaterial(pid, "Cement").sumOf { it.quantity }, 0.01)
        val edited = db.expenseDao().getForProject(pid).first { it.id == cement.id }
        expenses.deleteExpense(edited)
        assertTrue(db.materialPurchaseDao().getForMaterial(pid, "Cement").isEmpty())
        expenses.addExpense(edited.copy(id = 0))
        assertEquals(300.0, db.materialPurchaseDao().getForMaterial(pid, "Cement").sumOf { it.quantity }, 0.01)

        val v = db.vendorDao().getForProject(pid).single()
        vendors.deleteVendor(v)
        assertTrue(db.materialRateCardDao().getForVendor(v.id).isEmpty())
        vendors.restoreVendor(v)
        assertEquals(200_000.0, db.vendorDao().getById(v.id)!!.amountPaid, 0.01)
        assertEquals(1, db.materialRateCardDao().getForVendor(v.id).size)
    }

    @Test
    fun progressLog_booksAndReleasesStock() = runBlocking {
        val pid = seeder.seedDemoProject()
        val bmas = db.scheduleActivityDao().getForProject(pid).first { it.templateKey == "SUB-BMAS" }
        val before = db.materialUsageDao().getForProject(pid).size
        val logId = schedule.logProgress(bmas.id, LocalDate.now(), bmas.plannedQuantity * 0.5, null, 6, "")
        assertTrue(db.materialUsageDao().getForProject(pid).size > before)
        assertEquals(100.0, db.scheduleActivityDao().getById(bmas.id)!!.progressPercent, 0.01)
        schedule.deleteLog(db.activityProgressLogDao().getForActivity(bmas.id).first { it.id == logId })
        assertEquals(before, db.materialUsageDao().getForProject(pid).size)
    }

    @Test
    fun deleteProject_removesEverything() = runBlocking {
        val pid = seeder.seedDemoProject()
        projects.deleteProject(db.projectDao().getById(pid)!!)
        assertTrue(db.boqDao().getForProject(pid).isEmpty())
        assertTrue(db.expenseDao().getForProject(pid).isEmpty())
        assertTrue(db.scheduleActivityDao().getForProject(pid).isEmpty())
        assertTrue(db.scheduleDependencyDao().getForProject(pid).isEmpty())
        assertTrue(db.activityProgressLogDao().getForProject(pid).isEmpty())
        assertTrue(db.materialRateCardDao().getForProject(pid).isEmpty())
        assertTrue(db.vendorDao().getForProject(pid).isEmpty())
    }
}
