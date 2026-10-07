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

    @Query("SELECT COALESCE(SUM(totalAmount), 0) FROM expenses WHERE projectId = :projectId AND direction = 'EXPENSE'")
    fun observeTotalSpent(projectId: Long): Flow<Double>

    /** Source of truth for a vendor's paid amount — Vendor.amountPaid is a cache of this. */
    @Query("SELECT COALESCE(SUM(totalAmount), 0) FROM expenses WHERE vendorId = :vendorId AND direction = 'EXPENSE'")
    suspend fun sumPaidToVendor(vendorId: Long): Double

    @Query("UPDATE expenses SET itemOrMaterialName = :newName WHERE projectId = :projectId AND itemOrMaterialName = :oldName")
    suspend fun renameMaterial(projectId: Long, oldName: String, newName: String)

    @Query("UPDATE expenses SET itemOrMaterialName = :newName WHERE vendorId = :vendorId AND type = 'VENDOR'")
    suspend fun renameVendorLine(vendorId: Long, newName: String)

    @Query("DELETE FROM expenses WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: Long)
}
