package com.luma.core

/** Test-only synthetic protocol fixtures. No account/token from a real user. */
object QrProtocolChecks {
 @JvmStatic fun main(args:Array<String>){run()}
 fun run(){var passed=0
  fun test(name:String,body:()->Unit){body();passed++;println("PASS QR protocol: $name")}
  fun rejects(body:()->Unit){check(runCatching(body).isFailure){"Expected rejection"}}
  val now=1_800_000_000_000L;val nonce=QrProtocol.random()
  fun ticket(p:Platform=Platform.DOUYIN):Map<String,Any?> = mapOf("platform" to p.name,"nonce" to nonce,"session" to QrProtocol.random(),"proof" to QrProtocol.random(),"display_kind" to "OFFICIAL_PAGE","display_url" to "https://open.douyin.com/platform/oauth/connect/?state=test-only","expires_at" to now+180_000)
  test("capability flags do not invent unsupported QR flows"){
   check(Platform.entries.filter(QrCapability::supported).toSet()==setOf(Platform.BILIBILI,Platform.DOUYIN,Platform.KUAISHOU,Platform.TIKTOK))
   check(QrCapability.description(Platform.TIKTOK,false).contains("需配置"))
  }
  test("cryptographic capabilities have independent URL-safe values"){
   val values=(1..100).map{QrProtocol.random()};check(values.toSet().size==100);values.forEach(QrProtocol::secret)
  }
  test("capabilities reject empty, short, injected and oversized input"){
   listOf("","x","a".repeat(129),"a".repeat(31)+"&", "a".repeat(32)+"\n").forEach{v->rejects{QrProtocol.secret(v)}}
  }
  test("bridge origin cannot contain credentials, callback path or HTTP"){
   check(QrProtocol.bridgeBase("https://auth.example.com/")=="https://auth.example.com")
   listOf("http://auth.example.com","https://u:p@auth.example.com","https://auth.example.com/path","https://auth.example.com?secret=x","https://auth.example.com/#x").forEach{u->rejects{QrProtocol.bridgeBase(u)}}
  }
  test("query parsing rejects repeated and encoded repeated state"){
   rejects{QrProtocol.query("https://example.com?state=a&state=b")};rejects{QrProtocol.query("https://example.com?state=a&%73tate=b")}
   check(QrProtocol.query("https://example.com?code=a%2Bb%3D&state=x")["code"]=="a+b=")
  }
  test("only platform-owned QR pages accepted"){
   check(QrProtocol.displayAllowed(Platform.KUAISHOU,QrDisplay.OFFICIAL_PAGE,"https://open.kuaishou.com/oauth2/connect?app_id=fixture"))
   listOf("https://open.douyin.com.evil.example/platform/oauth/connect/","https://open.douyin.com/other","http://open.douyin.com/platform/oauth/connect/","https://open.douyin.com:8443/platform/oauth/connect/").forEach{check(!QrProtocol.displayAllowed(Platform.DOUYIN,QrDisplay.OFFICIAL_PAGE,it))}
  }
  test("TikTok QR requires returned native route and an inserted ticket"){
   check(QrProtocol.displayAllowed(Platform.TIKTOK,QrDisplay.CODE,"aweme://authorize?client_ticket=fixture"))
   listOf("https://www.tiktok.com","aweme://other?client_ticket=x","aweme://authorize?client_ticket=").forEach{check(!QrProtocol.displayAllowed(Platform.TIKTOK,QrDisplay.CODE,it))}
   check(!QrProtocol.displayAllowed(Platform.DOUYIN,QrDisplay.CODE,"aweme://authorize?client_ticket=x"))
  }
  test("valid ticket deserializes exact platform nonce and expiry"){
   val raw=ticket();val t=QrProtocol.ticket(raw,Platform.DOUYIN,nonce,now)
   check(t.session==raw["session"]&&t.expiresAt==now+180_000&&t.kind==QrDisplay.OFFICIAL_PAGE)
  }
  test("ticket cannot switch platform or nonce"){
   rejects{QrProtocol.ticket(ticket()+mapOf("platform" to "TIKTOK"),Platform.DOUYIN,nonce,now)}
   rejects{QrProtocol.ticket(ticket(),Platform.DOUYIN,QrProtocol.random(),now)}
  }
  test("ticket proof and TTL enforced"){
   rejects{QrProtocol.ticket(ticket()+mapOf("proof" to "short"),Platform.DOUYIN,nonce,now)}
   rejects{QrProtocol.ticket(ticket()+mapOf("expires_at" to now),Platform.DOUYIN,nonce,now)}
   rejects{QrProtocol.ticket(ticket()+mapOf("expires_at" to now+900_000),Platform.DOUYIN,nonce,now)}
  }
  val t=QrProtocol.ticket(ticket(),Platform.DOUYIN,nonce,now)
  val status=mapOf("session" to t.session,"nonce" to t.nonce,"platform" to t.platform.name,"status" to "WAITING")
  val profile=AuthorizationProfile(Platform.DOUYIN,"open-fixture","测试资料","https://cdn.example.com/avatar.png","user_info",now)
  test("pending response never creates a profile even if one is attached"){
   check(QrProtocol.result(status+mapOf("profile" to profile.json()),t,now).profile==null)
  }
  test("confirmed status requires complete verified profile"){
   val s=QrProtocol.result(status+mapOf("status" to "CONFIRMED","profile" to profile.json()),t,now)
   check(s.profile==profile);rejects{QrProtocol.result(status+mapOf("status" to "CONFIRMED"),t,now)}
  }
  test("confirmed response cannot belong to another session"){
   rejects{QrProtocol.result(status+mapOf("session" to QrProtocol.random()),t,now)}
   rejects{QrProtocol.result(status+mapOf("nonce" to QrProtocol.random()),t,now)}
   rejects{QrProtocol.result(status+mapOf("platform" to "KUAISHOU"),t,now)}
  }
  test("profile platform must match current QR"){
   rejects{QrProtocol.result(status+mapOf("status" to "CONFIRMED","profile" to profile.copy(platform=Platform.TIKTOK).json()),t,now)}
  }
  test("late successful response expires rather than authenticates"){
   check(QrProtocol.result(status+mapOf("status" to "CONFIRMED","profile" to profile.json()),t,t.expiresAt).phase==AuthorizationPhase.EXPIRED)
  }
  test("unsafe avatar and empty identity rejected"){
   rejects{AuthorizationProfile.from(profile.copy(id="").json())};rejects{AuthorizationProfile.from(profile.copy(avatar="http://cdn.example.com/x").json())}
  }
  test("serialized identity contains no token or Cookie fields"){
   val keys=profile.json().keys;check(keys==setOf("platform","id","name","avatar","scopes","verifiedAt"))
  }
  test("cancel gate prevents late account mutation"){
   val g=QrAttemptGate();val generation=g.start();g.cancel();var saved=false
   check(g.commit(generation){saved=true}==null&&!saved)
  }
  test("refresh invalidates previous generation before writing"){
   val g=QrAttemptGate();val a=g.start();val b=g.start();var saved=0
   check(g.commit(a){saved++}==null);check(g.commit(b){saved++;true}==true);check(saved==1)
  }
  test("account commit exactly once; failed persistence remains retryable"){
   val g=QrAttemptGate();val a=g.start();rejects{g.commit<Unit>(a){error("test disk failure")}}
   check(g.accepts(a));check(g.commit(a){true}==true);check(g.commit<Boolean>(a){error("duplicate")}==null)
  }
  println("QR protocol checks: $passed passed, 0 failed; fixtures only, no live authorization")
 }
}
