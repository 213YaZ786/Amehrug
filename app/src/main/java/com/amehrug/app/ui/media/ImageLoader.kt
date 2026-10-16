package com.amehrug.app.ui.media

import android.content.Context
import android.graphics.Bitmap
import com.amehrug.app.core.security.SafeImages
import com.amehrug.app.crypto.AttachmentStore
import com.amehrug.app.diagnostics.Diagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decrypts a picture and scales it down before it ever becomes a bitmap.
 *
 * A photo from a phone is twelve megapixels, about fifty megabytes once
 * decoded. Reading the header first and asking the decoder for a smaller
 * sample keeps a wall of thumbnails within a few megabytes.
 *
 * The cache holds the last thirty pictures, in memory only. It is dropped
 * when the process ends, and it never touches the disk, because a decrypted
 * picture belongs nowhere but in memory.
 */
object ImageLoader {
    private const val MAX_CACHED = 30

    private val cache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
            size > MAX_CACHED
    }

    /**
     * Decoded in the isolated DecoderService (Modules kit safemedia, SafeImages),
     * never in the app's process: a picture brought in from outside (Notally, a
     * backup, the photo picker) can reach nothing of the notes or their keys.
     */
    suspend fun load(context: Context, store: AttachmentStore, name: String, targetPx: Int): Bitmap? {
        val key = "$name@$targetPx"
        synchronized(cache) { cache[key] }?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val bytes = store.read(name)
                val bitmap = SafeImages.decode(context, bytes, targetPx.coerceAtLeast(1))
                if (bitmap != null) synchronized(cache) { cache[key] = bitmap }
                bitmap
            } catch (cancel: CancellationException) {
                // Leaving the screen cancels the coroutine, and
                // CancellationException is an Exception. Caught below it would
                // be reported as a failure and would break the cancellation.
                throw cancel
            } catch (e: Exception) {
                // A picture that cannot be read must not take the note down.
                Diagnostics.log.error("media", "reading a picture", e)
                null
            }
        }
    }

    fun forget(name: String) {
        synchronized(cache) {
            val keys = cache.keys.filter { it.startsWith("$name@") }
            for (key in keys) cache.remove(key)
        }
    }

}
