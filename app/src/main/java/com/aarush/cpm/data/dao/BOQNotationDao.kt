package com.aarush.cpm.data.dao

import androidx.room.*
import com.aarush.cpm.data.entity.BOQNotation
import com.aarush.cpm.data.entity.MaterialRateCard
import kotlinx.coroutines.flow.Flow

@Dao
interface BOQNotationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notations: List<BOQNotation>)

    @Update
    suspend fun update(notation: BOQNotation)

    @Delete
    suspend fun delete(notation: BOQNotation)

    @Query("SELECT * FROM boq_notations WHERE projectId = :projectId ORDER BY code")
    fun observeForProject(projectId: Long): Flow<List<BOQNotation>>

    @Query("SELECT * FROM boq_notations WHERE projectId = :projectId ORDER BY code")
    suspend fun getForProject(projectId: Long): List<BOQNotation>

    @Query("DELETE FROM boq_notations WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: Long)
}

@Dao
interface MaterialRateCardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rates: List<MaterialRateCard>)

    @Update
    suspend fun update(rate: MaterialRateCard)

    @Delete
    suspend fun delete(rate: MaterialRateCard)

    @Query("SELECT * FROM material_rates WHERE projectId = :projectId ORDER BY description")
    fun observeForProject(projectId: Long): Flow<List<MaterialRateCard>>

    @Query("SELECT * FROM material_rates WHERE projectId = :projectId ORDER BY description")
    suspend fun getForProject(projectId: Long): List<MaterialRateCard>

    @Query("DELETE FROM material_rates WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: Long)
}
