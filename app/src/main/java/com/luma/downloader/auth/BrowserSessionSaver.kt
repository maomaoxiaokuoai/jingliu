package com.luma.downloader.auth

import com.luma.core.*

/** Offline capture is not online identity verification. The caller runs this on Dispatchers.IO. */
class BrowserAccountChanged : Exception("账号已在别处修改，请重新打开登录页")
class BrowserSessionSaver(private val vault: SessionVault) {
    data class Snapshot(val account: Account?, val epoch: Long)
    fun initial(platform: Platform): Snapshot = synchronized(vault) { Snapshot(vault.get(platform), vault.epoch(platform)) }
    fun save(platform: Platform, expected: Snapshot, cookies: List<Cookie>, token: CancelToken): Snapshot {
        require(BrowserSessionPolicy.embeddedAllowed(platform))
        val own = cookies.filter { platform.owns(it.domain) }
        require(own.isNotEmpty()) { "尚未检测到 Cookie" }
        token.check()
        synchronized(vault) {
            token.check()
            val current = vault.get(platform)
            if (vault.epoch(platform) != expected.epoch || current?.revision != expected.account?.revision) throw BrowserAccountChanged()
            // A background checker may refresh display metadata without changing browser credentials.
            val sameLogin = current != null && LoginCookiePolicy.signature(platform, current.cookies) == LoginCookiePolicy.signature(platform, own)
            val next = if (sameLogin) current!!.copy(cookies = own, revision = current.revision) else Account(platform, own)
            if (next != current && !vault.saveIfUnchanged(current, next)) throw BrowserAccountChanged()
            return Snapshot(vault.get(platform), vault.epoch(platform))
        }
    }
}
