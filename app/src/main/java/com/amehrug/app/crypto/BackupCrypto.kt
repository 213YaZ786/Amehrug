package com.amehrug.app.crypto

import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * A backup file, sealed with a password the user chooses.
 *
 * The key comes from PBKDF2-HMAC-SHA256 over the password, with a random salt
 * and a high iteration count, both written in the header so a file made today
 * still opens when those numbers change. The body is the same chunked
 * AES-256-GCM as the media files, so a truncated or edited backup is refused
 * rather than half restored.
 *
 * A wrong password looks exactly like a damaged file: the tag simply fails.
 * Pure Kotlin on javax.crypto, checked outside Android.
 */
object BackupCrypto {
    const val MAGIC = "AMHBK1"
    const val VERSION = 1
    const val SALT_SIZE = 16
    const val ITERATIONS = 600_000
    const val MIN_PASSWORD = 8

    private val magicBytes = MAGIC.toByteArray(Charsets.US_ASCII)

    fun encrypt(
        password: CharArray,
        input: InputStream,
        output: OutputStream,
        random: SecureRandom = SecureRandom(),
        iterations: Int = ITERATIONS,
    ) {
        val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }
        output.write(magicBytes)
        output.write(VERSION)
        output.write(salt)
        writeInt(output, iterations)
        val key = deriveKey(password, salt, iterations)
        try {
            FileCrypto.encrypt(key, input, output, random)
        } finally {
            key.fill(0)
        }
    }

    /** Throws [GeneralSecurityException] on a wrong password or a damaged file. */
    fun decrypt(password: CharArray, input: InputStream, output: OutputStream) {
        val header = ByteArray(magicBytes.size + 1 + SALT_SIZE + 4)
        var read = 0
        while (read < header.size) {
            val step = input.read(header, read, header.size - read)
            if (step < 0) throw GeneralSecurityException("not an Amehrug backup")
            read += step
        }
        for (i in magicBytes.indices) {
            if (header[i] != magicBytes[i]) throw GeneralSecurityException("not an Amehrug backup")
        }
        val version = header[magicBytes.size].toInt()
        if (version != VERSION) throw GeneralSecurityException("backup version $version is not known")
        val salt = header.copyOfRange(magicBytes.size + 1, magicBytes.size + 1 + SALT_SIZE)
        val iterations = readInt(header, magicBytes.size + 1 + SALT_SIZE)
        if (iterations !in 1..10_000_000) throw GeneralSecurityException("bad iteration count")
        val key = deriveKey(password, salt, iterations)
        try {
            FileCrypto.decrypt(key, input, output)
        } finally {
            key.fill(0)
        }
    }

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, FileCrypto.KEY_SIZE * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun writeInt(output: OutputStream, value: Int) {
        output.write(value ushr 24)
        output.write((value ushr 16) and 0xFF)
        output.write((value ushr 8) and 0xFF)
        output.write(value and 0xFF)
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}
