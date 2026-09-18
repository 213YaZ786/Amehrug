package com.amehrug.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amehrug.app.model.AttachmentKind
import com.amehrug.app.model.Folder
import com.amehrug.app.model.NoteColor
import com.amehrug.app.model.NoteType
import com.amehrug.app.model.Repeat

// Schema version 1. Enums are stored by name.
// Every child row belongs to one note and disappears with it (ON DELETE CASCADE).

@Entity(
    tableName = "notes",
    indices = [Index("folder", "pinned", "modifiedAt")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: NoteType,
    val folder: Folder,
    val color: NoteColor,
    val title: String,
    val body: String,
    /** Text of the list items, kept only so full text search can see it. */
    val itemsText: String,
    val pinned: Boolean,
    val createdAt: Long,
    val modifiedAt: Long,
    val deletedAt: Long?,
)

/** External content FTS table. Room keeps it in sync with triggers on [NoteEntity]. */
@Entity(tableName = "notes_fts")
@Fts4(contentEntity = NoteEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
data class NoteFtsEntity(
    val title: String,
    val body: String,
    val itemsText: String,
)

@Entity(
    tableName = "spans",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class SpanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    // Not "start" and "end": END is an SQL keyword.
    val startIndex: Int,
    val endIndex: Int,
    val bold: Boolean,
    val italic: Boolean,
    val monospace: Boolean,
    val strikethrough: Boolean,
    val link: Boolean,
)

@Entity(
    tableName = "list_items",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class ListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val position: Int,
    val body: String,
    val checked: Boolean,
    val indent: Int,
)

@Entity(tableName = "labels")
data class LabelEntity(
    // NOCASE: "Work" and "work" are the same label.
    @PrimaryKey @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
)

@Entity(
    tableName = "note_labels",
    primaryKeys = ["noteId", "labelName"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LabelEntity::class,
            parentColumns = ["name"],
            childColumns = ["labelName"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("labelName")],
)
data class NoteLabelEntity(
    val noteId: Long,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val labelName: String,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId"), Index(value = ["fileName"], unique = true)],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val kind: AttachmentKind,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long?,
    val position: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId"), Index("atMillis")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val atMillis: Long,
    val repeat: Repeat,
)
