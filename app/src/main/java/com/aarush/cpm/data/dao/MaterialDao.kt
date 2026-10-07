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

    @Query("UPDATE materials SET name = :newName WHERE projectId = :projectId AND name = :oldName")
    suspend fun rename(projectId: Long, oldName: String, newName: String)

    @Query("DELETE FROM materials WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: Long)
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

    @Query("UPDATE material_coefficients SET materialName = :newName WHERE projectId = :projectId AND materialName = :oldName")
    suspend fun renameMaterial(projectId: Long, oldName: String, newName: String)

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

    /** Keeps stock in sync when the originating Expense is edited or deleted. */
    @Query("DELETE FROM material_purchases WHERE expenseId = :expenseId")
    suspend fun deleteForExpense(expenseId: Long)

    @Query("UPDATE material_purchases SET materialName = :newName WHERE projectId = :projectId AND materialName = :oldName")
    suspend fun rename(projectId: Long, oldName: String, newName: String)

    @Query("DELETE FROM material_purchases WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: Long)
}

@Dao
interface MaterialUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(usage: MaterialUsage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(usages: List<MaterialUsage>)

    @Delete
    suspend fun delete(usage: MaterialUsage)

    @Query("SELECT * FROM material_usages WHERE projectId = :projectId ORDER BY date DESC")
    fun observeForProject(projectId: Long): Flow<List<MaterialUsage>>

    @Query("SELECT * FROM material_usages WHERE projectId = :projectId")
    suspend fun getForProject(projectId: Long): List<MaterialUsage>

    @Query("DELETE FROM material_usages WHERE progressLogId = :logId")
    suspend fun deleteForProgressLog(logId: Long)

    @Query("UPDATE material_usages SET materialName = :newName WHERE projectId = :projectId AND materialName = :oldName")
    suspend fun rename(projectId: Long, oldName: String, newName: String)

    @Query("DELETE FROM material_usages WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: Long)
}
