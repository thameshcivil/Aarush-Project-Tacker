package com.aarush.cpm.data.dao

import androidx.room.*
import com.aarush.cpm.data.entity.CashEntry
import com.aarush.cpm.data.entity.ProjectPartner
import kotlinx.coroutines.flow.Flow

@Dao
interface CashDao {
    @Query("SELECT * FROM project_partners WHERE projectId = :projectId ORDER BY id")
    fun observePartners(projectId: Long): Flow<List<ProjectPartner>>

    @Query("SELECT * FROM project_partners WHERE projectId = :projectId ORDER BY id")
    suspend fun getPartners(projectId: Long): List<ProjectPartner>

    @Insert
    suspend fun insertPartner(partner: ProjectPartner): Long

    @Update
    suspend fun updatePartner(partner: ProjectPartner)

    @Delete
    suspend fun deletePartner(partner: ProjectPartner)

    @Query("SELECT * FROM cash_entries WHERE projectId = :projectId ORDER BY date DESC, id DESC")
    fun observeEntries(projectId: Long): Flow<List<CashEntry>>

    @Insert
    suspend fun insertEntry(entry: CashEntry): Long

    @Delete
    suspend fun deleteEntry(entry: CashEntry)

    @Query("SELECT COUNT(*) FROM cash_entries WHERE partnerId = :partnerId")
    suspend fun entryCount(partnerId: Long): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE paidByPartnerId = :partnerId")
    suspend fun expenseCount(partnerId: Long): Int

    @Query("DELETE FROM cash_entries WHERE projectId = :projectId")
    suspend fun deleteEntriesForProject(projectId: Long)

    @Query("DELETE FROM project_partners WHERE projectId = :projectId")
    suspend fun deletePartnersForProject(projectId: Long)
}
