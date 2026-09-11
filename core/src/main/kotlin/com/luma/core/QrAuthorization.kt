package com.luma.core

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Capability list is explicit. An app-launch intent is not a QR/OAuth implementation. */
object QrCapability {
    val bridged=setOf(Platform.DOUYIN,Platform.KUAISHOU,Platform.TIKTOK)
    fun supported(p:Platform)=p==Platform.BILIBILI||p in bridged
    fun description(p:Platform,configured:Boolean)=when {
        p==Platform.BILIBILI->"B站扫码登录；确认后校验并保存真实下载会话"
        p in bridged && configured->"开放平台身份授权；不提供浏览器 Cookie，也不代替下载会话"
        p in bridged->"需配置开发者应用与自有授权服务；不会生成假二维码"
        else->"未接入适用于本安卓应用的官方扫码授权，保留 App 与 Cookie 入口"
    }
}
enum class QrDisplay { CODE, OFFICIAL_PAGE }
enum class AuthorizationPhase { WAITING, SCANNED, VERIFYING, CONFIRMED, DENIED, EXPIRED, CANCELLED, FAILED }
data class AuthorizationProfile(val platform:Platform,val id:String,val name:String,val avatar:String,val scopes:String,val verifiedAt:Long) {
    fun json()=mapOf("platform" to platform.name,"id" to id,"name" to name,"avatar" to avatar,"scopes" to scopes,"verifiedAt" to verifiedAt)
    companion object {fun from(v:Any?):AuthorizationProfile {
        val p=Platform.valueOf(v.s("platform"));require(p in QrCapability.bridged)
        val id=v.s("id");require(id.isNotBlank()&&id.length<=512)
        val avatar=v.s("avatar");if(avatar.isNotBlank())UrlPolicy.checkTransport(avatar)
        val at=v.n("verifiedAt")?:error("Missing verified time");require(at>0)
        return AuthorizationProfile(p,id,v.s("name").take(256),avatar,v.s("scopes").take(1024),at)
    }}
}
data class RemoteQrTicket(val platform:Platform,val session:String,val proof:String,val nonce:String,val kind:QrDisplay,val url:String,val expiresAt:Long)
data class RemoteQrState(val phase:AuthorizationPhase,val profile:AuthorizationProfile?=null)

/** Closed generation gates also protect slow responses which arrive after cancellation/refresh. */
class QrAttemptGate {
    private var serial=0L
    private var open=false
    @Synchronized fun start():Long {serial++;open=true;return serial}
    @Synchronized fun cancel(){serial++;open=false}
    @Synchronized fun accepts(generation:Long)=open && generation==serial
    @Synchronized fun <T> commit(generation:Long,action:()->T):T? {
        if(!accepts(generation))return null
        val result=action();open=false;return result
    }
}
object QrProtocol {
    fun random():String=Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also{SecureRandom().nextBytes(it)})
    fun same(a:String,b:String)=MessageDigest.isEqual(a.toByteArray(),b.toByteArray())
    fun secret(value:String){require(value.matches(Regex("[A-Za-z0-9_-]{32,128}"))){"Invalid session capability"}}
    fun bridgeBase(raw:String):String {
        val u=UrlPolicy.checkTransport(raw);require(u.rawQuery==null&&u.rawFragment==null)
        require(u.rawPath.isNullOrBlank()||u.rawPath=="/"){"授权服务需使用独立 HTTPS 域名根路径"}
        return raw.trimEnd('/')
    }
    fun query(url:String):Map<String,String> {
        val q=URI(url).rawQuery.orEmpty();if(q.isBlank())return emptyMap()
        val out=linkedMapOf<String,String>()
        q.split('&').forEach {part->val key=URLDecoder.decode(part.substringBefore('='),"UTF-8");val value=URLDecoder.decode(part.substringAfter('=',""),"UTF-8")
            require(key !in out){"Repeated authorization parameter"};out[key]=value}
        return out
    }
    fun displayAllowed(platform:Platform,kind:QrDisplay,url:String):Boolean=runCatching {
        val u=URI(url);require(u.userInfo==null&&u.fragment==null)
        when(kind) {
            QrDisplay.OFFICIAL_PAGE -> u.scheme=="https" && (u.port==-1||u.port==443) && when(platform) {
                Platform.DOUYIN->u.host=="open.douyin.com" && u.path.trimEnd('/')=="/platform/oauth/connect"
                Platform.KUAISHOU->u.host=="open.kuaishou.com" && u.path.trimEnd('/')=="/oauth2/connect"
                else->false
            }
            QrDisplay.CODE -> platform==Platform.TIKTOK && u.scheme=="aweme" && u.host=="authorize" && query(url)["client_ticket"].orEmpty().isNotBlank()
        }
    }.getOrDefault(false)
    fun ticket(v:Any?,expected:Platform,nonce:String,now:Long):RemoteQrTicket {
        require(v.s("platform")==expected.name&&same(v.s("nonce"),nonce)){"授权会话不匹配"}
        val session=v.s("session");val proof=v.s("proof");secret(session);secret(proof)
        val kind=QrDisplay.valueOf(v.s("display_kind"));val url=v.s("display_url")
        require(url.length<=8192&&displayAllowed(expected,kind,url)){"授权二维码来源不合法"}
        val expires=v.n("expires_at")?:0;require(expires>now&&expires-now<=600_000){"授权二维码已经过期或有效期异常"}
        return RemoteQrTicket(expected,session,proof,nonce,kind,url,expires)
    }
    fun result(v:Any?,t:RemoteQrTicket,now:Long):RemoteQrState {
        require(v.s("session")==t.session && v.s("platform")==t.platform.name && same(v.s("nonce"),t.nonce)){"授权状态串线，已拒绝"}
        if(now>=t.expiresAt)return RemoteQrState(AuthorizationPhase.EXPIRED)
        val phase=AuthorizationPhase.valueOf(v.s("status"))
        if(phase!=AuthorizationPhase.CONFIRMED)return RemoteQrState(phase)
        val profile=AuthorizationProfile.from(v.at("profile"));require(profile.platform==t.platform)
        return RemoteQrState(phase,profile)
    }
}
