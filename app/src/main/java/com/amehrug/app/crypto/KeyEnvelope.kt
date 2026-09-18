package com.amehrug.app.crypto

/**
 * On disk form of a key wrapped by the Android Keystore:
 * one version byte, one length byte, the nonce, then the wrapped bytes.
 * Pure Kotlin so the format can be checked outside Android.
 */
object KeyEnvelope {
    const val VERSION = 1
    const val MAX_NONCE = 32

    fun encode(nonce: ByteArray, wrapped: ByteArray): ByteArray {
        require(nonce.isNotEmpty() && nonce.size <= MAX_NONCE) { "bad nonce size" }
        require(wrapped.isNotEmpty()) { "empty payload" }
        val out = ByteArray(2 + nonce.size + wrapped.size)
        out[0] = VERSION.toByte()
        out[1] = nonce.size.toByte()
        nonce.copyInto(out, 2)
        wrapped.copyInto(out, 2 + nonce.size)
        return out
    }

    /** Throws [IllegalArgumentException] on anything that is not a whole envelope. */
    fun decode(bytes: ByteArray): Pair<ByteArray, ByteArray> {
        require(bytes.size > 2) { "envelope too short" }
        require(bytes[0].toInt() == VERSION) { "unknown envelope version ${bytes[0].toInt()}" }
        val nonceSize = bytes[1].toInt() and 0xFF
        require(nonceSize in 1..MAX_NONCE) { "bad nonce size" }
        require(bytes.size > 2 + nonceSize) { "envelope truncated" }
        val nonce = bytes.copyOfRange(2, 2 + nonceSize)
        val wrapped = bytes.copyOfRange(2 + nonceSize, bytes.size)
        return nonce to wrapped
    }
}
