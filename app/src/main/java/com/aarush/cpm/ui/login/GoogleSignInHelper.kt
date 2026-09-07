package com.aarush.cpm.ui.login

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * Wraps the Credential Manager "Sign in with Google" flow. Requires a Web OAuth Client ID
 * from your own Firebase/Google Cloud project — see README "Setting up Google Sign-In".
 * This is a one-time setup step only the app owner can do (it needs your app's signing
 * certificate fingerprint registered with Google), so it's left as a placeholder here.
 */
data class GoogleAccountInfo(val email: String, val displayName: String)

object GoogleSignInHelper {

    /**
     * Launches the system "Sign in with Google" sheet and returns the chosen account's
     * email/display name on success. [serverClientId] is your OAuth 2.0 Web Client ID
     * (looks like "1234567890-abc...apps.googleusercontent.com").
     */
    suspend fun signIn(context: Context, serverClientId: String): Result<GoogleAccountInfo> {
        if (serverClientId.isBlank() || serverClientId.contains("REPLACE_WITH")) {
            return Result.failure(
                IllegalStateException(
                    "Google Sign-In isn't configured yet. Add your OAuth Web Client ID to " +
                        "res/values/strings.xml (google_web_client_id) — see README 'Setting up Google Sign-In'."
                )
            )
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val credentialManager = CredentialManager.create(context)
            val result = credentialManager.getCredential(request = request, context = context)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                Result.success(
                    GoogleAccountInfo(
                        email = googleIdTokenCredential.id,
                        displayName = googleIdTokenCredential.displayName ?: ""
                    )
                )
            } else {
                Result.failure(IllegalStateException("Unexpected credential type returned."))
            }
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: GoogleIdTokenParsingException) {
            Result.failure(e)
        }
    }
}
