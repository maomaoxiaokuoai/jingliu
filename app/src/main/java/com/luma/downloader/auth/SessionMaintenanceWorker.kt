package com.luma.downloader.auth

import android.content.Context
import androidx.work.*
import com.luma.core.*
import com.luma.downloader.AppGraph
import com.luma.downloader.download.Notifications
import kotlinx.coroutines.*

class SessionMaintenanceWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = coroutineScope {
        val graph = AppGraph.get(applicationContext)
        val token = CancelToken()
        val watcher = launch(Dispatchers.Default) { try { awaitCancellation() } finally { token.cancel() } }
        try {
            withContext(Dispatchers.IO) {
                graph.load()
                if(!graph.settings.current().enabled("autoRefresh")) return@withContext Result.success()
                val original = graph.vault.get(Platform.BILIBILI) ?: return@withContext Result.success()
                try { graph.bili.maintain(token, autoRefresh = true); Result.success() }
                catch(e: LoginRejected) {
                    graph.vault.saveIfUnchanged(original, original.copy(rejected = true, checked = 0))
                    if(graph.settings.current().enabled("expireNotify")) Notifications.event(applicationContext, "B站登录已失效", "请在镜流账号页面重新登录", 901)
                    Result.success()
                }
            }
        } catch(e: CancellationException) { throw e }
        catch(_: Exception) { if(runAttemptCount < 3) Result.retry() else Result.failure() }
        finally { watcher.cancel() }
    }
}
