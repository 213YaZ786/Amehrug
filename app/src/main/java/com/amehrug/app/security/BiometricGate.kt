package com.amehrug.app.security

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import com.amehrug.app.R

/**
 * The platform prompt, on purpose: minSdk is 31, so androidx.biometric would
 * add a dependency for an API the system already has.
 *
 * Fingerprint, face or the device credential, whichever the phone offers.
 * With DEVICE_CREDENTIAL allowed there must be no negative button.
 */
object BiometricGate {

    fun authenticate(
        activity: Activity,
        onSuccess: () -> Unit,
        onFailure: (CharSequence?) -> Unit,
    ) {
        val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
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
    }
}
