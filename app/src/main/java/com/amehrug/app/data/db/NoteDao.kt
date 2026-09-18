package com.amehrug.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Every function is suspend or returns a Flow: Room runs them off the main
// thread, and the builder never allows main thread queries.
// Folder names are passed as strings, the way the entity stores them.

@Dao
abstract class NoteDao {

    @Transaction
    @Query("SELECT * FROM notes WHERE folder = :folder ORDER BY pinned DESC, modifiedAt DESC")
    abstract fun observeFolder(folder: String): Flow<List<NoteWithChildren>>

    @Transaction
    @Query(
        "SELECT notes.* FROM notes " +
            "JOIN note_labels ON notes.id = note_labels.noteId " +
            "WHERE note_labels.labelName = :label AND notes.folder != 'DELETED' " +
            "ORDER BY notes.pinned DESC, notes.modifiedAt DESC",
    )
    abstract fun observeLabel(label: String): Flow<List<NoteWithChildren>>

    @Transaction
    @Query(
        "SELECT notes.* FROM notes " +
            "JOIN notes_fts ON notes.id = notes_fts.rowid " +
            "WHERE notes_fts MATCH :match AND notes.folder != 'DELETED' " +
            "ORDER BY notes.pinned DESC, notes.modifiedAt DESC",
    )
    abstract fun search(match: String): Flow<List<NoteWithChildren>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    abstract fun observeNote(id: Long): Flow<NoteWithChildren?>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    abstract suspend fun getNote(id: Long): NoteWithChildren?

    @Transaction
    @Query("SELECT * FROM notes ORDER BY id")
    abstract suspend fun allNotes(): List<NoteWithChildren>

    @Query("SELECT COUNT(*) FROM notes")
    abstract suspend fun count(): Int

    // Writes

    /** Returns the new row id on insert, -1 when an existing row was updated. */
    @Upsert
    abstract suspend fun upsertNote(note: NoteEntity): Long

    @Query("DELETE FROM spans WHERE noteId = :noteId")
    abstract suspend fun deleteSpans(noteId: Long)

    @Query("DELETE FROM list_items WHERE noteId = :noteId")
    abstract suspend fun deleteItems(noteId: Long)

    @Query("DELETE FROM note_labels WHERE noteId = :noteId")
    abstract suspend fun deleteNoteLabels(noteId: Long)

    @Query("DELETE FROM reminders WHERE noteId = :noteId")
    abstract suspend fun deleteReminders(noteId: Long)

    @Insert
    abstract suspend fun insertSpans(rows: List<SpanEntity>)

    @Insert
    abstract suspend fun insertItems(rows: List<ListItemEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertLabels(rows: List<LabelEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertNoteLabels(rows: List<NoteLabelEntity>)

    @Insert
    abstract suspend fun insertReminders(rows: List<ReminderEntity>)

    /**
     * Writes a note and replaces its spans, items, labels and reminders, all
     * or nothing. Attachments are left alone: they own files and are added or
     * removed one by one. Child rows get their noteId here.
     */
    @Transaction
    open suspend fun saveFull(
        note: NoteEntity,
        spans: List<SpanEntity>,
        items: List<ListItemEntity>,
        labels: List<String>,
        reminders: List<ReminderEntity>,
    ): Long {
        val result = upsertNote(note)
        val id = if (note.id == 0L) result else note.id
        deleteSpans(id)
        deleteItems(id)
        deleteNoteLabels(id)
        deleteReminders(id)
        if (spans.isNotEmpty()) insertSpans(spans.map { it.copy(id = 0, noteId = id) })
        if (items.isNotEmpty()) insertItems(items.map { it.copy(id = 0, noteId = id) })
        if (labels.isNotEmpty()) {
            insertLabels(labels.map { LabelEntity(it) })
            insertNoteLabels(labels.map { NoteLabelEntity(noteId = id, labelName = it) })
        }
        if (reminders.isNotEmpty()) insertReminders(reminders.map { it.copy(id = 0, noteId = id) })
        return id
    }

    @Query("UPDATE notes SET pinned = :pinned WHERE id IN (:ids)")
    abstract suspend fun setPinned(ids: List<Long>, pinned: Boolean)

    @Query("UPDATE notes SET color = :color, modifiedAt = :now WHERE id IN (:ids)")
    abstract suspend fun setColor(ids: List<Long>, color: String, now: Long)

    @Query("UPDATE notes SET folder = :folder, deletedAt = :deletedAt WHERE id IN (:ids)")
    abstract suspend fun moveTo(ids: List<Long>, folder: String, deletedAt: Long?)

    @Query("SELECT id FROM notes WHERE folder = 'DELETED' AND deletedAt IS NOT NULL AND deletedAt < :cutoff")
    abstract suspend fun expiredTrash(cutoff: Long): List<Long>

    @Query("SELECT fileName FROM attachments WHERE noteId IN (:ids)")
    abstract suspend fun attachmentFiles(ids: List<Long>): List<String>

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    abstract suspend fun deleteNotes(ids: List<Long>)

    /** Deletes the notes and returns the attachment files the caller must remove. */
    @Transaction
    open suspend fun deleteForever(ids: List<Long>): List<String> {
        if (ids.isEmpty()) return emptyList()
        val files = attachmentFiles(ids)
        deleteNotes(ids)
        return files
    }

    // Attachments, one at a time

    @Insert
    abstract suspend fun insertAttachment(row: AttachmentEntity): Long

    @Query("SELECT * FROM attachments WHERE id = :id")
    abstract suspend fun getAttachment(id: Long): AttachmentEntity?

    @Query("DELETE FROM attachments WHERE id = :id")
    abstract suspend fun deleteAttachment(id: Long)

    @Query("SELECT fileName FROM attachments")
    abstract suspend fun allAttachmentFiles(): List<String>

    @Query("SELECT * FROM reminders ORDER BY atMillis")
    abstract suspend fun allReminders(): List<ReminderEntity>
}
