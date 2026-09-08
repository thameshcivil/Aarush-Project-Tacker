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

    /**
     * Called after Credential Manager / Google has already authenticated the person on-device
     * (see [com.aarush.cpm.ui.login.GoogleSignInHelper]). We trust that signed assertion for
     * this local-only V1 the same way we trust a typed password: it never leaves the device.
     * A production build with a real backend should still verify the Google ID token
     * server-side before treating someone as logged in.
     */
    suspend fun loginWithGoogle(email: String, displayName: String): Result<User> {
        val existing = db.userDao().findByEmail(email)
        if (existing != null) return Result.success(existing)

        val id = db.userDao().insert(
            User(
                username = displayName.ifBlank { email.substringBefore("@") },
                email = email,
                passwordHash = "", // no local password for Google accounts
                rememberMe = true,
                authProvider = "GOOGLE"
            )
        )
        val created = db.userDao().findByEmail(email)
            ?: return Result.failure(IllegalStateException("Could not create account for $email."))
        return Result.success(created)
    }

    /**
     * Used for biometric quick sign-in: BiometricPrompt has already proven the person is the
     * device owner, so this looks up the previously-remembered account by email with no
     * password check at all — the biometric check *is* the credential here.
     */
    suspend fun loginRemembered(email: String): Result<User> =
        db.userDao().findByEmail(email)?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Remembered account no longer exists."))
}
