package android.content
// Compile only stub.
import java.io.File
class ContentResolver {
    fun openInputStream(uri: android.net.Uri): java.io.InputStream? = null
    fun openOutputStream(uri: android.net.Uri, mode: String): java.io.OutputStream? = null
}
open class Context {
    open val applicationContext: Context get() = this
    val filesDir: File get() = File(".")
    val cacheDir: File get() = File(".")
    val noBackupFilesDir: File get() = File(".")
    fun getDatabasePath(name: String): File = File(name)
    fun <T> getSystemService(type: Class<T>): T? = null
    val contentResolver: ContentResolver get() = ContentResolver()

}
