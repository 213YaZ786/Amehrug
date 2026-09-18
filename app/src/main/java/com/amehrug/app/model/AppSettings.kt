package com.amehrug.app.model

/** Everything the user can change. Stored inside the encrypted database. */
data class AppSettings(
    val lockEnabled: Boolean = false,
    val lockTimeoutSeconds: Int = 60,
)

/**
 * Settings live as name and value rows, so adding one never needs a
 * migration. Reading is forgiving: an unknown or damaged value falls back to
 * the default instead of stopping the app. Pure Kotlin.
 */
object SettingsCodec {
    const val LOCK_ENABLED = "lock.enabled"
    const val LOCK_TIMEOUT = "lock.timeoutSeconds"

    fun decode(rows: Map<String, String>): AppSettings {
        val defaults = AppSettings()
        val enabled = when (rows[LOCK_ENABLED]) {
            "true" -> true
            "false" -> false
            else -> defaults.lockEnabled
        }
        val timeout = rows[LOCK_TIMEOUT]?.toIntOrNull()
            ?.takeIf { it in LockPolicy.TIMEOUTS }
            ?: defaults.lockTimeoutSeconds
        return AppSettings(lockEnabled = enabled, lockTimeoutSeconds = timeout)
    }

    fun encodeLockEnabled(enabled: Boolean): Pair<String, String> = LOCK_ENABLED to enabled.toString()

    fun encodeLockTimeout(seconds: Int): Pair<String, String> =
        LOCK_TIMEOUT to (if (seconds in LockPolicy.TIMEOUTS) seconds else AppSettings().lockTimeoutSeconds).toString()
}
