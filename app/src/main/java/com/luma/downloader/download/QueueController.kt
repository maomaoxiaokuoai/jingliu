package com.luma.downloader.download

import android.content.Context
import androidx.work.*
import com.luma.downloader.data.*
import java.util.concurrent.TimeUnit

/** Call off the main thread. Persistent WorkManager jobs own every real transfer. */
class QueueController(context: Context, private val tasks: TaskStore, private val registry: TransferRegistry, private val settings: SettingsStore) {
    private val work = WorkManager.getInstance(context)
    private fun name(id: String) = "download-$id"
    fun add(items: List<DownloadTask>) { if(items.isEmpty()) return; tasks.add(items); items.forEach { schedule(it.id) } }
    @Synchronized fun schedule(id: String) {
        if(work.getWorkInfosForUniqueWork(name(id)).get().any { !it.state.isFinished }) return
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf("taskId" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag("shiliu-download").build()
        tasks.update(id) { it.copy(executionId = request.id.toString()) }
        work.enqueueUniqueWork(name(id), ExistingWorkPolicy.KEEP, request).result.get()
    }
    @Synchronized fun pause(id: String) {
        val old = tasks.get(id) ?: return
        if(old.stage !in setOf(TransferStage.QUEUED, TransferStage.RESOLVING, TransferStage.VIDEO, TransferStage.AUDIO, TransferStage.IMAGE, TransferStage.MERGING, TransferStage.SAVING)) return
        tasks.update(id) { it.copy(stage = TransferStage.PAUSED, speed = 0.0, message = "") }
        registry.cancel(id)
        work.cancelUniqueWork(name(id)).result.get()
    }
    @Synchronized fun resume(id: String) {
        val old = tasks.get(id) ?: return
        if(old.stage !in setOf(TransferStage.PAUSED, TransferStage.FAILED)) return
        work.cancelUniqueWork(name(id)).result.get()
        tasks.update(id) { it.copy(stage = TransferStage.QUEUED, message = "", speed = 0.0, attempt = 0) }
        schedule(id)
    }
    /** The UI must ask for confirmation before invoking this operation. */
    @Synchronized fun delete(id: String) {
        if(tasks.get(id) == null) return
        tasks.update(id) { it.copy(stage = TransferStage.DELETING, speed = 0.0, message = "") }
        registry.cancel(id)
        work.cancelUniqueWork(name(id)).result.get()
        scheduleDeletion(id)
    }
    private fun scheduleDeletion(id: String) {
        work.enqueueUniqueWork("delete-$id", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DeleteWorker>().setInputData(workDataOf("taskId" to id))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS).build()).result.get()
    }
    fun recover() {
        tasks.all().forEach { t -> when {
            t.stage == TransferStage.DELETING -> scheduleDeletion(t.id)
            t.stage == TransferStage.QUEUED || t.stage.isActive -> schedule(t.id)
            else -> Unit
        } }
    }
    fun rescheduleQueued() {
        // Active workers read Wi-Fi / concurrency preferences on their next progress sample.
        tasks.all().filter { it.stage == TransferStage.QUEUED }.forEach { schedule(it.id) }
    }
}
