package com.amehrug.app.security

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.os.SystemClock
import com.amehrug.app.diagnostics.Diagnostics
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

    private var decided = false

    /**
     * The departure time read back from disk, handed over before the first
     * settings arrive. Called more than once it does nothing, so a screen
     * that comes and goes cannot hand the app a second free window.
     */
    fun restore(storedLeftAt: Long?) {
        if (decided) return
        if (leftAt == null) leftAt = storedLeftAt
    }

    /**
     * Called once the settings are known, and on every change.
     *
     * The first call of a process is the one that decides. Until it lands
     * nothing is known about the timeout, so the app stays locked, which is
     * why it starts that way. Later calls never unlock: a colour changed in
     * the settings must not open a locked app.
     */
    fun onSettings(settings: AppSettings) {
        this.settings = settings
        if (!settings.lockEnabled) {
            lockedState.value = false
            return
        }
        if (!decided) {
            decided = true
            if (!LockPolicy.shouldLock(settings, leftAt, now())) lockedState.value = false
        }
    }

    fun onForeground() {
        val current = settings ?: return
        if (LockPolicy.shouldLock(current, leftAt, now())) lockedState.value = true
    }

    /** @return the time to write down, or null when there is nothing to keep. */
    fun onBackground(): Long? {
        val at = now()
        leftAt = at
        return if (settings?.lockEnabled == true) at else null
    }

    fun unlock() {
        lockedState.value = false
        leftAt = null
    }

    fun lockNow() {
        if (settings?.lockEnabled == true) {
            lockedState.value = true
            leftAt = null
        }
    }

    companion object {
        /**
         * The lock is only offered when the phone itself has a screen lock.
         * This asks the keyguard alone, which needs no permission and cannot
         * throw, so opening the settings is never at the mercy of the
         * biometric service.
         */
        fun available(context: Context): Boolean {
            val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return false
            return keyguard.isDeviceSecure
        }

        /**
         * Whether a fingerprint or a face is enrolled. Reading this needs
         * USE_BIOMETRIC and talks to another process, and it was a crash on
         * the settings screen once, so every failure is logged and read as
         * "no biometrics" instead of taking the app down.
         */
        fun biometricsAvailable(context: Context): Boolean = try {
            val biometrics = context.getSystemService(BiometricManager::class.java)
            biometrics != null &&
                biometrics.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            Diagnostics.log.error("lock", "asking for biometrics", e)
            false
        }
    }
}
