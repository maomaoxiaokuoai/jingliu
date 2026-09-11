// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package android.content
import android.net.Uri
open class Context { fun startActivity(intent:Intent) {} }
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
