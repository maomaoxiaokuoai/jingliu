// TEST-ONLY external API shapes. Not real Android/Chaquopy/yt-dlp libraries.
package android.app
import android.content.*
import android.os.IBinder
abstract class Service:Context() {
 abstract fun onBind(intent:Intent?):IBinder
 open fun onUnbind(intent:Intent?):Boolean=false
 open fun onDestroy(){}
}
