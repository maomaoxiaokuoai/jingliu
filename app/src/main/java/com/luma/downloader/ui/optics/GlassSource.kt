package com.luma.downloader.ui.optics

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize

/** One source owns one RenderNode. Sources form a DAG: background -> controls -> page -> overlays.
 * Never capture a surface into the same source that it is reading. No Bitmap readback or timer.
 */
@Stable
class GlassSource internal constructor(val layer: GraphicsLayer) {
    internal var coordinates: LayoutCoordinates? by mutableStateOf(null)
    internal var revision by mutableLongStateOf(0L)
    @Volatile internal var recorded = false
    val isReady:Boolean get()=recorded
    val blurEnabled: Boolean get() = true
}

@Composable fun rememberGlassSource(): GlassSource {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassSource(layer) }
}

fun Modifier.captureGlass(source: GlassSource): Modifier = this
    .onGloballyPositioned { source.coordinates = it }
    .drawWithContent {
        if(size.width > 0 && size.height > 0) {
            source.layer.record(size = IntSize(size.width.toInt(), size.height.toInt())) {
                this@drawWithContent.drawContent()
            }
            source.recorded = true
            // Readers observe only this value. The source never observes its own revision.
            androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation {
                source.revision = source.revision + 1L
            }
            drawLayer(source.layer)
        }
    }

/** A moving ancestor can change sampling coordinates without changing pixel content.
 * Read this only inside drawing so retargeted navigation does not recompose every label.
 */
val LocalGlassMotion=compositionLocalOf<()->Float>{{0f}}
