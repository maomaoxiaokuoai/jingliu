package com.luma.downloader.engine.local

import android.app.Service
import android.content.Intent
import android.os.*
import androidx.annotation.Keep
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.luma.core.Json
import com.luma.core.LocalParserProtocol
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** A bound same-UID component. It never starts an HTTP listener or downloads executable code. */
class LocalParseVideoService:Service() {
    private val requests=ConcurrentHashMap<String,RequestCancel>()
    private val inbox by lazy { Messenger(Handler(Looper.getMainLooper()) { message ->
        when(message.what) {
            RUN -> accept(message)
            CANCEL -> message.data.getString("id")?.let{requests[it]?.cancel()}
        }
        true
    }) }
    override fun onBind(intent:Intent?):IBinder = inbox.binder
    private fun accept(message:Message) {
        // exported=false is the first boundary. Also check caller UID for accidental future changes.
        if(message.sendingUid!=applicationInfo.uid)return
        val reply=message.replyTo?:return
        val id=message.data.getString("id").orEmpty()
        val body=message.data.getString("request").orEmpty()
        if(!id.matches(Regex("[a-fA-F0-9-]{36}"))||body.toByteArray(Charsets.UTF_8).size>LocalParserProtocol.REQUEST_BYTES) {
            send(reply,id,error("LOCAL_INPUT"));return
        }
        val cancel=RequestCancel()
        if(requests.putIfAbsent(id,cancel)!=null){send(reply,id,error("LOCAL_INPUT"));return}
        try {workers.execute {
            try {
                if(cancel.isCancelled()) {send(reply,id,error("LOCAL_CANCELLED"));return@execute}
                // Only this private process loads Chaquopy. The UI and existing yt-dlp runtime
                // keep their own address space. No initialization in Application.onCreate.
                if(!Python.isStarted())Python.start(AndroidPlatform(this))
                if(cancel.isCancelled()){send(reply,id,error("LOCAL_CANCELLED"));return@execute}
                val output=Python.getInstance().getModule("jingliu_local")
                    .callAttr("execute",body,cancel).toString()
                send(reply,id,if(cancel.isCancelled())error("LOCAL_CANCELLED")else output)
            }catch(_:Throwable) {
                // PyException may include a signed URL. Never log its message or traceback.
                send(reply,id,error(if(cancel.isCancelled())"LOCAL_CANCELLED" else "LOCAL_STARTUP"))
            }finally{requests.remove(id,cancel)}
        }}catch(_:java.util.concurrent.RejectedExecutionException){requests.remove(id,cancel);send(reply,id,error("LOCAL_QUEUE_FULL"))}
    }
    private fun send(reply:Messenger,id:String,body:String) {
        val checked=if(body.toByteArray(Charsets.UTF_8).size<=LocalParserProtocol.RESULT_BYTES)body else error("LOCAL_RESPONSE_TOO_LARGE")
        try {reply.send(Message.obtain(null,RESULT).apply{data=Bundle().apply{putString("id",id);putString("result",checked)}})}catch(_:RemoteException){}
    }
    private fun error(code:String)=Json.stringify(mapOf("ok" to false,"error" to code))
    override fun onUnbind(intent:Intent?):Boolean {
        requests.values.forEach{it.cancel()}
        return super.onUnbind(intent)
    }
    override fun onDestroy(){requests.values.forEach{it.cancel()};super.onDestroy()}
    @Keep class RequestCancel {
        private val stopped=AtomicBoolean(false)
        @Keep fun isCancelled():Boolean=stopped.get()
        fun cancel(){stopped.set(true)}
    }
    companion object {
        const val RUN=1;const val CANCEL=2;const val RESULT=3
        // Serial for the lifetime of this PROCESS, also across unbind/rebind Service instances.
        private val workers=ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,ArrayBlockingQueue(4)).apply {
            allowCoreThreadTimeOut(true)
        }
    }
}
