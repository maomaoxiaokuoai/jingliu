package com.luma.bridge

import com.luma.core.*
import java.util.concurrent.ConcurrentHashMap

/** Short-lived, single-use authorization sessions. No download Cookie or media endpoint exists here. */
class BridgeEngine(val publicBase:String,private val providers:Providers,private val clock:()->Long=System::currentTimeMillis) {
    private data class Session(val id:String,val proof:String,val nonce:String,val platform:Platform,val state:String,
        val callback:String,val qr:ProviderQr,val expires:Long,var status:AuthorizationPhase=AuthorizationPhase.WAITING,
        var profile:AuthorizationProfile?=null,var lastPoll:Long=0)
    private val sessions=ConcurrentHashMap<String,Session>()
    private val states=ConcurrentHashMap<String,String>()
    init {QrProtocol.bridgeBase(publicBase)}
    @Synchronized fun start(platform:Platform,nonce:String):Map<String,Any?> {
        QrProtocol.secret(nonce);require(platform in QrCapability.bridged)
        cleanup();require(sessions.size<256){"Too many authorization sessions"}
        if(!providers.configured(platform))throw ProviderUnavailable()
        val state=QrProtocol.random();val id=QrProtocol.random();val proof=QrProtocol.random();val callback="$publicBase/callback/${platform.name}"
        val qr=providers.begin(platform,state,callback)
        require(QrProtocol.displayAllowed(platform,qr.displayKind,qr.url))
        val session=Session(id,proof,nonce,platform,state,callback,qr,clock()+180_000)
        sessions[id]=session;states[state]=id
        return mapOf("platform" to platform.name,"nonce" to nonce,"session" to id,"proof" to proof,
            "display_kind" to qr.displayKind.name,"display_url" to qr.url,"expires_at" to session.expires)
    }
    fun poll(id:String,proof:String,nonce:String):Map<String,Any?> {
        val s=authenticated(id,proof,nonce)
        synchronized(s) {
            if(clock()>=s.expires && s.status!=AuthorizationPhase.CANCELLED){s.status=AuthorizationPhase.EXPIRED;s.profile=null}
            if(s.platform==Platform.TIKTOK && s.status in setOf(AuthorizationPhase.WAITING,AuthorizationPhase.SCANNED) && clock()-s.lastPoll>=1500) {
                s.lastPoll=clock()
                try {
                    val result=providers.pollTikTok(s.qr,s.state,s.callback)
                    s.status=result.phase
                    if(result.code.isNotBlank()){s.profile=providers.verify(s.platform,result.code,s.callback,clock());s.status=AuthorizationPhase.CONFIRMED;states.remove(s.state)}
                }catch(_:Exception){s.status=AuthorizationPhase.FAILED;s.profile=null;states.remove(s.state)}
            }
            return response(s)
        }
    }
    fun cancel(id:String,proof:String,nonce:String):Map<String,Any?> {
        val s=authenticated(id,proof,nonce)
        synchronized(s){s.status=AuthorizationPhase.CANCELLED;s.profile=null;states.remove(s.state);return response(s)}
    }
    fun callback(platform:Platform,params:Map<String,String>):Boolean {
        val state=params["state"].orEmpty();QrProtocol.secret(state)
        val id=states[state]?:return false;val s=sessions[id]?:return false
        synchronized(s) {
            if(s.platform!=platform||s.platform==Platform.TIKTOK||!QrProtocol.same(s.state,state)||clock()>=s.expires||s.status!=AuthorizationPhase.WAITING)return false
            states.remove(state) // consumes callback before exchange, so duplicate callbacks cannot exchange twice
            if(params["error"].orEmpty().isNotBlank()){s.status=AuthorizationPhase.DENIED;return false}
            val code=params["code"].orEmpty();if(code.isBlank()){s.status=AuthorizationPhase.FAILED;return false}
            s.status=AuthorizationPhase.VERIFYING
            return try {s.profile=providers.verify(platform,code,s.callback,clock());s.status=AuthorizationPhase.CONFIRMED;true}
                catch(_:Exception){s.profile=null;s.status=AuthorizationPhase.FAILED;false}
        }
    }
    private fun authenticated(id:String,proof:String,nonce:String):Session {
        QrProtocol.secret(id);QrProtocol.secret(proof);QrProtocol.secret(nonce)
        val s=sessions[id]?:error("Authorization session unavailable")
        require(QrProtocol.same(s.proof,proof)&&QrProtocol.same(s.nonce,nonce)){"Authorization session unavailable"}
        return s
    }
    private fun response(s:Session)=mapOf("session" to s.id,"platform" to s.platform.name,"nonce" to s.nonce,
        "status" to s.status.name,"profile" to if(s.status==AuthorizationPhase.CONFIRMED)s.profile?.json()else null)
    fun cleanup(){val now=clock();sessions.entries.removeIf{(_,s)->if(now-s.expires>60_000){states.remove(s.state);true}else false}}
}
