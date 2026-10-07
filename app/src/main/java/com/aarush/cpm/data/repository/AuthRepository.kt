package com.aarush.cpm.data.repository

import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.entity.User
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * V1: local authentication backed by Room. Swap this class's internals for FirebaseAuth
 * (or any REST backend) later without touching ViewModels/UI.
 *
 * Passwords are stored as salted PBKDF2-HMAC-SHA256 ("pbkdf2$iterations$salt$hash").
 * Accounts created by older builds (plain unsalted SHA-256) still log in, and are upgraded
 * to the salted format on their next successful login.
 */
class AuthRepository(private val db: AppDatabase) {

    private val iterations = 120_000

    private fun legacySha256(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun pbkdf2(password: String, salt: ByteArray, iter: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iter, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    internal fun hash(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val enc = Base64.getEncoder()
        return "pbkdf2\$$iterations\$${enc.encodeToString(salt)}\$${enc.encodeToString(pbkdf2(password, salt, iterations))}"
    }

    internal fun verify(password: String, stored: String): Boolean {
        if (stored.isBlank()) return false
        if (!stored.startsWith("pbkdf2$")) return MessageDigest.isEqual(legacySha256(password).toByteArray(), stored.toByteArray())
        val parts = stored.split('$')
        if (parts.size != 4) return false
        val dec = Base64.getDecoder()
        val expected = dec.decode(parts[3])
        return MessageDigest.isEqual(pbkdf2(password, dec.decode(parts[2]), parts[1].toInt()), expected)
    }

    /** Creates the account and returns it — callers must remember the *stored* email
     *  (trimmed), not the raw text typed, or biometric sign-in can't find it later. */
    suspend fun register(username: String, email: String, password: String): Result<User> {
        val cleanEmail = email.trim()
        if (!cleanEmail.contains("@")) return Result.failure(IllegalArgumentException("Enter a valid email address."))
        if (password.length < 6) return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        val existing = db.userDao().findByEmail(cleanEmail)
        if (existing != null) return Result.failure(IllegalStateException("An account already exists for this email."))
        if (db.userDao().findByUsernameOrEmail(username.trim()) != null)
            return Result.failure(IllegalStateException("That username is taken."))
        db.userDao().insert(User(username = username.trim(), email = cleanEmail, passwordHash = hash(password)))
        return db.userDao().findByEmail(cleanEmail)?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Could not create the account."))
    }

    suspend fun login(usernameOrEmail: String, password: String, rememberMe: Boolean): Result<User> {
        val user = db.userDao().findByUsernameOrEmail(usernameOrEmail.trim())
            ?: return Result.failure(IllegalStateException("No account found."))
        if (user.authProvider == "GOOGLE" && user.passwordHash.isBlank())
            return Result.failure(IllegalStateException("This account uses Sign in with Google."))
        return if (verify(password, user.passwordHash)) {
            val upgraded = if (!user.passwordHash.startsWith("pbkdf2$")) user.copy(passwordHash = hash(password)) else user
            if (upgraded != user || rememberMe != user.rememberMe) db.userDao().update(upgraded.copy(rememberMe = rememberMe))
            Result.success(upgraded)
        } else {
            Result.failure(IllegalStateException("Incorrect password."))
        }
    }

    /**
     * Called after Credential Manager / Google has already authenticated the person on-device.
     * A production build with a real backend should verify the Google ID token server-side.
     */
    suspend fun loginWithGoogle(email: String, displayName: String): Result<User> {
        val existing = db.userDao().findByEmail(email.trim())
        if (existing != null) return Result.success(existing)

        db.userDao().insert(
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

    /** Biometric quick sign-in: BiometricPrompt has already proven device ownership. */
    suspend fun loginRemembered(email: String): Result<User> =
        db.userDao().findByEmail(email.trim())?.let { Result.success(it) }
            ?: Result.failure(RememberedAccountMissing())

    /** True if the remembered account is still in this device's database. */
    suspend fun accountExists(email: String): Boolean = db.userDao().findByEmail(email.trim()) != null
}

/** The remembered email points at no account — e.g. the app data was cleared, the app was
 *  reinstalled, or a backup without that account was restored (the remembered sign-in is
 *  kept outside the database, so it can outlive the account). */
class RememberedAccountMissing : IllegalStateException(
    "This device no longer has the saved account (the app data was reset or a different backup was restored). " +
        "Sign in with your password, or create the account again."
) {
}
