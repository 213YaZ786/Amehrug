package com.amehrug.app.crypto

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM over a file, in chunks.
 *
 * One GCM pass over a whole file cannot be trusted while it streams: the tag
 * only arrives at the end, and CipherInputStream reports a bad tag as a plain
 * end of file. So the file is cut into 64 kB chunks, each with its own tag,
 * and each tag covers the chunk number and whether the chunk is the last one.
 * A cut, reordered, swapped or edited file is therefore rejected, not read.
 *
 * Layout: "AMH1", an 8 byte random prefix, then for every chunk a 4 byte
 * length and the sealed chunk. The nonce is the prefix plus the chunk number.
 * Pure Kotlin on javax.crypto, so it runs and is checked outside Android.
 */
object FileCrypto {
    const val MAGIC = "AMH1"
    const val CHUNK_SIZE = 64 * 1024
    const val TAG_BITS = 128
    const val PREFIX_SIZE = 8
    const val KEY_SIZE = 32
    private const val MAX_CHUNK_CIPHER = CHUNK_SIZE + TAG_BITS / 8

    private val magicBytes = MAGIC.toByteArray(Charsets.US_ASCII)

    fun encrypt(
        key: ByteArray,
        input: InputStream,
        output: OutputStream,
        random: SecureRandom = SecureRandom(),
    ) {
        require(key.size == KEY_SIZE) { "key must be 32 bytes" }
        val prefix = ByteArray(PREFIX_SIZE).also { random.nextBytes(it) }
        output.write(magicBytes)
        output.write(prefix)

        val buffer = ByteArray(CHUNK_SIZE)
        var pending = readChunk(input, buffer)
        var counter = 0
        do {
            // pending is a copy, so reusing the read buffer here is safe.
            val next = readChunk(input, buffer)
            val last = next.isEmpty()
            val sealed = cipher(Cipher.ENCRYPT_MODE, key, prefix, counter, last)
                .doFinal(pending, 0, pending.size)
            writeInt(output, sealed.size)
            output.write(sealed)
            pending = next
            counter += 1
        } while (pending.isNotEmpty())
        output.flush()
    }

    /** Throws [GeneralSecurityException] when the file was edited, cut or is not ours. */
    fun decrypt(key: ByteArray, input: InputStream, output: OutputStream) {
        require(key.size == KEY_SIZE) { "key must be 32 bytes" }
        val header = ByteArray(magicBytes.size + PREFIX_SIZE)
        readFully(input, header)
        for (i in magicBytes.indices) {
            if (header[i] != magicBytes[i]) throw GeneralSecurityException("not an Amehrug file")
        }
        val prefix = header.copyOfRange(magicBytes.size, header.size)

        var counter = 0
        var length = readIntOrNull(input) ?: throw GeneralSecurityException("empty file")
        while (true) {
            if (length !in 1..MAX_CHUNK_CIPHER) throw GeneralSecurityException("bad chunk size")
            val sealed = ByteArray(length)
            readFully(input, sealed)
            val next = readIntOrNull(input)
            val last = next == null
            val plain = try {
                cipher(Cipher.DECRYPT_MODE, key, prefix, counter, last).doFinal(sealed)
            } catch (e: GeneralSecurityException) {
                throw GeneralSecurityException("chunk $counter failed the check", e)
            }
            output.write(plain)
            plain.fill(0)
            if (last) break
            length = next
            counter += 1
        }
        output.flush()
    }

    private fun cipher(mode: Int, key: ByteArray, prefix: ByteArray, counter: Int, last: Boolean): Cipher {
        val nonce = ByteArray(12)
        prefix.copyInto(nonce)
        writeIntTo(nonce, PREFIX_SIZE, counter)
        val aad = ByteArray(5)
        writeIntTo(aad, 0, counter)
        aad[4] = if (last) 1 else 0
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(aad)
        }
    }

    /** Reads up to one chunk. Returns the bytes read, empty at the end of the stream. */
    private fun readChunk(input: InputStream, buffer: ByteArray): ByteArray {
        var read = 0
        while (read < buffer.size) {
            val step = input.read(buffer, read, buffer.size - read)
            if (step < 0) break
            read += step
        }
        return buffer.copyOf(read)
    }

    private fun readFully(input: InputStream, target: ByteArray) {
        var read = 0
        while (read < target.size) {
            val step = input.read(target, read, target.size - read)
            if (step < 0) throw GeneralSecurityException("file is truncated")
            read += step
        }
    }

    private fun writeInt(output: OutputStream, value: Int) {
        output.write(value ushr 24)
        output.write((value ushr 16) and 0xFF)
        output.write((value ushr 8) and 0xFF)
        output.write(value and 0xFF)
    }

    private fun writeIntTo(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        target[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 3] = (value and 0xFF).toByte()
    }

    private fun readIntOrNull(input: InputStream): Int? {
        val first = input.read()
        if (first < 0) return null
        val rest = ByteArray(3)
        try {
            readFully(input, rest)
        } catch (e: GeneralSecurityException) {
            throw EOFException("length field is cut")
        }
        return (first shl 24) or
            ((rest[0].toInt() and 0xFF) shl 16) or
            ((rest[1].toInt() and 0xFF) shl 8) or
            (rest[2].toInt() and 0xFF)
    }
}
