package com.amehrug.app.crypto

/** Lower case hexadecimal, no allocation surprises. Pure Kotlin. */
object Hex {
    private const val DIGITS = "0123456789abcdef"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            out.append(DIGITS[value ushr 4])
            out.append(DIGITS[value and 0x0F])
        }
        return out.toString()
    }

    fun decode(text: String): ByteArray {
        require(text.length % 2 == 0) { "odd hex length" }
        val out = ByteArray(text.length / 2)
        for (i in out.indices) {
            val high = Character.digit(text[i * 2], 16)
            val low = Character.digit(text[i * 2 + 1], 16)
            require(high >= 0 && low >= 0) { "not hex" }
            out[i] = ((high shl 4) or low).toByte()
        }
        return out
    }
}
