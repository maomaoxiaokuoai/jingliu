// TEST-ONLY external API shapes. Not real Android/Chaquopy/yt-dlp libraries.
package android.os
interface IBinder
class Bundle {private val values=mutableMapOf<String,String>();fun putString(k:String,v:String){values[k]=v};fun getString(k:String):String?=values[k]}
class Looper {companion object {private val main=Looper();fun getMainLooper()=main;fun myLooper():Looper?=null}}
class Handler(val looper:Looper,val block:(Message)->Boolean)
class HandlerThread(name:String):Thread(name) {val looper=Looper()}
class Messenger {
 constructor(handler:Handler)
 constructor(binder:IBinder)
 val binder=object:IBinder{}
 fun send(message:Message){}
}
class Message {var what=0;var data=Bundle();var replyTo:Messenger?=null;var sendingUid=1
 companion object{fun obtain(handler:Handler?,code:Int)=Message().apply{what=code}}
}
open class RemoteException:Exception()
