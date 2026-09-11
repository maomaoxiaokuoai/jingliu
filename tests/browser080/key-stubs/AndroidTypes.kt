// Compile-only Android Context. Tests inject a real AES key, NOT Android Keystore.
package android.content
import java.io.File
open class Context(val noBackupFilesDir: File)
