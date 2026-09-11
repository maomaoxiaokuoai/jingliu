package com.luma.downloader.auth

import android.content.Context
import com.luma.core.*
import com.luma.downloader.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

class QrNotConfigured:Exception("未配置自有扫码授权服务；需要注册平台应用、密钥与 HTTPS 回调")
/** Server sees only this separate authorization request, never media URLs or downloaded cookies. */
class OfficialQrClient {
    val configured:Boolean get()=BuildConfig.QR_AUTH_BRIDGE_URL.isNotBlank()
    private fun request(path:String,body:Map<String,String>,token:CancelToken):Any? {
        if(!configured)throw QrNotConfigured()
        val base=QrProtocol.bridgeBase(BuildConfig.QR_AUTH_BRIDGE_URL)
        val c=URI(base+path).toURL().openConnection() as HttpURLConnection
        c.instanceFollowRedirects=false;c.connectTimeout=12000;c.readTimeout=20000;c.requestMethod="POST"
        c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Cache-Control","no-store")
        c.doOutput=true
        val listener=token.listen{c.disconnect()}
        try {
            token.check();val bytes=Json.stringify(body).toByteArray();c.setFixedLengthStreamingMode(bytes.size)
            c.outputStream.use{it.write(bytes)}
            if(c.responseCode==503)throw QrNotConfigured()
            require(c.responseCode==200){"授权服务响应失败（${c.responseCode}）"}
            val out=ByteArrayOutputStream();c.inputStream.use{input->val b=ByteArray(8192);while(true){token.check();val n=input.read(b);if(n<0)break;require(out.size()+n<=128*1024);out.write(b,0,n)}}
            return Json.parse(out.toString("UTF-8"))
        }finally{listener();c.disconnect()}
    }
    fun start(platform:Platform,token:CancelToken):RemoteQrTicket {
        require(platform in QrCapability.bridged)
        val nonce=QrProtocol.random();val v=request("/api/qr/start",mapOf("platform" to platform.name,"nonce" to nonce),token)
        return QrProtocol.ticket(v,platform,nonce,System.currentTimeMillis())
    }
    fun poll(t:RemoteQrTicket,token:CancelToken)=QrProtocol.result(request("/api/qr/poll",auth(t),token),t,System.currentTimeMillis())
    fun cancel(t:RemoteQrTicket,token:CancelToken){request("/api/qr/cancel",auth(t),token)}
    private fun auth(t:RemoteQrTicket)=mapOf("session" to t.session,"proof" to t.proof,"nonce" to t.nonce)
}
/** Only non-secret, verified public profile data. Never masquerades as a platform Cookie session. */
class AuthorizationProfiles(context:Context) {
    private val file=File(context.noBackupFilesDir,"authorized-profiles-v1.json")
    private val state=MutableStateFlow<List<AuthorizationProfile>>(emptyList())
    val flow=state.asStateFlow()
    private var loaded=false
    @Synchronized fun load(){if(loaded)return;if(file.exists())state.value=Json.parse(file.readText()).arr().map{AuthorizationProfile.from(it)};loaded=true}
    @Synchronized fun save(profile:AuthorizationProfile){load();val next=state.value.filterNot{it.platform==profile.platform}+profile;atomicText(file,Json.stringify(next.map{it.json()}));state.value=next}
    @Synchronized fun remove(p:Platform){load();val next=state.value.filterNot{it.platform==p};atomicText(file,Json.stringify(next.map{it.json()}));state.value=next}
}
