package com.luma.core


/** Local expiry is not proof of server login. No guessed lifetime for pasted/session cookies. */
enum class SessionHealthCode(val label: String) {
    MISSING("未保存会话"), CHECKING("正在检查会话"), VERIFIED("本次平台已确认登录"),
    LOCAL_AVAILABLE("本地有效期未过 · 待平台确认"), UNKNOWN_EXPIRY("已持久保存 · 有效期未知"),
    EXPIRED("本地会话已过期"), REJECTED("本次平台已确认登录失效"),
    MISSING_LOGIN_COOKIE("缺少可发送的登录 Cookie"), UNCONFIRMED("暂时无法确认 · 会话已保留")
}

data class SessionHealth(
    val platform: Platform,
    val revision: String,
    val code: SessionHealthCode,
    val checkedAt: Long,
    val detail: String
)

object SessionHealthPolicy {

    fun local(platform: Platform, cookies: List<Cookie>, rejected: Boolean = false,
              nowSeconds: Long = System.currentTimeMillis() / 1000): SessionHealthCode {
        return LoginCookiePolicy.local(platform, cookies, rejected, nowSeconds)
    }

    fun canProbe(code: SessionHealthCode): Boolean = code in setOf(
        SessionHealthCode.LOCAL_AVAILABLE, SessionHealthCode.UNKNOWN_EXPIRY,
        SessionHealthCode.REJECTED
    )

    fun explanation(code: SessionHealthCode): String = when (code) {
        SessionHealthCode.MISSING -> "尚未保存此平台会话。"
        SessionHealthCode.CHECKING -> "正在向平台检查；不阻塞页面，也不会因网络波动删除 Cookie。"
        SessionHealthCode.VERIFIED -> "本次平台接口已确认登录。Cookie 仍受平台撤销和有效期限制。"
        SessionHealthCode.EXPIRED -> "保存的有效期已到；原凭据保留，重新登录后可替换。"
        SessionHealthCode.REJECTED -> "平台明确返回未登录。不会把普通媒体 403 当作账号失效。"
        SessionHealthCode.MISSING_LOGIN_COOKIE -> "未识别到可用的登录凭据，或作用域不匹配。"
        SessionHealthCode.LOCAL_AVAILABLE -> "登录凭据的本地期限未到，平台在线状态需单独确认。"
        SessionHealthCode.UNKNOWN_EXPIRY -> "请求头或会话 Cookie 未带到期时间，无法推算服务器何时撤销；不会显示永久有效。"
        SessionHealthCode.UNCONFIRMED -> "网络或接口暂时不可用；未修改已保存凭据，稍后可重新检查。"
    }
}
