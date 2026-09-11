package com.luma.downloader.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.luma.downloader.AppGraph
import com.luma.downloader.data.TransferStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

class DeleteWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result=withContext(Dispatchers.IO) {
        val id=inputData.getString("taskId")?:return@withContext Result.failure()
        val g=AppGraph.get(applicationContext);g.load()
        try {
            g.transfers.lock(id).withLock {
                val task=g.tasks.get(id)?:return@withLock
                if(task.stage !in setOf(TransferStage.DELETING,TransferStage.DELETE_FAILED))return@withLock
                listOf(task.finalUri,task.pendingUri).filter(String::isNotBlank).distinct().forEach{g.publisher.deleteUri(it)}
                val directory=g.transfers.folder(id)
                check(!directory.exists()||directory.deleteRecursively()){ "本地片段未删除完毕" }
                g.tasks.remove(id)
            };Result.success()
        }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){g.tasks.update(id){it.copy(stage=TransferStage.DELETE_FAILED,message="文件尚未全部删除。请保留此记录并重试，或检查系统文件权限。",speed=0.0)}
            if(runAttemptCount<3)Result.retry()else Result.failure()}
    }
}
