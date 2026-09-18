package com.amehrug.app.crypto

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Images, audio and files, encrypted one by one with [FileCrypto], inside
 * filesDir, which is private to the app. Notally kept them in the shared
 * media folder, where the media scanner and any app with photo access could
 * read them.
 *
 * Names are random, so a file name tells nothing about the note.
 */
class AttachmentStore(context: Context) {

    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "media")
    private val cache = File(appContext.cacheDir, "media")

    fun newName(): String = UUID.randomUUID().toString().replace("-", "")

    /**
     * Encrypts [input] into a new file and returns its name. The stream stays
     * open: the caller owns it, which matters when it is one entry of a zip.
     */
    suspend fun write(input: InputStream): String = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val name = newName()
        val file = File(directory, name)
        try {
            file.outputStream().use { output ->
                FileCrypto.encrypt(KeyVault.mediaKey(appContext), input, output)
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        name
    }

    /** Writes the clear content of [name] into [output], without holding it in memory. */
    suspend fun copyTo(name: String, output: OutputStream): Unit = withContext(Dispatchers.IO) {
        File(directory, name).inputStream().use { input ->
            FileCrypto.decrypt(KeyVault.mediaKey(appContext), input, output)
        }
    }

    fun names(): List<String> = directory.listFiles()?.map { it.name } ?: emptyList()

    /** Whole file in memory. For images and small files only. */
    suspend fun read(name: String): ByteArray = withContext(Dispatchers.IO) {
        val output = ByteArrayOutputStream()
        File(directory, name).inputStream().use { input ->
            FileCrypto.decrypt(KeyVault.mediaKey(appContext), input, output)
        }
        output.toByteArray()
    }

    /**
     * Decrypts into the cache folder and returns the file, for players that
     * need to seek. The cache is cleared on every start.
     */
    suspend fun decryptToCache(name: String, suffix: String): File = withContext(Dispatchers.IO) {
        cache.mkdirs()
        val target = File(cache, "$name$suffix")
        if (!target.isFile) {
            val temp = File(cache, "$name$suffix.part")
            try {
                File(directory, name).inputStream().use { input ->
                    temp.outputStream().use { output ->
                        FileCrypto.decrypt(KeyVault.mediaKey(appContext), input, output)
                    }
                }
                temp.renameTo(target)
            } finally {
                temp.delete()
            }
        }
        target
    }

    suspend fun delete(name: String): Boolean = withContext(Dispatchers.IO) {
        File(directory, name).delete()
    }

    suspend fun sizeOf(name: String): Long = withContext(Dispatchers.IO) {
        File(directory, name).length()
    }

    /** Removes files no note points at any more. Returns how many went. */
    suspend fun deleteOrphans(keep: Set<String>): Int = withContext(Dispatchers.IO) {
        val files = directory.listFiles() ?: return@withContext 0
        var removed = 0
        for (file in files) {
            if (file.name !in keep && file.delete()) removed += 1
        }
        removed
    }

    /** Clear text copies never outlive a session. */
    suspend fun clearCache(): Unit = withContext(Dispatchers.IO) {
        cache.listFiles()?.forEach { it.delete() }
    }
}
