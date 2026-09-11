package android.content
/** Explicit host-only file-directory substitute, not a real Android Context. Never built into app. */
class Context(val noBackupFilesDir:java.io.File)
