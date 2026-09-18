package com.amehrug.app.model

/** Rules applied to every note before it is stored. Pure Kotlin. */
object NoteRules {
    const val MAX_INDENT = 3
    const val MAX_LABEL_LENGTH = 50
    const val TRASH_DAYS = 30
    private const val DAY_MS = 24L * 60 * 60 * 1000

    private val spaces = Regex("\\s+")

    /** Trimmed, single spaced, at most 50 characters, or null when nothing is left. */
    fun normalizeLabel(raw: String): String? {
        val clean = raw.trim().replace(spaces, " ").take(MAX_LABEL_LENGTH).trim()
        return clean.ifEmpty { null }
    }

    /** The text of all list items, one per line, for full text search. */
    fun itemsText(items: List<ListItem>): String =
        items.map { it.body.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    fun isEmpty(note: Note): Boolean =
        note.title.isBlank() &&
            note.body.isBlank() &&
            note.items.all { it.body.isBlank() } &&
            note.attachments.isEmpty()

    /** Notes deleted before this instant are purged from the trash. */
    fun trashCutoff(nowMillis: Long, days: Int = TRASH_DAYS): Long = nowMillis - days * DAY_MS

    fun sanitize(note: Note): Note {
        val title = note.title.replace('\n', ' ').replace('\r', ' ')
        val length = note.body.length
        val spans = note.spans
            .map { span -> span.copy(start = span.start.coerceIn(0, length), end = span.end.coerceIn(0, length)) }
            .filter { span -> span.hasStyle && span.end > span.start }
            .distinct()
            .sortedWith(compareBy({ it.start }, { it.end }))
        val items = note.items.map { item ->
            item.copy(
                body = item.body.replace('\n', ' ').replace('\r', ' '),
                indent = item.indent.coerceIn(0, MAX_INDENT),
            )
        }
        val labels = LinkedHashMap<String, String>()
        for (raw in note.labels) {
            val label = normalizeLabel(raw) ?: continue
            // Labels are case insensitive, the first spelling wins.
            labels.putIfAbsent(label.lowercase(), label)
        }
        val reminders = note.reminders.distinct().sortedBy { it.atMillis }
        return note.copy(
            title = title,
            spans = spans,
            items = items,
            labels = labels.values.toList(),
            reminders = reminders,
        )
    }
}
