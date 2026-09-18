package com.amehrug.app.crypto

import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * A backup file, sealed with a password the user chooses.
 *
 * The key comes from **Argon2id**, which the OWASP Password Storage cheat
 * sheet puts first, read on 2026-09-18. It is memory hard, so a graphics card
 * or a custom chip cannot try billions of passwords cheaply, which PBKDF2
 * cannot prevent. The numbers are the offline set of RFC 9106: 64 MiB of
 * memory, three passes, four lanes. Android ships no memory hard derivation,
 * hence Bouncy Castle, in pure Java.
 *
 * PBKDF2-HMAC-SHA256 with 600000 iterations stays implemented, because the
 * header says which derivation a file used. Switching back is one constant.
 *
 * The body is the same chunked AES-256-GCM as the media files, so a cut or
 * edited backup is refused rather than half restored. A wrong password and a
 * damaged file look the same: the tag simply fails.
 */
object BackupCrypto {
    const val MIN_PASSWORD = 8

    // RFC 9106, second recommended option, for a one shot operation on a phone.
    const val ARGON2_MEMORY_KIB = 64 * 1024
    const val ARGON2_ITERATIONS = 3
    const val ARGON2_PARALLELISM = 4

    // OWASP figure for the FIPS case, used only if Argon2id ever has to go.
    const val PBKDF2_ITERATIONS = 600_000

    const val DEFAULT_KDF = BackupHeader.KDF_ARGON2ID

    fun newHeader(random: SecureRandom = SecureRandom(), kdf: Int = DEFAULT_KDF): BackupHeader {
        val salt = ByteArray(BackupHeader.SALT_SIZE).also { random.nextBytes(it) }
        return when (kdf) {
            BackupHeader.KDF_PBKDF2 -> BackupHeader(
                kdf = kdf,
                salt = salt,
                memoryKib = 1,
                iterations = PBKDF2_ITERATIONS,
                parallelism = 1,
            )
            else -> BackupHeader(
                kdf = BackupHeader.KDF_ARGON2ID,
                salt = salt,
                memoryKib = ARGON2_MEMORY_KIB,
                iterations = ARGON2_ITERATIONS,
                parallelism = ARGON2_PARALLELISM,
            )
        }
    }

    fun encrypt(
        password: CharArray,
        input: InputStream,
        output: OutputStream,
        random: SecureRandom = SecureRandom(),
        header: BackupHeader = newHeader(random),
    ) {
        output.write(BackupHeader.encode(header))
        val key = deriveKey(password, header)
        try {
            FileCrypto.encrypt(key, input, output, random)
        } finally {
            key.fill(0)
        }
    }

    /** Throws [GeneralSecurityException] on a wrong password or a damaged file. */
    fun decrypt(password: CharArray, input: InputStream, output: OutputStream) {
        val raw = ByteArray(BackupHeader.SIZE)
        var read = 0
        while (read < raw.size) {
            val step = input.read(raw, read, raw.size - read)
            if (step < 0) throw GeneralSecurityException("not an Amehrug backup")
            read += step
        }
        val header = try {
            BackupHeader.decode(raw)
        } catch (e: IllegalArgumentException) {
            throw GeneralSecurityException(e.message, e)
        }
        val key = deriveKey(password, header)
        try {
            FileCrypto.decrypt(key, input, output)
        } finally {
            key.fill(0)
        }
    }

    fun deriveKey(password: CharArray, header: BackupHeader): ByteArray = when (header.kdf) {
        BackupHeader.KDF_ARGON2ID -> argon2id(password, header)
        BackupHeader.KDF_PBKDF2 -> pbkdf2(password, header)
        else -> throw GeneralSecurityException("unknown key derivation ${header.kdf}")
    }

    private fun argon2id(password: CharArray, header: BackupHeader): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(header.salt)
            .withMemoryAsKB(header.memoryKib)
            .withIterations(header.iterations)
            .withParallelism(header.parallelism)
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(parameters)
        val key = ByteArray(FileCrypto.KEY_SIZE)
        generator.generateBytes(password, key)
        return key
    }

    private fun pbkdf2(password: CharArray, header: BackupHeader): ByteArray {
        val spec = PBEKeySpec(password, header.salt, header.iterations, FileCrypto.KEY_SIZE * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
