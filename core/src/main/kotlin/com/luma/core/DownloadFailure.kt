package com.luma.core

import java.io.IOException
import java.net.URI

/** Diagnostics contain controlled error codes, phase and code locations, never raw exceptions. */
object DownloadFailure {
    fun code(e:Throwable):String = when(e) {
        is PlatformError->e.failureCode.takeIf{it.matches(Regex("[A-Z0-9_]{1,64}"))}?:"PLATFORM_RESPONSE"
        is HttpFailure->"HTTP_${e.status}"
        is TransferCancelled->"CANCELLED"
        is MediaBodyRejected->"NOT_MEDIA_RESPONSE"
        is ResourceVersionChanged->"RESOURCE_CHANGED"
        is IncompleteTransfer->"INCOMPLETE_TRANSFER"
        is java.net.UnknownHostException->"DNS_FAILED"
        is java.net.SocketTimeoutException->"NETWORK_TIMEOUT"
        is javax.net.ssl.SSLException->"TLS_FAILED"
        is java.net.ConnectException->"NETWORK_UNREACHABLE"
        is SecurityException->"SYSTEM_PERMISSION"
        else->when {
            e.javaClass.simpleName.contains("ForegroundService",true)->"FOREGROUND_SERVICE"
            e.message.orEmpty().contains("拒绝不安全的媒体地址")->"MEDIA_ADDRESS_POLICY"
            e.message.orEmpty().contains("网页或验证提示")->"NOT_MEDIA_RESPONSE"
            e.message.orEmpty().contains("续传偏移")->"RANGE_MISMATCH"
            e.message.orEmpty().contains("音视频合并")->"MUX_FAILED"
            e.message.orEmpty().contains("ABI")->"ENGINE_ABI"
            e.message.orEmpty().contains("Code: 28")||e.message.orEmpty().contains("No space",true)->"STORAGE_FULL"
            e is IOException->"IO_FAILED"
            e is IllegalArgumentException->"INVALID_RESPONSE"
            e is IllegalStateException->"STATE_FAILURE"
            else->"UNEXPECTED_FAILURE"
        }
    }
    fun summary(e:Throwable):String = when(val c=code(e)) {
        "SESSION_REJECTED"->"平台明确拒绝当前会话。请校验账号；没有自动退出或清空 Cookie。"
        "PREVIEW_ONLY"->"平台只返回试看，未下载为完整版。"
        "DRM_PROTECTED"->"该资源有 DRM 保护，不支持下载。"
        "BILI_CDN_ADDRESS","MEDIA_ADDRESS_POLICY"->"媒体节点地址不可用，或被安全规则拒绝；不等于账号未登录。"
        "NOT_MEDIA_RESPONSE"->"节点返回网页或验证提示而非媒体，已停止保存。"
        "HTTP_401"->"目标接口返回 HTTP 401，需检查该接口的登录或服务认证。"
        "HTTP_403","HTTP_412"->"平台或媒体节点拒绝请求；可能是地址过期、节点限制或风控，不直接判定 Cookie 丢失。"
        "HTTP_404","HTTP_410"->"当前资源地址不存在或已过期。"
        "HTTP_429"->"请求受到频率限制，请稍后重试。"
        "DNS_FAILED","NETWORK_TIMEOUT","NETWORK_UNREACHABLE"->"无法连接目标，请检查网络后重试。"
        "TLS_FAILED"->"TLS 证书或握手失败，未关闭证书验证。"
        "ENGINE_ABI"->"此设备的原生解析组件未正确安装，请检查 ABI 与安装包。"
        "FOREGROUND_SERVICE","SYSTEM_PERMISSION"->"系统未允许当前前台任务或文件操作。请检查通知、前台服务与文件权限。"
        "STORAGE_FULL"->"本机存储空间不足。"
        "RANGE_MISMATCH"->"节点续传偏移不匹配；请重试或切换节点。"
        "MUX_FAILED"->"音视频合并未完成；原始片段保留，账号并非失败原因。"
        "INCOMPLETE_TRANSFER"->"网络提前中断，已保留片段，可以继续下载。"
        "RESOURCE_CHANGED"->"节点资源版本改变，旧片段已保留；重试将安全重新读取，不拼接不同资源。"
        "CANCELLED"->"操作已取消。"
        else->when {
            (c.startsWith("BACKUP_")||c.startsWith("LOCAL_"))->(e as? PlatformError)?.message.orEmpty().take(220)
            c=="NATIVE_UNSUPPORTED"->"本机原生引擎不支持此链接类型或没有返回媒体，请切换引擎。"
            c=="QUALITY_UNAVAILABLE"->"此项没有所选画质；未擅自降级，请重新选择。"
            c=="BILI_API"->(e as? PlatformError)?.message.orEmpty().take(180)
            c=="IO_FAILED"->"文件或网络操作失败，请根据诊断阶段检查网络和存储。"
            else->"此阶段处理失败。请复制脱敏诊断以定位具体代码，不必反复重新登录。"
        }
    }
    fun detail(e:Throwable,phase:String,engine:String,hasBiliSession:Boolean?=null):String {
        val locations=e.stackTrace.filter{it.className.startsWith("com.luma.")}.take(4)
            .map{"${it.fileName?.filter{c->c.isLetterOrDigit()||c=='.'||c=='_'}?:"source"}:${it.lineNumber}"}
        val cause=e.cause?.javaClass?.simpleName.orEmpty().filter{it.isLetterOrDigit()}.take(64)
        return listOf("镜流 0.7.8", "阶段="+phase.filter{it.isLetterOrDigit()||it=='_'}.take(40),
            "引擎="+ParseEngine.from(engine).id,"代码="+code(e),
            "异常类型="+e.javaClass.simpleName.filter{it.isLetterOrDigit()}.take(64),"原因类型=$cause",
            "调用位置="+locations.joinToString(" → "),
            "B站API可发送SESSDATA="+(hasBiliSession?.toString()?:"未检查"),
            "不含账号、Cookie值、签名链接或原始响应").joinToString("\n")
    }
}

/** Only switch between URLs supplied for the same chosen stream. Limit attempts, no quality change. */
object StreamCandidates {
    fun ordered(primary:String,backups:List<String>):List<String> = (listOf(primary)+backups).filter(String::isNotBlank).distinct().take(4)
    fun canRetry(e:Exception):Boolean=when(e) {
        is TransferCancelled -> false
        is HttpFailure -> e.status in setOf(403,404,410,412,416,500,502,503,504)
        is javax.net.ssl.SSLException -> false
        is IOException -> true
        else -> false
    }
    fun identity(base:String,url:String):String {val u=URI(url);return base+":"+digest(u.scheme+"://"+u.rawAuthority+u.rawPath).take(20)}
}
