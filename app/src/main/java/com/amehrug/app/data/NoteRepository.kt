package com.amehrug.app.data

import com.amehrug.app.data.db.AmehrugDatabase
import com.amehrug.app.data.db.AttachmentEntity
import com.amehrug.app.data.db.LabelEntity
import com.amehrug.app.model.Attachment
import com.amehrug.app.model.Folder
import com.amehrug.app.model.FtsQuery
import com.amehrug.app.model.Note
import com.amehrug.app.model.NoteColor
import com.amehrug.app.model.NoteRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The only door to the notes. Screens never see an entity.
 * Every call is main safe: Room moves the work to its own threads.
 */
class NoteRepository(
    database: AmehrugDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val notes = database.notes()
    private val labels = database.labels()

    // Reading

    fun observeFolder(folder: Folder): Flow<List<Note>> =
        notes.observeFolder(folder.name).map { rows -> rows.map { it.toModel() } }

    fun observeLabel(label: String): Flow<List<Note>> =
        notes.observeLabel(label).map { rows -> rows.map { it.toModel() } }

    fun observeNote(id: Long): Flow<Note?> =
        notes.observeNote(id).map { row -> row?.toModel() }

    suspend fun getNote(id: Long): Note? = notes.getNote(id)?.toModel()

    /** Notes outside the trash matching every word typed, as prefixes. */
    fun search(text: String): Flow<List<Note>> {
        val match = FtsQuery.build(text) ?: return flowOf(emptyList())
        return notes.search(match).map { rows -> rows.map { it.toModel() } }
    }

    suspend fun count(): Int = notes.count()

    /** Every note, for a backup. */
    suspend fun allNotes(): List<Note> = notes.allNotes().map { it.toModel() }

    suspend fun allLabels(): List<String> = labels.all()

    // Writing

    /**
     * Saves the whole note and returns its id. A new note with nothing in it
     * is not stored and 0 is returned, so opening the editor and leaving
     * does not litter the list.
     */
    suspend fun save(note: Note, force: Boolean = false): Long =
        write(note, keepDates = false, force = force)

    /** Restores a note as it was, with a new identifier and its original dates. */
    suspend fun importNote(note: Note): Long = write(note.copy(id = 0), keepDates = true, force = true)

    private suspend fun write(note: Note, keepDates: Boolean, force: Boolean = false): Long {
        val clean = NoteRules.sanitize(note)
        if (!force && clean.id == 0L && NoteRules.isEmpty(clean) && clean.attachments.isEmpty()) return 0
        return notes.saveFull(
            note = clean.toEntity(clock(), keepModified = keepDates),
            spans = clean.spanRows(),
            items = clean.itemRows(),
            labels = clean.labels,
            reminders = clean.reminderRows(),
        )
    }

    suspend fun setPinned(ids: List<Long>, pinned: Boolean) = notes.setPinned(ids, pinned)

    suspend fun setColor(ids: List<Long>, color: NoteColor) = notes.setColor(ids, color.name, clock())

    suspend fun archive(ids: List<Long>) = notes.moveTo(ids, Folder.ARCHIVED.name, null)

    suspend fun moveToTrash(ids: List<Long>) = notes.moveTo(ids, Folder.DELETED.name, clock())

    /** Back to the main list, from the archive or the trash. */
    suspend fun restore(ids: List<Long>) = notes.moveTo(ids, Folder.NOTES.name, null)

    /** Returns the attachment files that must now be deleted from storage. */
    suspend fun deleteForever(ids: List<Long>): List<String> = notes.deleteForever(ids)

    /** Deletes notes in the trash for more than [days]. Returns their attachment files. */
    suspend fun purgeTrash(days: Int = NoteRules.TRASH_DAYS): PurgeResult {
        val expired = notes.expiredTrash(NoteRules.trashCutoff(clock(), days))
        val files = notes.deleteForever(expired)
        return PurgeResult(notes = expired.size, files = files)
    }

    suspend fun addAttachment(noteId: Long, attachment: Attachment, position: Int): Long =
        notes.insertAttachment(
            AttachmentEntity(
                noteId = noteId,
                kind = attachment.kind,
                fileName = attachment.fileName,
                mimeType = attachment.mimeType,
                sizeBytes = attachment.sizeBytes,
                durationMs = attachment.durationMs,
                position = position,
                createdAt = if (attachment.createdAt > 0) attachment.createdAt else clock(),
            ),
        )

    /** Returns the file to delete, or null when the row did not exist. */
    suspend fun removeAttachment(id: Long): String? {
        val row = notes.getAttachment(id) ?: return null
        notes.deleteAttachment(id)
        return row.fileName
    }

    suspend fun allAttachmentFiles(): Set<String> = notes.allAttachmentFiles().toSet()

    // Labels

    fun observeLabels(): Flow<List<String>> = labels.observeAll()

    /** False when the name is blank or already taken. */
    suspend fun addLabel(raw: String): Boolean {
        val name = NoteRules.normalizeLabel(raw) ?: return false
        return labels.insert(LabelEntity(name)) != -1L
    }

    /**
     * False when the new name is blank or belongs to another label.
     * Changing only the case of a name is allowed.
     */
    suspend fun renameLabel(oldName: String, raw: String): Boolean {
        val name = NoteRules.normalizeLabel(raw) ?: return false
        val sameLabel = name.equals(oldName, ignoreCase = true)
        if (!sameLabel && labels.exists(name) > 0) return false
        return labels.rename(oldName, name) > 0
    }

    suspend fun deleteLabel(name: String): Boolean = labels.delete(name) > 0

    /** Adds or removes one label across several notes at once. */
    suspend fun setLabel(ids: List<Long>, raw: String, on: Boolean) {
        val name = NoteRules.normalizeLabel(raw) ?: return
        if (on) notes.setLabel(ids, name) else notes.unsetLabel(ids, name)
    }
}

data class PurgeResult(val notes: Int, val files: List<String>)
