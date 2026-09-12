package com.luma.downloader.ui.optics

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/** Backdrop -> full-height track scene -> thumb. The expanded thumb must not sample only an 8dp
 * strip surrounded by white. This replay is recorded before, and never includes, the thumb. */
@Composable
fun Modifier.replayGlassScene(source: GlassSource?, fallback: Color): Modifier {
    var destination by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val motion = LocalGlassMotion.current
    return onGloballyPositioned { destination = it }.drawWithCache {
        val matrix = Matrix()
        onDrawBehind {
            @Suppress("UNUSED_VARIABLE") val frame = motion()
            drawRect(fallback)
            val revision = source?.revision ?: 0L
            val from = source?.coordinates
            val to = destination
            if (revision > 0L && source != null && source.recorded && from?.isAttached == true && to?.isAttached == true) {
                val origin = to.localPositionOf(from, Offset.Zero)
                val x = to.localPositionOf(from, Offset(1f, 0f)) - origin
                val y = to.localPositionOf(from, Offset(0f, 1f)) - origin
                matrix.reset()
                matrix[0, 0] = x.x; matrix[0, 1] = x.y
                matrix[1, 0] = y.x; matrix[1, 1] = y.y
                matrix[3, 0] = origin.x; matrix[3, 1] = origin.y
                withTransform({ transform(matrix) }) { drawLayer(source.layer) }
            }
        }
    }
}
