package android.hardware.biometrics
// Compile only stub.
class BiometricManager {
    object Authenticators {
        const val BIOMETRIC_STRONG = 15
        const val DEVICE_CREDENTIAL = 32768
    }
    fun canAuthenticate(authenticators: Int): Int = BIOMETRIC_SUCCESS
    companion object { const val BIOMETRIC_SUCCESS = 0 }
}
