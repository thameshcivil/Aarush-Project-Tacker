package com.aarush.cpm.data.dao

import androidx.room.*
import com.aarush.cpm.data.entity.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("SELECT * FROM expenses WHERE projectId = :projectId ORDER BY date DESC")
    fun observeForProject(projectId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE projectId = :projectId")
    suspend fun getForProject(projectId: Long): List<Expense>

    @Query("SELECT * FROM expenses WHERE projectId = :projectId AND vendorId = :vendorId")
    suspend fun getForVendor(projectId: Long, vendorId: Long): List<Expense>

    @Query("SELECT COALESCE(SUM(totalAmount), 0) FROM expenses WHERE projectId = :projectId")
    fun observeTotalSpent(projectId: Long): Flow<Double>
}
