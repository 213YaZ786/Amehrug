package com.amehrug.app.data.db

import androidx.room.Embedded
import androidx.room.Relation

/** A note and all its rows, loaded in one transaction. */
data class NoteWithChildren(
    @Embedded val note: NoteEntity,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val spans: List<SpanEntity>,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val items: List<ListItemEntity>,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val labels: List<NoteLabelEntity>,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val attachments: List<AttachmentEntity>,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val reminders: List<ReminderEntity>,
)
