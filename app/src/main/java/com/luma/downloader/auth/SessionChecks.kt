package com.luma.downloader.auth

import com.luma.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Per-snapshot async checks. A refused media CDN, local marker or HTTP 200 is not login proof. */
class SessionChecks(
    private val vault: SessionVault,
    private val scope: CoroutineScope,
    private val verify: (Account, CancelToken) -> Account
) {
    // Test seam only: production always uses the real read-only self endpoint.
    internal var douyinCheckOverride: ((Account, CancelToken) -> Account)? = null
    private data class Running(val account: Account, val token: CancelToken)
    private val running = mutableMapOf<Platform, Running>()
    private val state = MutableStateFlow<Map<Platform, SessionHealth>>(emptyMap())
    val flow = state.asStateFlow()

    fun checkSaved(force: Boolean = false) {
        scope.launch(Dispatchers.IO) { vault.load(); vault.accounts.value.forEach { request(it.platform, force) } }
    }
    fun beforeUse(platform: Platform) {
        if (platform != Platform.DIRECT) scope.launch(Dispatchers.IO) { request(platform, false) }
    }
    @Synchronized fun request(platform: Platform, force: Boolean = false) {
        val account = vault.get(platform)
        if (account == null) { clear(platform); return }
        val local = SessionHealthPolicy.local(platform, account.cookies, account.rejected)
        val now = System.currentTimeMillis()
        if (running[platform]?.account?.let { it != account } == true) {
            running.remove(platform)?.token?.cancel()
        }
        if (platform !in setOf(Platform.BILIBILI, Platform.DOUYIN) || !SessionHealthPolicy.canProbe(local)) {
            running.remove(platform)?.token?.cancel(); publish(account, local, now); return
        }
        if (running[platform]?.account == account) return
        val prev = state.value[platform]
        if (!force && prev?.revision == account.revision && now - prev.checkedAt in 0..30_000L &&
            prev.code != SessionHealthCode.CHECKING) return
        val task = Running(account, CancelToken()); running[platform] = task
        publish(account, SessionHealthCode.CHECKING, now)
        scope.launch(Dispatchers.IO) {
            val timer = launch { delay(12_000); task.token.cancel() }
            try {
                val confirmed = if (platform == Platform.BILIBILI) verify(account, task.token) else (douyinCheckOverride ?: ::verifyDouyin)(account, task.token)
                task.token.check()
                synchronized(this@SessionChecks) {
                    if (running[platform] !== task) return@synchronized
                    if (vault.saveIfUnchanged(account, confirmed)) publish(confirmed, SessionHealthCode.VERIFIED, System.currentTimeMillis())
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                synchronized(this@SessionChecks) {
                    if (running[platform] !== task) return@synchronized
                    if (e is LoginRejected || e is SelfSessionRejected) {
                        val updated = account.copy(rejected = true, checked = 0)
                        if (vault.saveIfUnchanged(account, updated)) publish(updated, SessionHealthCode.REJECTED, System.currentTimeMillis())
                    } else if (vault.get(platform) == account) {
                        publish(account, SessionHealthCode.UNCONFIRMED, System.currentTimeMillis())
                    }
                }
            } finally {
                timer.cancel(); task.token.cancel()
                synchronized(this@SessionChecks) {
                    if (running[platform] === task) running.remove(platform)
                }
            }
        }
    }
    private fun verifyDouyin(account: Account, token: CancelToken): Account {
        val response = Http(MemoryCookies(account.cookies), true).text(
            DouyinSessionProbe.ENDPOINT, token, mapOf("Referer" to "https://www.douyin.com/user/self",
                "User-Agent" to BrowserSessionPolicy.DESKTOP_AGENT, "Accept" to "application/json", "Cache-Control" to "no-cache"))
        val result = DouyinSessionProbe.classify(response.status, response.body)
        token.check()
        return when (result.verdict) {
            OnlineVerdict.VERIFIED -> account.copy(uid = result.uid, name = result.name, avatar = result.avatar,
                checked = System.currentTimeMillis(), rejected = false)
            OnlineVerdict.REJECTED -> throw SelfSessionRejected()
            OnlineVerdict.UNCONFIRMED -> throw IllegalStateException("身份接口暂时无法确认")
        }
    }
    @Synchronized fun clear(platform: Platform) {
        running.remove(platform)?.token?.cancel(); state.value = state.value - platform
    }
    @Synchronized fun rememberVerified(account: Account) {
        if (vault.get(account.platform) != account) return
        running.remove(account.platform)?.token?.cancel()
        publish(account, SessionHealthCode.VERIFIED, System.currentTimeMillis())
    }
    private fun publish(account: Account, code: SessionHealthCode, now: Long) {
        state.value = state.value + (account.platform to SessionHealth(account.platform, account.revision, code,
            now, SessionHealthPolicy.explanation(code)))
    }
}
internal class SelfSessionRejected : Exception("当前用户接口明确返回未登录")
