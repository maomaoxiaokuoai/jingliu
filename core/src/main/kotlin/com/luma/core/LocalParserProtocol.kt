package com.luma.core

/** Bounded app-private IPC, not an HTTP service protocol. No credential values in errors. */
object LocalParserProtocol {
    const val REQUEST_BYTES = 96 * 1024
    const val RESULT_BYTES = 192 * 1024
    const val WAIT_MILLIS = 360_000L // up to four queued jobs + one active; each network parse is capped at 65 s
    const val COMMIT = "5fcf87256edb5ffcdebf0e4aac2a5a41745da76e"
    fun request(source:String, cookies:List<Cookie>, selfTest:Boolean=false):String {
        val url=if(selfTest)"" else UrlPolicy.normalize(source).also{UrlPolicy.checkTransport(it)}
        val platform=if(selfTest)Platform.DIRECT else Platform.of(url)
        // Import only this platform's account scope. Never pass a server Basic password or OAuth secret.
        val scoped=cookies.filter{platform!=Platform.DIRECT&&platform.owns(it.domain)&&
            (it.expires==0L||it.expires>System.currentTimeMillis()/1000)}.take(256)
        return Json.stringify(mapOf("schema" to 1,"operation" to if(selfTest)"selftest" else "parse",
            "url" to url,"cookies" to scoped.map{it.json()})).also{
            if(it.toByteArray(Charsets.UTF_8).size>REQUEST_BYTES)throw failure("LOCAL_INPUT")
        }
    }
    fun response(raw:String):Any? {
        if(raw.toByteArray(Charsets.UTF_8).size>RESULT_BYTES)throw failure("LOCAL_RESPONSE_TOO_LARGE")
        val value=try{Json.parse(raw)}catch(_:Exception){throw failure("LOCAL_SCHEMA")}
        if(value.at("ok")!=true)throw failure(value.s("error"))
        if(value.s("backend")!="parse_video_py"||value.s("commit")!=COMMIT)throw failure("LOCAL_UPSTREAM_CONTRACT")
        return value
    }
    fun failure(rawCode:String):PlatformError {
        val code=rawCode.takeIf{it.matches(Regex("LOCAL_[A-Z0-9_]{1,60}"))}?:"LOCAL_PARSE_FAILED"
        val message=when(code) {
            "LOCAL_UNSUPPORTED"->"parse-video-py 本地库未覆盖这个链接类型；YouTube / TikTok 请在解析页选 yt-dlp。"
            "LOCAL_DEPENDENCY","LOCAL_STARTUP"->"本地 Python 解析组件未正确安装或初始化。请运行本地组件自检并检查构建依赖；不需要部署服务器。"
            "LOCAL_TIMEOUT"->"手机本地解析超时，请检查网络或切换解析页引擎。"
            "LOCAL_NETWORK"->"手机无法连接平台接口；未配置远程解析服务器或应用代理。"
            "LOCAL_CANCELLED"->"本地解析已取消。"
            "LOCAL_QUEUE_FULL"->"本地解析队列繁忙，请等待当前任务或取消后重试。"
            "LOCAL_PROCESS_DIED","LOCAL_BIND_FAILED","LOCAL_DISCONNECTED"->"本地解析进程被系统停止或无法连接，可以重试；这不是服务地址配置错误。"
            "LOCAL_RESPONSE_TOO_LARGE"->"解析信息超过安全大小限制，请缩小图集或改用其他引擎。"
            "LOCAL_URL_POLICY"->"本地解析拒绝非 HTTPS、私网或不受支持的目标地址。"
            "LOCAL_SCHEMA","LOCAL_UPSTREAM_CONTRACT"->"本地解析返回结构与打包版本不一致，请检查固定的上游依赖。"
            "LOCAL_HTTP_401","LOCAL_HTTP_403","LOCAL_HTTP_412"->"平台拒绝本地解析请求，可能需要有效会话、验证或该内容不可访问；没有清空账号。"
            "LOCAL_HTTP_429"->"平台限制请求频率，请稍后再试。"
            "LOCAL_LEGACY_TASK"->"此任务使用旧远程引擎的源文件编号。请重新解析并选择当前实际画质；未改变旧任务或清空账号。"
            "LOCAL_INPUT"->"本地解析输入无效或超过大小限制。"
            else->"parse-video-py 本地解析未返回可用结果；保留错误代码，未记录 Cookie、完整链接或原始异常。"
        }
        return PlatformError(message,code)
    }
}

/** The engine is frozen when parsing starts, and is not a new option in a download dialog. */
data class ParsedEngineBinding(val requested:String) {
    val engineId:String = ParseEngine.from(requested).id
    fun taskEngine():String = engineId
}
