package com.luma.core

/** UI capabilities, not a claim that an App launch can return another app's private cookies. */
object AccountAccessPolicy {
    fun hasDownloadQr(platform:Platform) = platform == Platform.BILIBILI
    fun hasIdentityQr(platform:Platform) = platform in QrCapability.bridged
    fun downloadHelp(platform:Platform):String = if (hasDownloadQr(platform))
        "推荐使用 B站扫码登录。确认后镜流轮询平台结果、校验身份，并加密保存平台实际返回的会话；不需要从 B站 App 私有文件复制 Cookie。" else
        "此版本尚未为 ${platform.title} 接入可自动获取下载会话的登录流程。请只导入你自己已登录网页的 Cookie 请求头或 Netscape cookies.txt。打开官方 App 或完成开放平台身份授权，不会变成浏览器 Cookie。"
    fun identityHelp(configured:Boolean) = if(configured)
        "通过已配置的开放平台服务确认头像、昵称等获授权身份。不会生成下载 Cookie，也不会替换下载会话。" else
        "尚未配置开发者应用与 HTTPS 授权服务。此功能只确认公开身份，不提供下载 Cookie；普通下载无需先配置它。"
    fun nativeLaunchHelp(platform:Platform) =
        "这个按钮只打开 ${platform.title}，不会发起官方 SDK 授权，也不会弹出读取 Cookie 的系统权限。要给镜流连接下载会话，请返回使用扫码登录或导入自己的 Cookie。"
}

/** Export consent is limited to a live code. No OAuth web-page URL is turned into a fake QR. */
data class QrExportStamp(val platform:Platform,val identity:String,val kind:QrDisplay,val expiresAt:Long)
object QrPresentationPolicy {
    fun active(phase:AuthorizationPhase) = phase == AuthorizationPhase.WAITING || phase == AuthorizationPhase.SCANNED
    fun canShow(phase:AuthorizationPhase,expiresAt:Long,now:Long) =
        phase in setOf(AuthorizationPhase.WAITING,AuthorizationPhase.SCANNED,AuthorizationPhase.VERIFYING) && expiresAt > now
    fun canExport(stamp:QrExportStamp,phase:AuthorizationPhase,now:Long) =
        stamp.kind == QrDisplay.CODE && stamp.identity.isNotBlank() && stamp.expiresAt > now && active(phase)
    fun remainingSeconds(expiresAt:Long,now:Long):Long =
        if(expiresAt <= now) 0 else ((expiresAt-now-1)/1000+1).coerceAtMost(600)
}

/** Revoked on closing, replacing, or completing an attempt, including during a pending file write. */
class QrExportGuard(val stamp:QrExportStamp,private val clock:()->Long=System::currentTimeMillis) {
    private var revoked=false
    private var phase=AuthorizationPhase.WAITING
    @Synchronized fun phase(value:AuthorizationPhase){phase=value;if(!QrPresentationPolicy.active(value))revoked=true}
    @Synchronized fun revoke(){revoked=true}
    @Synchronized fun valid() = !revoked && QrPresentationPolicy.canExport(stamp,phase,clock())
    fun requireValid(){check(valid()){"二维码已经刷新、过期或完成；请重新申请后保存"}}
}
