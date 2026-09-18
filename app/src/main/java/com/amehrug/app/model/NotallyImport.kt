package com.amehrug.app.model

/**
 * One row of Notally's `BaseNote` table, as plain text, and the rules that
 * turn it into a note of ours.
 *
 * Notally keeps labels, styles, list items, images, audio and the reminder as
 * JSON inside text columns. Everything here is pure Kotlin, so the mapping is
 * checked outside Android against the real shapes read in its source.
 *
 * Nothing in this file throws on bad input. A row from a backup is data from
 * outside: a damaged piece is dropped, the rest of the note still arrives.
 */
data class NotallyRow(
    val type: String,
    val folder: String,
    val color: String,
    val title: String,
    val pinned: Boolean,
    val timestamp: Long,
    val labels: String,
    val body: String,
    val spans: String,
    val items: String,
    val images: String,
    val audios: String,
    val reminder: String?,
)

object NotallyImport {
    /** Notally's audio files are m4a, its images carry their own type. */
    const val AUDIO_MIME = "audio/mp4"

    fun toNote(row: NotallyRow, nowMillis: Long): Note {
        val type = enumOr(row.type, NoteType.entries, NoteType.NOTE)
        val folder = enumOr(row.folder, Folder.entries, Folder.NOTES)
        val note = Note(
            type = type,
            folder = folder,
            color = enumOr(row.color, NoteColor.entries, NoteColor.DEFAULT),
            title = row.title,
            body = row.body,
            spans = readSpans(row.spans),
            items = readItems(row.items),
            labels = readLabels(row.labels),
            attachments = readImages(row.images) + readAudios(row.audios),
            reminders = readReminder(row.reminder),
            pinned = row.pinned,
            createdAt = row.timestamp,
            modifiedAt = row.timestamp,
            // The thirty day clock starts at the import, so a note that sat
            // in Notally's bin for a year is not swept away on arrival.
            deletedAt = if (folder == Folder.DELETED) nowMillis else null,
        )
        return NoteRules.sanitize(note)
    }

    private fun readLabels(json: String): List<String> =
        array(json).mapNotNull { value -> (value as? JsonValue.Str)?.value }

    private fun readSpans(json: String): List<TextSpan> = array(json).mapNotNull { value ->
        val fields = value.asObject() ?: return@mapNotNull null
        TextSpan(
            start = fields.get("start").asInt(-1),
            end = fields.get("end").asInt(-1),
            bold = fields.get("bold").asBool(),
            italic = fields.get("italic").asBool(),
            monospace = fields.get("monospace").asBool(),
            strikethrough = fields.get("strikethrough").asBool(),
            link = fields.get("link").asBool(),
        ).takeIf { it.start >= 0 && it.end > it.start }
    }

    private fun readItems(json: String): List<ListItem> = array(json).mapNotNull { value ->
        val fields = value.asObject() ?: return@mapNotNull null
        ListItem(body = fields.get("body").asString(), checked = fields.get("checked").asBool())
    }

    private fun readImages(json: String): List<Attachment> = array(json).mapNotNull { value ->
        val fields = value.asObject() ?: return@mapNotNull null
        val name = fields.get("name").asString()
        if (!safeName(name)) return@mapNotNull null
        Attachment(
            kind = AttachmentKind.IMAGE,
            fileName = name,
            mimeType = fields.get("mimeType").asString("image/jpeg"),
        )
    }

    private fun readAudios(json: String): List<Attachment> = array(json).mapNotNull { value ->
        val fields = value.asObject() ?: return@mapNotNull null
        val name = fields.get("name").asString()
        if (!safeName(name)) return@mapNotNull null
        Attachment(
            kind = AttachmentKind.AUDIO,
            fileName = name,
            mimeType = AUDIO_MIME,
            durationMs = fields.get("duration").asLong().takeIf { it > 0 },
            createdAt = fields.get("timestamp").asLong(),
        )
    }

    private fun readReminder(json: String?): List<Reminder> {
        if (json.isNullOrBlank()) return emptyList()
        val fields = parse(json)?.asObject() ?: return emptyList()
        val at = fields.get("timestamp").asLong()
        if (at <= 0) return emptyList()
        // Notally knows ONCE, DAILY and MONTHLY. The others are ours.
        return listOf(Reminder(at, enumOr(fields.get("frequency").asString(), Repeat.entries, Repeat.ONCE)))
    }

    /** A name with a path in it would reach outside the backup. */
    private fun safeName(name: String): Boolean =
        name.isNotEmpty() && !name.contains('/') && !name.contains('\\') && !name.contains("..")

    private fun array(json: String): List<JsonValue> = parse(json).asArray()

    private fun parse(json: String): JsonValue? = try {
        Json.parse(json)
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun <T : Enum<T>> enumOr(name: String, values: List<T>, fallback: T): T =
        values.firstOrNull { it.name == name } ?: fallback
}
