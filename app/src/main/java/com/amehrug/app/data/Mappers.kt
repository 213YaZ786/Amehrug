package com.amehrug.app.data

import com.amehrug.app.data.db.AttachmentEntity
import com.amehrug.app.data.db.ListItemEntity
import com.amehrug.app.data.db.NoteEntity
import com.amehrug.app.data.db.NoteWithChildren
import com.amehrug.app.data.db.ReminderEntity
import com.amehrug.app.data.db.SpanEntity
import com.amehrug.app.model.Attachment
import com.amehrug.app.model.ListItem
import com.amehrug.app.model.Note
import com.amehrug.app.model.NoteRules
import com.amehrug.app.model.Reminder
import com.amehrug.app.model.TextSpan

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
