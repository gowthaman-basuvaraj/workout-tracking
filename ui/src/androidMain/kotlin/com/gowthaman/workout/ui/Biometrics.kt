package com.gowthaman.workout.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.getSystemService

/**
 * The device's own unlock, borrowed.
 *
 * The platform's [BiometricPrompt], not androidx.biometric: the AndroidX library exists to
 * reach Android 8, and taking it would add 338 KB and a fragment dependency to an app that
 * has neither. minSdk is 28 instead, which is what the framework prompt needs.
 *
 * Device credential is always allowed alongside the fingerprint. There is no recovery path
 * here and nothing to recover -- a sensor that stops recognising someone must not lock
 * them out of their own log, especially when the files themselves stay readable in Files.
 */
object Biometrics {

    /** Why the lock cannot be switched on, or null when it can. */
    fun unavailableReason(context: Context): String? {
        // BiometricManager arrived in API 29. On 28 a secure lock screen is the honest
        // proxy: if there is one, the prompt has something to fall back to.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val keyguard = context.getSystemService<KeyguardManager>()
            return if (keyguard?.isDeviceSecure == true) null
            else "This phone has no screen lock set up, so there is nothing to unlock with."
        }

        val manager = context.getSystemService<BiometricManager>()
            ?: return "This phone cannot do biometric authentication."

        // Wrapped because this is asked on every composition of a settings screen that
        // has nothing to do with the lock. The platform throws if the permission is
        // missing, and an optional feature must not be able to take the screen down with
        // it -- being told the lock is unavailable is a far better failure than a crash.
        val status = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.canAuthenticate(ALLOWED)
            } else {
                @Suppress("DEPRECATION")
                manager.canAuthenticate()
            }
        }.getOrNull() ?: return "Biometric unlock is not available on this phone."

        return when (status) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            -> "This phone has no fingerprint or face unlock available."
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "Set up a fingerprint, face unlock or a screen lock first, in the phone's " +
                    "own settings."
            else -> "Biometric unlock is not available on this phone right now."
        }
    }

    /**
     * Shows the prompt.
     *
     * [onFailure] is the user giving up or the system refusing, not a single bad read --
     * the prompt handles retries itself and only calls back when it is finished.
     */
    fun prompt(
        activity: Activity,
        onSuccess: () -> Unit,
        onFailure: (String?) -> Unit,
    ) {
        val builder = BiometricPrompt.Builder(activity)
            .setTitle("Uncomplicated")
            .setDescription("Unlock to see your log.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(ALLOWED)
        } else {
            // The 28-29 spelling of the same thing. A negative button must not be set
            // alongside it, which is why there is no cancel button configured here.
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(code: Int, message: CharSequence?) {
                onFailure(message?.toString())
            }
        }

        runCatching {
            builder.build().authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                callback,
            )
        }.onFailure { onFailure(it.message) }
    }

    private const val ALLOWED =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
}
