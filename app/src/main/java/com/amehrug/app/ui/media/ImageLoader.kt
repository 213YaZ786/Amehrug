package com.amehrug.app.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    suspend fun load(store: AttachmentStore, name: String, targetPx: Int): Bitmap? {
        val key = "$name@$targetPx"
        synchronized(cache) { cache[key] }?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val bytes = store.read(name)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, targetPx)
                }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
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

    /** Powers of two only, which is all the decoder honours. */
    fun sampleFor(width: Int, height: Int, targetPx: Int): Int {
        if (width <= 0 || height <= 0 || targetPx <= 0) return 1
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= targetPx) {
            longest /= 2
            sample *= 2
        }
        return sample
    }
}
