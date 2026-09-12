package com.luma.downloader.ui.optics

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.luma.downloader.data.*
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/** One mutable RuntimeShader per surface: sharing one would leak pressure/size between controls.
 * Sources form a DAG. No screen readback, per-frame bitmaps, or idle polling are used.
 *
 * 0.9.1 adds touch bulge, smooth-min fusion, edge softening and a gravity fill
 * light. All extra uniforms are quantized so rapid finger/sensor motion rebuilds
 * only the RenderEffect wrapper, never the AGSL program, and only when the
 * quantized bucket actually changes.
 */
@Composable
fun opticalBacking(
    source: GlassSource?, plan: GlassPlan, role: GlassRole, settings: UiSettings,
    radius: Dp, fallback: Color, tintColor: Color,
    pressure: () -> Float = { 0f }, lightPosition: () -> Offset = { Offset(.35f, .15f) },
    touchPoint: () -> Offset = { Offset(.5f, .5f) },
    touchStrength: () -> Float = { 0f },
    fuseCenter: () -> Offset = { Offset(.5f, .5f) },
    fuseStrength: () -> Float = { 0f },
    gravityPoint: () -> Offset = { Offset(.35f, .15f) },
    motion: () -> Float,
): Modifier {
    val destination = rememberGraphicsLayer()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    val hardware = LocalView.current.isHardwareAccelerated
    val livePressure by rememberUpdatedState(pressure)
    val liveLight by rememberUpdatedState(lightPosition)
    val liveTouch by rememberUpdatedState(touchPoint)
    val liveTouchStrength by rememberUpdatedState(touchStrength)
    val liveFuseCenter by rememberUpdatedState(fuseCenter)
    val liveFuseStrength by rememberUpdatedState(fuseStrength)
    val liveGravity by rememberUpdatedState(gravityPoint)
    val liveMotion by rememberUpdatedState(motion)
    val context = androidx.compose.ui.platform.LocalContext.current
    val powerSave = remember(context) {
        runCatching {
            (context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)
                ?.isPowerSaveMode == true
        }.getOrDefault(false)
    }
    return Modifier.onGloballyPositioned { coordinates = it; rootPosition = it.positionInRoot() }
        .drawWithCache {
            val width = size.width.coerceAtLeast(1f)
            val height = size.height.coerceAtLeast(1f)
            val lens = LensPolicy.plan(settings, role, Build.VERSION.SDK_INT, hardware, plan.enabled,
                min(width, height), density, powerSave)
            val corner = radius.toPx().coerceIn(0f, min(width, height) / 2f)
            val blur = plan.blur * density
            // Capture enough padding for the largest pressed refraction, not only resting depth.
            val pad = ceil(blur * 2f + lens.depth * 2f + min(width, height) * .04f + 2f)
                .toInt().coerceAtLeast(2)
            val recordingSize = IntSize((width.toInt() + pad * 2).coerceAtLeast(1),
                (height.toInt() + pad * 2).coerceAtLeast(1))
            val fallbackEffect = if (lens.tier != OpticalTier.SOLID && Build.VERSION.SDK_INT >= 31)
                runCatching { Api31.saturatedBlur(blur, lens.saturation) }.getOrNull() else null
            val dynamic = if (Build.VERSION.SDK_INT >= 33 && lens.tier == OpticalTier.LENS && !OpticalRuntime.shaderDisabled)
                runCatching { Api33.lens(width, height, pad.toFloat(), corner, lens, blur) }
                    .onFailure(OpticalRuntime::report).getOrNull() else null
            destination.renderEffect = fallbackEffect?.asComposeRenderEffect()
            var previousEffect: RenderEffect? = fallbackEffect
            val matrix = Matrix()
            onDrawBehind {
                @Suppress("UNUSED_VARIABLE") val frame = liveMotion() + rootPosition.x + rootPosition.y
                val effect = if (dynamic != null && !OpticalRuntime.shaderDisabled) {
                    runCatching {
                        dynamic.update(
                            livePressure().coerceIn(0f, 1f),
                            liveLight(),
                            liveTouch(),
                            liveTouchStrength().coerceIn(0f, 1f),
                            liveFuseCenter(),
                            liveFuseStrength().coerceIn(0f, 1f),
                            liveGravity(),
                        )
                    }
                        .onFailure(OpticalRuntime::report).getOrNull() ?: fallbackEffect
                } else fallbackEffect
                if (effect !== previousEffect) {
                    destination.renderEffect = effect?.asComposeRenderEffect()
                    previousEffect = effect
                }
                drawRect(fallback)
                val from = source?.coordinates
                val to = coordinates
                val revision = source?.revision ?: 0L
                if (lens.tier != OpticalTier.SOLID && source != null && revision > 0 && source.recorded &&
                    from?.isAttached == true && to?.isAttached == true && (effect != null || blur == 0f)) {
                    val origin = to.localPositionOf(from, Offset.Zero)
                    val axisX = to.localPositionOf(from, Offset(1f, 0f)) - origin
                    val axisY = to.localPositionOf(from, Offset(0f, 1f)) - origin
                    matrix.reset()
                    matrix[0, 0] = axisX.x; matrix[0, 1] = axisX.y
                    matrix[1, 0] = axisY.x; matrix[1, 1] = axisY.y
                    matrix[3, 0] = origin.x + pad; matrix[3, 1] = origin.y + pad
                    destination.record(size = recordingSize) {
                        drawRect(fallback)
                        withTransform({ transform(matrix) }) { drawLayer(source.layer) }
                    }
                    translate(-pad.toFloat(), -pad.toFloat()) { drawLayer(destination) }
                    drawRect(fallback, alpha = plan.tint)
                    if (tintColor != Color.Unspecified) drawRect(tintColor)
                }
            }
        }
}

private interface LiveLens {
    fun update(
        pressure: Float, light: Offset,
        touch: Offset, touchStrength: Float,
        fuseCenter: Offset, fuseStrength: Float,
        gravity: Offset,
    ): RenderEffect
}

@RequiresApi(31)
private object Api31 {
    fun saturatedBlur(radius: Float, saturation: Float): RenderEffect {
        val color = RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturation) }))
        val blur = blur(radius)
        return if (blur != null) RenderEffect.createChainEffect(color, blur) else color
    }
    fun blur(radius: Float): RenderEffect? = if (radius > 0f)
        RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) else null
}

@RequiresApi(33)
private object Api33 {
    fun lens(width: Float, height: Float, pad: Float, radius: Float, lens: LensPlan, blur: Float): LiveLens {
        val shader = RuntimeShader(LiquidShader.SOURCE)
        shader.setFloatUniform("glassSize", width, height)
        shader.setFloatUniform("padding", pad)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("depth", lens.depth)
        shader.setFloatUniform("bevel", lens.bevel)
        shader.setFloatUniform("dispersion", lens.dispersion)
        shader.setFloatUniform("saturation", lens.saturation)
        shader.setFloatUniform("edgeSoft", lens.edgeSoftPx)
        shader.setFloatUniform("touchPoint", 0.5f, 0.5f)
        shader.setFloatUniform("touchStrength", 0f)
        shader.setFloatUniform("fuseCenter", 0.5f, 0.5f)
        shader.setFloatUniform("fuseStrength", 0f)
        shader.setFloatUniform("gravityPoint", 0.35f, 0.15f)
        val blurred = Api31.blur(blur)
        return object : LiveLens {
            private var lastPressure = -1
            private var lastX = -1
            private var lastY = -1
            private var lastTx = -1
            private var lastTy = -1
            private var lastTouch = -1
            private var lastFx = -1
            private var lastFy = -1
            private var lastFuse = -1
            private var lastGx = -1
            private var lastGy = -1
            private var cached: RenderEffect? = null
            override fun update(
                pressure: Float, light: Offset,
                touch: Offset, touchStrength: Float,
                fuseCenter: Offset, fuseStrength: Float,
                gravity: Offset,
            ): RenderEffect {
                val p = ((if (pressure.isFinite()) pressure else 0f) * 200f).roundToInt().coerceIn(0, 200)
                val x = ((if (light.x.isFinite()) light.x else .35f) * 100f).roundToInt().coerceIn(0, 100)
                val y = ((if (light.y.isFinite()) light.y else .15f) * 100f).roundToInt().coerceIn(0, 100)
                val tx = ((if (touch.x.isFinite()) touch.x else .5f) * 60f).roundToInt().coerceIn(0, 60)
                val ty = ((if (touch.y.isFinite()) touch.y else .5f) * 60f).roundToInt().coerceIn(0, 60)
                val ts = ((if (touchStrength.isFinite()) touchStrength else 0f) * 60f).roundToInt().coerceIn(0, 60)
                val fx = ((if (fuseCenter.x.isFinite()) fuseCenter.x else .5f) * 60f).roundToInt().coerceIn(0, 60)
                val fy = ((if (fuseCenter.y.isFinite()) fuseCenter.y else .5f) * 60f).roundToInt().coerceIn(0, 60)
                val fs = ((if (fuseStrength.isFinite()) fuseStrength else 0f) * 60f).roundToInt().coerceIn(0, 60)
                val gx = ((if (gravity.x.isFinite()) gravity.x else .35f) * 60f).roundToInt().coerceIn(0, 60)
                val gy = ((if (gravity.y.isFinite()) gravity.y else .15f) * 60f).roundToInt().coerceIn(0, 60)
                if (cached == null || p != lastPressure || x != lastX || y != lastY ||
                    tx != lastTx || ty != lastTy || ts != lastTouch ||
                    fx != lastFx || fy != lastFy || fs != lastFuse ||
                    gx != lastGx || gy != lastGy
                ) {
                    shader.setFloatUniform("pressure", p / 200f)
                    shader.setFloatUniform("lightPoint", x / 100f, y / 100f)
                    shader.setFloatUniform("touchPoint", tx / 60f, ty / 60f)
                    shader.setFloatUniform("touchStrength", ts / 60f)
                    shader.setFloatUniform("fuseCenter", fx / 60f, fy / 60f)
                    shader.setFloatUniform("fuseStrength", fs / 60f)
                    shader.setFloatUniform("gravityPoint", gx / 60f, gy / 60f)
                    // Effects snapshot uniforms. Rebuild only the effect, not the AGSL program.
                    val refracted = RenderEffect.createRuntimeShaderEffect(shader, "scene")
                    cached = if (blurred != null) RenderEffect.createChainEffect(refracted, blurred) else refracted
                    lastPressure = p; lastX = x; lastY = y
                    lastTx = tx; lastTy = ty; lastTouch = ts
                    lastFx = fx; lastFy = fy; lastFuse = fs
                    lastGx = gx; lastGy = gy
                }
                return requireNotNull(cached)
            }
        }
    }
}

object OpticalRuntime {
    var shaderDisabled by mutableStateOf(false)
        private set
    private val reported = java.util.concurrent.atomic.AtomicBoolean(false)
    fun report(error: Throwable) {
        if (reported.compareAndSet(false, true)) {
            shaderDisabled = true
            android.util.Log.w("JingliuGlass", "Optical effect unavailable; using readable fallback. ${error.javaClass.simpleName}")
        }
    }
}
