package com.luma.downloader.performance

import android.app.Activity
import android.os.Build
import android.hardware.display.DisplayManager
import android.view.Display
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import com.luma.downloader.data.FrameStats

/** Enabled only by the non-debuggable profile build. Runs off the UI thread, one summary on stop.
 * FIRST_DRAW_FRAME is excluded. Budget comes from the current display refresh rate.
 * FrameMetrics is a diagnostic, not a substitute for Perfetto/real input latency measurements. */
class FrameMonitor(private val activity:Activity) {
    private val thread=HandlerThread("jingliu-frames").apply{start()}
    private val handler=Handler(thread.looper)
    private val display=if(Build.VERSION.SDK_INT>=30)activity.display else activity.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
    private val hz=display?.refreshRate?.toDouble()?.takeIf{it in 24.0..240.0}?:60.0
    private val stats=FrameStats((1_000_000_000.0/hz).toLong())
    private val listener=Window.OnFrameMetricsAvailableListener{_,metrics,dropped ->
        if(metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME)==0L)stats.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION),dropped,(if(Build.VERSION.SDK_INT>=31)metrics.getMetric(FrameMetrics.DEADLINE).takeIf{it>0}else null) ?: (1_000_000_000.0/(display?.refreshRate?.toDouble()?.takeIf{it in 24.0..240.0}?:hz)).toLong())
    }
    private var closed=false
    init {activity.window.addOnFrameMetricsAvailableListener(listener,handler)}
    fun close() {
        if(closed)return
        closed=true
        activity.window.removeOnFrameMetricsAvailableListener(listener)
        handler.post {Log.i("JingliuFrames",stats.summary());thread.quitSafely()}
    }
}
