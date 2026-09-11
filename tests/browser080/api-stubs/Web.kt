// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package android.webkit
import android.content.Context
import android.net.Uri
import android.view.View
open class WebView(context:Context):View(context) {
 val settings=WebSettings()
 var webViewClient=WebViewClient();var webChromeClient=WebChromeClient()
 fun onPause() {}
 fun canGoBack()=false
 fun goBack() {};fun reload() {};fun clearCache(includeDiskFiles:Boolean) {}
 fun clearHistory() {};fun loadUrl(url:String) {};fun stopLoading() {}
 fun removeAllViews() {};fun destroy() {}
 fun setDownloadListener(listener:(String,String,String,String,Long)->Unit) {}
}
class WebSettings {
 var javaScriptEnabled=false;var domStorageEnabled=false
 var allowFileAccess=false;var allowContentAccess=false
 var cacheMode=0
 var mixedContentMode=0;var safeBrowsingEnabled=false
 var javaScriptCanOpenWindowsAutomatically=false;var mediaPlaybackRequiresUserGesture=true
 var userAgentString="";var useWideViewPort=false;var loadWithOverviewMode=false
 fun setSupportMultipleWindows(enabled:Boolean) {}
 companion object {const val LOAD_DEFAULT=-1;const val MIXED_CONTENT_NEVER_ALLOW=1;fun getDefaultUserAgent(context:Context)="UA"}
}
interface WebResourceRequest {val isForMainFrame:Boolean;val url:Uri;fun hasGesture():Boolean}
class WebResourceError
open class WebViewClient {
 open fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean=false
 open fun onPageStarted(view:WebView,url:String,favicon:android.graphics.Bitmap?) {}
 open fun onPageCommitVisible(view:WebView,url:String) {}
 open fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse?=null
 open fun onPageFinished(view:WebView,url:String) {}
 open fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError) {}
}
open class WebChromeClient {open fun onProgressChanged(view:WebView,value:Int) {} }
class IgnoredWebChromeClient
class CookieManager {
 fun setAcceptCookie(accept:Boolean) {};fun setAcceptThirdPartyCookies(view:WebView,accept:Boolean) {}
 fun removeAllCookies(callback:((Boolean)->Unit)?) {}
 fun setCookie(url:String,value:String,callback:((Boolean)->Unit)?) {}
 fun getCookie(url:String):String?=null
 fun flush() {}
 companion object {private val i=CookieManager();fun getInstance()=i}
}
class WebStorage {fun deleteAllData() {};companion object {private val i=WebStorage();fun getInstance()=i}}

class WebResourceResponse(mime:String,encoding:String,stream:java.io.InputStream)
