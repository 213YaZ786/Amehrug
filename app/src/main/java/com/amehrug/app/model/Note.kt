package com.amehrug.app.model

// Pure Kotlin model, shared by the database, the importers and the screens.
// No Android import, so it runs under kotlinc.

enum class NoteType { NOTE, LIST }

enum class Folder { NOTES, ARCHIVED, DELETED }

// Same names as Notally, so its backups map one to one.
enum class NoteColor {
    DEFAULT, CORAL, ORANGE, SAND, STORM, FOG, SAGE, MINT, DUSK, FLOWER, BLOSSOM, CLAY,
}

enum class AttachmentKind { IMAGE, AUDIO, FILE }

// Notally knows ONCE, DAILY and MONTHLY. The rest serves roadmap task 28.
enum class Repeat { ONCE, DAILY, WEEKLY, MONTHLY, YEARLY }

data class TextSpan(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val monospace: Boolean = false,
    val strikethrough: Boolean = false,
    val link: Boolean = false,
) {
    val hasStyle: Boolean get() = bold || italic || monospace || strikethrough || link
}

data class ListItem(
    val body: String,
    val checked: Boolean = false,
    val indent: Int = 0,
)

data class Attachment(
    val id: Long = 0,
    val kind: AttachmentKind,
    /** Random name of the encrypted file in app storage, never a user path. */
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long = 0,
    val durationMs: Long? = null,
    val createdAt: Long = 0,
)

data class Reminder(
    val atMillis: Long,
    val repeat: Repeat = Repeat.ONCE,
)

data class Note(
    val id: Long = 0,
    val type: NoteType = NoteType.NOTE,
    val folder: Folder = Folder.NOTES,
    val color: NoteColor = NoteColor.DEFAULT,
    val title: String = "",
    val body: String = "",
    val spans: List<TextSpan> = emptyList(),
    val items: List<ListItem> = emptyList(),
    val labels: List<String> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val pinned: Boolean = false,
    val createdAt: Long = 0,
    val modifiedAt: Long = 0,
    val deletedAt: Long? = null,
)
