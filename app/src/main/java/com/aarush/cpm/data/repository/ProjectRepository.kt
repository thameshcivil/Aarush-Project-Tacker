package com.aarush.cpm.data.repository

import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.entity.CostAllocation
import com.aarush.cpm.data.entity.CostCategory
import com.aarush.cpm.data.entity.Project
import com.aarush.cpm.domain.calculation.CalculationEngine
import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val db: AppDatabase) {

    fun observeAll(): Flow<List<Project>> = db.projectDao().observeAll()

    fun observeById(id: Long): Flow<Project?> = db.projectDao().observeById(id)

    fun search(query: String): Flow<List<Project>> = db.projectDao().search(query)

    suspend fun getById(id: Long): Project? = db.projectDao().getById(id)

    /** Default thumb-rule split, per section 6. Fully editable afterward. */
    private fun defaultAllocations(projectId: Long): List<CostAllocation> = listOf(
        CostAllocation(projectId = projectId, category = CostCategory.CIVIL_STRUCTURAL, percentOfProjectValue = 50.0, materialPercent = 65.0, labourPercent = 35.0),
        CostAllocation(projectId = projectId, category = CostCategory.MEP, percentOfProjectValue = 15.0, materialPercent = 70.0, labourPercent = 30.0),
        CostAllocation(projectId = projectId, category = CostCategory.PAINTING, percentOfProjectValue = 8.0, materialPercent = 55.0, labourPercent = 45.0),
        CostAllocation(projectId = projectId, category = CostCategory.JOINERY, percentOfProjectValue = 12.0, materialPercent = 75.0, labourPercent = 25.0),
        CostAllocation(projectId = projectId, category = CostCategory.OTHER_MISC, percentOfProjectValue = 15.0, materialPercent = 50.0, labourPercent = 50.0)
    )

    /** Creates the project and seeds default category cost allocations in one step. */
    suspend fun createProject(
        name: String,
        clientName: String,
        clientContact: String,
        location: String,
        projectType: String,
        numberOfFloors: Int,
        plinthAreaSqft: Double,
        ratePerSqft: Double,
        manualProjectValue: Double? = null,
        startDate: Long,
        expectedCompletionDate: Long,
        notes: String = "",
        isDemoData: Boolean = false
    ): Long {
        val computedValue = CalculationEngine.projectValue(plinthAreaSqft, ratePerSqft)
        val finalValue = manualProjectValue ?: computedValue
        val project = Project(
            name = name,
            clientName = clientName,
            clientContact = clientContact,
            location = location,
            projectType = projectType,
            numberOfFloors = numberOfFloors,
            plinthAreaSqft = plinthAreaSqft,
            ratePerSqft = ratePerSqft,
            projectValue = finalValue,
            isProjectValueManuallyOverridden = manualProjectValue != null,
            startDate = startDate,
            expectedCompletionDate = expectedCompletionDate,
            notes = notes,
            isDemoData = isDemoData
        )
        val id = db.projectDao().insert(project)
        db.costAllocationDao().insertAll(defaultAllocations(id))
        return id
    }

    suspend fun updateProject(project: Project) =
        db.projectDao().update(project.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteProject(project: Project) = db.projectDao().delete(project)

    fun observeCostAllocations(projectId: Long): Flow<List<CostAllocation>> =
        db.costAllocationDao().observeForProject(projectId)

    suspend fun updateCostAllocation(allocation: CostAllocation) =
        db.costAllocationDao().update(allocation.copy(updatedAt = System.currentTimeMillis()))

    suspend fun getCostAllocations(projectId: Long): List<CostAllocation> =
        db.costAllocationDao().getForProject(projectId)
}
