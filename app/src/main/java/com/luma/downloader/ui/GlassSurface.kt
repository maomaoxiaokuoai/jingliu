package com.luma.downloader.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luma.downloader.data.*
import com.luma.downloader.ui.optics.*
import kotlin.math.max

// Preserve the existing call sites. These sources are GPU layers, not Haze renderers.
val LocalContentHaze = staticCompositionLocalOf<GlassSource?> { null }
val LocalBackdropHaze = staticCompositionLocalOf<GlassSource?> { null }
val LocalControlHaze = staticCompositionLocalOf<GlassSource?> { null }
val LocalOverlayGlass = staticCompositionLocalOf<GlassSource?> { null }
val GlassRenderingKey = SemanticsPropertyKey<String>("GlassRendering")
var SemanticsPropertyReceiver.glassRendering by GlassRenderingKey

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier, radius: Dp = 32.dp, role: GlassRole = GlassRole.NAVIGATION,
    tint: Color = Color.Unspecified, fallback: Color = Color.Unspecified,
    materialOutput: GlassSource? = null, sourceOverride: GlassSource? = null,
    pressed: Float = 0f, pressProgress: (() -> Float)? = null,
    lightPosition: (() -> Offset)? = null, motionSample: () -> Float = { 0f },
    touchPoint: (() -> Offset)? = null, touchStrength: (() -> Float)? = null,
    fuseCenter: (() -> Offset)? = null, fuseStrength: (() -> Float)? = null,
    lensOnly: Boolean = false, observePress: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalLumaPalette.current
    val settings = LocalUiSettings.current
    val mode = LocalAppearance.current.motion
    val gravitySource = LocalGravityLight.current
    val source = sourceOverride ?: when (role) {
        GlassRole.MENU -> LocalOverlayGlass.current ?: LocalContentHaze.current ?: LocalBackdropHaze.current
        GlassRole.GROUP -> LocalBackdropHaze.current
        GlassRole.BUTTON, GlassRole.FIELD, GlassRole.THUMB -> LocalControlHaze.current ?: LocalBackdropHaze.current
        else -> LocalContentHaze.current ?: LocalBackdropHaze.current
    }
    val supported = Build.VERSION.SDK_INT >= 31 && LocalView.current.isHardwareAccelerated
    val plan = remember(settings, role, supported, source != null, lensOnly) {
        val base = GlassPolicy.plan(settings, role, supported, source != null)
        // The selected lens samples an ALREADY composed track. Blurring it again hides the
        // very details being refracted. Contrast/solid accessibility modes take precedence.
        if (lensOnly && base.enabled && !settings.enabled("increaseContrast"))
            base.copy(blur = 0f, tint = base.tint.coerceAtMost(.10f)) else base
    }
    val shape = remember(radius) { RoundedCornerShape(radius) }
    val fill = if (fallback.isSpecified) fallback else palette.group
    var down by remember { mutableStateOf(false) }
    var point by remember { mutableStateOf(Offset(.35f, .15f)) }
    val automatic = animateFloatAsState(
        if (down && plan.enabled && mode != MotionMode.OFF) 1f else 0f,
        settings.motionSpec(MotionChannel.LIFT, mode), label = "surface-pressure",
    )
    val external = rememberUpdatedState(pressed)
    val externalReader = rememberUpdatedState(pressProgress)
    val externalLight = rememberUpdatedState(lightPosition)
    val externalTouch = rememberUpdatedState(touchPoint)
    val externalTouchStrength = rememberUpdatedState(touchStrength)
    val externalFuseCenter = rememberUpdatedState(fuseCenter)
    val externalFuseStrength = rememberUpdatedState(fuseStrength)
    val allowPointerLight = settings.enabled("pointerLight")
    val gravityEnabled = settings.enabled("gravityLight") && mode != MotionMode.OFF &&
        !settings.enabled("reduceMotion")
    val touchBulgeEnabled = settings.enabled("touchBulge")
    val fusionEnabled = settings.enabled("lensFusion")
    val rolePressure = if (role == GlassRole.GROUP || role == GlassRole.MENU) .25f else 1f
    val pressure: () -> Float = {
        if (mode == MotionMode.OFF) 0f else max(
            externalReader.value?.invoke() ?: external.value,
            automatic.value * rolePressure,
        ).coerceIn(0f, 1f)
    }
    // Pointer wins while touching; otherwise gravity pose drives the highlight.
    // Read gravity only inside draw (via this lambda) so sensor ticks redraw glass
    // layers instead of recomposing labels.
    val light: () -> Offset = {
        externalLight.value?.invoke()
            ?: if (down && allowPointerLight) point
            else if (gravityEnabled) gravitySource()
            else if (allowPointerLight) point
            else Offset(.35f, .15f)
    }
    val gravityLight: () -> Offset = {
        if (gravityEnabled) gravitySource() else Offset(.35f, .15f)
    }
    val touch: () -> Offset = { externalTouch.value?.invoke() ?: point }
    val touchAmt: () -> Float = {
        externalTouchStrength.value?.invoke()
            ?: if (touchBulgeEnabled && mode != MotionMode.OFF) pressure() * 0.9f else 0f
    }
    val fusePos: () -> Offset = { externalFuseCenter.value?.invoke() ?: Offset(.5f, .5f) }
    val fuseAmt: () -> Float = {
        externalFuseStrength.value?.invoke()
            ?: 0f
    }.let { base ->
        { if (fusionEnabled) base().coerceIn(0f, 1f) else 0f }
    }
    val ancestorMotion = LocalGlassMotion.current
    val liveMotion = rememberUpdatedState(motionSample)
    val optical = opticalBacking(source, plan, role, settings, radius, fill, tint,
        pressure = pressure, lightPosition = light,
        touchPoint = touch, touchStrength = touchAmt,
        fuseCenter = fusePos, fuseStrength = fuseAmt,
        gravityPoint = gravityLight) { ancestorMotion() + liveMotion.value() }
    val observer = if (observePress && plan.enabled && mode != MotionMode.OFF) {
        Modifier.pointerInput(plan.enabled, mode, allowPointerLight) {
            // Observation only: never consume the child's click, slider, scroll or text selection.
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                down = true
                try {
                    fun updatePoint(position: Offset) {
                        if (size.width > 0 && size.height > 0) point = Offset(
                            (position.x / size.width).coerceIn(0f, 1f),
                            (position.y / size.height).coerceIn(0f, 1f),
                        )
                    }
                    updatePoint(first.position)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        updatePoint(change.position)
                        if (!change.pressed) break
                        // Let a scrolling row release its generic press; explicit control pressure
                        // still comes from its own gesture state and remains active during dragging.
                        if ((change.position - first.position).getDistance() > viewConfiguration.touchSlop * 1.5f)
                            down = false
                    } while (true)
                } finally { down = false }
            }
        }
    } else Modifier
    val finishPressure: () -> Float = { if (settings.enabled("pressLight")) pressure() else 0f }
    Box(modifier.then(observer).shadow(plan.shadow.dp, shape, clip = false).clip(shape).semantics {
        glassRendering = "${role.name}:${when {
            !plan.enabled -> "solid"
            Build.VERSION.SDK_INT >= 33 && !OpticalRuntime.shaderDisabled &&
                settings.text("material") in setOf("glass", "regular") && settings.number("refraction") > 0f -> "lens"
            else -> "blur"
        }}"
    }) {
        Box(Modifier.matchParentSize()
            .then(if (materialOutput != null) Modifier.captureGlass(materialOutput) else Modifier)
            .then(optical).glassFinish(plan, radius, palette.dark, finishPressure, light))
        content()
    }
}

private object GrainTexture {
    val image: ImageBitmap by lazy {
        Bitmap.createBitmap(GrainPixels.create(64), 64, 64, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

private fun Modifier.glassFinish(
    plan: GlassPlan, radius: Dp, dark: Boolean, pressure: () -> Float, lightPosition: () -> Offset,
) = drawWithCache {
    val corner = CornerRadius(radius.toPx(), radius.toPx())
    val stroke = Stroke(.8.dp.toPx())
    val edge = Brush.linearGradient(listOf(
        Color.White.copy(alpha = plan.rim), Color.White.copy(alpha = plan.rim * .20f),
        Color.Black.copy(alpha = plan.rim * if (dark) .26f else .10f),
        Color.White.copy(alpha = plan.rim * .5f)), end = Offset(size.width, size.height))
    val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = plan.rim * .25f),
        Color.Transparent, Color.White.copy(alpha = plan.rim * .04f)))
    var cachedPoint = Offset(.35f, .15f)
    fun touchBrush(point: Offset) = Brush.radialGradient(
        listOf(Color.White.copy(alpha = .22f), Color.Transparent),
        center = Offset(size.width * point.x, size.height * point.y),
        radius = size.width.coerceAtLeast(1f),
    )
    var touch = touchBrush(cachedPoint)
    val noise = if (plan.noise > 0f)
        ShaderBrush(ImageShader(GrainTexture.image, TileMode.Repeated, TileMode.Repeated)) else null
    onDrawWithContent {
        drawContent()
        if (plan.enabled) {
            drawRect(sheen)
            if (noise != null) drawRect(noise, alpha = plan.noise)
            val amount = pressure().coerceIn(0f, 1f)
            if (amount > 0f) {
                val point = lightPosition()
                if (point != cachedPoint) { cachedPoint = point; touch = touchBrush(point) }
                drawRect(touch, alpha = amount)
            }
            val inset = .5.dp.toPx()
            drawRoundRect(edge, Offset(inset, inset),
                Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f)),
                corner, style = stroke)
        }
    }
}

/** Stationary silk bands supply actual detail behind every glass module. There is deliberately
 * no animated wallpaper loop. The source is shared, while text/icons are drawn above the glass. */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val p = LocalLumaPalette.current
    val s = LocalUiSettings.current
    val active = s.text("material") != "solid" && !s.enabled("reduceTransparency") &&
        GlassRole.entries.any { s.enabled(it.setting) }
    Box(modifier.background(p.background).drawWithCache {
        val pearl = if (p.dark) Color(0xFF202B40) else Color(0xFFD4E5F7)
        val base = Brush.linearGradient(listOf(p.background, pearl, p.background),
            start = Offset(-size.width * .1f, 0f), end = Offset(size.width, size.height))
        val silk = Brush.linearGradient(listOf(Color.Transparent,
            p.accent.copy(alpha = .16f), Color.White.copy(alpha = if (p.dark) .07f else .5f),
            p.accent.copy(alpha = .08f), Color.Transparent),
            start = Offset(0f, size.height * .16f), end = Offset(size.width, size.height * .67f))
        val second = Brush.linearGradient(listOf(Color.Transparent, pearl.copy(alpha = .75f), Color.Transparent),
            start = Offset(size.width * .15f, size.height * .62f), end = Offset(size.width, size.height * .84f))
        onDrawBehind {
            if (active) { drawRect(base); drawRect(silk); drawRect(second) }
        }
    })
}
