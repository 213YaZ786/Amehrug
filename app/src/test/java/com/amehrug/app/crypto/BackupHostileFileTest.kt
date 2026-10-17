package com.amehrug.app.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

// Hostile backup files built here: a header or a chunk length claiming a huge
// size, an empty file, a cut one. Every length read from the file must be
// refused before anything of that size is allocated (OWASP MASVS-CODE-4,
// untrusted input; the bounds of RFC 9106 for Argon2's parameters).
class BackupHostileFileTest {

    private val password = "correct horse".toCharArray()

    // Cheap derivation so a test does not wait on Argon2 or 600000 rounds.
    private val cheap = BackupHeader(
        kdf = BackupHeader.KDF_PBKDF2,
        salt = ByteArray(BackupHeader.SALT_SIZE) { 3 },
        memoryKib = 1,
        iterations = 1000,
        parallelism = 1,
    )

    private fun sealed(plain: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypt(password, ByteArrayInputStream(plain), out, SecureRandom(), cheap)
        return out.toByteArray()
    }

    private fun open(file: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.decrypt(password, ByteArrayInputStream(file), out)
        return out.toByteArray()
    }

    private fun assertRefused(file: ByteArray) {
        try {
            open(file)
            fail("should have been refused")
        } catch (expected: GeneralSecurityException) {
            // the app's own error: a wrong password and a damaged file look the same
        }
    }

    private fun withHeader(memory: Int = 1, iterations: Int = 1000, parallelism: Int = 1, kdf: Int = 1): ByteArray {
        val bytes = BackupHeader.encode(cheap)
        bytes[6] = kdf.toByte()
        fun put(offset: Int, value: Int) {
            bytes[offset] = (value ushr 24).toByte()
            bytes[offset + 1] = (value ushr 16).toByte()
            bytes[offset + 2] = (value ushr 8).toByte()
            bytes[offset + 3] = value.toByte()
        }
        put(7 + BackupHeader.SALT_SIZE, memory)
        put(11 + BackupHeader.SALT_SIZE, iterations)
        bytes[15 + BackupHeader.SALT_SIZE] = parallelism.toByte()
        return bytes
    }

    @Test
    fun `an honest file opens`() {
        val plain = ByteArray(150_000) { (it * 7).toByte() }
        assertArrayEquals(plain, open(sealed(plain)))
    }

    @Test
    fun `a header claiming two gigabytes of memory is refused before any allocation`() {
        assertRefused(withHeader(memory = Int.MAX_VALUE, kdf = BackupHeader.KDF_ARGON2ID))
        assertRefused(withHeader(memory = BackupHeader.MAX_MEMORY_KIB + 1, kdf = BackupHeader.KDF_ARGON2ID))
        assertRefused(withHeader(memory = -1, kdf = BackupHeader.KDF_ARGON2ID))
        assertRefused(withHeader(memory = 0, kdf = BackupHeader.KDF_ARGON2ID))
    }

    @Test
    fun `a header claiming endless work is refused`() {
        assertRefused(withHeader(iterations = Int.MAX_VALUE))
        assertRefused(withHeader(iterations = BackupHeader.MAX_PBKDF2_ROUNDS + 1))
        assertRefused(withHeader(iterations = BackupHeader.MAX_ARGON2_PASSES + 1, kdf = BackupHeader.KDF_ARGON2ID))
        assertRefused(withHeader(iterations = 0))
        assertRefused(withHeader(iterations = -5))
        assertRefused(withHeader(parallelism = 0))
        assertRefused(withHeader(parallelism = BackupHeader.MAX_PARALLELISM + 1))
        assertRefused(withHeader(parallelism = -1))
    }

    @Test
    fun `what this app writes is inside the bounds`() {
        val argon = BackupCrypto.newHeader(kdf = BackupHeader.KDF_ARGON2ID)
        assertEquals(argon, BackupHeader.decode(BackupHeader.encode(argon)))
        val pbkdf2 = BackupCrypto.newHeader(kdf = BackupHeader.KDF_PBKDF2)
        assertEquals(pbkdf2, BackupHeader.decode(BackupHeader.encode(pbkdf2)))
    }

    @Test
    fun `a chunk length claiming two gigabytes is refused before it is allocated`() {
        val good = sealed(ByteArray(1000))
        val at = BackupHeader.SIZE + FileCrypto.MAGIC.length + FileCrypto.PREFIX_SIZE
        for (length in listOf(Int.MAX_VALUE, -1, 0, FileCrypto.CHUNK_SIZE + 17)) {
            val bad = good.copyOf()
            bad[at] = (length ushr 24).toByte()
            bad[at + 1] = (length ushr 16).toByte()
            bad[at + 2] = (length ushr 8).toByte()
            bad[at + 3] = length.toByte()
            assertRefused(bad)
        }
    }

    @Test
    fun `an empty file is refused`() {
        assertRefused(ByteArray(0))
    }

    @Test
    fun `a file cut at any point is refused, never half read`() {
        val good = sealed(ByteArray(70_000) { it.toByte() })
        // Inside the header, the magic, the length field, the first chunk,
        // between chunks, the last chunk.
        val points = listOf(1, 10, BackupHeader.SIZE - 1, BackupHeader.SIZE, BackupHeader.SIZE + 6,
            BackupHeader.SIZE + 12, BackupHeader.SIZE + 14, BackupHeader.SIZE + 100,
            good.size - 30, good.size - 1)
        for (point in points) assertRefused(good.copyOf(point))
    }

    @Test
    fun `a file that is not ours is refused`() {
        assertRefused("PK\u0003\u0004 not a backup at all, only text".toByteArray())
        assertRefused(ByteArray(500) { 0x41 })
    }

    @Test
    fun `a wrong password is refused like a damaged file`() {
        val out = ByteArrayOutputStream()
        try {
            BackupCrypto.decrypt("another one".toCharArray(), ByteArrayInputStream(sealed(ByteArray(10))), out)
            fail("should have been refused")
        } catch (expected: GeneralSecurityException) {
            assertEquals(0, out.size())
        }
    }
}
