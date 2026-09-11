package com.luma.downloader

import android.content.Context
import androidx.work.*
import com.luma.downloader.auth.*
import com.luma.downloader.data.*
import com.luma.downloader.download.*
import com.luma.downloader.engine.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class AppGraph private constructor(context: Context) {
    private val app = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = SettingsStore(app, scope)
    val tasks = TaskStore(app)
    val vault = SessionVault(app)
    val runtime = MediaRuntime(app, vault)
    val localParser = com.luma.downloader.engine.local.LocalParseVideoClient(app, vault)
    val bili = BiliAuth(vault)
    val sessionChecks = SessionChecks(vault, scope) { account, token -> bili.verify(account, token) }
    val extractors = ExtractorRouter(vault, runtime, localParser, sessionChecks::beforeUse)
    val transfers = TransferRegistry(app, settings)
    val publisher = MediaPublisher(app, tasks)
    val queue by lazy { QueueController(app, tasks, transfers, settings) }
    @Synchronized fun load() { settings.load(); tasks.load(); vault.load() }
    fun startMaintenance() {
        Notifications.init(app)
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("session-maintenance", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SessionMaintenanceWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
    companion object {
        @Volatile private var instance: AppGraph? = null
        fun get(context: Context): AppGraph = instance ?: synchronized(this) { instance ?: AppGraph(context).also { instance = it } }
    }
}
