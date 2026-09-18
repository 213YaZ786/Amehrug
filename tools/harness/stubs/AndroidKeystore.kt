package android.security.keystore
// Compile only stub. Mirrors the signatures used, not the real behaviour.
object KeyProperties {
    const val PURPOSE_ENCRYPT = 1
    const val PURPOSE_DECRYPT = 2
    const val BLOCK_MODE_GCM = "GCM"
    const val ENCRYPTION_PADDING_NONE = "NoPadding"
    const val KEY_ALGORITHM_AES = "AES"
}
class KeyGenParameterSpec private constructor() : java.security.spec.AlgorithmParameterSpec {
    class Builder(alias: String, purposes: Int) {
        fun setBlockModes(vararg modes: String): Builder = this
        fun setEncryptionPaddings(vararg paddings: String): Builder = this
        fun setKeySize(size: Int): Builder = this
        fun setRandomizedEncryptionRequired(required: Boolean): Builder = this
        fun setIsStrongBoxBacked(backed: Boolean): Builder = this
        fun build(): KeyGenParameterSpec = TODO()
    }
}
