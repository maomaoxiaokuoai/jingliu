package com.luma.core

import java.net.URI

/** Runs actual core classes on the JVM. No real account, Android key or network used. */
object CookieImportChecks {
 @JvmStatic fun main(args:Array<String>) {
  var passed=0
  fun test(name:String,body:()->Unit){body();passed++;println("PASS $name")}
  fun rejects(body:()->Unit){var rejected=false;try{body()}catch(_:IllegalArgumentException){rejected=true};check(rejected)}
  val platforms=Platform.entries.filter{it!=Platform.DIRECT}
  test("seven account platforms"){check(platforms.size==7)}
  for(p in platforms) {
   val domain=p.domains.first()
   test("${p.name} accepts own Netscape file including HttpOnly") {
    val rows=listOf(Cookie(domain,"session_fixture","dummy=opaque",httpOnly=true),Cookie(domain,"setting_fixture","value",path="/a",hostOnly=true))
    check(CookieCodec.parse(CookieCodec.netscape(rows),p)==rows)
   }
   test("${p.name} accepts own Cookie request header") {
    val list=CookieCodec.parse("Cookie: session_fixture=dummy=opaque; pref_fixture=value",p)
    check(list.size==2&&list.all{it.domain==domain})
    check(list[0].value=="dummy=opaque")
   }
   test("${p.name} foreign domains do not leak") {
    val foreign=Cookie("unrelated.example","not_for_this_app","dummy")
    val own=Cookie(domain,"session_fixture","dummy")
    check(CookieCodec.parse(CookieCodec.netscape(listOf(foreign,own)),p)==listOf(own))
    check(MemoryCookies(listOf(own)).header("https://unrelated.example/").isEmpty())
   }
   test("${p.name} request excludes expired values") {
    val expired=Cookie(domain,"old_fixture","dummy",expires=1)
    val current=Cookie(domain,"valid_fixture","dummy")
    val restored=CookieCodec.parse(CookieCodec.netscape(listOf(expired,current)),p)
    check(MemoryCookies(restored).header("https://$domain/")=="valid_fixture=dummy")
   }
  }
  test("Cookie JSON metadata roundtrip") {
   val c=Cookie("x.com","fixture","dummy","/test",secure=true,expires=1_900_000_000L,hostOnly=true,httpOnly=true)
   check(Cookie.from(Json.parse(Json.stringify(c.json())))==c)
  }
  test("https host-only and path matching preserved") {
   val c=Cookie("www.bilibili.com","fixture","dummy",path="/test",hostOnly=true)
   check(c.matches(URI("https://www.bilibili.com/test/a")))
   check(!c.matches(URI("http://www.bilibili.com/test/a")))
   check(!c.matches(URI("https://api.bilibili.com/test/a")))
   check(!c.matches(URI("https://www.bilibili.com/test-other")))
  }
  test("empty and foreign-only input rejected") {
   rejects { CookieCodec.parse("",Platform.BILIBILI) }
   rejects { CookieCodec.parse(CookieCodec.netscape(listOf(Cookie("evil.example","s","dummy"))),Platform.BILIBILI) }
  }
  test("oversized input rejected") {rejects{CookieCodec.parse("s="+"x".repeat(1024*1024),Platform.BILIBILI)}}
  test("injected carriage return rejected") {rejects{CookieCodec.parse("s=dummy\rAuthorization: bad",Platform.BILIBILI)}}
  test("YouTube import is independent of embedded-login exclusion") {
   check(!BrowserSessionPolicy.embeddedAllowed(Platform.YOUTUBE))
   check(CookieCodec.parse("SID=fixture",Platform.YOUTUBE).isNotEmpty())
  }
  println("CookieImportChecks: $passed cases passed. Actual core only; not Android persistence or live login.")
 }
}
