package com.aarush.cpm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class NotificationType {
    MATERIAL_SHORTAGE, BUDGET_EXCEEDED, VENDOR_PAYMENT_DUE, CLIENT_PAYMENT_PENDING,
    LABOUR_SHORTAGE, SCHEDULE_DELAY, UPCOMING_ACTIVITY, PROJECT_COMPLETION_APPROACHING
}

@Entity(tableName = "notifications")
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val type: NotificationType,
    val message: String,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/** Global, editable defaults referenced by the Calculation Engine unless a project overrides them. */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val email: String,
    val passwordHash: String,
    val rememberMe: Boolean = false,
    /** "LOCAL" for username/password accounts, "GOOGLE" for Google Sign-In accounts.
     *  Plain String (not an enum) to keep the schema migration trivial — see AppDatabase. */
    val authProvider: String = "LOCAL",
    val createdAt: Long = System.currentTimeMillis()
)
