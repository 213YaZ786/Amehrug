package com.amehrug.app.model

/**
 * What a backup holds, in JSON. Pure Kotlin, so the format is checked
 * outside Android.
 *
 * Reading is forgiving on purpose: a note with an unknown colour or type
 * comes back with the default instead of failing the whole restore. What is
 * not forgiven is malformed JSON.
 */
data class BackupContent(
    val version: Int = BackupFormat.VERSION,
    val createdAt: Long = 0,
    val notes: List<Note> = emptyList(),
    val labels: List<String> = emptyList(),
)

object BackupFormat {
    const val VERSION = 1
    const val JSON_ENTRY = "amehrug.json"
    const val MEDIA_PREFIX = "media/"

    fun encode(content: BackupContent): String = Json.write(
        JsonValue.Obj(
            linkedMapOf(
                "format" to JsonValue.Str("amehrug-backup"),
                "version" to JsonValue.Num(content.version.toDouble()),
                "createdAt" to JsonValue.Num(content.createdAt.toDouble()),
                "labels" to JsonValue.Arr(content.labels.map { JsonValue.Str(it) }),
                "notes" to JsonValue.Arr(content.notes.map { note(it) }),
            ),
        ),
    )

    fun decode(text: String): BackupContent {
        val root = Json.parse(text).asObject()
            ?: throw IllegalArgumentException("the backup is not an object")
        require(root.get("format").asString() == "amehrug-backup") { "not an Amehrug backup" }
        val version = root.get("version").asInt(1)
        require(version <= VERSION) { "this backup was written by a newer version" }
        return BackupContent(
            version = version,
            createdAt = root.get("createdAt").asLong(),
            labels = root.get("labels").asArray().mapNotNull { NoteRules.normalizeLabel(it.asString()) },
            notes = root.get("notes").asArray().mapNotNull { readNote(it) },
        )
    }

    private fun note(note: Note): JsonValue = JsonValue.Obj(
        linkedMapOf(
            "type" to JsonValue.Str(note.type.name),
            "folder" to JsonValue.Str(note.folder.name),
            "color" to JsonValue.Str(note.color.name),
            "title" to JsonValue.Str(note.title),
            "body" to JsonValue.Str(note.body),
            "pinned" to JsonValue.Bool(note.pinned),
            "createdAt" to JsonValue.Num(note.createdAt.toDouble()),
            "modifiedAt" to JsonValue.Num(note.modifiedAt.toDouble()),
            "deletedAt" to (note.deletedAt?.let { JsonValue.Num(it.toDouble()) } ?: JsonValue.Null),
            "labels" to JsonValue.Arr(note.labels.map { JsonValue.Str(it) }),
            "spans" to JsonValue.Arr(note.spans.map { span(it) }),
            "items" to JsonValue.Arr(note.items.map { item(it) }),
            "attachments" to JsonValue.Arr(note.attachments.map { attachment(it) }),
            "reminders" to JsonValue.Arr(note.reminders.map { reminder(it) }),
        ),
    )

    private fun span(span: TextSpan): JsonValue = JsonValue.Obj(
        linkedMapOf(
            "start" to JsonValue.Num(span.start.toDouble()),
            "end" to JsonValue.Num(span.end.toDouble()),
            "bold" to JsonValue.Bool(span.bold),
            "italic" to JsonValue.Bool(span.italic),
            "monospace" to JsonValue.Bool(span.monospace),
            "strikethrough" to JsonValue.Bool(span.strikethrough),
            "link" to JsonValue.Bool(span.link),
        ),
    )

    private fun item(item: ListItem): JsonValue = JsonValue.Obj(
        linkedMapOf(
            "body" to JsonValue.Str(item.body),
            "checked" to JsonValue.Bool(item.checked),
            "indent" to JsonValue.Num(item.indent.toDouble()),
        ),
    )

    private fun attachment(attachment: Attachment): JsonValue = JsonValue.Obj(
        linkedMapOf(
            "kind" to JsonValue.Str(attachment.kind.name),
            "file" to JsonValue.Str(attachment.fileName),
            "mime" to JsonValue.Str(attachment.mimeType),
            "size" to JsonValue.Num(attachment.sizeBytes.toDouble()),
            "duration" to (attachment.durationMs?.let { JsonValue.Num(it.toDouble()) } ?: JsonValue.Null),
            "createdAt" to JsonValue.Num(attachment.createdAt.toDouble()),
        ),
    )

    private fun reminder(reminder: Reminder): JsonValue = JsonValue.Obj(
        linkedMapOf(
            "at" to JsonValue.Num(reminder.atMillis.toDouble()),
            "repeat" to JsonValue.Str(reminder.repeat.name),
        ),
    )

    private fun readNote(value: JsonValue): Note? {
        val fields = value.asObject() ?: return null
        val note = Note(
            type = enum(fields.get("type").asString(), NoteType.entries, NoteType.NOTE),
            folder = enum(fields.get("folder").asString(), Folder.entries, Folder.NOTES),
            color = enum(fields.get("color").asString(), NoteColor.entries, NoteColor.DEFAULT),
            title = fields.get("title").asString(),
            body = fields.get("body").asString(),
            pinned = fields.get("pinned").asBool(),
            createdAt = fields.get("createdAt").asLong(),
            modifiedAt = fields.get("modifiedAt").asLong(),
            deletedAt = (fields.get("deletedAt") as? JsonValue.Num)?.value?.toLong(),
            labels = fields.get("labels").asArray().map { it.asString() },
            spans = fields.get("spans").asArray().mapNotNull { readSpan(it) },
            items = fields.get("items").asArray().mapNotNull { readItem(it) },
            attachments = fields.get("attachments").asArray().mapNotNull { readAttachment(it) },
            reminders = fields.get("reminders").asArray().mapNotNull { readReminder(it) },
        )
        return NoteRules.sanitize(note)
    }

    private fun readSpan(value: JsonValue): TextSpan? {
        val fields = value.asObject() ?: return null
        return TextSpan(
            start = fields.get("start").asInt(),
            end = fields.get("end").asInt(),
            bold = fields.get("bold").asBool(),
            italic = fields.get("italic").asBool(),
            monospace = fields.get("monospace").asBool(),
            strikethrough = fields.get("strikethrough").asBool(),
            link = fields.get("link").asBool(),
        )
    }

    private fun readItem(value: JsonValue): ListItem? {
        val fields = value.asObject() ?: return null
        return ListItem(
            body = fields.get("body").asString(),
            checked = fields.get("checked").asBool(),
            indent = fields.get("indent").asInt(),
        )
    }

    private fun readAttachment(value: JsonValue): Attachment? {
        val fields = value.asObject() ?: return null
        val file = fields.get("file").asString()
        // A name with a path in it is a way into other folders. Refuse it.
        if (file.isEmpty() || file.contains('/') || file.contains('\\') || file.contains("..")) return null
        return Attachment(
            kind = enum(fields.get("kind").asString(), AttachmentKind.entries, AttachmentKind.FILE),
            fileName = file,
            mimeType = fields.get("mime").asString("application/octet-stream"),
            sizeBytes = fields.get("size").asLong(),
            durationMs = (fields.get("duration") as? JsonValue.Num)?.value?.toLong(),
            createdAt = fields.get("createdAt").asLong(),
        )
    }

    private fun readReminder(value: JsonValue): Reminder? {
        val fields = value.asObject() ?: return null
        val at = fields.get("at").asLong(-1)
        if (at <= 0) return null
        return Reminder(atMillis = at, repeat = enum(fields.get("repeat").asString(), Repeat.entries, Repeat.ONCE))
    }

    private fun <T : Enum<T>> enum(name: String, values: List<T>, fallback: T): T =
        values.firstOrNull { it.name == name } ?: fallback
}
