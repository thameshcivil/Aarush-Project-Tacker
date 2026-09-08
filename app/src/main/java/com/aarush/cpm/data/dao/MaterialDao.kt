package com.aarush.cpm.data.dao

import androidx.room.*
import com.aarush.cpm.data.entity.Material
import com.aarush.cpm.data.entity.MaterialCoefficient
import com.aarush.cpm.data.entity.MaterialPurchase
import com.aarush.cpm.data.entity.MaterialUsage
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(material: Material): Long

    @Update
    suspend fun update(material: Material)

    @Delete
    suspend fun delete(material: Material)

    @Query("SELECT * FROM materials WHERE projectId = :projectId ORDER BY name")
    fun observeForProject(projectId: Long): Flow<List<Material>>

    @Query("SELECT * FROM materials WHERE projectId = :projectId")
    suspend fun getForProject(projectId: Long): List<Material>
}

@Dao
interface MaterialCoefficientDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(coefficient: MaterialCoefficient): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(coefficients: List<MaterialCoefficient>)

    @Update
    suspend fun update(coefficient: MaterialCoefficient)

    @Delete
    suspend fun delete(coefficient: MaterialCoefficient)

    @Query("SELECT * FROM material_coefficients WHERE projectId = :projectId")
    fun observeForProject(projectId: Long): Flow<List<MaterialCoefficient>>

    @Query("SELECT * FROM material_coefficients WHERE projectId = :projectId")
    suspend fun getForProject(projectId: Long): List<MaterialCoefficient>

    @Query("SELECT * FROM material_coefficients WHERE projectId = :projectId AND workItemKey = :workItemKey")
    suspend fun getForWorkItem(projectId: Long, workItemKey: String): List<MaterialCoefficient>

    @Query("DELETE FROM material_coefficients WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: Long)
}

@Dao
interface MaterialPurchaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(purchase: MaterialPurchase): Long

    @Delete
    suspend fun delete(purchase: MaterialPurchase)

    @Query("SELECT * FROM material_purchases WHERE projectId = :projectId ORDER BY date DESC")
    fun observeForProject(projectId: Long): Flow<List<MaterialPurchase>>

    @Query("SELECT * FROM material_purchases WHERE projectId = :projectId")
    suspend fun getForProject(projectId: Long): List<MaterialPurchase>

    @Query("SELECT * FROM material_purchases WHERE projectId = :projectId AND materialName = :materialName")
    suspend fun getForMaterial(projectId: Long, materialName: String): List<MaterialPurchase>
}

@Dao
interface MaterialUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(usage: MaterialUsage): Long

    @Delete
    suspend fun delete(usage: MaterialUsage)

    @Query("SELECT * FROM material_usages WHERE projectId = :projectId ORDER BY date DESC")
    fun observeForProject(projectId: Long): Flow<List<MaterialUsage>>

    @Query("SELECT * FROM material_usages WHERE projectId = :projectId")
    suspend fun getForProject(projectId: Long): List<MaterialUsage>
}
