package com.amehrug.app.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.amehrug.app.crypto.AttachmentStore
import com.amehrug.app.data.NoteRepository
import com.amehrug.app.diagnostics.Diagnostics
import com.amehrug.app.model.NotallyImport
import com.amehrug.app.model.NotallyRow
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportReport(val notes: Int, val files: Int, val missingFiles: Int, val millis: Long)

/**
 * Reads a backup made by Notally: a zip holding its plain SQLite database and
 * the folders `Images` and `Audios`.
 *
 * Notes are added, never merged and never replacing anything, so importing
 * twice gives two copies rather than a surprise. Media are re-encrypted into
 * this app's own store under new random names, and the clear copies pulled
 * out of the zip are deleted whatever happens.
 */
class NotallyImporter(
    context: Context,
    private val notes: NoteRepository,
    private val attachments: AttachmentStore,
) {
    private val appContext = context.applicationContext

    suspend fun importFrom(source: Uri): ImportReport = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val work = File(appContext.cacheDir, "notally-import")
        work.deleteRecursively()
        val media = File(work, "media")
        media.mkdirs()
        try {
            val database = unpack(source, work, media)
                ?: throw IllegalArgumentException("no Notally database in this file")
            val report = read(database, media)
            val millis = (System.nanoTime() - started) / 1_000_000
            Diagnostics.log.info(
                "import",
                "notally: ${report.notes} notes, ${report.files} files, " +
                    "${report.missingFiles} missing, $millis ms",
            )
            report.copy(millis = millis)
        } finally {
            work.deleteRecursively()
        }
    }

    /** Returns the database file, or null when the zip holds none. */
    private fun unpack(source: Uri, work: File, media: File): File? {
        var database: File? = null
        val input = appContext.contentResolver.openInputStream(source)
            ?: throw IllegalStateException("cannot read the chosen file")
        input.use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    // Only the last part of the name is kept, so an entry
                    // called ../../something cannot escape the folder.
                    val name = File(entry.name).name
                    if (!entry.isDirectory && name.isNotEmpty()) {
                        val target = if (name == DATABASE_ENTRY) File(work, name) else File(media, name)
                        FileOutputStream(target).use { output -> zip.copyTo(output) }
                        if (name == DATABASE_ENTRY) database = target
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return database
    }

    private suspend fun read(database: File, media: File): ImportReport {
        var imported = 0
        var files = 0
        var missing = 0
        // Read only: this file is never written back to.
        val sqlite = SQLiteDatabase.openDatabase(
            database.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        sqlite.use { source ->
            source.rawQuery("SELECT value FROM Label", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val label = cursor.getString(0)
                    if (label != null) notes.addLabel(label)
                }
            }
            source.rawQuery("SELECT * FROM BaseNote", null).use { cursor ->
                val now = System.currentTimeMillis()
                while (cursor.moveToNext()) {
                    fun text(column: String, fallback: String = ""): String {
                        val index = cursor.getColumnIndex(column)
                        if (index < 0 || cursor.isNull(index)) return fallback
                        return cursor.getString(index) ?: fallback
                    }

                    fun number(column: String): Long {
                        val index = cursor.getColumnIndex(column)
                        if (index < 0 || cursor.isNull(index)) return 0
                        return cursor.getLong(index)
                    }

                    val row = NotallyRow(
                        type = text("type", "NOTE"),
                        folder = text("folder", "NOTES"),
                        color = text("color", "DEFAULT"),
                        title = text("title"),
                        pinned = number("pinned") != 0L,
                        timestamp = number("timestamp"),
                        labels = text("labels", "[]"),
                        body = text("body"),
                        spans = text("spans", "[]"),
                        items = text("items", "[]"),
                        images = text("images", "[]"),
                        audios = text("audios", "[]"),
                        reminder = text("reminder").ifBlank { null },
                    )
                    val note = NotallyImport.toNote(row, now)
                    val id = notes.importNote(note.copy(attachments = emptyList()))
                    if (id <= 0) continue
                    imported += 1
                    note.attachments.forEachIndexed { position, attachment ->
                        val file = File(media, attachment.fileName)
                        if (!file.isFile) {
                            missing += 1
                            return@forEachIndexed
                        }
                        val stored = file.inputStream().use { attachments.write(it) }
                        notes.addAttachment(
                            noteId = id,
                            attachment = attachment.copy(
                                fileName = stored,
                                sizeBytes = file.length(),
                            ),
                            position = position,
                        )
                        files += 1
                    }
                }
            }
        }
        return ImportReport(notes = imported, files = files, missingFiles = missing, millis = 0)
    }

    private companion object {
        const val DATABASE_ENTRY = "NotallyDatabase"
    }
}
