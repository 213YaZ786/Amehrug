package com.yaz.notes.data

import com.yaz.notes.data.db.AttachmentEntity
import com.yaz.notes.data.db.ListItemEntity
import com.yaz.notes.data.db.NoteEntity
import com.yaz.notes.data.db.NoteWithChildren
import com.yaz.notes.data.db.ReminderEntity
import com.yaz.notes.data.db.SpanEntity
import com.yaz.notes.model.Attachment
import com.yaz.notes.model.ListItem
import com.yaz.notes.model.Note
import com.yaz.notes.model.NoteRules
import com.yaz.notes.model.Reminder
import com.yaz.notes.model.TextSpan

internal fun NoteWithChildren.toModel(): Note = Note(
    id = note.id,
    type = note.type,
    folder = note.folder,
    color = note.color,
    title = note.title,
    body = note.body,
    spans = spans.sortedWith(compareBy({ it.startIndex }, { it.endIndex })).map { it.toModel() },
    items = items.sortedBy { it.position }.map { ListItem(it.body, it.checked, it.indent) },
    labels = labels.map { it.labelName }.sortedBy { it.lowercase() },
    attachments = attachments.sortedBy { it.position }.map { it.toModel() },
    reminders = reminders.sortedBy { it.atMillis }.map { Reminder(it.atMillis, it.repeat) },
    pinned = note.pinned,
    createdAt = note.createdAt,
    modifiedAt = note.modifiedAt,
    deletedAt = note.deletedAt,
)

private fun SpanEntity.toModel() = TextSpan(
    start = startIndex,
    end = endIndex,
    bold = bold,
    italic = italic,
    monospace = monospace,
    strikethrough = strikethrough,
    link = link,
)

internal fun AttachmentEntity.toModel() = Attachment(
    id = id,
    kind = kind,
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    durationMs = durationMs,
    createdAt = createdAt,
)

/**
 * [note] must already be sanitized. noteId of the children is set by the DAO.
 * [keepModified] is for a restore, which must not stamp every note with today.
 */
internal fun Note.toEntity(now: Long, keepModified: Boolean = false) = NoteEntity(
    id = id,
    type = type,
    folder = folder,
    color = color,
    title = title,
    body = body,
    itemsText = NoteRules.itemsText(items),
    pinned = pinned,
    createdAt = if (createdAt > 0) createdAt else now,
    modifiedAt = if (keepModified && modifiedAt > 0) modifiedAt else now,
    deletedAt = deletedAt,
)

internal fun Note.spanRows() = spans.map { span ->
    SpanEntity(
        noteId = id,
        startIndex = span.start,
        endIndex = span.end,
        bold = span.bold,
        italic = span.italic,
        monospace = span.monospace,
        strikethrough = span.strikethrough,
        link = span.link,
    )
}

internal fun Note.itemRows() = items.mapIndexed { index, item ->
    ListItemEntity(
        noteId = id,
        position = index,
        body = item.body,
        checked = item.checked,
        indent = item.indent,
    )
}

internal fun Note.reminderRows() = reminders.map { reminder ->
    ReminderEntity(noteId = id, atMillis = reminder.atMillis, repeat = reminder.repeat)
}
