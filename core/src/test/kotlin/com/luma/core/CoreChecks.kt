package com.luma.core

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic fixtures exist only in test sources. No live platform or login is assumed. */
object CoreChecks {
    private var assertions = 0
    private fun ok(value: Boolean, name: String) { check(value) { name }; assertions++ }
    private fun bad(name: String, block: () -> Unit) { ok(runCatching(block).isFailure, name) }
    fun run(): Int {
        assertions = 0
        val sample = mapOf("标题" to "一行\n\"引号\"\\", "list" to listOf(1L, true, null, "\\u"), "big" to 9007199254740993L)
        ok(Json.parse(Json.stringify(sample)) == sample, "JSON lossless integer/string round trip")
        listOf("", "{", "[1,]", "{\"x\":1,}", "NaN", "01", "1e", "1e9999", "null x", "\"\n\"", "undefined").forEach { bad("strict JSON rejects $it") { Json.parse(it) } }
        bad("JSON nesting bound") { Json.parse("[".repeat(130) + "0" + "]".repeat(130)) }
        bad("JSON nonfinite writer") { Json.stringify(Double.NaN) }
        val state = assignedJson("window.__INITIAL_STATE__ = {\"x\":undefined,\"s\":\"} bracket\",\"n\":{\"noteId\":\"n1\"}}; other();", "__INITIAL_STATE__")
        ok(state.s("s") == "} bracket" && state.at("x") == null, "balanced state extraction")
        ok(findObject(state) { it.containsKey("noteId") }.s("noteId") == "n1", "bounded object search")
        ok(assignedJson("window.X = alert('bad')", "X") == null, "never evaluate JavaScript")
        ok("1.2万".longOrNull() == 12000L && "1,234".longOrNull() == 1234L, "localized counts")
        ok(fps("60000/1001") in 59.9..60.0, "fractional fps")
        ok(countText(null) == "未提供", "missing statistics are not zero")
        ok(Platform.of("https://b23.tv/123") == Platform.BILIBILI, "short URL platform")
        ok(Platform.of("https://douyin.com.attacker.invalid/x") == Platform.DIRECT, "suffix host isolation")
        ok(!domainMatches("evilbilibili.com", "bilibili.com"), "cookie suffix boundary")
        ok(UrlPolicy.normalize("分享 https://www.douyin.com/video/123?a=%2F%26）。") == "https://www.douyin.com/video/123?a=%2F%26", "share text and percent encoding")
        listOf("https://user:pass@example.com/x", "https://example.com:444/x").forEach { bad("invalid URL") { UrlPolicy.normalize(it) } }
        listOf("http://example.com/x", "https://127.0.0.1/x", "https://10.0.0.1/x", "https://192.168.1.1/x", "https://172.20.1.1/x").forEach { bad("private/insecure media URL") { UrlPolicy.checkTransport(it) } }
        val cookies = CookieCodec.parse("SESSDATA=test-only; bili_jct=test-csrf", Platform.BILIBILI)
        ok(cookies.size == 2, "Cookie header parsing")
        ok(CookieCodec.parse(CookieCodec.netscape(cookies), Platform.BILIBILI) == cookies, "Netscape round trip")
        val jar = MemoryCookies(cookies)
        ok(jar.header("https://api.bilibili.com/x").contains("SESSDATA"), "same domain cookies")
        ok(jar.header("https://www.youtube.com/x").isEmpty(), "cross-platform cookie isolation")
        ok(!Cookie("example.com", "a", "b", "/user", hostOnly = true).matches(URI("https://sub.example.com/user")), "host-only scope")
        ok(!Cookie("example.com", "a", "b", "/user").matches(URI("https://example.com/users")), "cookie path boundary")
        ok(!Cookie("example.com", "a", "b", expires = 1).matches(URI("https://example.com/")), "expired cookies")
        bad("foreign cookie import rejected") { CookieCodec.parse(".youtube.com\tTRUE\t/\tTRUE\t0\ta\tb", Platform.BILIBILI) }
        bad("header injection rejected") { CookieCodec.parse("SESSDATA=a\r\nX: b", Platform.BILIBILI) }
        val ids = setOf("1", "2", "3")
        ok(Selection.all(emptySet(), ids) == ids, "first select-all")
        ok(Selection.all(setOf("2"), ids) == ids, "partial select-all")
        ok(Selection.all(ids, ids).isEmpty(), "second select-all clears")
        ok(Selection.toggle(ids, "2") == setOf("1", "3"), "individual deselection")
        ok(Selection.all(emptySet(), emptySet()).isEmpty(), "empty selection")
        val f = Format("f", url = "https://cdn.example/v", audioUrl = "https://cdn.example/a", bitrate = 8_000_000, audioBitrate = 128_000)
        ok(f.size(10.0) == SizeEstimate(10_160_000, true), "combined bitrate estimate")
        ok(f.copy(bytes = 100, audioBytes = 20).size(10.0) == SizeEstimate(120, false), "combined known size")
        ok(f.copy(audioBitrate = null).size(10.0).bytes == null, "unknown audio size stays unknown")
        val m = Media("id", "https://example.com", Platform.DIRECT, "t", formats = listOf(Format("720", height = 720), Format("1080", height = 1080), Format("drm", width = 9000, height = 9000, drm = true)))
        ok(m.best?.id == "1080", "actual highest format, missing width, no DRM")
        ok(!safeName("../../坏:name.mp4").contains('/'), "path-safe filename")
        val dy = PlatformJson.douyin(Json.parse("""{"aweme_id":"1","desc":"真实字段结构测试","author":{"nickname":"fixture","follower_count":123},"statistics":{"digg_count":5},"images":[{"url_list":["https://cdn.example/1.jpg"]},{"url_list":["https://cdn.example/2.jpg"]}]}"""), "https://www.douyin.com/note/1")
        ok(dy.kind == Kind.GALLERY && dy.entries.size == 2, "Douyin gallery")
        ok(dy.stats.likes == 5L && dy.stats.shares == null && dy.author.followers == 123L, "Douyin missing statistics")
        val xhs = PlatformJson.xhs(Json.parse("""{"noteId":"n","type":"normal","title":"图集","imageList":[{"urlDefault":"https://cdn.example/1.jpg"}],"interactInfo":{"likedCount":"1.2万"}}"""), "https://www.xiaohongshu.com/explore/n")
        ok(xhs.entries.size == 1 && xhs.stats.likes == 12000L, "XHS image and counts")
        val ks = PlatformJson.kuaishou(Json.parse("""{"photoId":"k","photoUrl":"https://cdn.example/v.mp4","height":1080,"width":1920}"""), "https://www.kuaishou.com/short-video/k")
        ok(ks.best?.height == 1080, "Kuaishou dimensions")
        val tt = PlatformJson.tiktok(Json.parse("""{"id":"t","desc":"image","imagePost":{"images":[{"imageURL":{"urlList":["https://cdn.example/1.jpg"]}}]}}"""), "https://www.tiktok.com/@user/photo/1")
        ok(tt.kind == Kind.GALLERY && tt.entries.size == 1, "TikTok gallery")
        val bili = PlatformJson.biliFormats(Json.parse("""{"accept_quality":[80,120],"accept_description":["1080P","4K"],"dash":{"video":[{"id":80,"codecid":7,"baseUrl":"https://cdn.example/v","width":1920,"height":1080}],"audio":[{"baseUrl":"https://cdn.example/a","codecs":"mp4a.40.2","bandwidth":128000}]}}"""))
        ok(bili.count { it.video } == 1 && bili.first().label == "1080P", "Bili requested labels do not create unavailable streams")
        ok(bili.first().audioUrl.isNotBlank() && audioBest(bili) != null, "Bili DASH separate audio")
        val yt = PlatformJson.yt(Json.parse("""{"id":"yt","title":"fixture","formats":[{"format_id":"137","url":"https://cdn.example/v","vcodec":"avc1","acodec":"none","width":1920,"height":1080,"ext":"mp4"},{"format_id":"140","url":"https://cdn.example/a","vcodec":"none","acodec":"mp4a","ext":"m4a","abr":128},{"format_id":"sb","url":"https://cdn.example/sb","vcodec":"images","protocol":"mhtml"}]}"""), "https://www.youtube.com/watch?v=01234567890")
        ok(yt.best?.id == "137+140", "yt-dlp audio-video selection")
        ok(yt.formats.size == 2 && yt.best?.extension == "mkv", "no storyboards, stream-copy container")
        bad("live rejection") { PlatformJson.yt(Json.parse("""{"id":"l","is_live":true}"""), "https://example.com/") }
        bad("DRM-only rejection") { PlatformJson.yt(Json.parse("""{"formats":[{"format_id":"x","url":"https://cdn.example/v","has_drm":true}]}"""), "https://example.com/") }
        val playlist = PlatformJson.yt(mapOf("_type" to "playlist", "playlist_count" to 220, "entries" to (0 until 100).map { mapOf("id" to "$it", "url" to "https://www.youtube.com/watch?v=01234567890", "title" to "$it") }), "https://www.youtube.com/playlist?list=test")
        ok(playlist.kind == Kind.PLAYLIST && playlist.nextOffset == 100 && playlist.entries.size == 100, "playlist pagination")
        val cancelled = CancelToken(); var callbacks = 0; val remove = cancelled.listen { callbacks++ }; cancelled.cancel(); cancelled.cancel(); remove()
        ok(callbacks == 1, "cancel listener once"); bad("cancel check") { cancelled.check() }
        httpChecks()
        println("Core regression: $assertions assertions passed (synthetic fixtures + localhost HTTP; no live platform verification).")
        return assertions
    }
    private fun httpChecks() {
        val directory = Files.createTempDirectory("shiliu-core-checks").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }
        server.executor = executor
        val bytes = ByteArray(524288) { ((it * 17 + 31) % 256).toByte() }
        val count = AtomicInteger()
        var lastRange: String? = null
        server.createContext("/") { e ->
            try {
                count.incrementAndGet(); val path = e.requestURI.path; lastRange = e.requestHeaders.getFirst("Range")
                if(path == "/redirect") { e.responseHeaders.add("Location", "/file"); e.sendResponseHeaders(302, -1); return@createContext }
                if(path == "/html") { e.responseHeaders.add("Content-Type", "text/html"); val b = "<html>validation</html>".toByteArray(); e.sendResponseHeaders(200, b.size.toLong()); e.responseBody.write(b); return@createContext }
                e.responseHeaders.add("Content-Type", "application/octet-stream")
                e.responseHeaders.add("ETag", if(path == "/changed") "\"new\"" else "\"v1\"")
                val start = if(path == "/ignore") 0 else lastRange?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                if(start >= bytes.size) { e.responseHeaders.add("Content-Range", "bytes */${bytes.size}"); e.sendResponseHeaders(416, -1); return@createContext }
                if(start > 0) e.responseHeaders.add("Content-Range", "bytes $start-${bytes.lastIndex}/${bytes.size}")
                e.sendResponseHeaders(if(start > 0) 206 else 200, (bytes.size - start).toLong())
                if(path == "/slow") { var p = start; while(p < bytes.size) { val n = minOf(4096, bytes.size - p); e.responseBody.write(bytes, p, n); e.responseBody.flush(); p += n; Thread.sleep(15) } }
                else e.responseBody.write(bytes, start, bytes.size - start)
            } catch(_: Exception) { /* Client cancellation is expected in /slow. */ } finally { e.close() }
        }
        server.start()
        val base = "http://127.0.0.1:${server.address.port}"
        val transport = ResumableTransfer(Http(direct = true, allowLoopback = true))
        fun seed(file: File, length: Int, validator: String = "\"v1\"") {
            File(file.path + ".part").writeBytes(bytes.copyOf(length))
            atomicText(File(file.path + ".resume.json"), Json.stringify(mapOf("identity" to "id", "validator" to validator)))
        }
        try {
            val file = File(directory, "full")
            transport.download("$base/file", file, "id", CancelToken()) { }
            ok(file.readBytes().contentEquals(bytes), "actual HTTP full download")
            val n = count.get(); transport.download("$base/file", file, "id", CancelToken()) { }
            ok(count.get() == n, "completed file reuse")
            val resumed = File(directory, "resumed"); seed(resumed, 10000)
            transport.download("$base/file", resumed, "id", CancelToken()) { }
            ok(lastRange == "bytes=10000-" && resumed.readBytes().contentEquals(bytes), "Range 206 resume")
            val ignored = File(directory, "ignored"); seed(ignored, 10000)
            transport.download("$base/ignore", ignored, "id", CancelToken()) { }
            ok(ignored.readBytes().contentEquals(bytes), "Range ignored 200 truncates")
            val changed = File(directory, "changed"); seed(changed, 10000)
            bad("changed validator rejects partial append") { transport.download("$base/changed", changed, "id", CancelToken()) { } }
            ok(!File(changed.path + ".part").exists(), "changed partial removed")
            val completed = File(directory, "416"); seed(completed, bytes.size)
            transport.download("$base/file", completed, "id", CancelToken()) { }
            ok(completed.readBytes().contentEquals(bytes), "416 matching length+ETag completes")
            val noValidator = File(directory, "novalidator"); seed(noValidator, 10000, "")
            transport.download("$base/file", noValidator, "id", CancelToken()) { }
            ok(lastRange == null && noValidator.readBytes().contentEquals(bytes), "no validator restarts safely")
            bad("validation page is not media") { transport.download("$base/html", File(directory, "html"), "id", CancelToken()) { } }
            val redirect = File(directory, "redirect")
            transport.download("$base/redirect", redirect, "id", CancelToken()) { }
            ok(redirect.readBytes().contentEquals(bytes), "redirect followed")
            val stopped = CancelToken(); val partial = File(directory, "cancelled")
            bad("midstream transfer cancellation") { transport.download("$base/slow", partial, "id", stopped) { if(it.downloaded > 0) stopped.cancel() } }
            ok(!partial.exists() && File(partial.path + ".part").length() > 0, "cancel retains partial, not completed file")
        } finally { server.stop(0); executor.shutdownNow(); directory.deleteRecursively() }
    }
}
fun main() { CoreChecks.run() }
