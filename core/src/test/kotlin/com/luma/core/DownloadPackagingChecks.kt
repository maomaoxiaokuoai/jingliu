package com.luma.core

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/** Source integration tests: synthetic responses and localhost only, no platform account requests. */
object DownloadPackagingChecks {
 @JvmStatic fun main(args:Array<String>) {
  var count=0
  fun test(name:String,block:()->Unit){block();count++;println("PASS $name")}
  fun fails(block:()->Unit){check(runCatching(block).isFailure)}
  val v=mapOf("baseUrl" to "https://a.bilivideo.com:4483/x?v=1","backupUrl" to listOf("https://b.bilivideo.com/x?v=2"))
  test("standard HTTPS backup preferred without path rewriting"){check(BiliMediaPolicy.addresses(v)==listOf("https://b.bilivideo.com/x?v=2","https://a.bilivideo.com:4483/x?v=1"))}
  test("alternate port is restricted to platform media domains"){UrlPolicy.checkTransport("https://a.bilivideo.com:4483/x");fails{UrlPolicy.checkTransport("https://a.bilivideo.com.evil.example:4483/x")};fails{UrlPolicy.checkTransport("https://a.bilivideo.com:8080/x")}}
  test("snake case backups are kept"){check(BiliMediaPolicy.addresses(mapOf("base_url" to "https://x.example/a","backup_url" to listOf("https://x.example/b"))).size==2)}
  val play=mapOf("accept_quality" to listOf(120,80),"accept_description" to listOf("4K","1080P"),"dash" to mapOf("video" to listOf(v+mapOf("id" to 80,"codecid" to 7,"width" to 1920,"height" to 1080)),"audio" to listOf(mapOf("baseUrl" to "https://a.example/audio","backupUrl" to listOf("https://b.example/audio"),"codecs" to "mp4a","bandwidth" to 128000))))
  val formats=BiliMediaPolicy.formats(play)
  test("actual qn and independent video/audio backups"){val f=formats.first();check(f.qualityCode==80&&f.backupUrls.size==1&&f.audioBackupUrls.size==1)}
  test("advertised but missing 4K is not invented"){check(formats.none{it.qualityCode==120})}
  test("preview and DRM are refused"){fails{BiliMediaPolicy.formats(play+("is_preview" to true))};fails{BiliMediaPolicy.formats(play+("has_drm" to true))}}
  val m=Media("a","https://www.bilibili.com/video/BV1xx411c7mD/",Platform.BILIBILI,"fixture",formats=formats)
  test("batch quality uses qn not first item format id"){check(BatchQuality.options(m).any{it.key=="bili:80"});check(BatchQuality.select(m.copy(formats=formats.map{it.copy(id=it.id+"changed")}),"bili:80")?.qualityCode==80)}
  test("fixed batch quality does not silently downgrade"){check(BatchQuality.select(m,"bili:120")==null)}
  test("highest and audio are evaluated per item"){check(BatchQuality.select(m,"best")?.video==true);check(BatchQuality.select(m,"audio")?.video==false)}
  test("portrait matching uses short dimension"){val p=m.copy(formats=listOf(Format("a",width=1080,height=1920)));check(BatchQuality.select(p,"short:1080")?.id=="a")}
  test("Cookie scoped to API but not a foreign CDN"){val j=MemoryCookies(listOf(Cookie("bilibili.com","SESSDATA","test-only")));check(j.header("https://api.bilibili.com/").isNotBlank());check(j.header("https://a.bilivideo.com/x").isBlank())}
  test("Cookie hostOnly and percent-encoded data retained"){val j=MemoryCookies(listOf(Cookie("passport.bilibili.com","SESSDATA","a%2Fb%3Dc",hostOnly=true)));check(j.header("https://api.bilibili.com/").isBlank());check(CookieCodec.parse(CookieCodec.netscape(j.cookies()),Platform.BILIBILI).first().value=="a%2Fb%3Dc")}
  test("CRLF supported but embedded CR rejected"){check(CookieCodec.parse(".bilibili.com\tTRUE\t/\tTRUE\t0\tSESSDATA\tx\r\n",Platform.BILIBILI).size==1);fails{CookieCodec.parse("SESSDATA=a\rb",Platform.BILIBILI)}}
  test("only explicit valid service origins"){check(PrivateParserPolicy.origin("https://parser.example.com/")=="https://parser.example.com");fails{PrivateParserPolicy.origin("https://user:password@parser.example.com")};fails{PrivateParserPolicy.origin("https://parser.example.com/path")}}
  test("HTTP only for Debug localhost or emulator endpoint"){check(PrivateParserPolicy.origin("http://10.0.2.2:8000",true).endsWith(":8000"));fails{PrivateParserPolicy.origin("http://10.0.2.2:8000")};fails{PrivateParserPolicy.origin("http://other.example:8000",true)}}
  test("Bili p>1 forbidden for first-part-only fallback"){fails{PrivateParserPolicy.source("https://www.bilibili.com/video/BV1xx411c7mD/?p=2")}}
  val payload=mapOf("title" to "real-schema-fixture","video_url" to "https://cdn.example/file.mp4","cover_url" to "","music_url" to "https://cdn.example/music.mp3","author" to mapOf("name" to "fixture"))
  val dy="https://www.douyin.com/video/123"
  test("envelope and plain response accepted with unknown statistics"){val a=ParseVideoResponse.decode(mapOf("code" to 200,"data" to payload),dy);val b=ParseVideoResponse.decode(payload,dy);check(a==b&&a.stats.likes==null&&a.best?.bytes==null&&a.best?.audioUrl=="")}
  test("bad optional preview does not discard valid video"){check(ParseVideoResponse.decode(payload+("cover_url" to "javascript:bad"),dy).formats.size==1)}
  test("upstream errors do not leak supplied token"){val e=runCatching{ParseVideoResponse.decode(mapOf("code" to 500,"msg" to "secret-token"),dy)}.exceptionOrNull();check(e!=null&&!e.message.orEmpty().contains("secret-token"))}
  test("gallery exact count and no invented quality"){val g=ParseVideoResponse.decode(mapOf("images" to (1..10).map{mapOf("url" to "https://cdn.example/$it.jpg")}),dy);check(g.entries.size==10&&g.kind==Kind.GALLERY&&g.formats.isEmpty())}
  test("manifest is not published as fake mp4"){fails{ParseVideoResponse.decode(payload+("video_url" to "https://cdn.example/a.m3u8"),dy)}}
  test("share tokens retain original encoding"){check(PrivateParserPolicy.source("https://www.xiaohongshu.com/explore/a?xsec_token=a%2Bb%3D").endsWith("a%2Bb%3D"))}
  test("no Cookie raw exception values in diagnostic"){val d=DownloadFailure.detail(IllegalStateException("Cookie: SECRET https://x/?signed=SECRET"),"VIDEO","native",true);check(!d.contains("SECRET")&&d.contains("SESSDATA=true"))}
  test("stream attempts deduplicated and bounded"){check(StreamCandidates.ordered("a",listOf("a","b","c","d","e")).size==4);check(!StreamCandidates.canRetry(HttpFailure(429)));check(!StreamCandidates.canRetry(javax.net.ssl.SSLException("bad")))}
  test("candidate identity ignores query but not host"){check(StreamCandidates.identity("v","https://a.example/x?a=1")==StreamCandidates.identity("v","https://a.example/x?a=2"));check(StreamCandidates.identity("v","https://a.example/x")!=StreamCandidates.identity("v","https://b.example/x"))}
  test("Douyin fallback bounded and ID validated"){check(DouyinSharePolicy.id("https://www.iesdouyin.com/share/video/123/")=="123");check(DouyinSharePolicy.endpoints("123").size==2);fails{DouyinSharePolicy.endpoints("123&cookie=x")}}
  val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
  val calls=AtomicInteger();val bytes=ByteArray(65536){(it%251).toByte()};var seenAuth="";var seenCookie="";var seenSource=""
  server.createContext("/fail"){e->e.sendResponseHeaders(403,-1);e.close()}
  server.createContext("/ok"){e->calls.incrementAndGet();e.responseHeaders.set("Content-Type","video/mp4");e.sendResponseHeaders(200,bytes.size.toLong());e.responseBody.use{it.write(bytes)}}
  server.createContext("/limit"){e->e.sendResponseHeaders(429,-1);e.close()}
  var redirect=false
  server.createContext("/video/share/url/parse"){e->
   seenAuth=e.requestHeaders.getFirst("Authorization").orEmpty();seenCookie=e.requestHeaders.getFirst("Cookie").orEmpty();seenSource=queryParams("https://local"+e.requestURI.toString())["url"].orEmpty()
   if(redirect){e.responseHeaders.set("Location","/ok");e.sendResponseHeaders(302,-1);e.close()}else{val data=Json.stringify(mapOf("code" to 200,"data" to payload)).toByteArray();e.sendResponseHeaders(200,data.size.toLong());e.responseBody.use{it.write(data)}}
  }
  server.start();val base="http://127.0.0.1:${server.address.port}";val folder=Files.createTempDirectory("jingliu-pack-").toFile()
  try {
   val downloader=CandidateTransfer(ResumableTransfer(Http(allowLoopback=true)))
   test("real localhost bytes 403 -> 200 fallback"){val f=File(folder,"a");downloader.download(base+"/fail",listOf(base+"/ok"),f,"asset",CancelToken()){ };check(f.readBytes().contentEquals(bytes))}
   test("HTTP 429 stops without another node"){val before=calls.get();fails{downloader.download(base+"/limit",listOf(base+"/ok"),File(folder,"b"),"asset",CancelToken()){ }};check(calls.get()==before)}
   test("pre-cancel does not start request"){val token=CancelToken().apply{cancel()};val before=calls.get();fails{downloader.download(base+"/ok",emptyList(),File(folder,"c"),"asset",token){ }};check(calls.get()==before)}
   test("private service uses only its own basic credential"){val m2=PrivateParserClient(base,"operator","password",true).parse(dy,CancelToken());check(m2.extractor=="parse_video_py"&&seenCookie.isEmpty()&&seenAuth=="Basic b3BlcmF0b3I6cGFzc3dvcmQ="&&seenSource==dy)}
   test("service redirect is never followed"){redirect=true;val before=calls.get();fails{PrivateParserClient(base,"operator","password",true).parse(dy,CancelToken())};check(calls.get()==before)}
  }finally{server.stop(0);folder.deleteRecursively()}
  println("DownloadPackagingChecks: $count cases passed; fixtures/local HTTP only, not Android or live media.")
 }
}
