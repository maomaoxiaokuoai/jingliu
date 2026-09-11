package com.luma.core

/** A read-only, current-user web endpoint, NOT open-platform OAuth and not a token refresher.
 * Only positive self identity proves online state. An empty/blocked/403 result is inconclusive. */
enum class OnlineVerdict { VERIFIED, REJECTED, UNCONFIRMED }
data class SelfIdentity(val verdict: OnlineVerdict, val uid: String = "", val name: String = "", val avatar: String = "")
object DouyinSessionProbe {
    const val ENDPOINT = "https://www.douyin.com/aweme/v1/web/user/profile/self/?aid=6383&device_platform=webapp"
    fun classify(status: Int, body: String): SelfIdentity {
        if (status !in 200..299 || body.length > 512 * 1024) return SelfIdentity(OnlineVerdict.UNCONFIRMED)
        val v = runCatching { Json.parse(body) }.getOrNull() ?: return SelfIdentity(OnlineVerdict.UNCONFIRMED)
        val success = v.n("status_code") == 0L
        val user = v.at("user")
        val uid = user.s("uid").ifBlank { user.s("sec_uid") }
        val explicitOut = v.at("is_login") == false || v.at("isLogin") == false ||
            user.at("is_login") == false || user.at("isLogin") == false
        // Keep positive and negative claims exclusive. Contradictory responses are not trusted.
        if (success && uid.isNotBlank() && uid != "0" && !explicitOut) {
            return SelfIdentity(OnlineVerdict.VERIFIED, uid.take(256), user.s("nickname").take(200),
                user.list("avatar_thumb", "url_list").firstOrNull().str().takeIf {
                    it.startsWith("https://") && runCatching { UrlPolicy.checkTransport(it) }.isSuccess
                }.orEmpty())
        }
        if (success && explicitOut && (uid.isBlank() || uid == "0")) return SelfIdentity(OnlineVerdict.REJECTED)
        // Numeric error codes, LOGIN_STATUS/localStorage, HTML and missing user are not a rejection proof.
        return SelfIdentity(OnlineVerdict.UNCONFIRMED)
    }
}
