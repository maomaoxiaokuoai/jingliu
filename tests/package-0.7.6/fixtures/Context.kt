package android.content
/** Host-only directory stand-in. No Android runtime is present in this test. */
open class Context(val noBackupFilesDir:java.io.File)
