package com.luma.downloader

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.animation.ValueAnimator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.luma.downloader.data.*
import com.luma.downloader.ui.ShiliuApp
import com.luma.downloader.performance.FrameMonitor
import com.luma.downloader.performance.RefreshRateController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var frameMonitor:FrameMonitor?=null
    private val vm: LumaViewModel by viewModels()
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        receive(intent)
        setContent { ShiliuApp(vm, requestNotifications = { if(Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
    private fun receive(intent: Intent?) {
        if(intent?.action == Intent.ACTION_SEND) {
            // Sharing only fills the input. It never starts a request or download without a tap.
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.length < 32768 }?.let { vm.link = it; vm.page = AppPage.PARSE }
        }
        if(intent?.getBooleanExtra("openDownloads", false) == true) vm.page = AppPage.DOWNLOADS
    }
    override fun onResume(){super.onResume();RefreshRateController.apply(this)}
    override fun onStart(){super.onStart();vm.checkSavedAccounts();if(BuildConfig.FRAME_METRICS_ENABLED)frameMonitor=FrameMonitor(this)}
    override fun onStop() { frameMonitor?.close();frameMonitor=null;vm.flushSettings(); val graph=AppGraph.get(this);graph.scope.launch {graph.tasks.flush()}; super.onStop() }

}
