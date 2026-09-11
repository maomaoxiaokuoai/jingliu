package com.luma.downloader.auth

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.luma.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** SDK reads only; it never queries document.cookie, input fields or another application's store. */
class BrowserCookieStore private constructor(val manager: CookieManager, private val storage: WebStorage) {
    companion object {
        fun attach(view: WebView, platform: Platform): BrowserCookieStore {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                WebViewCompat.setProfile(view, "jingliu-login-${platform.name.lowercase()}")
                val profile = WebViewCompat.getProfile(view)
                return BrowserCookieStore(profile.cookieManager, profile.webStorage)
            }
            return BrowserCookieStore(CookieManager.getInstance(), WebStorage.getInstance())
        }
    }
    suspend fun restore(cookies: List<Cookie>) {
        require(cookies.size <= 512) { "Cookie 数量过多" }
        storage.deleteAllData()
        eraseCookies()
        val lines = cookies.mapNotNull { c -> BrowserSessionPolicy.cookieLine(c)?.let { c to it } }
        for ((cookie, line) in lines) {
            suspendCancellableCoroutine<Unit> { wait ->
                manager.setCookie("https://${cookie.domain}${cookie.path}", line) {
                    if (wait.isActive) wait.resume(Unit)
                }
            }
        }
        // Do not block the UI thread with CookieManager.flush or clear compiled/cache resources.
    }
    suspend fun snapshot(platform: Platform, urls: List<String>, previous: List<Cookie>): List<Cookie> =
        withContext(Dispatchers.IO) {
            val exact = linkedMapOf<String, List<String>>()
            val fallback = linkedMapOf<String, String>()
            val metadata = WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)
            for (url in BrowserSessionPolicy.probes(platform, urls)) {
                if (metadata) {
                    val info = runCatching { CookieManagerCompat.getCookieInfo(manager, url).toList() }.getOrNull()
                    if (info != null) { exact[url] = info; continue }
                }
                fallback[url] = manager.getCookie(url).orEmpty()
            }
            BrowserSessionPolicy.retainKnownAttributes(
                BrowserSessionPolicy.captureWithAttributes(platform, exact, fallback), previous)
        }
    suspend fun eraseCookies() = suspendCancellableCoroutine<Unit> { wait ->
        manager.removeAllCookies { if (wait.isActive) wait.resume(Unit) }
    }
    fun eraseTemporary(onDone: () -> Unit) {
        manager.removeAllCookies { onDone() }
    }
    fun clearSiteStorage() { storage.deleteAllData() }
}
