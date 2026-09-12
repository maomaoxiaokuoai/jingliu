package com.luma.downloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luma.downloader.data.GlassRole
import com.luma.downloader.data.UiSettings

/**
 * 0.9.1 live material preview.
 *
 * Uses the real shared [GlassSurface]/optical pipeline (not a mock gradient),
 * so Clear / Regular / Frost / tinted medium look exactly like production glass
 * on this device, theme and GPU. Text is drawn above the lens and never warped.
 */
@Composable
fun GlassMaterialPreview(modifier: Modifier = Modifier) {
    val base = LocalUiSettings.current
    val palette = LocalLumaPalette.current
    // Preview each material with the user's other glass tuning intact.
    val clear = remember(base) { base.withValue("material", "glass") }
    val regular = remember(base) { base.withValue("material", "regular") }
    val frost = remember(base) { base.withValue("material", "frost") }
    var pressed by remember { mutableStateOf(false) }
    var point by remember { mutableStateOf(Offset(0.62f, 0.3f)) }
    var pressure by remember { mutableFloatStateOf(0.55f) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("实时材质预览 · 与正式界面同一渲染管线", color = palette.muted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MaterialCell("清透", clear, Modifier.weight(1f))
            MaterialCell("标准", regular, Modifier.weight(1f))
            MaterialCell("磨砂", frost, Modifier.weight(1f))
        }
        // Interactive lens: press feedback is visible here without leaving settings.
        CompositionLocalProvider(LocalUiSettings provides base) {
            GlassSurface(
                Modifier.fillMaxWidth().height(54.dp),
                radius = 27.dp, role = GlassRole.BUTTON,
                tint = palette.accent.copy(alpha = 0.10f),
                pressProgress = { pressure },
                lightPosition = { point },
                touchPoint = { point },
                touchStrength = { pressure * 0.8f },
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("按压提亮 · 高光跟随", color = palette.ink, fontSize = 13.sp)
                }
            }
        }
        // Keep the preview honest: it reflects the live toggles, not a frozen bitmap.
        Text(
            "当前：折射 ${base.number("refraction").toInt()}dp · 色散 ${base.number("dispersion").toInt()}% · " +
                "柔化 ${base.number("edgeSoft").toInt()}% · " +
                if (base.enabled("gravityLight")) "重力高光开" else "重力高光关",
            color = palette.muted, fontSize = 11.sp,
        )
    }
}

@Composable
private fun MaterialCell(label: String, settings: UiSettings, modifier: Modifier = Modifier) {
    val palette = LocalLumaPalette.current
    CompositionLocalProvider(LocalUiSettings provides settings) {
        GlassSurface(modifier.height(64.dp), radius = 18.dp, role = GlassRole.GROUP) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = palette.ink, fontSize = 13.sp)
                    Text(
                        when (label) {
                            "清透" -> "Clear"
                            "标准" -> "Regular"
                            else -> "Frost"
                        },
                        color = palette.muted, fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

/** Small tinted-medium swatch used in button settings. */
@Composable
fun TintedMediumPreview(modifier: Modifier = Modifier) {
    val palette = LocalLumaPalette.current
    GlassSurface(modifier.size(64.dp, 40.dp), radius = 20.dp, role = GlassRole.BUTTON,
        tint = palette.accent.copy(alpha = 0.12f)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Aa", color = palette.accent, fontSize = 15.sp)
        }
    }
}
