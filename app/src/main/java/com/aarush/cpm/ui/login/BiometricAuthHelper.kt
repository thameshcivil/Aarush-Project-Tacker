package com.aarush.cpm.ui.login

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.resume

/** Wraps BiometricPrompt (fingerprint/face/device-credential unlock) as a plain suspend
 *  function so it's easy to call from a ViewModel/coroutine without callback boilerplate. */
object BiometricAuthHelper {

    private const val ALLOWED_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** True if this device has usable biometric or device-credential (PIN/pattern) unlock
     *  enrolled right now — check before showing a "Sign in with biometrics" button. */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    suspend fun authenticate(activity: FragmentActivity, title: String, subtitle: String): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(Result.success(Unit))
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(Result.failure(IllegalStateException(errString.toString())))
                }
                override fun onAuthenticationFailed() {
                    // A single failed match (e.g. unrecognized finger) — the prompt stays open
                    // for another attempt, so we don't resolve the coroutine here.
                }
            }
            val prompt = BiometricPrompt(activity, executor, callback)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                .build()
            prompt.authenticate(promptInfo)
        }
}
