package com.aarush.cpm.data.repository

import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.entity.User
import java.security.MessageDigest

/**
 * V1: local/mock authentication backed by Room. Swap this class's internals for
 * FirebaseAuth (or any REST backend) later without touching ViewModels/UI —
 * they only depend on the suspend functions below.
 */
class AuthRepository(private val db: AppDatabase) {

    private fun hash(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun register(username: String, email: String, password: String): Result<Long> {
        val existing = db.userDao().findByEmail(email)
        if (existing != null) return Result.failure(IllegalStateException("An account already exists for this email."))
        val id = db.userDao().insert(User(username = username, email = email, passwordHash = hash(password)))
        return Result.success(id)
    }

    suspend fun login(usernameOrEmail: String, password: String, rememberMe: Boolean): Result<User> {
        val user = db.userDao().findByUsernameOrEmail(usernameOrEmail)
            ?: return Result.failure(IllegalStateException("No account found."))
        return if (user.passwordHash == hash(password)) {
            if (rememberMe != user.rememberMe) db.userDao().insert(user.copy(rememberMe = rememberMe))
            Result.success(user)
        } else {
            Result.failure(IllegalStateException("Incorrect password."))
        }
    }
}
