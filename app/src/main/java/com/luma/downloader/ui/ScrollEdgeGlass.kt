package com.luma.downloader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 0.9.1 scroll-edge progressive fade.
 *
 * True per-pixel blur masks would need an extra full-width offscreen pass per
 * frame; instead this draws bounded gradient scrims that suggest the top and
 * bottom dissolving into glass. It reuses the shared backdrop palette, adds no
 * Bitmap/PixelCopy work, and is fully disabled by the scrollFade toggle,
 * reduce-transparency, or solid material.
 */
fun Modifier.scrollEdgeFade(
    enabled: Boolean,
    topAlpha: () -> Float,
    bottomAlpha: () -> Float,
    base: Color,
): Modifier = if (!enabled) this else drawWithContent {
    drawContent()
    val top = topAlpha().coerceIn(0f, 1f)
    if (top > 0.01f) {
        val h = (size.height * 0.11f).coerceAtMost(96.dp.toPx())
        drawRect(
            Brush.verticalGradient(
                listOf(base.copy(alpha = 0.55f * top), Color.Transparent),
                startY = 0f, endY = h,
            ),
        )
    }
    val bottom = bottomAlpha().coerceIn(0f, 1f)
    if (bottom > 0.01f) {
        val h = (size.height * 0.13f).coerceAtMost(120.dp.toPx())
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, base.copy(alpha = 0.62f * bottom)),
                startY = size.height - h, endY = size.height,
            ),
        )
    }
}

@Composable
fun rememberScrollEdgeAlphas(state: LazyListState): Pair<() -> Float, () -> Float> {
    val top by remember {
        derivedStateOf {
            if (state.firstVisibleItemIndex > 0) 1f
            else (state.firstVisibleItemScrollOffset / 120f).coerceIn(0f, 1f)
        }
    }
    val bottom by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) 0f
            else {
                val last = info.visibleItemsInfo.lastOrNull()
                if (last == null || last.index >= total - 1) 0f else 1f
            }
        }
    }
    // Capture snapshots as lambdas read in draw so scrolling redraws only the fade.
    return remember { Pair({ top }, { bottom }) }
}

/** Thin top/bottom scrims for fixed Column screens without a LazyListState. */
@Composable
fun ScrollEdgeOverlay(top: Float, bottom: Float, modifier: Modifier = Modifier) {
    val palette = LocalLumaPalette.current
    Box(modifier) {
        if (top > 0.01f) Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(28.dp)
                .drawWithContent {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(palette.background.copy(alpha = 0.5f * top), Color.Transparent),
                        )
                    )
                },
        )
        if (bottom > 0.01f) Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(32.dp)
                .drawWithContent {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, palette.background.copy(alpha = 0.55f * bottom)),
                        )
                    )
                },
        )
    }
}
