package com.amehrug.app.crypto

/**
 * The first bytes of a backup file: which key derivation was used and with
 * which numbers. They travel with the file, so a backup written today still
 * opens after these numbers are raised, and a new derivation can be added
 * without breaking old files.
 *
 * Layout, 32 bytes: "AMHBK", version, kdf, 16 byte salt, memory in KiB,
 * iterations, parallelism. Pure Kotlin.
 */
data class BackupHeader(
    val kdf: Int,
    val salt: ByteArray,
    val memoryKib: Int,
    val iterations: Int,
    val parallelism: Int,
) {
    // Generated equals would compare the salt by reference.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupHeader) return false
        return kdf == other.kdf &&
            salt.contentEquals(other.salt) &&
            memoryKib == other.memoryKib &&
            iterations == other.iterations &&
            parallelism == other.parallelism
    }

    override fun hashCode(): Int {
        var result = kdf
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + memoryKib
        result = 31 * result + iterations
        result = 31 * result + parallelism
        return result
    }

    companion object {
        const val MAGIC = "AMHBK"
        const val VERSION = 2
        const val SALT_SIZE = 16
        const val SIZE = 5 + 1 + 1 + SALT_SIZE + 4 + 4 + 1

        /** Argon2id, memory hard, first choice of the OWASP cheat sheet. */
        const val KDF_ARGON2ID = 2

        /** PBKDF2-HMAC-SHA256, kept readable as a fallback and for old files. */
        const val KDF_PBKDF2 = 1

        const val MAX_MEMORY_KIB = 128 * 1024
        const val MAX_PARALLELISM = 16
        const val MAX_ARGON2_PASSES = 10
        const val MAX_PBKDF2_ROUNDS = 1_200_000

        private val magicBytes =MAGIC.toByteArray(Charsets.US_ASCII)

        fun encode(header: BackupHeader): ByteArray {
            require(header.salt.size == SALT_SIZE) { "salt must be $SALT_SIZE bytes" }
            val out = ByteArray(SIZE)
            magicBytes.copyInto(out)
            out[5] = VERSION.toByte()
            out[6] = header.kdf.toByte()
            header.salt.copyInto(out, 7)
            writeInt(out, 7 + SALT_SIZE, header.memoryKib)
            writeInt(out, 11 + SALT_SIZE, header.iterations)
            out[15 + SALT_SIZE] = header.parallelism.toByte()
            return out
        }

        /** Throws [IllegalArgumentException] when this is not a backup we can open. */
        fun decode(bytes: ByteArray): BackupHeader {
            require(bytes.size >= SIZE) { "not an Amehrug backup" }
            for (i in magicBytes.indices) {
                require(bytes[i] == magicBytes[i]) { "not an Amehrug backup" }
            }
            val version = bytes[5].toInt()
            require(version == VERSION) { "backup version $version is not known" }
            val kdf = bytes[6].toInt()
            require(kdf == KDF_ARGON2ID || kdf == KDF_PBKDF2) { "unknown key derivation $kdf" }
            val salt = bytes.copyOfRange(7, 7 + SALT_SIZE)
            val memoryKib = readInt(bytes, 7 + SALT_SIZE)
            val iterations = readInt(bytes, 11 + SALT_SIZE)
            val parallelism = bytes[15 + SALT_SIZE].toInt()
            // Bounds keep a damaged or hostile header from asking for a gigabyte
            // of memory or for a week of work, checked before anything is
            // allocated. Twice what this app writes (64 MiB, 3 passes, 4 lanes;
            // 600000 PBKDF2 rounds), so a later raise still reads old files.
            // Argon2's parameters are those of RFC 9106.
            require(memoryKib in 1..MAX_MEMORY_KIB) { "memory out of range" }
            require(parallelism in 1..MAX_PARALLELISM) { "parallelism out of range" }
            val maxIterations = if (kdf == KDF_ARGON2ID) MAX_ARGON2_PASSES else MAX_PBKDF2_ROUNDS
            require(iterations in 1..maxIterations) { "iterations out of range" }
            return BackupHeader(kdf, salt, memoryKib, iterations, parallelism)
        }

        private fun writeInt(target: ByteArray, offset: Int, value: Int) {
            target[offset] = (value ushr 24).toByte()
            target[offset + 1] = ((value ushr 16) and 0xFF).toByte()
            target[offset + 2] = ((value ushr 8) and 0xFF).toByte()
            target[offset + 3] = (value and 0xFF).toByte()
        }

        private fun readInt(bytes: ByteArray, offset: Int): Int =
            ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
    }
}
