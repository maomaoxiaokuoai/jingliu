package com.luma.downloader.auth

import com.luma.core.*
import java.nio.file.Files
import java.io.File
import javax.crypto.KeyGenerator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*

/** Production policies/Vault/saver/check coordinator. No Android WebView or real platform contacts. */
object Browser080Checks {
 @JvmStatic fun main(args:Array<String>) {
  var passed=0
  fun test(name:String, body:()->Unit){body();passed++;println("PASS $name")}
  fun eq(a:Any?, b:Any?){check(a==b){"expected $b, got $a"}}
  fun rejected(body:()->Unit){check(runCatching(body).isFailure)}
  val now=System.currentTimeMillis()/1000
  val dy=Cookie("douyin.com","sessionid","test-douyin-token",expires=now+3600,httpOnly=true)
  val p=Platform.DOUYIN
  test("all seven platforms have recognised credential hints") {
   Platform.entries.filter{it!=Platform.DIRECT}.forEach{check(LoginCookiePolicy.names(it).isNotEmpty())}
  }
  test("Google restriction is explicit, not a fake embedded login") {
   check(!BrowserSessionPolicy.embeddedAllowed(Platform.YOUTUBE));check(!BrowserSessionPolicy.embeddedAllowed(Platform.DIRECT))
   Platform.entries.filter{it !in setOf(Platform.DIRECT,Platform.YOUTUBE)}.forEach{check(BrowserSessionPolicy.allowed(it,BrowserSessionPolicy.entry(it)))}
  }
  test("only expired analytics does not expire valid Douyin login") {
   eq(LoginCookiePolicy.local(p,listOf(dy,Cookie("douyin.com","ttwid","analytics",expires=now-10)),false,now),SessionHealthCode.LOCAL_AVAILABLE)
  }
  test("long-lived analytics does not mask expired login") {
   eq(LoginCookiePolicy.local(p,listOf(dy.copy(expires=now-1),Cookie("douyin.com","ttwid","analytics",expires=now+999999)),false,now),SessionHealthCode.EXPIRED)
  }
  test("session/header-only cookie has unknown expiry, not permanent") {
   eq(LoginCookiePolicy.local(p,listOf(dy.copy(expires=0)),false,now),SessionHealthCode.UNKNOWN_EXPIRY)
   check(LoginCookiePolicy.expiry(p,listOf(dy.copy(expires=0)),now).nextExpirySeconds==null)
  }
  test("earliest known login expiry and unknown attributes remain distinct") {
   val e=LoginCookiePolicy.expiry(p,listOf(dy,dy.copy(name="sid_tt",expires=now+120),dy.copy(name="sid_guard",expires=0)),now)
   eq(e.nextExpirySeconds,now+120);check(e.hasUnknown&&!e.allExpired)
  }
  test("foreign platform cookies and wrong API paths are not online credentials") {
   eq(LoginCookiePolicy.local(p,listOf(Cookie("tiktok.com","sessionid","x")),false,now),SessionHealthCode.MISSING)
   eq(LoginCookiePolicy.local(p,listOf(dy.copy(path="/unrelated")),false,now),SessionHealthCode.MISSING_LOGIN_COOKIE)
  }
  test("known full expiry is reported without wiping the source") {
   val c=listOf(dy.copy(expires=now-10));check(LoginCookiePolicy.expiry(p,c,now).allExpired);eq(c.size,1)
  }
  val api=DouyinSessionProbe.ENDPOINT
  test("Set-Cookie attributes preserve actual expiry domain path Secure HttpOnly") {
   val observed=BrowserSessionPolicy.captureWithAttributes(p,mapOf(api to listOf("sessionid=x; Domain=.douyin.com; Path=/; Max-Age=3600; Secure; HttpOnly")),emptyMap()).single()
   check(observed.expires in (now+3500)..(now+3700));check(observed.secure&&observed.httpOnly&&!observed.hostOnly);eq(observed.domain,"douyin.com")
  }
  test("Expires is read, not inferred from token text") {
   val observed=BrowserSessionPolicy.captureWithAttributes(p,mapOf(api to listOf("sessionid=x; Path=/; Expires=Wed, 09 Jun 2038 10:18:14 GMT; Secure")),emptyMap()).single()
   check(observed.expires>now);check(observed.hostOnly)
  }
  test("partitioned cookie cannot fall back as a normal header cookie") {
   eq(BrowserSessionPolicy.captureWithAttributes(p,mapOf(api to listOf("sessionid=x; Secure; Path=/; Partitioned")),mapOf(api to "sessionid=x")).size,0)
  }
  test("unsupported metadata fallback is host/path limited with unknown expiry") {
   val c=BrowserSessionPolicy.capture(p,mapOf(api to "sessionid=x")).single()
   check(c.hostOnly);eq(c.domain,"www.douyin.com");eq(c.path,"/aweme/v1/web/user/profile/self/");eq(c.expires,0L)
  }
  test("metadata preserved only for exactly matching credential and scope") {
   eq(BrowserSessionPolicy.retainKnownAttributes(listOf(dy.copy(expires=0)),listOf(dy)).single().expires,dy.expires)
   eq(BrowserSessionPolicy.retainKnownAttributes(listOf(dy.copy(expires=0,value="new")),listOf(dy)).single().expires,0L)
  }
  test("old expired metadata does not invent the new unknown expiry") {
   eq(BrowserSessionPolicy.retainKnownAttributes(listOf(dy.copy(expires=0)),listOf(dy.copy(expires=now-10))).single().expires,0L)
  }
  test("restore sends remaining Max-Age and never revives expired cookies") {
   check(BrowserSessionPolicy.cookieLine(dy,now)!!.contains("Max-Age=3600"));eq(BrowserSessionPolicy.cookieLine(dy.copy(expires=now-1),now),null)
   check(!BrowserSessionPolicy.cookieLine(dy.copy(expires=0),now)!!.contains("Max-Age"))
  }
  test("browser cookie restore rejects header injection") {rejected{BrowserSessionPolicy.cookieLine(dy.copy(value="value; Domain=evil.com"),now)}}
  test("light mode blocks only subresource video extension; off really off") {
   check(BrowserSessionPolicy.skipVideoInLogin("https://v.kwaicdn.com/media/movie.mp4?x=1",false,true))
   check(!BrowserSessionPolicy.skipVideoInLogin("https://v.kwaicdn.com/media/movie.mp4",false,false))
   check(!BrowserSessionPolicy.skipVideoInLogin("https://v.kwaicdn.com/media/movie.mp4",true,true))
  }
  test("scripts QR captcha images and login media are not blocked by light mode") {
   listOf("/app.js","/qr.png","/captcha/movie.mp4","/login/movie.webm","/style.css","/font.woff2").forEach{
    check(!BrowserSessionPolicy.skipVideoInLogin("https://www.kuaishou.com$it",false,true))}
  }
  test("desktop UA keeps actual installed Chromium version") {
   check(BrowserSessionPolicy.desktopAgent("Mozilla Android Chrome/133.0.1234.12 Mobile").contains("Chrome/133.0.1234.12"))
  }
  test("cookie probes are bounded and never include external origins") {
   val values=BrowserSessionPolicy.probes(p,(1..60).map{"https://www.douyin.com/path/$it"}+"https://evil.example/")
   check(values.size<=14&&values.all{BrowserSessionPolicy.allowed(p,it)});check(values.contains(api))
  }
  test("Douyin current-self identity is a positive online result") {
   eq(DouyinSessionProbe.classify(200,"""{"status_code":0,"user":{"uid":"123","nickname":"fixture"}}""").verdict,OnlineVerdict.VERIFIED)
  }
  test("explicit current-self logged-out is rejected") {
   eq(DouyinSessionProbe.classify(200,"""{"status_code":0,"is_login":false,"user":{}}""").verdict,OnlineVerdict.REJECTED)
  }
  test("HTTP200 empty user LOGIN_STATUS or invented error code is not login proof") {
   listOf("{}","{\"status_code\":0}","{\"status_code\":8}","{\"LOGIN_STATUS\":1}","{\"status_code\":0,\"user\":{\"uid\":\"0\"}}")
    .forEach{eq(DouyinSessionProbe.classify(200,it).verdict,OnlineVerdict.UNCONFIRMED)}
  }
  test("403 CAPTCHA network HTML and malformed body stay inconclusive") {
   for(code in listOf(401,403,429,500))eq(DouyinSessionProbe.classify(code,"{}" ).verdict,OnlineVerdict.UNCONFIRMED)
   eq(DouyinSessionProbe.classify(200,"<html>captcha</html>").verdict,OnlineVerdict.UNCONFIRMED)
  }
  test("contradictory identity response is not trusted") {
   eq(DouyinSessionProbe.classify(200,"""{"status_code":0,"is_login":false,"user":{"uid":"123"}}""").verdict,OnlineVerdict.UNCONFIRMED)
  }
  test("auto capture waits for two stable observations") {
   val gate=BrowserCaptureGate(p);gate.baseline(emptyList())
   check(!gate.consider(listOf(dy),0,true));check(!gate.consider(listOf(dy),800,true));check(gate.consider(listOf(dy),1300,true))
  }
  test("saved credential not continually rewritten") {
   val gate=BrowserCaptureGate(p);gate.baseline(emptyList());gate.markSaved(listOf(dy))
   check(!gate.consider(listOf(dy.copy(expires=dy.expires+1)),10000,true))
  }
  test("late expiry metadata upgrades same-token import without repeated writes") {
   val gate=BrowserCaptureGate(p);gate.baseline(listOf(dy.copy(expires=0)))
   check(!gate.consider(listOf(dy),0,true));check(gate.consider(listOf(dy),1400,true));gate.markSaved(listOf(dy))
   check(!gate.consider(listOf(dy),9999,true))
  }
  test("partial/rotating tokens restart settle time") {
   val gate=BrowserCaptureGate(p);gate.baseline(emptyList());gate.consider(listOf(dy),0,true)
   check(!gate.consider(listOf(dy.copy(value="rotated")),1500,true));check(gate.consider(listOf(dy.copy(value="rotated")),3000,true))
  }
  test("anonymous XHS first sample and no interaction not auto-saved") {
   val gate=BrowserCaptureGate(Platform.XIAOHONGSHU);gate.baseline(emptyList());val x=Cookie("xiaohongshu.com","web_session","anonymous")
   check(!gate.consider(listOf(x),0,false,true));check(!gate.consider(listOf(x),2000,true))
   check(!gate.consider(listOf(x.copy(value="logged")),4000,true));check(gate.consider(listOf(x.copy(value="logged")),5500,true))
  }
  test("expired/no credential will never be auto-saved") {
   val gate=BrowserCaptureGate(p);gate.baseline(emptyList())
   check(!gate.consider(listOf(dy.copy(expires=now-5)),9999,true));check(!gate.consider(listOf(dy.copy(name="ttwid")),20000,true))
  }
  val root=Files.createTempDirectory("jingliu-session080-").toFile()
  val key=KeyGenerator.getInstance("AES").apply{init(256)}.generateKey()
  fun vault(name:String)=SessionVault(File(root,name),{key})
  try {
   test("auto saver writes real AES-GCM ciphertext and persists exact expiry") {
    val v=vault("persistent");val save=BrowserSessionSaver(v);val next=save.save(p,save.initial(p),listOf(dy),CancelToken())
    check(next.account!!.checked==0L);check(!File(root,"persistent").readText().contains(dy.value))
    eq(vault("persistent").get(p)!!.cookies.single(),dy)
   }
   test("background metadata-only check cannot break browser capture CAS") {
    val v=vault("metadata");v.save(Account(p,listOf(dy)));val save=BrowserSessionSaver(v);val before=save.initial(p)
    v.save(v.get(p)!!.copy(checked=123,name="verified-fixture"));eq(v.epoch(p),before.epoch)
    val result=save.save(p,before,listOf(dy),CancelToken());eq(result.account!!.name,"verified-fixture")
   }
   test("new import prevents old browser overwriting credentials") {
    val v=vault("new-import");v.save(Account(p,listOf(dy)));val save=BrowserSessionSaver(v);val before=save.initial(p)
    val imported=Account(p,listOf(dy.copy(value="replacement")));v.save(imported)
    rejected{save.save(p,before,listOf(dy),CancelToken())};eq(v.get(p),imported)
   }
   test("delete and null/import/delete ABA never resurrect account") {
    val v=vault("delete-aba");val save=BrowserSessionSaver(v);val empty=save.initial(p)
    v.save(Account(p,listOf(dy)));v.delete(p)
    rejected{save.save(p,empty,listOf(dy),CancelToken())};eq(v.get(p),null)
   }
   test("cancelled save makes no file or account") {
    val v=vault("cancelled");val save=BrowserSessionSaver(v);val t=CancelToken().apply{cancel()}
    rejected{save.save(p,save.initial(p),listOf(dy),t)};eq(v.get(p),null);check(!File(root,"cancelled").exists())
   }
   test("same login retains Bili refresh metadata; changed login resets it") {
    val v=vault("refresh");val p2=Platform.BILIBILI;val c=Cookie("bilibili.com","SESSDATA","fixture")
    v.save(Account(p2,listOf(c),refreshToken="test-refresh",uid="1",checked=123));val save=BrowserSessionSaver(v)
    save.save(p2,save.initial(p2),listOf(c.copy(expires=now+500)),CancelToken());eq(v.get(p2)!!.refreshToken,"test-refresh")
    save.save(p2,save.initial(p2),listOf(c.copy(value="new-account")),CancelToken());eq(v.get(p2)!!.checked,0L);eq(v.get(p2)!!.refreshToken,"")
   }
   test("Douyin checker success then HTTP403 preserves session, reports inconclusive") {
    val v=vault("probe");v.save(Account(p,listOf(dy)));val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val checker=SessionChecks(v,scope){a,_->a};checker.douyinCheckOverride={a,_->a.copy(checked=System.currentTimeMillis(),name="fixture")}
    try {checker.request(p,true);waitFor{checker.flow.value[p]?.code==SessionHealthCode.VERIFIED}
     val before=v.get(p);checker.douyinCheckOverride={_,_->throw HttpFailure(403)};checker.request(p,true)
     waitFor{checker.flow.value[p]?.code==SessionHealthCode.UNCONFIRMED};eq(v.get(p),before)
    }finally{scope.cancel()}
   }
   test("concurrent same account requests coalesce, not flood platform") {
    val v=vault("coalesce");v.save(Account(p,listOf(dy)));val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO);val calls=AtomicInteger();val barrier=CountDownLatch(1)
    val checker=SessionChecks(v,scope){a,_->a};checker.douyinCheckOverride={a,_->calls.incrementAndGet();barrier.await(3,TimeUnit.SECONDS);a.copy(checked=1)}
    try{repeat(20){checker.request(p,true)};waitFor{calls.get()==1};eq(calls.get(),1);barrier.countDown();waitFor{checker.flow.value[p]?.code==SessionHealthCode.VERIFIED}}
    finally{barrier.countDown();scope.cancel()}
   }
   test("late online success after import cannot overwrite new token or status") {
    val v=vault("late");v.save(Account(p,listOf(dy)));val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO);val entered=CountDownLatch(1);val release=CountDownLatch(1)
    val checker=SessionChecks(v,scope){a,_->a};checker.douyinCheckOverride={a,_->entered.countDown();release.await(3,TimeUnit.SECONDS);a.copy(checked=1)}
    try{checker.request(p,true);check(entered.await(2,TimeUnit.SECONDS));val next=Account(p,listOf(dy.copy(value="new")));v.save(next);release.countDown();Thread.sleep(250);eq(v.get(p),next)}
    finally{release.countDown();scope.cancel()}
   }
   test("explicit rejection persists flag but never deletes credentials") {
    val v=vault("rejected");v.save(Account(p,listOf(dy)));val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val checker=SessionChecks(v,scope){a,_->a};checker.douyinCheckOverride={_,_->throw SelfSessionRejected()}
    try{checker.request(p,true);waitFor{checker.flow.value[p]?.code==SessionHealthCode.REJECTED};check(v.get(p)!!.rejected);eq(v.get(p)!!.cookies,listOf(dy))}
    finally{scope.cancel()}
   }
  } finally {root.deleteRecursively()}
  println("Browser080Checks: $passed cases passed. Host JVM policies/AES/files/coroutines only; no WebView, real platform or Android build.")
 }
 private fun waitFor(predicate:()->Boolean){val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);while(!predicate()&&System.nanoTime()<end)Thread.sleep(10);check(predicate()){"async test timed out"}}
}
