package com.luma.downloader.engine.local

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import com.luma.core.*
import com.luma.downloader.auth.SessionVault
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Blocking API, only called from existing Dispatchers.IO paths, never a Compose frame callback. */
class LocalParseVideoClient(context:Context,private val vault:SessionVault) {
    private val app=context.applicationContext
    private val callbackThread by lazy { HandlerThread("jingliu-local-parser-replies").apply{start()} }
    fun parse(source:String,token:CancelToken):Media {
        val request=LocalParserProtocol.request(source,vault.get(Platform.of(source))?.cookies.orEmpty())
        val response=LocalParserProtocol.response(call(request,token))
        val media=ParseVideoResponse.decode(mapOf("data" to response.at("data")),source,local=true)
        val userAgent=response.s("headers","User-Agent").takeIf {
            it.isNotBlank()&&it.length<=2048&&!it.contains('\r')&&!it.contains('\n')
        }
        return media.copy(headers=media.headers+("Referer" to response.s("source").ifBlank{source})+
            (userAgent?.let{mapOf("User-Agent" to it)}?:emptyMap()))
    }
    fun selfTest(token:CancelToken):String {
        val result=LocalParserProtocol.response(call(LocalParserProtocol.request("",emptyList(),selfTest=true),token))
        val count=result.n("parser_count")?:throw LocalParserProtocol.failure("LOCAL_SCHEMA")
        return "本地组件已载入 · parse-video-py ${result.s("version")} · $count 个上游解析器（未测试平台联网）"
    }
    private fun call(request:String,token:CancelToken):String {
        check(Looper.myLooper()!=Looper.getMainLooper()){ "本地解析不得在主线程执行" }
        token.check()
        val id=UUID.randomUUID().toString()
        val connected=CompletableFuture<Messenger>()
        val result=CompletableFuture<String>()
        val remote=AtomicReference<Messenger?>()
        val closed=AtomicBoolean(false)
        val replies=Messenger(Handler(callbackThread.looper){ message ->
            if(!closed.get()&&message.what==LocalParseVideoService.RESULT&&message.data.getString("id")==id)
                result.complete(message.data.getString("result").orEmpty())
            true
        })
        val connection=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName,binder:IBinder) {
                if(!closed.get())connected.complete(Messenger(binder))
            }
            override fun onServiceDisconnected(name:ComponentName){
                val e=LocalParserProtocol.failure("LOCAL_DISCONNECTED")
                connected.completeExceptionally(e);result.completeExceptionally(e)
            }
            override fun onBindingDied(name:ComponentName){onServiceDisconnected(name)}
            override fun onNullBinding(name:ComponentName){onServiceDisconnected(name)}
        }
        fun cancelRemote() {
            try {remote.get()?.send(Message.obtain(null,LocalParseVideoService.CANCEL).apply{data=Bundle().apply{putString("id",id)}})}catch(_:RemoteException){}
        }
        val unregister=token.listen{cancelRemote()}
        var bound=false
        val deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(LocalParserProtocol.WAIT_MILLIS)
        fun <T> waitFor(future:CompletableFuture<T>):T {
            while(true) {
                token.check()
                if(System.nanoTime()>deadline)throw LocalParserProtocol.failure("LOCAL_TIMEOUT")
                try{return future.get(120,TimeUnit.MILLISECONDS)}catch(_:TimeoutException){}
                catch(e:java.util.concurrent.ExecutionException){throw (e.cause as? Exception?:LocalParserProtocol.failure("LOCAL_PROCESS_DIED"))}
                catch(_:InterruptedException){Thread.currentThread().interrupt();throw TransferCancelled()}
            }
        }
        try {
            bound=app.bindService(Intent(app,LocalParseVideoService::class.java),connection,Context.BIND_AUTO_CREATE)
            if(!bound)throw LocalParserProtocol.failure("LOCAL_BIND_FAILED")
            val service=waitFor(connected);remote.set(service);token.check()
            service.send(Message.obtain(null,LocalParseVideoService.RUN).apply {
                replyTo=replies
                data=Bundle().apply{putString("id",id);putString("request",request)}
            })
            return waitFor(result).also{token.check()}
        }finally {
            closed.set(true);cancelRemote();unregister()
            if(bound)runCatching{app.unbindService(connection)}
        }
    }
}
