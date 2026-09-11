// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package android.content
import android.net.Uri
open class Context {
 val noBackupFilesDir=java.io.File("test-placeholder-not-executed")
 fun startActivity(intent:Intent) {}
 fun getSystemService(name:String):Any?=null
 companion object {const val CLIPBOARD_SERVICE="clipboard"}
}
class ClipboardManager {val primaryClip:ClipData?=null}
class ClipData {
 val itemCount:Int=0
 fun getItemAt(index:Int)=Item()
 class Item {val text:CharSequence?=null}
}
class Intent {
 constructor()
 constructor(context:Context, clazz:Class<*>)
 constructor(action:String,uri:Uri)
 fun getStringExtra(name:String):String?=null
 fun getBooleanExtra(name:String,fallback:Boolean):Boolean=fallback
 fun putExtra(name:String,value:String):Intent=this
 fun putExtra(name:String,value:Boolean):Intent=this
 fun setPackage(name:String):Intent=this
 fun addCategory(name:String):Intent=this
 companion object {const val ACTION_VIEW="VIEW";const val CATEGORY_BROWSABLE="BROWSABLE"}
}
