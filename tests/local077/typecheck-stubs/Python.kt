// TEST-ONLY external API shapes. Not real Android/Chaquopy/yt-dlp libraries.
package com.chaquo.python
class Python {
 fun getModule(name:String)=PyObject()
 companion object{fun isStarted()=false;fun start(platform:com.chaquo.python.android.AndroidPlatform){};fun getInstance()=Python()}
}
class PyObject {fun callAttr(name:String,vararg args:Any?)=PyObject()}
