package com.aarush.cpm.data.dao

import androidx.room.*
import com.aarush.cpm.data.entity.CostAllocation
import kotlinx.coroutines.flow.Flow

@Dao
interface CostAllocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(allocations: List<CostAllocation>)

    @Update
    suspend fun update(allocation: CostAllocation)

    @Query("SELECT * FROM cost_allocations WHERE projectId = :projectId")
    fun observeForProject(projectId: Long): Flow<List<CostAllocation>>

    @Query("SELECT * FROM cost_allocations WHERE projectId = :projectId")
    suspend fun getForProject(projectId: Long): List<CostAllocation>

    @Query("DELETE FROM cost_allocations WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: Long)
}
