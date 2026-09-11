package com.luma.core

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.io.File

/** Current production rules with synthetic data and actual localhost bytes. Not a device/site test. */
object Package078Checks {
 @JvmStatic fun main(args:Array<String>)=run()
 fun run() {
  var count=0
  fun test(name:String,block:()->Unit){block();count++;println("PASS $name")}
  test("platform media Referer replaces short link, no credential metadata") {
   val h=MediaRequestPolicy.headers("https://sns-img-qc.xhscdn.com/actual!nd_dft?sig=A%2Bb", "https://xhslink.com/a",
    mapOf("Cookie" to "DO_NOT_SEND","Authorization" to "DO_NOT_SEND","referer" to "https://wrong.example/"))
   check(h["Referer"]=="https://www.xiaohongshu.com/"&&!h.toString().contains("DO_NOT_SEND"))
  }
  test("actual transform query and percent escapes preserved") {
   val u="https://sns-img-qc.xhscdn.com/actual!nd_dft?sig=A%2Bb%3D"
   check(MediaRequestPolicy.candidates(u,listOf(u,"javascript:bad")).single()==u)
  }
  test("legacy photo identity accepted, different photo rejected") {
   check(MediaRequestPolicy.sameImage("https://ci.xiaohongshu.com/notes_pre_post/abcdefghijklmnop","https://sns-img-qc.xhscdn.com/a/abcdefghijklmnop!nd_dft?sig=1"))
   check(!MediaRequestPolicy.sameImage("https://ci.xiaohongshu.com/notes_pre_post/abcdefghijklmnop","https://sns-img-qc.xhscdn.com/other-image-000000"))
  }
  test("gallery backup addresses carried through actual response mapping") {
   val m=ParseVideoResponse.decode(mapOf("images" to listOf(mapOf("url" to "https://sns-img-qc.xhscdn.com/a!nd_dft?q=1","backup_urls" to listOf("https://sns-img-bd.xhscdn.com/a!nd_dft?q=2")))),"https://www.xiaohongshu.com/explore/abcd",true)
   check(m.entries.single().backupUrls.size==1&&m.stats.likes==null)
  }
  test("video backups survive mapping") {
   val m=ParseVideoResponse.decode(mapOf("video_url" to "https://cdn.example/a.mp4","video_backup_urls" to listOf("https://cdn2.example/a.mp4")),"https://www.kuaishou.com/short-video/a",true)
   check(m.formats.single().backupUrls.size==1)
  }
  test("embedded Google not enabled") {check(!BrowserSessionPolicy.embeddedAllowed(Platform.YOUTUBE));check(!BrowserSessionPolicy.embeddedAllowed(Platform.DIRECT))}
  test("official browser host boundary and HTTPS") {
   check(BrowserSessionPolicy.allowed(Platform.BILIBILI,"https://passport.bilibili.com/login"))
   check(!BrowserSessionPolicy.allowed(Platform.BILIBILI,"https://bilibili.com.attacker.example/login"))
   check(!BrowserSessionPolicy.allowed(Platform.BILIBILI,"http://bilibili.com/login"))
  }
  test("WebView cookies are host-only, unknown expiry, no foreign cookies") {
   val cookies=BrowserSessionPolicy.capture(Platform.BILIBILI,mapOf("https://api.bilibili.com/x/web-interface/nav" to "SESSDATA=own%2Ftest", "https://other.example/" to "other=secret"))
   check(cookies.size==1&&cookies.single().hostOnly&&cookies.single().expires==0L)
   check(MemoryCookies(cookies).header("https://api.bilibili.com/x/web-interface/nav").isNotEmpty())
   check(MemoryCookies(cookies).header("https://www.bilibili.com/").isEmpty())
  }
  test("unsafe launch schemes are never allowed") {check(!BrowserSessionPolicy.appScheme(Platform.BILIBILI,"intent"));check(!BrowserSessionPolicy.appScheme(Platform.BILIBILI,"file"));check(BrowserSessionPolicy.appScheme(Platform.BILIBILI,"bilibili"))}
  test("default retries and retry classification") {check(TransferRecoveryPolicy.DEFAULT_RETRIES==10);check(TransferRecoveryPolicy.retryable(IncompleteTransfer()));check(!TransferRecoveryPolicy.retryable(HttpFailure(403)));check(TransferRecoveryPolicy.refreshAddress(HttpFailure(403)));check(!TransferRecoveryPolicy.retryable(javax.net.ssl.SSLException("test")))}
  test("new errors remain redacted") {val d=DownloadFailure.detail(IncompleteTransfer(),"VIDEO","native");check(d.contains("INCOMPLETE_TRANSFER")&&d.contains("0.7.8"))}
  val dir=Files.createTempDirectory("jingliu-package078-").toFile()
  val bytes=ByteArray(65536){(it%251).toByte()}
  var range=""
  val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
  server.createContext("/data"){e->
   range=e.requestHeaders.getFirst("Range").orEmpty();val start=range.substringAfter("bytes=", "0").substringBefore('-').toInt()
   e.responseHeaders.set("Content-Type","image/jpeg");e.responseHeaders.set("ETag","\"v1\"")
   if(range.isNotEmpty())e.responseHeaders.set("Content-Range","bytes $start-${bytes.lastIndex}/${bytes.size}")
   e.sendResponseHeaders(if(range.isBlank())200 else 206,(bytes.size-start).toLong());e.responseBody.use{it.write(bytes,start,bytes.size-start)}
  }
  server.createContext("/fail"){e->e.sendResponseHeaders(403,-1);e.close()}
  server.start()
  val base="http://127.0.0.1:${server.address.port}"
  val transfer=ResumableTransfer(Http(allowLoopback=true))
  fun seed(out:File,n:Int,identity:String,validator:String="\"v1\"") {
   File(out.path+".part").writeBytes(bytes.copyOf(n));atomicText(File(out.path+".resume.json"),Json.stringify(mapOf("identity" to identity,"validator" to validator)))
  }
  try {
   test("valid partial sends Range and resumes exactly") {
    val out=File(dir,"resume");seed(out,1234,"same")
    transfer.download("$base/data",out,"same",CancelToken()){}
    check(range=="bytes=1234-"&&out.readBytes().contentEquals(bytes))
   }
   test("unknown validator preserves partial and safely restarts") {
    val out=File(dir,"unknown");seed(out,1234,"same","")
    transfer.download("$base/data",out,"same",CancelToken()){}
    check(range.isBlank()&&out.readBytes().contentEquals(bytes))
    check(dir.listFiles()!!.any{it.name.startsWith("unknown.retained-")&&it.extension=="part"&&it.length()==1234L})
   }
   test("candidate rejection does not destroy second candidate's resume slot") {
    val out=File(dir,"candidate");val identity="image-2";val url="$base/data"
    val key=StreamCandidates.identity(identity,url);val slot=File(out.path+".candidate-"+digest(key).take(16));seed(slot,2048,key)
    CandidateTransfer(transfer).download("$base/fail",listOf(url),out,identity,CancelToken()){}
    check(range=="bytes=2048-"&&out.readBytes().contentEquals(bytes))
   }
   test("completed candidate is reused without network data mutation") {
    val out=File(dir,"candidate");CandidateTransfer(transfer).download("$base/fail",emptyList(),out,"image-2",CancelToken()){}
    check(out.readBytes().contentEquals(bytes))
   }
  }finally{server.stop(0);dir.deleteRecursively()}
  println("$count package-0.7.8 rule/localhost checks passed; no Android or live account test.")
 }
}
