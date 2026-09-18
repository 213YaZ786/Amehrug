package com.amehrug.app.backup

import android.content.Context
import android.net.Uri
import com.amehrug.app.crypto.AttachmentStore
import com.amehrug.app.crypto.BackupCrypto
import com.amehrug.app.data.NoteRepository
import com.amehrug.app.diagnostics.Diagnostics
import com.amehrug.app.model.BackupContent
import com.amehrug.app.model.BackupFormat
import com.amehrug.app.model.Attachment
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BackupReport(val notes: Int, val files: Int, val bytes: Long, val millis: Long)

data class RestoreReport(val notes: Int, val files: Int, val labels: Int, val millis: Long)

/**
 * One file, sealed with a password: a zip holding the notes as JSON and the
 * attachments in clear, wrapped in [BackupCrypto].
 *
 * The clear zip only ever exists inside the app cache, for the time of the
 * operation, and is deleted whatever happens. What the user sees on their
 * drive or memory card is the sealed file.
 */
class BackupManager(
    context: Context,
    private val notes: NoteRepository,
    private val attachments: AttachmentStore,
) {
    private val appContext = context.applicationContext

    suspend fun writeTo(target: Uri, password: CharArray, now: Long): BackupReport =
        withContext(Dispatchers.IO) {
            val started = System.nanoTime()
            val staging = File.createTempFile("backup", ".zip", appContext.cacheDir)
            try {
                val all = notes.allNotes()
                val labels = notes.allLabels()
                var files = 0
                ZipOutputStream(FileOutputStream(staging)).use { zip ->
                    zip.putNextEntry(ZipEntry(BackupFormat.JSON_ENTRY))
                    val json = BackupFormat.encode(
                        BackupContent(createdAt = now, notes = all, labels = labels),
                    )
                    zip.write(json.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    for (note in all) {
                        for (attachment in note.attachments) {
                            zip.putNextEntry(ZipEntry(BackupFormat.MEDIA_PREFIX + attachment.fileName))
                            attachments.copyTo(attachment.fileName, zip)
                            zip.closeEntry()
                            files += 1
                        }
                    }
                }
                val written = appContext.contentResolver.openOutputStream(target, "wt")
                    ?: throw IllegalStateException("cannot write to the chosen file")
                written.use { output ->
                    staging.inputStream().use { input ->
                        BackupCrypto.encrypt(password, input, output)
                    }
                }
                val millis = (System.nanoTime() - started) / 1_000_000
                Diagnostics.log.info("backup", "written: ${all.size} notes, $files files, $millis ms")
                BackupReport(notes = all.size, files = files, bytes = staging.length(), millis = millis)
            } finally {
                staging.delete()
            }
        }

    /**
     * Adds the notes of a backup to what is already there. Nothing is
     * replaced and nothing is erased, so a restore can never cost notes.
     */
    suspend fun restoreFrom(source: Uri, password: CharArray): RestoreReport =
        withContext(Dispatchers.IO) {
            val started = System.nanoTime()
            val staging = File.createTempFile("restore", ".zip", appContext.cacheDir)
            try {
                val input = appContext.contentResolver.openInputStream(source)
                    ?: throw IllegalStateException("cannot read the chosen file")
                input.use { stream ->
                    FileOutputStream(staging).use { output ->
                        BackupCrypto.decrypt(password, stream, output)
                    }
                }
                val report = readZip(staging)
                val millis = (System.nanoTime() - started) / 1_000_000
                Diagnostics.log.info(
                    "backup",
                    "restored: ${report.notes} notes, ${report.files} files, $millis ms",
                )
                report.copy(millis = millis)
            } finally {
                staging.delete()
            }
        }

    private suspend fun readZip(staging: File): RestoreReport {
        var content: BackupContent? = null
        // file name in the backup, and where its note landed here
        val waiting = HashMap<String, PendingAttachment>()
        var restoredNotes = 0
        var restoredFiles = 0

        ZipInputStream(staging.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == BackupFormat.JSON_ENTRY -> {
                        content = BackupFormat.decode(zip.readBytes().toString(Charsets.UTF_8))
                        restoredNotes = insertNotes(content, waiting)
                    }
                    name.startsWith(BackupFormat.MEDIA_PREFIX) -> {
                        val shortName = name.removePrefix(BackupFormat.MEDIA_PREFIX)
                        val pending = waiting[shortName]
                        if (pending != null) {
                            // The stream is not closed here, the zip owns it.
                            val stored = attachments.write(zip)
                            notes.addAttachment(
                                noteId = pending.noteId,
                                attachment = pending.attachment.copy(fileName = stored),
                                position = pending.position,
                            )
                            restoredFiles += 1
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val known = content ?: throw IllegalArgumentException("this file holds no Amehrug backup")
        return RestoreReport(
            notes = restoredNotes,
            files = restoredFiles,
            labels = known.labels.size,
            millis = 0,
        )
    }

    private suspend fun insertNotes(
        content: BackupContent?,
        waiting: HashMap<String, PendingAttachment>,
    ): Int {
        val known = content ?: return 0
        for (label in known.labels) notes.addLabel(label)
        var count = 0
        for (note in known.notes) {
            // Attachments are added once their file has been read from the zip.
            val id = notes.importNote(note.copy(attachments = emptyList()))
            if (id <= 0) continue
            count += 1
            note.attachments.forEachIndexed { index, attachment ->
                waiting[attachment.fileName] = PendingAttachment(id, attachment, index)
            }
        }
        return count
    }

    private data class PendingAttachment(
        val noteId: Long,
        val attachment: Attachment,
        val position: Int,
    )
}
