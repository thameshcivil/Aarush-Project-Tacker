package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "client_payments")
data class ClientPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val date: Long,
    val amountReceived: Double,
    val paymentMode: PaymentMode,
    val referenceNumber: String = "",
    val notes: String = ""
)
