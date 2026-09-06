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
}
