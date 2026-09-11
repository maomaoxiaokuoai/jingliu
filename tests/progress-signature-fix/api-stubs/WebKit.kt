// TEST-ONLY signatures: AndroidX WebKit 1.12.1 API. Not an implementation.
package androidx.webkit
import android.webkit.*
object WebViewFeature {const val GET_COOKIE_INFO="GET_COOKIE_INFO";const val MULTI_PROFILE="MULTI_PROFILE";fun isFeatureSupported(s:String)=false}
object CookieManagerCompat {fun getCookieInfo(m:CookieManager,url:String):List<String> = emptyList()}
class Profile {val cookieManager=CookieManager.getInstance();val webStorage=WebStorage.getInstance()}
object WebViewCompat {fun setProfile(v:WebView,name:String) {};fun getProfile(v:WebView)=Profile()}
