package com.luma.bridge

import com.luma.core.*
import java.net.URI
import java.net.HttpURLConnection
import java.util.concurrent.ExecutorService

/** Documented-response fixtures only; no production endpoint, account or client secret is used. */
object BridgeChecks {
 private data class Request(val url:String,val method:String,val body:Map<String,String>,val headers:Map<String,String>)
 private class Fixtures:Upstream {
  val requests=mutableListOf<Request>()
  var badIdentity=false
  var denyToken=false
  var ttStatus="new"
  var ttTicket=""
  var ttState=""
  var ttMismatch=false
  override fun call(url:String,method:String,body:Map<String,String>,headers:Map<String,String>):Any? {
   requests+=Request(url,method,body,headers)
   return when {
    url=="https://open.douyin.com/oauth/access_token/"->mapOf("data" to mapOf("error_code" to if(denyToken)123 else 0,"access_token" to "fixture-dy-token","open_id" to "dy-id","scope" to "user_info"))
    url=="https://open.douyin.com/oauth/userinfo/"->mapOf("data" to mapOf("error_code" to 0,"open_id" to if(badIdentity)"other-id" else "dy-id","nickname" to "Test identity","avatar" to "https://cdn.example.com/dy.png"))
    url=="https://open.kuaishou.com/oauth2/access_token"->mapOf("result" to if(denyToken)0 else 1,"access_token" to "fixture-ks-token","open_id" to "ks-id","scopes" to listOf("user_info"))
    url=="https://open.kuaishou.com/openapi/user_info"->mapOf("result" to 1,"user_info" to mapOf("name" to "Test identity","head" to "http://cdn.example.com/ks.png"))
    url=="https://open.tiktokapis.com/v2/oauth/get_qrcode/"->{ttState=body.getValue("state");mapOf("scan_qrcode_url" to "aweme://authorize?authType=100&client_key=fixture-public-key&client_ticket=tobefilled","token" to "fixture-qr-token")}
    url=="https://open.tiktokapis.com/v2/oauth/check_qrcode/"->mapOf("status" to ttStatus,"state" to ttState,"client_ticket" to if(ttMismatch)"wrong" else ttTicket,"code" to if(ttStatus=="confirmed")"fixture-code" else "")
    url=="https://open.tiktokapis.com/v2/oauth/token/"->mapOf("open_id" to "tt-id","access_token" to "fixture-tt-token","scope" to "user.info.basic")
    url.startsWith("https://open.tiktokapis.com/v2/user/info/")->mapOf("error" to mapOf("code" to "ok"),"data" to mapOf("user" to mapOf("open_id" to if(badIdentity)"wrong" else "tt-id","display_name" to "Test identity","avatar_url" to "https://cdn.example.com/tt.png")))
    else->error("Unexpected upstream test call: $url")
   }
  }
 }
 private class Harness {
  var now=1_800_000_000_000L
  val mock=Fixtures()
  val credentials=QrCapability.bridged.associateWith{Credentials("fixture-public-key","fixture-secret-only-in-test")}
  val engine=BridgeEngine("https://auth.example.com",Providers(credentials,mock)){now}
  fun start(p:Platform):RemoteQrTicket {
   val nonce=QrProtocol.random();val t=QrProtocol.ticket(engine.start(p,nonce),p,nonce,now)
   if(p==Platform.TIKTOK)mock.ttTicket=QrProtocol.query(t.url).getValue("client_ticket")
   return t
  }
  fun state(t:RemoteQrTicket)=QrProtocol.query(t.url).getValue("state")
  fun poll(t:RemoteQrTicket)=QrProtocol.result(engine.poll(t.session,t.proof,t.nonce),t,now)
  fun callback(t:RemoteQrTicket,params:Map<String,String> = mapOf("state" to state(t),"code" to "fixture-code"))=engine.callback(t.platform,params)
 }
 @JvmStatic fun main(args:Array<String>){run()}
 fun run(){var n=0
  fun test(name:String,body:()->Unit){body();n++;println("PASS auth bridge: $name")}
  fun rejects(body:()->Unit){check(runCatching(body).isFailure){"Expected rejection"}}
  test("missing platform credentials produce no authorization URL"){
   val engine=BridgeEngine("https://auth.example.com",Providers(emptyMap()))
   check(runCatching{engine.start(Platform.DOUYIN,QrProtocol.random())}.exceptionOrNull() is ProviderUnavailable)
  }
  test("unsupported platforms do not generate QR attempts"){
   val h=Harness();rejects{h.engine.start(Platform.YOUTUBE,QrProtocol.random())};check(h.mock.requests.isEmpty())
  }
  test("Douyin uses official QR webpage and registered callback"){
   val h=Harness();val t=h.start(Platform.DOUYIN);val q=QrProtocol.query(t.url)
   check(t.kind==QrDisplay.OFFICIAL_PAGE&&URI(t.url).path=="/platform/oauth/connect/")
   check(q["redirect_uri"]=="https://auth.example.com/callback/DOUYIN"&&q["scope"]=="user_info")
   check(q["state"]!=t.nonce&&q["state"]!=t.proof&&q["state"]!=t.session);check(h.mock.requests.isEmpty())
  }
  test("Kuaishou uses connect QR page, not custom QR of authorize URL"){
   val h=Harness();val t=h.start(Platform.KUAISHOU);check(URI(t.url).path=="/oauth2/connect");check(QrProtocol.query(t.url)["app_id"]=="fixture-public-key")
  }
  test("pending responses carry no identity or secrets"){
   val h=Harness();val t=h.start(Platform.DOUYIN);val response=h.engine.poll(t.session,t.proof,t.nonce)
   check(response["profile"]==null);val s=Json.stringify(response)
   check(!s.contains("fixture-secret")&&!s.contains(t.proof)&&!s.contains("access_token"))
  }
  test("valid callback exchanges token and then verifies identity"){
   val h=Harness();val t=h.start(Platform.DOUYIN);check(h.callback(t));val p=h.poll(t).profile!!
   check(p.id=="dy-id"&&p.platform==Platform.DOUYIN);check(h.mock.requests.size==2)
   check(h.mock.requests[0].body["client_secret"]=="fixture-secret-only-in-test")
   check(h.mock.requests[1].headers["access-token"]=="fixture-dy-token")
  }
  test("callback replay cannot exchange an authorization code twice"){
   val h=Harness();val t=h.start(Platform.DOUYIN);check(h.callback(t));check(!h.callback(t));check(h.mock.requests.size==2)
  }
  test("wrong callback state cannot authorize"){
   val h=Harness();val t=h.start(Platform.DOUYIN);check(!h.callback(t,mapOf("state" to QrProtocol.random(),"code" to "x")));check(h.mock.requests.isEmpty())
  }
  test("right state with wrong platform does not consume the correct attempt"){
   val h=Harness();val t=h.start(Platform.DOUYIN);check(!h.engine.callback(Platform.KUAISHOU,mapOf("state" to h.state(t),"code" to "fixture")));check(h.callback(t))
  }
  test("poll requires both private proof and initiating nonce"){
   val h=Harness();val t=h.start(Platform.DOUYIN)
   rejects{h.engine.poll(t.session,QrProtocol.random(),t.nonce)};rejects{h.engine.poll(t.session,t.proof,QrProtocol.random())}
  }
  test("denial is terminal without token request"){
   val h=Harness();val t=h.start(Platform.KUAISHOU);check(!h.callback(t,mapOf("state" to h.state(t),"error" to "access_denied")))
   check(h.poll(t).phase==AuthorizationPhase.DENIED&&h.mock.requests.isEmpty())
  }
  test("cancel blocks a delayed callback and preserves no new profile"){
   val h=Harness();val t=h.start(Platform.DOUYIN);h.engine.cancel(t.session,t.proof,t.nonce);check(!h.callback(t))
   check(h.poll(t).phase==AuthorizationPhase.CANCELLED&&h.mock.requests.isEmpty())
  }
  test("expired callback does not exchange token"){
   val h=Harness();val t=h.start(Platform.DOUYIN);h.now=t.expiresAt+1;check(!h.callback(t));check(h.poll(t).phase==AuthorizationPhase.EXPIRED&&h.mock.requests.isEmpty())
  }
  test("expiry cleanup invalidates session capability"){
   val h=Harness();val t=h.start(Platform.DOUYIN);h.now=t.expiresAt+60_001;h.engine.cleanup();rejects{h.engine.poll(t.session,t.proof,t.nonce)}
  }
  test("bad token response does not produce a verified identity"){
   val h=Harness();h.mock.denyToken=true;val t=h.start(Platform.DOUYIN);check(!h.callback(t));check(h.poll(t).phase==AuthorizationPhase.FAILED);check(h.mock.requests.size==1)
  }
  test("identity response must match token open ID"){
   val h=Harness();h.mock.badIdentity=true;val t=h.start(Platform.DOUYIN);check(!h.callback(t));check(h.poll(t).profile==null)
  }
  test("Kuaishou identity flow uses documented token parameters and user-info"){
   val h=Harness();val t=h.start(Platform.KUAISHOU);check(h.callback(t));check(h.poll(t).profile!!.id=="ks-id")
   check(h.mock.requests[0].method=="GET"&&h.mock.requests[0].body.containsKey("app_secret"))
   check(h.poll(t).profile!!.avatar.isEmpty()) // HTTP-only avatar is not silently rewritten to HTTPS.
  }
  test("TikTok QR uses upstream native URL with random bound client_ticket"){
   val h=Harness();val t=h.start(Platform.TIKTOK);check(t.kind==QrDisplay.CODE);check(h.mock.ttTicket!="tobefilled")
   QrProtocol.secret(h.mock.ttTicket);check(h.mock.requests.single().url.endsWith("/v2/oauth/get_qrcode/"))
  }
  test("TikTok new status throttles repeated polling"){
   val h=Harness();val t=h.start(Platform.TIKTOK);check(h.poll(t).phase==AuthorizationPhase.WAITING);val count=h.mock.requests.size
   h.poll(t);check(h.mock.requests.size==count)
  }
  test("TikTok scanned requires ticket equality"){
   val h=Harness();val t=h.start(Platform.TIKTOK);h.mock.ttStatus="scanned";check(h.poll(t).phase==AuthorizationPhase.SCANNED)
   val x=Harness();val xt=x.start(Platform.TIKTOK);x.mock.ttStatus="scanned";x.mock.ttMismatch=true;check(x.poll(xt).phase==AuthorizationPhase.FAILED)
  }
  test("TikTok confirmed ticket then token then ID verification"){
   val h=Harness();val t=h.start(Platform.TIKTOK);h.mock.ttStatus="confirmed";val status=h.poll(t)
   check(status.phase==AuthorizationPhase.CONFIRMED&&status.profile!!.id=="tt-id")
   check(h.mock.requests.any{it.url.endsWith("/v2/oauth/token/")&&it.body["redirect_uri"]=="https://auth.example.com/callback/TIKTOK"})
  }
  test("TikTok wrong state cannot exchange code"){
   val h=Harness();val t=h.start(Platform.TIKTOK);h.mock.ttStatus="confirmed";h.mock.ttState="wrong"
   check(h.poll(t).phase==AuthorizationPhase.FAILED);check(h.mock.requests.none{it.url.endsWith("/v2/oauth/token/")})
  }
  test("TikTok expired and used QR are not success"){
   listOf("expired","utilised").forEach{status->val h=Harness();val t=h.start(Platform.TIKTOK);h.mock.ttStatus=status;check(h.poll(t).phase==AuthorizationPhase.EXPIRED)}
  }
  test("TikTok profile mismatch is not accepted"){
   val h=Harness();val t=h.start(Platform.TIKTOK);h.mock.ttStatus="confirmed";h.mock.badIdentity=true;check(h.poll(t).profile==null)
  }
  test("actual local HTTP returns JSON, prevents caching and returns 503 when not configured"){
   val server=createBridgeServer(BridgeEngine("https://auth.example.com",Providers(emptyMap())),0);server.start()
   try {
    val c=URI("http://127.0.0.1:${server.address.port}/api/qr/start").toURL().openConnection() as HttpURLConnection
    c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json")
    c.outputStream.use{it.write(Json.stringify(mapOf("platform" to "DOUYIN","nonce" to QrProtocol.random())).toByteArray())}
    check(c.responseCode==503&&c.getHeaderField("Cache-Control")=="no-store")
    check(Json.parse(c.errorStream.use{it.readBytes().toString(Charsets.UTF_8)}).s("error")=="platform_not_configured");c.disconnect()
   }finally{server.stop(0);(server.executor as ExecutorService).shutdownNow()}
  }
  test("actual local HTTP rejects malformed callback without reflecting its contents"){
   val h=Harness();val server=createBridgeServer(h.engine,0);server.start()
   try {
    val c=URI("http://127.0.0.1:${server.address.port}/callback/DOUYIN?state=malformed&code=test-reflection").toURL().openConnection() as HttpURLConnection
    check(c.responseCode==400);val result=c.errorStream.use{it.readBytes().toString(Charsets.UTF_8)}
    check(!result.contains("test-reflection")&&h.mock.requests.isEmpty());c.disconnect()
   }finally{server.stop(0);(server.executor as ExecutorService).shutdownNow()}
  }
  println("Authorization bridge checks: $n passed, 0 failed; upstream fixtures + localhost HTTP, not live platform login")
 }
}
