package com.aarush.cpm.data.repository

import androidx.room.withTransaction
import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.entity.CostAllocation
import com.aarush.cpm.data.entity.CostCategory
import com.aarush.cpm.data.entity.Project
import com.aarush.cpm.data.entity.ProjectAreaComponent
import com.aarush.cpm.domain.calculation.CalculationEngine
import kotlinx.coroutines.flow.Flow

/** One "area & rate" line item supplied when creating/editing a project — e.g. a residence
 *  costed at one ₹/sqft rate and a staircase costed at another. Not a Room entity itself;
 *  ProjectRepository turns these into ProjectAreaComponent rows tied to the new project id. */
data class AreaRateComponent(
    val label: String,
    val areaSqft: Double,
    val ratePerSqft: Double,
    /** Compound wall: areaSqft = running feet (or 1 for a lump sum), ratePerSqft = rate per
     *  rft (or the lump-sum amount). Budgeted to Civil and Painting only. */
    val isCompoundWall: Boolean = false,
    val isLumpSum: Boolean = false
)

class ProjectRepository(private val db: AppDatabase) {

    fun observeAll(): Flow<List<Project>> = db.projectDao().observeAll()

    fun observeById(id: Long): Flow<Project?> = db.projectDao().observeById(id)

    fun search(query: String): Flow<List<Project>> = db.projectDao().search(query)

    suspend fun getById(id: Long): Project? = db.projectDao().getById(id)

    /** Default thumb-rule split, per section 6. Fully editable afterward. */
    private fun defaultAllocations(projectId: Long): List<CostAllocation> = listOf(
        CostAllocation(projectId = projectId, category = CostCategory.CIVIL_STRUCTURAL, percentOfProjectValue = 45.0, materialPercent = 65.0, labourPercent = 35.0),
        CostAllocation(projectId = projectId, category = CostCategory.MEP, percentOfProjectValue = 15.0, materialPercent = 70.0, labourPercent = 30.0),
        CostAllocation(projectId = projectId, category = CostCategory.PAINTING, percentOfProjectValue = 8.0, materialPercent = 55.0, labourPercent = 45.0),
        CostAllocation(projectId = projectId, category = CostCategory.JOINERY, percentOfProjectValue = 12.0, materialPercent = 75.0, labourPercent = 25.0),
        CostAllocation(projectId = projectId, category = CostCategory.TILING, percentOfProjectValue = 8.0, materialPercent = 70.0, labourPercent = 30.0),
        CostAllocation(projectId = projectId, category = CostCategory.OTHER_MISC, percentOfProjectValue = 12.0, materialPercent = 50.0, labourPercent = 50.0)
    )

    /** Creates the project from one or more area/rate components (e.g. "Residence" at one
     *  ₹/sqft rate plus "Staircase" at another) and seeds default category cost allocations
     *  in the same step. Project.projectValue = sum of (areaSqft × ratePerSqft) across every
     *  component, unless manualProjectValue overrides it. Project.plinthAreaSqft/ratePerSqft
     *  are kept as a blended total-area / effective-rate summary for screens that just want a
     *  single headline figure — the itemized breakdown lives in ProjectAreaComponent rows. */
    suspend fun createProject(
        name: String,
        clientName: String,
        clientContact: String,
        location: String,
        projectType: String,
        numberOfFloors: Int,
        areaComponents: List<AreaRateComponent>,
        manualProjectValue: Double? = null,
        startDate: Long,
        expectedCompletionDate: Long,
        notes: String = "",
        isDemoData: Boolean = false,
        /** Runs inside the same transaction right after the project row exists — used to
         *  seed built-in notations/rates so a project is never left half-created. */
        afterCreate: suspend (Long) -> Unit = {}
    ): Long = db.withTransaction {
        require(areaComponents.isNotEmpty()) { "At least one area & rate component is required." }

        // Plinth area and the blended rate describe the BUILDING only; a compound wall's running
        // feet (or lump sum) is not floor area. Its value still counts in the project value.
        val building = areaComponents.filterNot { it.isCompoundWall }
        val totalArea = building.sumOf { it.areaSqft }
        val buildingValue = building.sumOf { CalculationEngine.projectValue(it.areaSqft, it.ratePerSqft) }
        val computedValue = areaComponents.sumOf { CalculationEngine.projectValue(it.areaSqft, it.ratePerSqft) }
        val blendedRate = if (totalArea > 0) buildingValue / totalArea else 0.0
        val finalValue = manualProjectValue ?: computedValue

        val project = Project(
            name = name,
            clientName = clientName,
            clientContact = clientContact,
            location = location,
            projectType = projectType,
            numberOfFloors = numberOfFloors,
            plinthAreaSqft = totalArea,
            ratePerSqft = blendedRate,
            projectValue = finalValue,
            isProjectValueManuallyOverridden = manualProjectValue != null,
            startDate = startDate,
            expectedCompletionDate = expectedCompletionDate,
            notes = notes,
            isDemoData = isDemoData
        )
        val id = db.projectDao().insert(project)
        db.costAllocationDao().insertAll(defaultAllocations(id))
        db.projectAreaComponentDao().insertAll(
            areaComponents.map {
                ProjectAreaComponent(projectId = id, label = it.label, areaSqft = it.areaSqft, ratePerSqft = it.ratePerSqft,
                    isCompoundWall = it.isCompoundWall, isLumpSum = it.isLumpSum)
            }
        )
        afterCreate(id)
        id
    }

    fun observeAreaComponents(projectId: Long): Flow<List<ProjectAreaComponent>> =
        db.projectAreaComponentDao().observeForProject(projectId)

    suspend fun getAreaComponents(projectId: Long): List<ProjectAreaComponent> =
        db.projectAreaComponentDao().getForProject(projectId)

    /** Recomputes Project.plinthAreaSqft (total area), ratePerSqft (blended), and
     *  projectValue (sum of area×rate, unless manually overridden) from the current set of
     *  ProjectAreaComponent rows. Call after any add/edit/delete of a component so the
     *  headline figures on the dashboard stay correct. */
    private suspend fun recalculateAggregatesFromComponents(projectId: Long) {
        val project = db.projectDao().getById(projectId) ?: return
        val components = db.projectAreaComponentDao().getForProject(projectId)
        val building = components.filterNot { it.isCompoundWall }
        val totalArea = building.sumOf { it.areaSqft }
        val buildingValue = building.sumOf { CalculationEngine.projectValue(it.areaSqft, it.ratePerSqft) }
        val computedValue = components.sumOf { CalculationEngine.projectValue(it.areaSqft, it.ratePerSqft) }
        val blendedRate = if (totalArea > 0) buildingValue / totalArea else 0.0
        val newValue = if (project.isProjectValueManuallyOverridden) project.projectValue else computedValue
        db.projectDao().update(
            project.copy(plinthAreaSqft = totalArea, ratePerSqft = blendedRate, projectValue = newValue, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun addAreaComponent(
        projectId: Long, label: String, areaSqft: Double, ratePerSqft: Double,
        isCompoundWall: Boolean = false, isLumpSum: Boolean = false
    ) = db.withTransaction {
        db.projectAreaComponentDao().insertAll(listOf(ProjectAreaComponent(projectId = projectId, label = label, areaSqft = areaSqft,
            ratePerSqft = ratePerSqft, isCompoundWall = isCompoundWall, isLumpSum = isLumpSum)))
        recalculateAggregatesFromComponents(projectId)
    }

    suspend fun updateAreaComponent(component: ProjectAreaComponent) = db.withTransaction {
        db.projectAreaComponentDao().update(component)
        recalculateAggregatesFromComponents(component.projectId)
    }

    suspend fun deleteAreaComponent(component: ProjectAreaComponent) = db.withTransaction {
        db.projectAreaComponentDao().delete(component)
        recalculateAggregatesFromComponents(component.projectId)
    }

    suspend fun updateProject(project: Project) =
        db.projectDao().update(project.copy(updatedAt = System.currentTimeMillis()))

    /**
     * Saves the editable project details (Project Settings → Project). When the manual
     * override is switched off, the value is recomputed from the area/rate rows again.
     */
    suspend fun updateProjectDetails(project: Project, manualValue: Double?) = db.withTransaction {
        val updated = if (manualValue != null) project.copy(projectValue = manualValue, isProjectValueManuallyOverridden = true)
                      else project.copy(isProjectValueManuallyOverridden = false)
        db.projectDao().update(updated.copy(updatedAt = System.currentTimeMillis()))
        recalculateAggregatesFromComponents(project.id)
    }

    /**
     * Deletes a project and EVERYTHING that belongs to it, in one transaction. The schema
     * links child rows by plain projectId columns (no FK cascades), so previously deleting a
     * project left all its BOQ, expenses, vendors, schedule etc. orphaned in the database.
     */
    suspend fun deleteProject(project: Project) = db.withTransaction {
        val id = project.id
        db.scheduleDependencyDao().deleteForProject(id) // must run before activities are gone
        db.activityProgressLogDao().deleteForProject(id)
        db.scheduleActivityDao().deleteForProject(id)
        db.projectProgressDao().deleteForProject(id)
        db.labourEntryDao().deleteForProject(id)
        db.labourRateDao().deleteForProject(id)
        db.notificationDao().clearForProject(id)
        db.materialUsageDao().deleteForProject(id)
        db.materialPurchaseDao().deleteForProject(id)
        db.expenseDao().deleteForProject(id)
        db.clientPaymentDao().deleteForProject(id)
        db.vendorWorkDao().deleteForProject(id)
        db.vendorDao().deleteForProject(id)
        db.materialRateCardDao().deleteForProject(id)
        db.materialCoefficientDao().deleteForProject(id)
        db.materialDao().deleteForProject(id)
        db.boqNotationDao().deleteForProject(id)
        db.boqDao().deleteForProject(id)
        db.costAllocationDao().deleteForProject(id)
        db.projectAreaComponentDao().deleteForProject(id)
        db.projectDao().delete(project)
    }

    /** Removes every project flagged as demo data (and all its rows). */
    suspend fun deleteDemoProjects(): Int {
        val demos = db.projectDao().getDemoProjects()
        demos.forEach { deleteProject(it) }
        return demos.size
    }

    fun observeCostAllocations(projectId: Long): Flow<List<CostAllocation>> =
        db.costAllocationDao().observeForProject(projectId)

    suspend fun updateCostAllocation(allocation: CostAllocation) =
        db.costAllocationDao().update(allocation.copy(updatedAt = System.currentTimeMillis()))

    /** Saves the whole cost-distribution table at once (validated to 100% by the caller). */
    suspend fun saveCostAllocations(projectId: Long, allocations: List<CostAllocation>) = db.withTransaction {
        db.costAllocationDao().deleteForProject(projectId)
        db.costAllocationDao().insertAll(allocations.map { it.copy(id = 0, projectId = projectId, updatedAt = System.currentTimeMillis()) })
    }

    suspend fun getCostAllocations(projectId: Long): List<CostAllocation> =
        db.costAllocationDao().getForProject(projectId)
}
