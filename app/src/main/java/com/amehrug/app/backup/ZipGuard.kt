package com.amehrug.app.backup

import java.io.Closeable
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/** A zip that breaks a cap or names a path outside its folder. A bad file, not a crash. */
class ZipRefusedException(message: String) : IllegalArgumentException(message)

/**
 * Caps on a zip read from outside. The shape (entry count, size of an entry,
 * ratio of expanded to compressed bytes with a grace size below which the
 * ratio is not judged) is Apache POI's ZipSecureFile:
 * https://github.com/apache/poi/blob/trunk/poi-ooxml/src/main/java/org/apache/poi/openxml4j/util/ZipSecureFile.java
 * and ZipArchiveThresholdInputStream in the same folder. POI's ratio is
 * 0.01, that is 100 expanded bytes for one compressed.
 */
data class ZipLimits(
    val maxEntries: Int,
    val maxTotalBytes: Long,
    val maxEntryBytes: Long,
    val maxRatio: Long,
    val graceBytes: Long,
) {
    companion object {
        /** A Notally backup: a database and the pictures and sounds of the notes. */
        val NOTALLY = ZipLimits(
            maxEntries = 20_000,
            maxTotalBytes = 4L * 1024 * 1024 * 1024,
            maxEntryBytes = 1L * 1024 * 1024 * 1024,
            maxRatio = 100,
            graceBytes = 1L * 1024 * 1024,
        )

        /** The zip inside an Amehrug backup: the notes as JSON and the attachments. */
        val BACKUP = NOTALLY
    }
}

/**
 * Reads a zip entry by entry and stops it at the first cap broken. Bytes are
 * counted as they come out of the inflater, never from the size an entry
 * declares, which a hostile file writes as it likes. Compressed bytes are
 * counted on the stream under the zip, as Apache Commons IO's CountingInputStream:
 * https://github.com/apache/commons-io/blob/master/src/main/java/org/apache/commons/io/input/CountingInputStream.java
 */
class GuardedZip(source: InputStream, private val limits: ZipLimits) : Closeable {
    private val counting = CountedInput(source)
    private val zip = ZipInputStream(counting)
    private var entries = 0
    private var total = 0L
    private var entryBytes = 0L
    private var entryStart = 0L

    /** The bytes of the current entry. The caller never closes it: the zip owns it. */
    val stream: InputStream = object : FilterInputStream(zip) {
        override fun read(): Int {
            val value = super.read()
            if (value >= 0) counted(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0) counted(read.toLong())
            return read
        }

        override fun skip(n: Long): Long {
            // ZipInputStream.skip inflates too: read, so the caps hold.
            val scratch = ByteArray(8192)
            var left = n
            while (left > 0) {
                val read = read(scratch, 0, minOf(left, scratch.size.toLong()).toInt())
                if (read < 0) break
                left -= read
            }
            return n - left
        }
    }

    fun nextEntry(): ZipEntry? {
        val entry = zip.nextEntry ?: return null
        entries += 1
        if (entries > limits.maxEntries) throw ZipRefusedException("too many entries in the zip")
        entryBytes = 0
        entryStart = counting.count
        return entry
    }

    /** The whole entry in memory, refused when it passes [max] bytes. */
    fun readCapped(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            if (out.size() + read > max) throw ZipRefusedException("an entry is bigger than $max bytes")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun counted(bytes: Long) {
        entryBytes += bytes
        total += bytes
        if (entryBytes > limits.maxEntryBytes) throw ZipRefusedException("an entry inflates past its cap")
        if (total > limits.maxTotalBytes) throw ZipRefusedException("the zip inflates past its cap")
        if (entryBytes > limits.graceBytes) {
            val packed = maxOf(1L, counting.count - entryStart)
            if (entryBytes / packed > limits.maxRatio) throw ZipRefusedException("an entry inflates too much")
        }
    }

    override fun close() = zip.close()

    private class CountedInput(source: InputStream) : FilterInputStream(source) {
        var count = 0L
            private set

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) count += 1
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0) count += read
            return read
        }

        override fun skip(n: Long): Long {
            val skipped = super.skip(n)
            if (skipped > 0) count += skipped
            return skipped
        }
    }
}

/**
 * Zip path traversal: the destination of an entry must stay inside the folder.
 * The canonical path test is Android's recipe,
 * https://developer.android.com/privacy-and-security/risks/zip-path-traversal
 * and NotallyX's QuillpadImporter.newFile, the same test:
 * https://github.com/Crustack/NotallyX/blob/master/app/src/main/java/com/philkes/notallyx/data/imports/quillpad/QuillpadImporter.kt
 * An absolute name or a `..` part is refused first: File(folder, "/x") would
 * land inside the folder, but such a name is never an honest backup's.
 */
object ZipNames {
    private const val MAX_NAME = 1024

    fun resolve(folder: File, name: String): File {
        if (name.isEmpty() || name.length > MAX_NAME) throw ZipRefusedException("bad entry name")
        if (name.startsWith("/") || name.startsWith("\\") || name.contains('\u0000')) {
            throw ZipRefusedException("entry outside the folder")
        }
        if (name.split('/', '\\').any { it == ".." }) throw ZipRefusedException("entry outside the folder")
        val file = File(folder, name)
        val root = folder.canonicalPath
        if (!file.canonicalPath.startsWith(root + File.separator)) {
            throw ZipRefusedException("entry outside the folder")
        }
        return file
    }
}

/**
 * Plain copies in the cache folder: removed in `finally` by their owners, and
 * the ones a killed run left are swept at the next start. Android's guidance
 * on cache files, which the system may clear but does not promise to:
 * https://developer.android.com/training/data-storage/app-specific#internal-cache-create
 */
object CacheSweep {
    const val BACKUP_PREFIX = "backup"
    const val RESTORE_PREFIX = "restore"
    const val IMPORT_FOLDER = "notally-import"

    private val staging = Regex("^($BACKUP_PREFIX|$RESTORE_PREFIX)-?\\d+\\.zip$")

    /** Removes what these runs left that is older than [before] (epoch millis). Returns how many. */
    fun sweep(cacheDir: File, before: Long): Int {
        var removed = 0
        val files = cacheDir.listFiles() ?: return 0
        for (file in files) {
            val ours = (file.isFile && staging.matches(file.name)) ||
                (file.isDirectory && file.name == IMPORT_FOLDER)
            if (ours && file.lastModified() < before) {
                val gone = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (gone) removed += 1
            }
        }
        return removed
    }
}
