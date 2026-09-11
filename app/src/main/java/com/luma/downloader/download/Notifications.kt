package com.luma.downloader.download

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import com.luma.downloader.*
import com.luma.downloader.data.*
import com.luma.core.sizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Notifications {
    private const val DOWNLOADS = "downloads"
    private const val EVENTS = "events"
    fun init(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(DOWNLOADS, "下载任务", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(EVENTS, "完成与账号状态", NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun id(value: String): Int = (value.hashCode() and 0x3fffffff) + 1000
    private fun launch(context: Context): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java).putExtra("openDownloads", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun notification(context: Context, task: DownloadTask): Notification {
        init(context)
        val pause = PendingIntent.getBroadcast(context, id(task.id), Intent(context, TaskActionReceiver::class.java)
            .setAction("com.luma.downloader.PAUSE").putExtra("taskId", task.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, DOWNLOADS).setSmallIcon(R.drawable.ic_download)
            .setContentTitle(task.title).setContentText("${task.stage.title} · ${sizeText(task.downloaded)} / ${sizeText(task.total)}")
            .setOnlyAlertOnce(true).setOngoing(true).setContentIntent(launch(context))
            .setProgress(100, ((task.fraction ?: 0f) * 100).toInt(), task.fraction == null)
            .addAction(R.drawable.ic_download, "暂停", pause).build()
    }
    fun foreground(context: Context, task: DownloadTask): ForegroundInfo {
        val n = notification(context, task)
        return if(Build.VERSION.SDK_INT >= 29) ForegroundInfo(id(task.id), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(id(task.id), n)
    }
    private fun allowed(context: Context): Boolean = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    fun update(context: Context, task: DownloadTask) {
        if(allowed(context)) context.getSystemService(NotificationManager::class.java).notify(id(task.id), notification(context, task))
    }
    fun cancel(context: Context, taskId: String) { context.getSystemService(NotificationManager::class.java).cancel(id(taskId)) }
    fun event(context: Context, title: String, text: String, id: Int) {
        init(context)
        if(allowed(context)) context.getSystemService(NotificationManager::class.java).notify(id,
            NotificationCompat.Builder(context, EVENTS).setSmallIcon(R.drawable.ic_download).setContentTitle(title)
                .setContentText(text).setContentIntent(launch(context)).setAutoCancel(true).build())
    }
}
class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action != "com.luma.downloader.PAUSE") return
        val id = intent.getStringExtra("taskId") ?: return
        val pending = goAsync(); val graph = AppGraph.get(context)
        graph.scope.launch(Dispatchers.IO) { try { graph.load(); graph.queue.pause(id) } finally { pending.finish() } }
    }
}
