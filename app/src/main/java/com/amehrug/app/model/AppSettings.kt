package com.amehrug.app.model

/**
 * What sits in the dock at the bottom of the notes wall, in order. The
 * stored form is the enum name, so a value written by an older or a newer
 * version is simply dropped rather than breaking the dock.
 */
enum class DockItem { HOME, SEARCH, LAYOUT, LIST, NOTE, SETTINGS }

/** Everything the user can change. Stored inside the encrypted database. */
data class AppSettings(
    val lockEnabled: Boolean = false,
    val lockTimeoutSeconds: Int = 60,
    val dockOrder: List<DockItem> = DockItem.entries.toList(),
)

/**
 * Settings live as name and value rows, so adding one never needs a
 * migration. Reading is forgiving: an unknown or damaged value falls back to
 * the default instead of stopping the app. Pure Kotlin.
 */
object SettingsCodec {
    const val LOCK_ENABLED = "lock.enabled"
    const val LOCK_TIMEOUT = "lock.timeoutSeconds"
    const val DOCK_ORDER = "dock.order"

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
        return AppSettings(
            lockEnabled = enabled,
            lockTimeoutSeconds = timeout,
            dockOrder = decodeDockOrder(rows[DOCK_ORDER]),
        )
    }

    /**
     * Forgiving on purpose. A name nobody knows is dropped, a repeat is
     * ignored, and anything the stored order leaves out is appended in its
     * normal place. The dock always ends up complete and never empty, so a
     * damaged row cannot hide a button.
     */
    fun decodeDockOrder(raw: String?): List<DockItem> {
        val known = DockItem.entries.associateBy { it.name }
        val out = LinkedHashSet<DockItem>()
        for (name in raw.orEmpty().split(',')) {
            val item = known[name.trim()] ?: continue
            out.add(item)
        }
        out.addAll(DockItem.entries)
        return out.toList()
    }

    fun encodeLockEnabled(enabled: Boolean): Pair<String, String> = LOCK_ENABLED to enabled.toString()

    fun encodeLockTimeout(seconds: Int): Pair<String, String> =
        LOCK_TIMEOUT to (if (seconds in LockPolicy.TIMEOUTS) seconds else AppSettings().lockTimeoutSeconds).toString()

    fun encodeDockOrder(order: List<DockItem>): Pair<String, String> =
        DOCK_ORDER to decodeDockOrder(order.joinToString(",") { it.name }).joinToString(",") { it.name }
}
