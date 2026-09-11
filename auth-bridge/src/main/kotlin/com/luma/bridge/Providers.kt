package com.luma.bridge

import com.luma.core.*
import java.net.HttpURLConnection
import java.net.URI
import java.io.ByteArrayOutputStream

class ProviderUnavailable:Exception("Platform credentials not configured")
data class Credentials(val key:String,val secret:String)
data class ProviderQr(val displayKind:QrDisplay,val url:String,val token:String="",val clientTicket:String="")
data class ProviderPoll(val phase:AuthorizationPhase,val code:String="")
fun interface Upstream {
    fun call(url:String,method:String,body:Map<String,String>,headers:Map<String,String>):Any?
}
class OfficialTransport:Upstream {
    override fun call(url:String,method:String,body:Map<String,String>,headers:Map<String,String>):Any? {
        val uri=URI(url)
        require(uri.scheme=="https"&&uri.host in setOf("open.douyin.com","open.kuaishou.com","open.tiktokapis.com") && uri.userInfo==null)
        val json=headers["Content-Type"]=="application/json"
        val encoded=if(json)Json.stringify(body)else body.entries.joinToString("&"){enc(it.key)+"="+enc(it.value)}
        val target=if(method=="GET"&&body.isNotEmpty())url+(if(uri.rawQuery==null)"?" else "&")+encoded else url
        val connection=URI(target).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects=false;connection.connectTimeout=12000;connection.readTimeout=18000;connection.requestMethod=method
        headers.forEach{(k,v)->require(!v.contains('\r')&&!v.contains('\n'));connection.setRequestProperty(k,v)}
        try {
            if(method=="POST") {
                connection.setRequestProperty("Content-Type",if(json)"application/json" else "application/x-www-form-urlencoded")
                val bytes=encoded.toByteArray();connection.doOutput=true;connection.setFixedLengthStreamingMode(bytes.size);connection.outputStream.use{it.write(bytes)}
            }
            require(connection.responseCode==200){"Upstream authorization HTTP failure"}
            val out=ByteArrayOutputStream();connection.inputStream.use{input->val b=ByteArray(8192);while(true){val n=input.read(b);if(n<0)break;require(out.size()+n<=1024*1024);out.write(b,0,n)}}
            return Json.parse(out.toString("UTF-8"))
        }finally{connection.disconnect()}
    }
}
/** Fixed official providers. A callback alone is never identity verification. */
class Providers(private val credentials:Map<Platform,Credentials>,private val transport:Upstream=OfficialTransport()) {
    fun configured(p:Platform)=credentials[p]?.let{it.key.isNotBlank()&&it.secret.isNotBlank()}==true
    private fun credentials(p:Platform)=credentials[p]?.takeIf{configured(p)}?:throw ProviderUnavailable()
    private fun post(url:String,values:Map<String,String>)=transport.call(url,"POST",values,emptyMap())
    fun begin(p:Platform,state:String,callback:String):ProviderQr {
        val c=credentials(p)
        val args=mapOf("response_type" to "code","redirect_uri" to callback,"state" to state)
        fun page(url:String,values:Map<String,String>)=ProviderQr(QrDisplay.OFFICIAL_PAGE,url+"?"+values.entries.joinToString("&"){enc(it.key)+"="+enc(it.value)})
        return when(p) {
            Platform.DOUYIN->page("https://open.douyin.com/platform/oauth/connect/",args+mapOf("client_key" to c.key,"scope" to "user_info","is_call_app" to "0"))
            Platform.KUAISHOU->page("https://open.kuaishou.com/oauth2/connect",args+mapOf("app_id" to c.key,"scope" to "user_info","size" to "260"))
            Platform.TIKTOK->{
                val v=post("https://open.tiktokapis.com/v2/oauth/get_qrcode/",mapOf("client_key" to c.key,"scope" to "user.info.basic","state" to state))
                require(v.s("error").isBlank()&&v.s("token").isNotBlank()){"TikTok QR rejected"}
                val raw=v.s("scan_qrcode_url");val u=URI(raw);require(u.scheme=="aweme"&&u.host=="authorize"&&u.userInfo==null)
                val ticket=QrProtocol.random();val values=QrProtocol.query(raw).toMutableMap();values["client_ticket"]=ticket
                val link="aweme://authorize?"+values.entries.joinToString("&"){enc(it.key)+"="+enc(it.value)}
                require(QrProtocol.displayAllowed(p,QrDisplay.CODE,link))
                ProviderQr(QrDisplay.CODE,link,v.s("token"),ticket)
            }
            else->throw ProviderUnavailable()
        }
    }
    fun pollTikTok(qr:ProviderQr,state:String,callback:String):ProviderPoll {
        val c=credentials(Platform.TIKTOK)
        val v=post("https://open.tiktokapis.com/v2/oauth/check_qrcode/",mapOf("client_key" to c.key,"client_secret" to c.secret,"token" to qr.token))
        require(v.s("error").isBlank()){"TikTok QR polling rejected"}
        val status=v.s("status")
        if(status in setOf("scanned","confirmed","utilised"))require(QrProtocol.same(v.s("client_ticket"),qr.clientTicket)){"TikTok client_ticket mismatch"}
        return when(status) {
            "new"->ProviderPoll(AuthorizationPhase.WAITING)
            "scanned"->ProviderPoll(AuthorizationPhase.SCANNED)
            "expired","utilised"->ProviderPoll(AuthorizationPhase.EXPIRED)
            "confirmed"->{
                val redirect=v.s("redirect_uri");val codeField=v.s("code")
                val params=if(redirect.isNotBlank())QrProtocol.query(redirect)else if(codeField.startsWith("https://"))QrProtocol.query(codeField)else emptyMap()
                val url=redirect.ifBlank{codeField.takeIf{it.startsWith("https://")}.orEmpty()}
                if(url.isNotBlank()){val u=URI(url);val configured=URI(callback);require(u.scheme==configured.scheme&&u.host==configured.host&&u.port==configured.port&&u.path==configured.path){"TikTok redirect mismatch"}}
                val actualState=v.s("state").ifBlank{params["state"].orEmpty()}
                require(QrProtocol.same(actualState,state)){"TikTok state mismatch"}
                val code=params["code"].orEmpty().ifBlank{codeField.takeUnless{it.startsWith("https://")}.orEmpty()}
                require(code.isNotBlank()&&code.length<=4096)
                ProviderPoll(AuthorizationPhase.VERIFYING,code)
            }
            else->ProviderPoll(AuthorizationPhase.FAILED)
        }
    }
    fun verify(p:Platform,code:String,callback:String,now:Long):AuthorizationProfile {
        require(code.isNotBlank()&&code.length<=4096);val c=credentials(p)
        return when(p) {
            Platform.DOUYIN->{
                val token=post("https://open.douyin.com/oauth/access_token/",mapOf("client_key" to c.key,"client_secret" to c.secret,"code" to code,"grant_type" to "authorization_code")).at("data")
                require(token.n("error_code")==0L&&token.s("access_token").isNotBlank()&&token.s("open_id").isNotBlank())
                val scopes=token.s("scope");require("user_info" in scopes.split(','))
                val info=transport.call("https://open.douyin.com/oauth/userinfo/","POST",mapOf("access_token" to token.s("access_token"),"open_id" to token.s("open_id")),mapOf("Content-Type" to "application/json","access-token" to token.s("access_token"))).at("data")
                require(info.n("error_code")==0L&&info.s("open_id")==token.s("open_id"))
                AuthorizationProfile(p,info.s("open_id"),info.s("nickname"),avatar(info.s("avatar")),scopes,now)
            }
            Platform.KUAISHOU->{
                val token=transport.call("https://open.kuaishou.com/oauth2/access_token","GET",mapOf("app_id" to c.key,"app_secret" to c.secret,"code" to code,"grant_type" to "authorization_code"),emptyMap())
                require(token.n("result")==1L&&token.s("access_token").isNotBlank()&&token.s("open_id").isNotBlank())
                val scopes=token.list("scopes").map{it.str()};require("user_info" in scopes)
                val response=transport.call("https://open.kuaishou.com/openapi/user_info","GET",mapOf("app_id" to c.key,"access_token" to token.s("access_token")),emptyMap())
                require(response.n("result")==1L&&response.at("user_info")!=null)
                val info=response.at("user_info")
                AuthorizationProfile(p,token.s("open_id"),info.s("name"),avatar(info.s("bigHead").ifBlank{info.s("head")}),scopes.joinToString(","),now)
            }
            Platform.TIKTOK->{
                val token=post("https://open.tiktokapis.com/v2/oauth/token/",mapOf("client_key" to c.key,"client_secret" to c.secret,"code" to code,"grant_type" to "authorization_code","redirect_uri" to callback))
                require(token.s("error").isBlank()&&token.s("access_token").isNotBlank()&&token.s("open_id").isNotBlank())
                val scopes=token.s("scope");require("user.info.basic" in scopes.split(','))
                val v=transport.call("https://open.tiktokapis.com/v2/user/info/?fields=open_id,display_name,avatar_url","GET",emptyMap(),mapOf("Authorization" to "Bearer ${token.s("access_token")}"))
                require(v.s("error","code")=="ok")
                val info=v.at("data","user");require(info.s("open_id")==token.s("open_id"))
                AuthorizationProfile(p,info.s("open_id"),info.s("display_name"),avatar(info.s("avatar_url")),scopes,now)
            }
            else->throw ProviderUnavailable()
        }.also {AuthorizationProfile.from(it.json())}
    }
    private fun avatar(raw:String):String = raw.takeIf{it.startsWith("https://")&&runCatching{UrlPolicy.checkTransport(it)}.isSuccess}.orEmpty()
}
