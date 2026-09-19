package com.amehrug.app.security

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import com.amehrug.app.R
import com.amehrug.app.diagnostics.Diagnostics
import com.amehrug.app.model.LockMethod

/**
 * The platform prompt, on purpose: minSdk is 31, so androidx.biometric would
 * add a dependency for an API the system already has.
 *
 * The system asks and the system answers. Amehrug never sees a fingerprint
 * and never sees a code. BIOMETRIC offers the fingerprint or the face with
 * the screen code behind it, CODE asks for the screen code alone. With
 * DEVICE_CREDENTIAL allowed there must be no negative button.
 *
 * Both this call and the availability check need USE_BIOMETRIC. It is
 * declared in the manifest, granted at install, and grants no access to any
 * data. Anything that still goes wrong is logged and reported as a failed
 * unlock, never as a crash.
 */
object BiometricGate {

    fun authenticate(
        activity: Activity,
        method: LockMethod,
        onSuccess: () -> Unit,
        onFailure: (CharSequence?) -> Unit,
    ) {
        val allowed = when (method) {
            LockMethod.BIOMETRIC ->
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            LockMethod.CODE -> BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
        try {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(activity.getString(R.string.lock_prompt_title))
                .setDescription(activity.getString(R.string.lock_prompt_description))
                .setConfirmationRequired(false)
                .setAllowedAuthenticators(allowed)
                .build()
            prompt.authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        onFailure(errString)
                    }
                },
            )
        } catch (e: Exception) {
            Diagnostics.log.error("lock", "showing the unlock prompt", e)
            onFailure(activity.getString(R.string.lock_unavailable))
        }
    }
}
