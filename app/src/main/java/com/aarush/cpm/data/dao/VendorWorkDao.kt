package com.aarush.cpm.data.dao

import androidx.room.*
import com.aarush.cpm.data.entity.VendorWork
import kotlinx.coroutines.flow.Flow

@Dao
interface VendorWorkDao {
    @Insert
    suspend fun insert(work: VendorWork): Long

    @Update
    suspend fun update(work: VendorWork)

    @Delete
    suspend fun delete(work: VendorWork)

    @Query("SELECT * FROM vendor_works WHERE projectId = :projectId ORDER BY id")
    fun observeForProject(projectId: Long): Flow<List<VendorWork>>

    @Query("SELECT * FROM vendor_works WHERE vendorId = :vendorId ORDER BY id")
    suspend fun getForVendor(vendorId: Long): List<VendorWork>

    @Query("DELETE FROM vendor_works WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: Long)
}
