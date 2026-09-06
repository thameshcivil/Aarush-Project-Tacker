package com.aarush.cpm.data.dao

import androidx.room.*
import com.aarush.cpm.data.entity.ClientPayment
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientPaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: ClientPayment): Long

    @Update
    suspend fun update(payment: ClientPayment)

    @Delete
    suspend fun delete(payment: ClientPayment)

    @Query("SELECT * FROM client_payments WHERE projectId = :projectId ORDER BY date DESC")
    fun observeForProject(projectId: Long): Flow<List<ClientPayment>>

    @Query("SELECT * FROM client_payments WHERE projectId = :projectId")
    suspend fun getForProject(projectId: Long): List<ClientPayment>

    @Query("SELECT COALESCE(SUM(amountReceived), 0) FROM client_payments WHERE projectId = :projectId")
    fun observeTotalReceived(projectId: Long): Flow<Double>
}
