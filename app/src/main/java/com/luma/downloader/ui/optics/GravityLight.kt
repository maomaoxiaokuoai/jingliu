package com.luma.downloader.ui.optics

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.luma.downloader.data.LiquidFusionMath
import com.luma.downloader.data.MotionMode
import com.luma.downloader.ui.LocalAppearance
import com.luma.downloader.ui.LocalUiSettings

/**
 * Shared gravity-driven highlight.
 *
 * One sensor listener per window (provided in ShiliuApp/GlassWindow), read only
 * inside draw scopes via [LocalGravityLight] so sensor ticks invalidate glass
 * layers instead of recomposing every label. Stops in background, when
 * reduce-motion is on, when the gravity toggle is off, or in power-save.
 *
 * Kyant highlight idea (light follows interaction/pose) + QWEA ambient/specular
 * model, reimplemented with low-pass filtering, portrait/landscape remap and a
 * flat-device static fallback.
 */
val LocalGravityLight = compositionLocalOf<() -> Offset> { { Offset(0.35f, 0.15f) } }

@Composable
fun GravityLightProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = com.luma.downloader.ui.LocalUiSettings.current
    val mode = LocalAppearance.current.motion
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var gravity by remember { mutableStateOf(Offset(0.35f, 0.15f)) }
    var filteredX by remember { mutableStateOf(0f) }
    var filteredY by remember { mutableStateOf(0f) }
    var filteredZ by remember { mutableStateOf(9.81f) }
    var foreground by remember { mutableStateOf(true) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            foreground = event != Lifecycle.Event.ON_PAUSE &&
                event != Lifecycle.Event.ON_STOP &&
                event != Lifecycle.Event.ON_DESTROY
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val enabled = settings.enabled("gravityLight") &&
        mode != MotionMode.OFF &&
        !settings.enabled("reduceMotion") &&
        settings.text("performance") != "efficient"

    DisposableEffect(context, enabled, foreground) {
        if (!enabled || !foreground) return@DisposableEffect onDispose {}
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) return@DisposableEffect onDispose {}
        var lastEmit = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val ax = event.values.getOrNull(0) ?: return
                val ay = event.values.getOrNull(1) ?: return
                val az = event.values.getOrNull(2) ?: return
                if (!ax.isFinite() || !ay.isFinite() || !az.isFinite()) return
                // Low-pass: ~0.18 keeps tilt smooth without per-frame jitter.
                filteredX += (ax - filteredX) * 0.18f
                filteredY += (ay - filteredY) * 0.18f
                filteredZ += (az - filteredZ) * 0.18f
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastEmit < 66) return
                lastEmit = now
                val portrait = context.resources.configuration.orientation !=
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val (lx, ly) = LiquidFusionMath.gravityToLight(filteredX, filteredY, filteredZ, portrait)
                gravity = Offset(lx, ly)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }

    // Power-save forces the static fallback without tearing down composition.
    val powerSave = remember(context) {
        runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
        }.getOrDefault(false)
    }
    val snapshot = if (enabled && !powerSave) gravity else Offset(0.35f, 0.15f)
    CompositionLocalProvider(LocalGravityLight provides { snapshot }) {
        content()
    }
}

/** Power-save check for the optical tier: avoids a Context read inside draw caches. */
@Composable
fun rememberPowerSave(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
        }.getOrDefault(false)
    }
}
