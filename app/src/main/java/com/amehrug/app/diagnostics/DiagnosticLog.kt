package com.amehrug.app.diagnostics

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.IdentityHashMap

// Pure Kotlin on purpose: no Android import, so it runs under kotlinc.
//
// Privacy rule: this log never holds note content. Callers write their own
// short, fixed messages. Exceptions are recorded by class and stack frames
// only, because an exception message can quote user data.

enum class Level { INFO, WARN, ERROR }

data class Entry(
    val atMillis: Long,
    val level: Level,
    val tag: String,
    val message: String,
)

class DiagnosticLog(
    private val capacity: Int = 500,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val nanoClock: () -> Long = { System.nanoTime() },
) {
    private val lock = Any()
    private val entries = ArrayDeque<Entry>()

    fun info(tag: String, message: String) = add(Level.INFO, tag, message)

    fun warn(tag: String, message: String) = add(Level.WARN, tag, message)

    fun error(tag: String, message: String) = add(Level.ERROR, tag, message)

    fun error(tag: String, where: String, throwable: Throwable) =
        add(Level.ERROR, tag, where + "\n" + describe(throwable), multiline = true)

    /** Adds a block of text written earlier by this app, such as a saved crash. */
    fun restored(tag: String, text: String) = add(Level.ERROR, tag, text, multiline = true)

    /** Runs [block] and logs how long it took, in milliseconds. */
    fun <T> time(tag: String, label: String, block: () -> T): T {
        val start = nanoClock()
        try {
            return block()
        } finally {
            val elapsedMs = (nanoClock() - start) / 1_000_000
            add(Level.INFO, tag, "$label $elapsedMs ms")
        }
    }

    fun snapshot(): List<Entry> = synchronized(lock) { entries.toList() }

    fun clear() = synchronized(lock) { entries.clear() }

    fun export(header: List<String>, zone: ZoneId = ZoneId.systemDefault()): String {
        val lines = ArrayList<String>()
        lines.addAll(header)
        lines.add("")
        for (entry in snapshot()) {
            lines.add(formatEntry(entry, zone))
        }
        return lines.joinToString(separator = "\n", postfix = "\n")
    }

    private fun add(level: Level, tag: String, message: String, multiline: Boolean = false) {
        val clean = if (multiline) message.take(MAX_BLOCK) else oneLine(message)
        val entry = Entry(clock(), level, oneLine(tag).take(MAX_TAG), clean)
        synchronized(lock) {
            entries.addLast(entry)
            while (entries.size > capacity) entries.removeFirst()
        }
    }

    companion object {
        const val MAX_LINE = 300
        const val MAX_TAG = 40
        const val MAX_BLOCK = 8_000
        const val MAX_FRAMES = 15
        const val MAX_CAUSES = 5

        private val timeFormat: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

        fun oneLine(text: String): String =
            text.replace('\n', ' ').replace('\r', ' ').take(MAX_LINE)

        fun formatEntry(entry: Entry, zone: ZoneId): String {
            val time = timeFormat.format(Instant.ofEpochMilli(entry.atMillis).atZone(zone))
            return "$time ${entry.level.name.padEnd(5)} ${entry.tag}: ${entry.message}"
        }

        /** Class names and frames of [throwable] and its causes. Messages are dropped. */
        fun describe(throwable: Throwable): String {
            val out = StringBuilder()
            val seen: MutableSet<Throwable> = Collections.newSetFromMap(IdentityHashMap())
            var current: Throwable? = throwable
            var depth = 0
            while (current != null && depth < MAX_CAUSES && seen.add(current)) {
                if (depth > 0) out.append("Caused by: ")
                out.append(current.javaClass.name).append('\n')
                val frames = current.stackTrace
                val shown = minOf(frames.size, MAX_FRAMES)
                for (i in 0 until shown) {
                    out.append("  at ").append(frames[i].toString()).append('\n')
                }
                if (frames.size > shown) {
                    out.append("  ... ").append(frames.size - shown).append(" more\n")
                }
                current = current.cause
                depth += 1
            }
            return out.toString().trimEnd()
        }
    }
}
