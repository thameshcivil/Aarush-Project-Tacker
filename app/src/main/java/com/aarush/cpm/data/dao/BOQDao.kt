package com.aarush.cpm.data.dao

import androidx.room.*
import com.aarush.cpm.data.entity.BOQItem
import com.aarush.cpm.data.entity.CostCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface BOQDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BOQItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BOQItem>)

    @Update
    suspend fun update(item: BOQItem)

    @Delete
    suspend fun delete(item: BOQItem)

    @Query("SELECT * FROM boq_items WHERE projectId = :projectId ORDER BY category, itemCode")
    fun observeForProject(projectId: Long): Flow<List<BOQItem>>

    @Query("SELECT * FROM boq_items WHERE projectId = :projectId AND category = :category ORDER BY itemCode")
    fun observeForCategory(projectId: Long, category: CostCategory): Flow<List<BOQItem>>

    @Query("SELECT * FROM boq_items WHERE projectId = :projectId")
    suspend fun getForProject(projectId: Long): List<BOQItem>

    /** Keeps BOQ rows linked when a notation code is renamed in project Settings. */
    @Query("UPDATE boq_items SET itemCode = :newCode WHERE projectId = :projectId AND itemCode = :oldCode")
    suspend fun renameItemCode(projectId: Long, oldCode: String, newCode: String)

    @Query("UPDATE boq_items SET materialType = :newCode WHERE projectId = :projectId AND materialType = :oldCode")
    suspend fun renameMaterialType(projectId: Long, oldCode: String, newCode: String)

    @Query("DELETE FROM boq_items WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: Long)
}
