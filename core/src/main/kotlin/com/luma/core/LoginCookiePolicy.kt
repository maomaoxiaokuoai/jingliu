package com.luma.core

import java.net.URI

/** Credential names are detection hints, never online-login proof. Ignore analytics expiry. */
object LoginCookiePolicy {
    fun names(p: Platform): Set<String> = when (p) {
        Platform.BILIBILI -> setOf("SESSDATA")
        Platform.DOUYIN -> setOf("sessionid", "sessionid_ss", "sid_tt", "sid_guard")
        Platform.KUAISHOU -> setOf("passToken", "kuaishou.server.web_st", "kuaishou.server.web_ph")
        Platform.XIAOHONGSHU -> setOf("web_session")
        Platform.TIKTOK -> setOf("sessionid", "sessionid_ss", "sid_tt", "sid_guard")
        Platform.X -> setOf("auth_token")
        Platform.YOUTUBE -> setOf("SID", "__Secure-1PSID", "__Secure-3PSID", "SAPISID")
        Platform.DIRECT -> emptySet()
    }
    fun relevant(p: Platform, cookies: List<Cookie>): List<Cookie> = cookies.filter {
        p.owns(it.domain) && it.name in names(p) && it.value.isNotBlank()
    }
    fun active(p: Platform, cookies: List<Cookie>, now: Long = System.currentTimeMillis() / 1000) =
        relevant(p, cookies).filter { it.expires == 0L || it.expires > now }
    fun signature(p: Platform, cookies: List<Cookie>): String {
        val items = relevant(p, cookies).sortedWith(compareBy({it.domain}, {it.path}, {it.name}))
        return if (items.isEmpty()) "" else digest(Json.stringify(items.map {
            listOf(it.domain, it.path, it.name, it.value, it.hostOnly, it.secure)
        }))
    }
    fun exactSnapshot(cookies: List<Cookie>): String = digest(Json.stringify(
        cookies.sortedWith(compareBy({it.domain}, {it.path}, {it.name})).map { it.json() }))

    /** A known expiry is the earliest live authentication-cookie expiry, not a promise of login. */
    fun expiry(p: Platform, cookies: List<Cookie>, now: Long = System.currentTimeMillis() / 1000): CookieExpiry {
        val auth = relevant(p, cookies)
        if (auth.isEmpty()) return CookieExpiry(null, true, false, false)
        val live = auth.filter { it.expires == 0L || it.expires > now }
        val expired = live.isEmpty()
        val candidate = (if (expired) auth else live).map { it.expires }.filter { it > 0 }.minOrNull()
        return CookieExpiry(candidate, live.any { it.expires == 0L }, expired, true)
    }

    fun local(p: Platform, cookies: List<Cookie>, rejected: Boolean, now: Long): SessionHealthCode {
        val own = cookies.filter { p.owns(it.domain) && it.value.isNotBlank() }
        if (own.isEmpty()) return SessionHealthCode.MISSING
        val expiry = expiry(p, own, now)
        if (expiry.allExpired || own.all { it.expires > 0 && it.expires <= now }) return SessionHealthCode.EXPIRED
        if (!expiry.hasKnownCredential) return SessionHealthCode.MISSING_LOGIN_COOKIE
        if (p == Platform.BILIBILI && active(p, own, now).none {
            it.matches(URI("https://api.bilibili.com/x/web-interface/nav"), now)
        }) return SessionHealthCode.MISSING_LOGIN_COOKIE
        if (p == Platform.DOUYIN && active(p, own, now).none {
            it.matches(URI("https://www.douyin.com/aweme/v1/web/user/profile/self/"), now)
        }) return SessionHealthCode.MISSING_LOGIN_COOKIE
        if (rejected) return SessionHealthCode.REJECTED
        return if (expiry.hasUnknown) SessionHealthCode.UNKNOWN_EXPIRY else SessionHealthCode.LOCAL_AVAILABLE
    }
}

data class CookieExpiry(val nextExpirySeconds: Long?, val hasUnknown: Boolean,
                        val allExpired: Boolean, val hasKnownCredential: Boolean)

/** Poll browser-local credentials, wait for two stable observations and never save anonymous churn.
 * Browser interaction is necessary for an XHS web_session change: anonymous pages also issue it.
 * All methods use monotonic milliseconds. No Cookie values or hashes are logged. */
class BrowserCaptureGate(private val platform: Platform) {
    private var baselineMetadata = ""
    private var initialized = false
    private var waiting = ""
    private var since = 0L
    private var saved = ""
    private fun captureKey(cookies: List<Cookie>): String {
        val active = LoginCookiePolicy.active(platform, cookies)
        val signature = LoginCookiePolicy.signature(platform, active)
        if (signature.isBlank()) return ""
        // Only known-vs-unknown changes are automatically upgraded for an unchanged token.
        // Do not re-save on every incidental one-second Max-Age rounding change.
        val metadata = active.sortedWith(compareBy({it.domain}, {it.path}, {it.name})).joinToString {
            "${it.domain}:${it.path}:${it.name}:${it.expires > 0}"
        }
        return signature + ":" + digest(metadata)
    }
    fun baseline(cookies: List<Cookie>) {
        baselineMetadata = captureKey(cookies)
        initialized = true
        waiting = ""
    }
    fun consider(cookies: List<Cookie>, nowMs: Long, userInteracted: Boolean,
                 firstCommittedSample: Boolean = false): Boolean {
        val key = captureKey(cookies)
        if (!initialized) { baseline(cookies); return false }
        if (platform == Platform.XIAOHONGSHU && firstCommittedSample && !userInteracted) {
            baselineMetadata = key; return false
        }
        if (key.isBlank() || key == baselineMetadata || key == saved ||
            (platform == Platform.XIAOHONGSHU && !userInteracted)) {
            waiting = ""; return false
        }
        if (waiting != key) { waiting = key; since = nowMs; return false }
        return nowMs - since >= 1200L
    }
    fun markSaved(cookies: List<Cookie>) { saved = captureKey(cookies); baselineMetadata = saved; waiting = "" }
}
