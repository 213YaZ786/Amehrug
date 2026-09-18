package com.amehrug.app.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The keys exist but cannot be opened. Never continue as if the data were fine. */
class KeyUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Two secrets, one for the database and one for the media files, each 32
 * random bytes. They are never stored in clear: a single AES-256-GCM key
 * inside the Android Keystore wraps them, and only the wrapped form is on
 * disk, under noBackupFilesDir, which no backup and no device transfer reads.
 *
 * The Keystore key never leaves secure hardware, so copying the app folder
 * off a rooted phone, or restoring it onto another one, yields nothing.
 */
object KeyVault {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "amehrug.master"
    private const val DATABASE_SECRET = "db.key"
    private const val MEDIA_SECRET = "media.key"
    private const val SECRET_SIZE = 32
    private const val TAG_BITS = 128

    @Volatile private var databaseCache: ByteArray? = null

    @Volatile private var mediaCache: ByteArray? = null

    /**
     * The 32 byte secret as 64 hexadecimal characters. SQLCipher takes a
     * passphrase and derives the real key from it, so the secret is handed
     * over in a form that has exactly one reading.
     */
    fun databasePassphrase(context: Context): ByteArray =
        Hex.encode(databaseSecret(context)).toByteArray(Charsets.US_ASCII)

    fun mediaKey(context: Context): ByteArray {
        mediaCache?.let { return it }
        synchronized(this) {
            mediaCache?.let { return it }
            val secret = loadOrCreate(context, MEDIA_SECRET)
            mediaCache = secret
            return secret
        }
    }

    /** True when the secrets have already been created on this device. */
    fun exists(context: Context): Boolean = File(directory(context), DATABASE_SECRET).isFile

    private fun databaseSecret(context: Context): ByteArray {
        databaseCache?.let { return it }
        synchronized(this) {
            databaseCache?.let { return it }
            val secret = loadOrCreate(context, DATABASE_SECRET)
            databaseCache = secret
            return secret
        }
    }

    private fun directory(context: Context): File =
        File(context.noBackupFilesDir, "keys").apply { mkdirs() }

    private fun loadOrCreate(context: Context, name: String): ByteArray {
        val file = File(directory(context), name)
        if (file.isFile) {
            val bytes = try {
                file.readBytes()
            } catch (e: Exception) {
                throw KeyUnavailableException("cannot read $name", e)
            }
            return unwrap(bytes)
        }
        val secret = ByteArray(SECRET_SIZE).also { SecureRandom().nextBytes(it) }
        val envelope = wrap(secret)
        // Written aside then moved, so a crash never leaves half a key.
        val temp = File(file.parentFile, "$name.new")
        try {
            temp.writeBytes(envelope)
            if (!temp.renameTo(file)) throw KeyUnavailableException("cannot store $name")
        } catch (e: KeyUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw KeyUnavailableException("cannot store $name", e)
        } finally {
            temp.delete()
        }
        return secret
    }

    private fun wrap(secret: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val sealed = cipher.doFinal(secret)
        return KeyEnvelope.encode(cipher.iv, sealed)
    }

    private fun unwrap(envelope: ByteArray): ByteArray {
        val (nonce, sealed) = try {
            KeyEnvelope.decode(envelope)
        } catch (e: IllegalArgumentException) {
            throw KeyUnavailableException("the key file is damaged", e)
        }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(TAG_BITS, nonce))
            cipher.doFinal(sealed)
        } catch (e: Exception) {
            // The Keystore key is gone or refuses: the data cannot be read.
            throw KeyUnavailableException("the device key no longer opens this data", e)
        }
    }

    private fun masterKey(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return createMasterKey()
    }

    private fun createMasterKey(): SecretKey {
        // Secure element when the phone has one, normal Keystore otherwise.
        return try {
            generate(strongBox = true)
        } catch (e: Exception) {
            generate(strongBox = false)
        }
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
        if (strongBox) builder.setIsStrongBoxBacked(true)
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(builder.build())
        return generator.generateKey()
    }

    /** Used by the tests of later tasks and by a future wipe. */
    fun forgetCache() {
        synchronized(this) {
            databaseCache?.fill(0)
            mediaCache?.fill(0)
            databaseCache = null
            mediaCache = null
        }
    }
}
