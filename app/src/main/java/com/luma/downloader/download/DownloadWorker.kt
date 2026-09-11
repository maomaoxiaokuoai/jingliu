package com.luma.downloader.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.luma.core.*
import com.luma.downloader.AppGraph
import com.luma.downloader.data.*
import com.luma.downloader.engine.EngineFailure
import kotlinx.coroutines.*
import java.io.File
import java.net.URI
import kotlinx.coroutines.sync.withLock

class DownloadWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    @Volatile private var failurePhase="START"
    private val execution: String get() = id.toString()
    override suspend fun doWork():Result=coroutineScope {
        val id=inputData.getString("taskId")?:return@coroutineScope Result.failure()
        val graph=AppGraph.get(applicationContext)
        val token=CancelToken()
        val cancellationWatcher=launch(Dispatchers.Default){try{awaitCancellation()}finally{token.cancel()}}
        try {
            val task=withContext(Dispatchers.IO){graph.load();graph.tasks.get(id)}?:return@coroutineScope Result.success()
            if(task.executionId != execution)return@coroutineScope Result.success()
            if(task.stage in setOf(TransferStage.COMPLETE,TransferStage.PAUSED,TransferStage.DELETING,TransferStage.DELETE_FAILED))return@coroutineScope Result.success()
            failurePhase="FOREGROUND"
            setForeground(Notifications.foreground(applicationContext,task))
            withContext(Dispatchers.IO) {
                graph.transfers.lock(id).withLock {
                    graph.transfers.register(id,token)
                    try {
                        failurePhase="NETWORK_WAIT";graph.transfers.acquire(id,token)
                        try {execute(graph,id,token)} catch(e:Exception) {
                            token.check()
                            // One bounded refresh of expiring media addresses, using this task's bound engine.
                            if(TransferRecoveryPolicy.refreshAddress(e))execute(graph,id,token)else throw e
                        }
                    }finally{graph.transfers.unregister(id)}
                }
            }
            Result.success()
        }catch(e:CancellationException){throw e}catch(e:Exception){
            withContext(Dispatchers.IO) {
                val current=graph.tasks.get(id)
                if(current!=null&&current.executionId==execution&&current.stage !in setOf(TransferStage.PAUSED,TransferStage.DELETING,TransferStage.DELETE_FAILED,TransferStage.COMPLETE)) {
                    val wifi=e is WifiRequired
                    val retry=wifi||TransferRecoveryPolicy.retryable(e)
                    val again=wifi||(retry&&runAttemptCount<graph.settings.current().number("retry").toInt())
                    val hasSession=runCatching {graph.vault.jar(Platform.BILIBILI).cookies().any {it.name=="SESSDATA"&&it.value.isNotBlank()&&it.matches(URI("https://api.bilibili.com/x/web-interface/nav"))}}.getOrNull()
                    val message=humanError(e)+"（阶段：$failurePhase）"
                    val details=DownloadFailure.detail(e,failurePhase,current.resolvedEngine.ifBlank{current.parserEngine},if(current.platform==Platform.BILIBILI)hasSession else null)
                    graph.tasks.update(id){if(it.executionId!=execution||it.stage in setOf(TransferStage.COMPLETE,TransferStage.PAUSED,TransferStage.DELETING,TransferStage.DELETE_FAILED))it
                        else it.copy(stage=if(again)TransferStage.QUEUED else TransferStage.FAILED,message=message,failureDetails=details,speed=0.0,attempt=runAttemptCount+1)}
                    if(again)return@withContext Result.retry()
                };Result.failure()
            }
        }finally{cancellationWatcher.cancel();token.cancel()}
    }
    private fun execute(g:AppGraph,id:String,token:CancelToken) {
        var task=g.tasks.get(id)?:return
        val dir=g.transfers.folder(id)
        if(task.executionId!=execution||task.stage in setOf(TransferStage.PAUSED,TransferStage.DELETING,TransferStage.DELETE_FAILED))throw TransferCancelled()
        if(task.finalUri.isNotBlank()&&g.publisher.exists(task.finalUri)) {
            token.check();g.tasks.update(id){if(it.executionId!=execution||it.stage in setOf(TransferStage.PAUSED,TransferStage.DELETING,TransferStage.DELETE_FAILED))it else it.copy(stage=TransferStage.COMPLETE,speed=0.0,message="",failureDetails="")};return
        }
        task.pendingUri.takeIf(String::isNotBlank)?.let{g.publisher.deleteUri(it);g.tasks.update(id){v->v.copy(pendingUri="")}}
        stage(g,id,TransferStage.RESOLVING)
        val media=g.extractors.parse(task.source,token,one=task.imageId.isBlank(),engine=task.parserEngine)
        token.check()
        g.tasks.update(id){if(it.executionId==execution)it.copy(resolvedEngine=media.extractor)else it}
        val format:Format?;val image:MediaEntry?
        if(task.imageId.isNotBlank()) {
            image=media.entries.firstOrNull{it.id==task.imageId&&it.image}?:error("图集已变更或所选图片已不存在，请重新解析")
            if(task.thumbnail.isNotBlank()&&!MediaRequestPolicy.sameImage(task.thumbnail,image.url))throw PlatformError("所选图片地址已变更，请重新解析确认")
            format=null
        } else {
            image=null
            format=when {
                task.formatId.startsWith("policy:")->BatchQuality.select(media,task.formatId.removePrefix("policy:"))
                else->media.formats.firstOrNull{it.id==task.formatId&&!it.drm}
            }?:throw PlatformError("所选画质不再可用；不会自动降为另一画质，请重新解析","QUALITY_UNAVAILABLE")
        }
        if(format!=null)g.tasks.update(id){if(it.executionId==execution)it.copy(formatLabel=format.display,total=format.size(media.duration).bytes)else it}
        var lastNotify=0L
        fun progress(s:TransferStage):(ByteProgress)->Unit={p->
            token.check();g.transfers.ensureNetwork(token)
            g.tasks.progress(id,s,p.downloaded,p.total,p.speed,execution)
            val now=System.currentTimeMillis();if(now-lastNotify>1000){g.tasks.get(id)?.let{Notifications.update(applicationContext,it)};lastNotify=now}
        }
        val native=CandidateTransfer(ResumableTransfer(Http(g.vault.jar(media.platform),media.platform.domestic)))
        val source:File
        val extension:String
        if(image!=null) {
            stage(g,id,TransferStage.IMAGE)
            source=File(dir,"image.source");native.download(image.url,image.backupUrls,source,"${media.id}:${image.id}",token,MediaRequestPolicy.headers(image.url,media.url,media.headers),progress=progress(TransferStage.IMAGE))
            extension=imageExtension(source)
        } else {
            val f=requireNotNull(format)
            stage(g,id,if(f.video)TransferStage.VIDEO else TransferStage.AUDIO)
            val video=File(dir,"video.source")
            if(media.extractor==ParseEngine.PARSE_VIDEO_PY.id&&f.id=="pv-manifest")g.runtime.downloadManifest(media,f,video,token,progress(TransferStage.VIDEO))
            else if(f.engine)g.runtime.downloadStream(media.url,f.id.substringBefore('+'),video,token,progress(if(f.video)TransferStage.VIDEO else TransferStage.AUDIO))
            else native.download(f.url,f.backupUrls,video,"${media.id}:${f.id}:main",token,MediaRequestPolicy.headers(f.url,media.url,media.headers),progress=progress(if(f.video)TransferStage.VIDEO else TransferStage.AUDIO))
            if(f.audioUrl.isNotBlank()) {
                stage(g,id,TransferStage.AUDIO)
                val audio=File(dir,"audio.source")
                if(f.engine)g.runtime.downloadStream(media.url,f.id.substringAfter('+'),audio,token,progress(TransferStage.AUDIO))
                else native.download(f.audioUrl,f.audioBackupUrls,audio,"${media.id}:${f.id}:audio",token,MediaRequestPolicy.headers(f.audioUrl,media.url,media.headers),progress=progress(TransferStage.AUDIO))
                extension=if(f.engine)"mkv" else "mp4";source=File(dir,"merged.$extension")
                stage(g,id,TransferStage.MERGING);token.check();g.runtime.mux(video,audio,source,token)
            } else if(media.extractor==ParseEngine.PARSE_VIDEO_PY.id&&f.id=="pv-manifest") {
                extension="mkv";source=File(dir,"manifest.mkv")
                stage(g,id,TransferStage.MERGING);g.runtime.remuxManifest(video,source,token)
            } else {source=video;extension=f.extension}
        }
        token.check();task=g.tasks.get(id)?:throw TransferCancelled()
        if(task.executionId!=execution||task.stage in setOf(TransferStage.PAUSED,TransferStage.DELETING,TransferStage.DELETE_FAILED)){token.cancel();token.check()}
        val label=when(g.settings.current().text("filename")){"author-title"->"${media.author.name.ifBlank{"作者"}} — ${task.title}";"platform-title"->"${media.platform.title} — ${task.title}";else->task.title}
        val fileName="${safeName(label)}-${id.take(8)}.${extension.filter{it.isLetterOrDigit()}.take(8)}"
        stage(g,id,TransferStage.SAVING)
        g.publisher.publish(id,source,fileName,token)
        token.check();val done=g.tasks.update(id){old->if(old.executionId!=execution||old.stage in setOf(TransferStage.DELETING,TransferStage.PAUSED,TransferStage.DELETE_FAILED))old else old.copy(stage=TransferStage.COMPLETE,message="",failureDetails="",speed=0.0)}
        // Original streams are only removed AFTER successful export; all failures remain resumable.
        if(done?.stage==TransferStage.COMPLETE&&done.executionId==execution)dir.deleteRecursively()
        if(done?.stage==TransferStage.COMPLETE&&g.settings.current().enabled("notifyDone"))Notifications.event(applicationContext,"下载完成",done.title,Notifications.id(id)+1)
    }
    private fun stage(g:AppGraph,id:String,s:TransferStage){val t=g.tasks.get(id)?:throw TransferCancelled();if(t.executionId!=execution||t.stage in setOf(TransferStage.PAUSED,TransferStage.DELETING,TransferStage.DELETE_FAILED))throw TransferCancelled();failurePhase=s.name;g.tasks.progress(id,s,0,null,0.0,execution);g.tasks.get(id)?.let{Notifications.update(applicationContext,it)}}
    private fun imageExtension(file:File):String {
        val b=ByteArray(16);file.inputStream().use{it.read(b)}
        return when {b[0]==0xff.toByte()&&b[1]==0xd8.toByte()->"jpg";b[0]==0x89.toByte()&&b[1]=='P'.code.toByte()->"png";String(b,0,4)=="RIFF"&&String(b,8,4)=="WEBP"->"webp";String(b,0,3)=="GIF"->"gif";String(b,4,4)=="ftyp"->"avif";else->throw PlatformError("图片内容无法识别，拒绝保存验证页或未知文件")}
    }
}
fun humanError(e:Exception):String=when(e){
    is EngineFailure,is WifiRequired -> e.message.orEmpty().take(250)
    else -> DownloadFailure.summary(e)
}
