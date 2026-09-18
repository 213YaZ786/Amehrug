package com.amehrug.app.model

/**
 * When the app must ask again. Pure Kotlin, so the arithmetic is checked
 * outside Android.
 *
 * Times are taken from a monotonic clock, not from the wall clock, so
 * changing the date on the phone cannot buy extra unlocked time.
 */
object LockPolicy {
    /** Seconds allowed away from the app before it locks. 0 means at once. */
    val TIMEOUTS = listOf(0, 15, 60, 300, 900)

    /** A fresh process always starts locked when the lock is on. */
    fun lockedAtStart(settings: AppSettings): Boolean = settings.lockEnabled

    /**
     * @param leftAtMillis monotonic time when the app went to the background,
     *   or null when it never did in this process.
     */
    fun shouldLock(settings: AppSettings, leftAtMillis: Long?, nowMillis: Long): Boolean {
        if (!settings.lockEnabled) return false
        if (leftAtMillis == null) return true
        val away = nowMillis - leftAtMillis
        // A clock that went backwards is treated as a long absence.
        if (away < 0) return true
        return away >= settings.lockTimeoutSeconds * 1000L
    }
}
