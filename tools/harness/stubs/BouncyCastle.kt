package org.bouncycastle.crypto.params
// Compile only stub. Signatures read in the sources of tag r1rv86.
class Argon2Parameters private constructor() {
    class Builder(type: Int) {
        fun withVersion(version: Int): Builder = this
        fun withSalt(salt: ByteArray): Builder = this
        fun withMemoryAsKB(memory: Int): Builder = this
        fun withIterations(iterations: Int): Builder = this
        fun withParallelism(parallelism: Int): Builder = this
        fun build(): Argon2Parameters = TODO()
    }
    companion object {
        const val ARGON2_id = 0x02
        const val ARGON2_VERSION_13 = 0x13
    }
}
