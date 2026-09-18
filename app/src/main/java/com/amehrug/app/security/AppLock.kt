package com.amehrug.app.security

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.os.SystemClock
import com.amehrug.app.model.AppSettings
import com.amehrug.app.model.LockPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the notes are hidden right now.
 *
 * Starts locked and stays locked until the settings say otherwise, so no note
 * can appear for an instant before the lock has decided. Holds no Compose
 * type, so it can be read outside Android.
 */
class AppLock(private val now: () -> Long = { SystemClock.elapsedRealtime() }) {

    private val lockedState = MutableStateFlow(true)

    val locked: StateFlow<Boolean> = lockedState

    private var settings: AppSettings? = null

    private var leftAt: Long? = null

    /** Called once the settings are known, and on every change. */
    fun onSettings(settings: AppSettings) {
        this.settings = settings
        if (!settings.lockEnabled) lockedState.value = false
    }

    fun onForeground() {
        val current = settings ?: return
        if (LockPolicy.shouldLock(current, leftAt, now())) lockedState.value = true
    }

    fun onBackground() {
        leftAt = now()
    }

    fun unlock() {
        lockedState.value = false
        leftAt = null
    }

    fun lockNow() {
        if (settings?.lockEnabled == true) lockedState.value = true
    }

    companion object {
        /** The lock is only offered when the phone itself has a screen lock. */
        fun available(context: Context): Boolean {
            val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return false
            if (!keyguard.isDeviceSecure) return false
            val biometrics = context.getSystemService(BiometricManager::class.java) ?: return false
            val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            return biometrics.canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS
        }
    }
}
