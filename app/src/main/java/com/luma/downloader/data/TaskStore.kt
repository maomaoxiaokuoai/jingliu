package com.luma.downloader.data

import android.content.Context
import com.luma.core.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TransferStage(val title: String) {
    QUEUED("排队 / 等待网络"), RESOLVING("读取真实媒体地址"), VIDEO("下载视频轨道"), AUDIO("下载音频轨道"),
    IMAGE("下载图片"), MERGING("无损合并音视频"), SAVING("保存至本机"), COMPLETE("已完成"),
    PAUSED("已暂停"), FAILED("下载失败"), DELETING("正在停止并删除文件"), DELETE_FAILED("删除未完成")
}
val TransferStage.isActive: Boolean get() = this in setOf(TransferStage.RESOLVING, TransferStage.VIDEO, TransferStage.AUDIO, TransferStage.IMAGE, TransferStage.MERGING, TransferStage.SAVING)
data class DownloadTask(
    val id: String = UUID.randomUUID().toString(), val source: String, val title: String,
    val platform: Platform, val formatId: String = "policy:best", val formatLabel: String = "最高可用画质",
    val imageId: String = "", val thumbnail: String = "", val stage: TransferStage = TransferStage.QUEUED,
    val downloaded: Long = 0, val total: Long? = null, val speed: Double = 0.0, val attempt: Int = 0,
    val message: String = "", val finalUri: String = "", val pendingUri: String = "", val fileName: String = "",
    val mime: String = "", val executionId: String = "", val createdAt: Long = System.currentTimeMillis(),
    val parserEngine: String = "auto", val resolvedEngine: String = "", val failureDetails: String = "",
) {
    val fraction: Float? get() = if(total != null && total > 0) (downloaded.toDouble() / total).toFloat().coerceIn(0f, 1f) else null
    fun json(): Map<String, Any?> = mapOf("id" to id, "source" to source, "title" to title, "platform" to platform.name,
        "formatId" to formatId, "formatLabel" to formatLabel, "imageId" to imageId, "thumbnail" to thumbnail,
        "stage" to stage.name, "downloaded" to downloaded, "total" to total, "speed" to speed, "attempt" to attempt,
        "message" to message, "finalUri" to finalUri, "pendingUri" to pendingUri, "fileName" to fileName, "mime" to mime, "createdAt" to createdAt, "executionId" to executionId, "parserEngine" to parserEngine, "resolvedEngine" to resolvedEngine, "failureDetails" to failureDetails)
    companion object {
        fun from(v: Any?): DownloadTask {
            val id = v.s("id"); UUID.fromString(id) // Never allow a path segment as a task id.
            return DownloadTask(id = id, source = v.s("source"), title = v.s("title"), platform = Platform.valueOf(v.s("platform")),
                formatId = v.s("formatId"), formatLabel = v.s("formatLabel"), imageId = v.s("imageId"), thumbnail = v.s("thumbnail"),
                stage = TransferStage.valueOf(v.s("stage")), downloaded = v.n("downloaded") ?: 0, total = v.n("total"),
                speed = 0.0, attempt = v.n("attempt")?.toInt() ?: 0, message = v.s("message"), finalUri = v.s("finalUri"),
                pendingUri = v.s("pendingUri"), fileName = v.s("fileName"), mime = v.s("mime"), executionId = v.s("executionId"), createdAt = v.n("createdAt") ?: 0, parserEngine = ParseEngine.from(v.s("parserEngine")).id, resolvedEngine = v.s("resolvedEngine"), failureDetails = v.s("failureDetails"))
        }
    }
}
/** Atomic persistent ledger. Do not delete its rows unless file deletion has succeeded. */
class TaskStore(context: Context) {
    private val file = File(context.noBackupFilesDir, "tasks-v7.json")
    private val mutable = MutableStateFlow<List<DownloadTask>>(emptyList())
    val flow = mutable.asStateFlow()
    private var loaded = false
    private var dirty = false
    private var lastWrite = 0L
    @Synchronized fun load() {
        if(loaded) return
        if(file.exists()) mutable.value = Json.parse(file.readText()).list("tasks").map { DownloadTask.from(it) }
        loaded = true
    }
    @Synchronized fun get(id: String): DownloadTask? = mutable.value.firstOrNull { it.id == id }
    @Synchronized fun all(): List<DownloadTask> = mutable.value
    @Synchronized fun add(items: List<DownloadTask>) {
        require(loaded); val existing = mutable.value.map { it.id }.toSet()
        require(items.map { it.id }.toSet().size == items.size && items.none { it.id in existing })
        save((items + mutable.value).sortedByDescending { it.createdAt })
    }
    @Synchronized fun update(id: String, transform: (DownloadTask) -> DownloadTask): DownloadTask? {
        val old = get(id) ?: return null
        val next = transform(old); require(next.id == old.id)
        save(mutable.value.map { if(it.id == id) next else it }); return next
    }
    @Synchronized fun progress(id: String, stage: TransferStage, bytes: Long, total: Long?, speed: Double, executionId: String? = null) {
        val old=get(id)?:return
        if((executionId!=null&&executionId!=old.executionId)||old.stage in setOf(TransferStage.PAUSED,TransferStage.FAILED,TransferStage.DELETING,TransferStage.DELETE_FAILED,TransferStage.COMPLETE))return
        val next=old.copy(stage=stage,downloaded=bytes.coerceAtLeast(0),total=total?.takeIf{it>0},speed=speed.takeIf{it.isFinite()&&it>=0}?:0.0,message="")
        if(next==old)return
        val items=mutable.value.map{if(it.id==id)next else it}
        if(old.stage!=stage || System.nanoTime()-lastWrite>=1_000_000_000L)save(items)
        else {mutable.value=items;dirty=true}
    }
    @Synchronized fun flush(){if(loaded&&dirty)save(mutable.value)}
    @Synchronized fun remove(id: String) { save(mutable.value.filterNot { it.id == id }) }
    private fun save(items: List<DownloadTask>) {
        if(items == mutable.value&&!dirty) return
        atomicText(file, Json.stringify(mapOf("schema" to 1, "tasks" to items.map { it.json() })))
        mutable.value = items;dirty=false;lastWrite=System.nanoTime()
    }
}
