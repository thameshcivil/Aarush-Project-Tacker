package com.aarush.cpm.data.dao

import androidx.room.*
import com.aarush.cpm.data.entity.Vendor
import kotlinx.coroutines.flow.Flow

@Dao
interface VendorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vendor: Vendor): Long

    @Update
    suspend fun update(vendor: Vendor)

    @Delete
    suspend fun delete(vendor: Vendor)

    @Query("SELECT * FROM vendors WHERE projectId = :projectId ORDER BY name")
    fun observeForProject(projectId: Long): Flow<List<Vendor>>

    @Query("SELECT * FROM vendors WHERE id = :id")
    suspend fun getById(id: Long): Vendor?
}
