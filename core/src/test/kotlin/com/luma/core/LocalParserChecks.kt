package com.luma.core

/** Host-only protocol and result mapping checks. No Android or real Python package import. */
object LocalParserChecks {
    @JvmStatic fun main(args:Array<String>)=run()
    fun run() {
        var count=0
        fun test(name:String,block:()->Unit){block();count++;println("PASS $name")}
        fun rejected(code:String,block:()->Unit) {
            val e=runCatching(block).exceptionOrNull()
            check(e is PlatformError && e.failureCode==code){"expected $code, got ${e?.javaClass?.simpleName}"}
        }
        val src="https://www.douyin.com/video/12345"
        val valid=mapOf("ok" to true,"backend" to "parse_video_py","commit" to LocalParserProtocol.COMMIT,"data" to mapOf("video_url" to "https://cdn.example/video.mp4"))
        test("request is local IPC schema, not server configuration") {
            val req=Json.parse(LocalParserProtocol.request(src,emptyList()))
            check(req.n("schema")==1L&&req.s("operation")=="parse"&&req.s("url")==src)
            check(req.at("server")==null&&req.at("origin")==null)
        }
        test("only selected platform cookies enter private IPC") {
            val req=Json.parse(LocalParserProtocol.request(src,listOf(Cookie("douyin.com","sessionid","own"),Cookie("bilibili.com","SESSDATA","foreign"))))
            check(req.list("cookies").size==1&&req.list("cookies")[0].s("name")=="sessionid")
            check(!Json.stringify(req).contains("foreign"))
        }
        test("expired cookies and unrelated domains not passed") {
            val req=Json.parse(LocalParserProtocol.request(src,listOf(Cookie("douyin.com","old","x",expires=1))))
            check(req.list("cookies").isEmpty())
        }
        test("self test never contains a source URL or credentials") {
            val req=Json.parse(LocalParserProtocol.request("",listOf(Cookie("douyin.com","session","secret")),true))
            check(req.s("operation")=="selftest"&&req.s("url").isBlank()&&req.list("cookies").isEmpty())
        }
        test("IPC request byte size is bounded") {
            rejected("LOCAL_INPUT"){LocalParserProtocol.request(src,listOf(Cookie("douyin.com","a","x".repeat(LocalParserProtocol.REQUEST_BYTES))))}
        }
        test("IPC success requires correct actual backend and pinned commit") {
            check(LocalParserProtocol.response(Json.stringify(valid)).at("ok")==true)
            rejected("LOCAL_UPSTREAM_CONTRACT"){LocalParserProtocol.response(Json.stringify(valid+("commit" to "wrong")))}
            rejected("LOCAL_UPSTREAM_CONTRACT"){LocalParserProtocol.response(Json.stringify(valid+("backend" to "remote")))}
        }
        test("IPC result byte size and malformed JSON rejected") {
            rejected("LOCAL_RESPONSE_TOO_LARGE"){LocalParserProtocol.response("x".repeat(LocalParserProtocol.RESULT_BYTES+1))}
            rejected("LOCAL_SCHEMA"){LocalParserProtocol.response("{")}
        }
        test("error codes do not echo credential-bearing exception details") {
            val e=LocalParserProtocol.failure("https://example.org/?Cookie=secret")
            check(e.failureCode=="LOCAL_PARSE_FAILED"&&!e.message.orEmpty().contains("secret"))
        }
        test("upstream missing fields remain unknown") {
            val m=ParseVideoResponse.decode(valid.at("data"),src,local=true)
            check(m.best?.id=="pv-source"&&m.stats.likes==null&&m.author.followers==null)
            check(m.best?.size(m.duration)?.bytes==null&&m.notice.contains("手机内置"))
        }
        test("returned graph/gallery converted locally without fake quality tiers") {
            val m=ParseVideoResponse.decode(mapOf("title" to "T","images" to (1..10).map{mapOf("url" to "https://cdn.example/$it.jpg")}),src,local=true)
            check(m.entries.size==10&&m.kind==Kind.GALLERY&&m.formats.isEmpty())
        }
        test("manifest is handled as native transport input, not a saved text video") {
            val d=mapOf("video_url" to "https://cdn.example/hls.m3u8?token=example")
            val m=ParseVideoResponse.decode(d,src,local=true)
            check(m.best?.id=="pv-manifest"&&m.best?.engine==true&&m.best?.url==d["video_url"])
            rejected("BACKUP_MANIFEST"){ParseVideoResponse.decode(d,src)}
        }
        test("Live Photo limitation is explicit, not a fake paired export") {
            val m=ParseVideoResponse.decode(mapOf("images" to listOf(mapOf("url" to "https://cdn.example/a.jpg","live_photo_url" to "https://cdn.example/a.mp4"))),src,true)
            check(m.notice.contains("仅保存静态")&&m.entries.size==1)
        }
        test("engine binding does not read changing settings at download confirmation") {
            var selected=ParseEngine.PARSE_VIDEO_PY.id
            val bind=ParsedEngineBinding(selected)
            selected=ParseEngine.YTDLP.id
            check(bind.taskEngine()=="parse_video_py"&&selected!=bind.taskEngine())
        }
        test("old engine id stays compatible while display title becomes local") {
            check(ParseEngine.from("parse_video_py")==ParseEngine.PARSE_VIDEO_PY)
            check(ParseEngine.PARSE_VIDEO_PY.title.contains("手机本地"))
        }
        test("local errors have actionable download diagnostics") {
            val e=LocalParserProtocol.failure("LOCAL_DEPENDENCY")
            check(DownloadFailure.summary(e).contains("本地"))
            check(DownloadFailure.detail(e,"RESOLVING","parse_video_py").contains("LOCAL_DEPENDENCY"))
        }
        println("$count local protocol/mapping checks passed; not Android/native-runtime or live-platform tests.")
    }
}
