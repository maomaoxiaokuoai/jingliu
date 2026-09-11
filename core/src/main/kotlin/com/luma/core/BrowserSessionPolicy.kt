package com.luma.core

import java.net.URI

/** Own WebView cookie store only. Desktop mode is a layout choice, never an OAuth bypass. */
object BrowserSessionPolicy {
    const val DESKTOP_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    fun embeddedAllowed(p: Platform) = p != Platform.DIRECT && p != Platform.YOUTUBE
    fun allowed(p: Platform, url: String): Boolean = embeddedAllowed(p) && runCatching {
        val u = UrlPolicy.checkTransport(url)
        u.port in setOf(-1, 443) && p.owns(u.host)
    }.getOrDefault(false)
    fun desktopAgent(defaultAgent: String): String {
        val version = Regex("(?:Chrome|Chromium)/([0-9.]+)").find(defaultAgent)?.groupValues?.get(1)
            ?: return DESKTOP_AGENT
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$version Safari/537.36"
    }
    fun entry(p: Platform): String = p.loginUrl
    // Retained for callers outside this screen; EmbeddedLoginActivity never starts an App.
    fun appScheme(p: Platform, scheme: String): Boolean = scheme.lowercase() in when (p) {
        Platform.BILIBILI -> setOf("bilibili")
        Platform.DOUYIN -> setOf("snssdk1128", "douyin")
        Platform.KUAISHOU -> setOf("kwai", "ksnebula")
        Platform.XIAOHONGSHU -> setOf("xhsdiscover")
        Platform.TIKTOK -> setOf("snssdk1233", "snssdk1180")
        Platform.X -> setOf("twitter")
        else -> emptySet()
    }
    fun probes(p: Platform, visited: List<String>): List<String> {
        if (!embeddedAllowed(p)) return emptyList()
        val hosts = when (p) {
            Platform.BILIBILI -> listOf("www.bilibili.com", "api.bilibili.com", "passport.bilibili.com")
            Platform.DOUYIN -> listOf("www.douyin.com", "douyin.com", "passport.douyin.com", "sso.douyin.com")
            Platform.KUAISHOU -> listOf("www.kuaishou.com", "kuaishou.com", "passport.kuaishou.com", "id.kuaishou.com")
            Platform.XIAOHONGSHU -> listOf("www.xiaohongshu.com", "xiaohongshu.com", "edith.xiaohongshu.com")
            Platform.TIKTOK -> listOf("www.tiktok.com", "tiktok.com")
            Platform.X -> listOf("x.com", "twitter.com")
            else -> emptyList()
        }
        val exact = when (p) {
            Platform.BILIBILI -> listOf("https://api.bilibili.com/x/web-interface/nav")
            Platform.DOUYIN -> listOf(DouyinSessionProbe.ENDPOINT)
            else -> emptyList()
        }
        return (hosts.map { "https://$it/" } + exact + visited.takeLast(6))
            .filter { allowed(p, it) }.distinct().take(14)
    }
    fun capture(p: Platform, headers: Map<String, String>): List<Cookie> {
        require(embeddedAllowed(p))
        return headers.entries.filter { allowed(p, it.key) }.flatMap { (url, raw) ->
            val u = URI(url)
            if (raw.isBlank()) emptyList() else runCatching {
                CookieCodec.parse(raw, p).map { it.copy(domain = u.host.lowercase(), hostOnly = true,
                    path = u.path.orEmpty().ifBlank { "/" }, expires = 0, secure = true) }
            }.getOrDefault(emptyList())
        }.distinctBy { Triple(it.domain, it.path, it.name) }
    }
    fun captureWithAttributes(p: Platform, details: Map<String, List<String>>,
                              fallback: Map<String, String>): List<Cookie> {
        require(embeddedAllowed(p))
        val exact = details.entries.filter { allowed(p, it.key) }.flatMap { (url, values) ->
            values.filterNot { Regex("(?:^|;)\\s*Partitioned(?:;|$)", RegexOption.IGNORE_CASE).containsMatchIn(it) }
                .flatMap { CookieCodec.received(url, listOf(it)) }
                .filter { p.owns(it.domain) && it.path.startsWith('/') && it.name.isNotBlank() &&
                    it.value.none { ch -> ch < ' ' || ch == '\u007f' } }
        }
        // Prefer real metadata. Never downgrade a partitioned cookie into an ordinary header cookie.
        return (exact + capture(p, fallback.filterKeys { it !in details }))
            .distinctBy { Triple(it.domain, it.path, it.name) }
    }
    fun retainKnownAttributes(current: List<Cookie>, previous: List<Cookie>): List<Cookie> = current.map { c ->
        val old = previous.firstOrNull { it.domain == c.domain && it.path == c.path && it.name == c.name &&
            it.value == c.value && it.hostOnly == c.hostOnly && it.secure == c.secure }
        if (c.expires == 0L && old != null && old.expires > System.currentTimeMillis() / 1000) c.copy(expires = old.expires) else c
    }
    fun cookieLine(c: Cookie, now: Long = System.currentTimeMillis() / 1000): String? {
        if (c.expires != 0L && c.expires <= now) return null
        require(c.domain.isNotBlank() && c.path.startsWith('/') && listOf(c.name, c.value, c.path, c.domain)
            .none { it.any { ch -> ch == '\r' || ch == '\n' || ch == ';' } })
        return "${c.name}=${c.value}; Path=${c.path}" + (if (c.hostOnly) "" else "; Domain=${c.domain}") +
            (if (c.secure) "; Secure" else "") + (if (c.httpOnly) "; HttpOnly" else "") +
            (if (c.expires > 0) "; Max-Age=${c.expires - now}" else "")
    }
    /** Login-only optimisation. Keep JS, CSS, images, QR, CAPTCHA and font resources intact. */
    fun skipVideoInLogin(url: String, mainFrame: Boolean, enabled: Boolean): Boolean {
        if (!enabled || mainFrame) return false
        val path = runCatching { URI(url).path.lowercase() }.getOrDefault("")
        if (listOf("captcha", "verify", "challenge", "qrcode", "login", "security").any { it in path }) return false
        return listOf(".mp4", ".m4v", ".m3u8", ".flv", ".webm").any { path.endsWith(it) }
    }
}
