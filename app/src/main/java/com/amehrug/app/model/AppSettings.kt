package com.amehrug.app.model

/**
 * What sits in the dock at the bottom of the notes wall, in order. The
 * stored form is the enum name, so a value written by an older or a newer
 * version is simply dropped rather than breaking the dock.
 */
enum class DockItem { HOME, SEARCH, LAYOUT, LIST, NOTE, SETTINGS }

/**
 * How the system is asked to confirm it is you. BIOMETRIC offers the
 * fingerprint or the face and still accepts the screen code, CODE asks for
 * the screen code alone. Both are the system prompt: Amehrug never sees and
 * never stores a code.
 */
enum class LockMethod { BIOMETRIC, CODE }

/** Everything the user can change. Stored inside the encrypted database. */
data class AppSettings(
    val lockEnabled: Boolean = false,
    val lockMethod: LockMethod = LockMethod.BIOMETRIC,
    val lockTimeoutSeconds: Int = 60,
    val noteTimestamp: NoteTimestamp = NoteTimestamp.DATE_TIME,
    val dockOrder: List<DockItem> = DockItem.entries.toList(),
)

/**
 * Settings live as name and value rows, so adding one never needs a
 * migration. Reading is forgiving: an unknown or damaged value falls back to
 * the default instead of stopping the app. Pure Kotlin.
 */
object SettingsCodec {
    const val LOCK_ENABLED = "lock.enabled"
    const val LOCK_METHOD = "lock.method"
    const val LOCK_TIMEOUT = "lock.timeoutSeconds"
    const val DOCK_ORDER = "dock.order"
    const val NOTE_TIMESTAMP = "notes.timestamp"

    /**
     * When the app was last put away, on the clock that counts since the
     * phone booted. It is not part of [AppSettings]: nothing on screen shows
     * it, and putting it there would make every trip to the background look
     * like a settings change to everything watching them.
     */
    const val LOCK_LEFT_AT = "lock.leftAt"

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
            lockMethod = decodeLockMethod(rows[LOCK_METHOD]),
            lockTimeoutSeconds = timeout,
            noteTimestamp = decodeNoteTimestamp(rows[NOTE_TIMESTAMP]),
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

    /** An unknown or damaged name falls back to the default method. */
    fun decodeLockMethod(raw: String?): LockMethod =
        LockMethod.entries.firstOrNull { it.name == raw?.trim() } ?: AppSettings().lockMethod

    fun encodeLockMethod(method: LockMethod): Pair<String, String> = LOCK_METHOD to method.name

    /** An unknown or damaged name falls back to the default format. */
    fun decodeNoteTimestamp(raw: String?): NoteTimestamp =
        NoteTimestamp.entries.firstOrNull { it.name == raw?.trim() } ?: AppSettings().noteTimestamp

    fun encodeNoteTimestamp(format: NoteTimestamp): Pair<String, String> = NOTE_TIMESTAMP to format.name

    fun encodeLockEnabled(enabled: Boolean): Pair<String, String> = LOCK_ENABLED to enabled.toString()

    fun encodeLockTimeout(seconds: Int): Pair<String, String> =
        LOCK_TIMEOUT to (if (seconds in LockPolicy.TIMEOUTS) seconds else AppSettings().lockTimeoutSeconds).toString()

    /** Anything that is not a plain positive number reads as "never". */
    fun decodeLockLeftAt(raw: String?): Long? = raw?.trim()?.toLongOrNull()?.takeIf { it >= 0L }

    fun encodeLockLeftAt(value: Long?): Pair<String, String> =
        LOCK_LEFT_AT to (value?.toString() ?: "")

    fun encodeDockOrder(order: List<DockItem>): Pair<String, String> =
        DOCK_ORDER to decodeDockOrder(order.joinToString(",") { it.name }).joinToString(",") { it.name }
}
