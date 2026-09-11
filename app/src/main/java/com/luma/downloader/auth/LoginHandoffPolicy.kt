package com.luma.downloader.auth

import com.luma.core.*
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

enum class LoginHandoffMode { NATIVE_APP, EXTERNAL_BROWSER }
/** These are launch attempts, not proof that the user or platform authorised anything. */
data class BiliHandoff(val officialUrl:String,val nativeUrl:String,val packageName:String="tv.danmaku.bili")
object LoginHandoffPolicy {
    private val terminal=setOf(AuthorizationPhase.CONFIRMED,AuthorizationPhase.DENIED,AuthorizationPhase.EXPIRED,
        AuthorizationPhase.CANCELLED,AuthorizationPhase.FAILED)
    fun bili(url:String,key:String,expiresAt:Long,phase:AuthorizationPhase,now:Long):BiliHandoff {
        require(phase !in terminal && phase!=AuthorizationPhase.VERIFYING) {"当前登录请求不再允许跳转"}
        require(now<expiresAt && expiresAt-now<=180_000L && now>0) {"二维码已过期，请重新申请"}
        require(key.matches(Regex("[a-fA-F0-9]{32}"))) {"未识别平台返回的扫码编号"}
        val u=URI(url)
        require(u.scheme=="https"&&u.host=="passport.bilibili.com"&&u.port==-1&&u.userInfo==null&&u.fragment==null) {"不是允许的 B站登录来源"}
        require(u.rawPath=="/h5-app/passport/login/scan") {"平台改变了确认页面，请使用原始二维码扫码"}
        val query=u.rawQuery.orEmpty().split('&').map{part->
            URLDecoder.decode(part.substringBefore('='),"UTF-8") to URLDecoder.decode(part.substringAfter('=',""),"UTF-8")}
        require(query.count{it.first=="qrcode_key"}==1&&query.single{it.first=="qrcode_key"}.second==key) {"跳转与当前扫码请求不匹配"}
        // Generic browser deep link, NOT the official OAuth SDK. Its ability to present the
        // confirmation page varies by Bili version and is explicitly labelled experimental.
        val native="bilibili://browser?url="+URLEncoder.encode(url,"UTF-8").replace("+","%20")
        return BiliHandoff(url,native)
    }
}
