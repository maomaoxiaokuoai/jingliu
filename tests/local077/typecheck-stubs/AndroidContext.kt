// TEST-ONLY external API shapes. Not real Android/Chaquopy/yt-dlp libraries.
package android.content
import android.os.IBinder
import java.io.File
open class Context {
 val applicationContext:Context get()=this
 val applicationInfo=android.content.pm.ApplicationInfo()
 val noBackupFilesDir=File("/tmp")
 val cacheDir=File("/tmp")
 fun bindService(intent:Intent,connection:ServiceConnection,flags:Int)=true
 fun unbindService(connection:ServiceConnection){}
 companion object {const val BIND_AUTO_CREATE=1}
}
class Intent(context:Context,cls:Class<*>)
class ComponentName
interface ServiceConnection {
 fun onServiceConnected(name:ComponentName,binder:IBinder)
 fun onServiceDisconnected(name:ComponentName)
 fun onBindingDied(name:ComponentName){}
 fun onNullBinding(name:ComponentName){}
}
