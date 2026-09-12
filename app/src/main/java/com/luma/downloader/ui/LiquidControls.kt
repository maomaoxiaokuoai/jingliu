@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.luma.downloader.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.luma.downloader.data.*
import com.luma.downloader.ui.optics.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

val LocalUiSettings = compositionLocalOf { UiSettings() }

@Composable
fun LiquidActionButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    GlassButtonBody(onClick, modifier, enabled, compact = false) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** Native toggle semantics plus cancel-safe dragging; a held press opens a transparent lens.
 * Reference: Kyant's LiquidToggle track/foreground separation; adapted to Jingliu's sources,
 * motion settings, accessibility and controlled Boolean state (no View SDK dependency). */
@Composable
fun LiquidSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, description: String) {
    val settings = LocalUiSettings.current
    val palette = LocalLumaPalette.current
    val mode = LocalAppearance.current.motion
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val direction = if (rtl) -1f else 1f
    val travel = with(density) { 20.dp.toPx() }
    val rail = rememberGlassSource()
    val backing = LocalControlHaze.current ?: LocalBackdropHaze.current
    val offset = remember { Animatable(if (checked) 1f else 0f) }
    var direct by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    var gestureVelocity by remember { mutableFloatStateOf(0f) }
    var releaseVelocity by remember { mutableFloatStateOf(0f) }
    var serial by remember { mutableIntStateOf(0) }
    var releaseJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val currentChecked by rememberUpdatedState(checked)
    val onChange by rememberUpdatedState(onCheckedChange)
    val haptic = LocalHapticFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val notify: (Boolean) -> Unit = { next ->
        if (next != currentChecked) {
            if (settings.enabled("haptics")) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onChange(next)
        }
    }
    val currentNotify by rememberUpdatedState(notify)
    LaunchedEffect(checked, dragging, serial, settings.text("motion"), settings.text("duration"), settings.text("bounce"), mode) {
        if (!dragging) {
            val speed = releaseVelocity
            releaseVelocity = 0f
            offset.animateTo(if (checked) 1f else 0f, settings.motionSpec(MotionChannel.CONTROL, mode), initialVelocity = speed)
        }
    }
    val pressure = animateFloatAsState(
        if ((pressed || dragging) && mode != MotionMode.OFF) 1f else 0f,
        settings.motionSpec(MotionChannel.LIFT, mode), label = "toggle-pressure")
    val stretch = animateFloatAsState(if (dragging) gestureVelocity else 0f,
        settings.motionSpec(MotionChannel.LIFT, mode), label = "toggle-velocity")
    val display: () -> Float = { if (dragging) direct else offset.value }
    val lift = (settings.number("controlLift") / 100f - 1f).coerceIn(0f, .55f)
    val off = palette.muted.copy(alpha = .28f)
    val on = if (settings.text("switchColor") == "system")
        if (palette.dark) Color(0xFF30D158) else Color(0xFF34C759) else palette.accent
    Box(Modifier.size(64.dp, 48.dp)
        .toggleable(checked, interactionSource = interactions, indication = null, role = Role.Switch, onValueChange = notify)
        .pointerInput(travel, rtl) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var over = 0f
                val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, extra ->
                    change.consume(); over = extra
                }
                if (start != null) {
                    releaseJob?.cancel()
                    direct = (offset.value + direction * over / travel).coerceIn(0f, 1f)
                    dragging = true
                    tracker.addPosition(start.uptimeMillis, start.position)
                    var complete = false
                    try {
                        complete = horizontalDrag(start.id) { event ->
                            tracker.addPosition(event.uptimeMillis, event.position)
                            gestureVelocity = (tracker.calculateVelocity().x * direction / travel).coerceIn(-20f, 20f)
                            direct = (direct + event.positionChange().x * direction / travel).coerceIn(0f, 1f)
                            event.consume()
                        }
                    } finally {
                        val finalValue = direct
                        val speed = if (complete) (tracker.calculateVelocity().x * direction / travel).coerceIn(-20f, 20f) else 0f
                        val next = LiquidGestureMath.switchTarget(finalValue, speed, complete, currentChecked)
                        // Snap before leaving direct mode so the release spring starts at the finger,
                        // not the stale pre-drag checked position. Cancellation never toggles state.
                        releaseJob = scope.launch {
                            offset.snapTo(finalValue)
                            releaseVelocity = speed
                            if (complete) currentNotify(next)
                            dragging = false
                            gestureVelocity = 0f
                            serial++
                        }
                    }
                }
            }
        }.semantics { contentDescription = description }, contentAlignment = Alignment.CenterStart) {
        // Capture the whole expanded-thumb envelope, including the group backdrop around the rail.
        // Do NOT put the following thumb inside this capture: that creates a recursive source.
        Box(Modifier.fillMaxSize().captureGlass(rail).replayGlassScene(backing, palette.group)) {
            Canvas(Modifier.fillMaxSize()) {
                val trackHeight = 30.dp.toPx()
                val y = (size.height - trackHeight) / 2f
                drawRoundRect(lerp(off, on, display().coerceIn(0f, 1f)),
                    Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, trackHeight),
                    CornerRadius(trackHeight / 2f), alpha = .88f)
            }
        }
        GlassSurface(Modifier.padding(start = 2.dp).graphicsLayer {
            translationX = direction * display().coerceIn(-.06f, 1.06f) * travel
            val grow = 1f + lift * pressure.value
            scaleX = grow * LiquidGestureMath.stretchX(stretch.value * pressure.value)
            scaleY = grow * LiquidGestureMath.stretchY(stretch.value * pressure.value)
        }.size(40.dp, 28.dp), radius = 100.dp, role = GlassRole.THUMB,
            sourceOverride = rail, fallback = Color.White, lensOnly = true, observePress = false,
            pressProgress = { pressure.value },
            touchPoint = { Offset(0.5f, 0.5f) }, touchStrength = { pressure.value * 0.9f },
            motionSample = { display() + pressure.value + stretch.value }) {
            Canvas(Modifier.fillMaxSize()) {
                // Resting white thumb progressively becomes glass while the finger stays down.
                drawRect(Color.White, alpha = .82f * (1f - pressure.value.coerceIn(0f, 1f)))
            }
        }
    }
}

/** Keep Material3's drag/tap/keyboard/TalkBack engine; only replace its track and thumb visuals.
 * The track's capture includes its surrounding background, not just the narrow filled line. */
@Composable
fun LiquidSlider(
    value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0, enabled: Boolean = true, description: String = "参数",
    onValueChangeFinished: () -> Unit = {},
) {
    val validRange = valueRange.start.isFinite() && valueRange.endInclusive.isFinite() && valueRange.endInclusive > valueRange.start
    val range = if (validRange) valueRange else 0f..1f
    val isEnabled = enabled && validRange
    val settings = LocalUiSettings.current
    val palette = LocalLumaPalette.current
    val mode = LocalAppearance.current.motion
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val rail = rememberGlassSource()
    val backing = LocalControlHaze.current ?: LocalBackdropHaze.current
    val haptic = LocalHapticFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val dragging by interactions.collectIsDraggedAsState()
    val active = isEnabled && (pressed || dragging)
    var lastCommitted by remember { mutableFloatStateOf(value) }
    val pressure = animateFloatAsState(if (active && mode != MotionMode.OFF) 1f else 0f,
        settings.motionSpec(MotionChannel.LIFT, mode), label = "slider-pressure")
    val lift = (settings.number("controlLift") / 100f - 1f).coerceIn(0f, .55f)
    val safeValue = if (value.isFinite()) value.coerceIn(range.start, range.endInclusive) else range.start
    Slider(value = safeValue, onValueChange = onValueChange, valueRange = range, steps = steps.coerceAtLeast(0),
        enabled = isEnabled, interactionSource = interactions,
        onValueChangeFinished = {
            if (value != lastCommitted && settings.enabled("haptics"))
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastCommitted = value
            onValueChangeFinished()
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = description },
        thumb = {
            GlassSurface(Modifier.size(36.dp, 28.dp).graphicsLayer {
                scaleX = 1f + lift * pressure.value
                scaleY = 1f + lift * pressure.value * .85f
                alpha = if (isEnabled) 1f else .45f
            }, radius = 100.dp, role = GlassRole.THUMB, sourceOverride = rail,
                fallback = Color.White, lensOnly = true, observePress = false,
                pressProgress = { pressure.value },
                touchPoint = { Offset(0.5f, 0.5f) }, touchStrength = { pressure.value * 0.85f },
                motionSample = { safeValue + pressure.value }) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Color.White, alpha = .78f * (1f - pressure.value.coerceIn(0f, 1f)))
                }
            }
        },
        track = { state ->
            val fraction = ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(48.dp).captureGlass(rail).replayGlassScene(backing, palette.group)) {
                Canvas(Modifier.fillMaxSize()) {
                    val y = size.height / 2f
                    val weight = 7.dp.toPx()
                    drawLine(palette.muted.copy(alpha = if (isEnabled) .20f else .10f),
                        Offset(0f, y), Offset(size.width, y), weight, StrokeCap.Round)
                    val start = if (rtl) size.width else 0f
                    val end = if (rtl) size.width * (1f - fraction) else size.width * fraction
                    if (fraction > 0f) drawLine(palette.accent.copy(alpha = if (isEnabled) .90f else .30f),
                        Offset(start, y), Offset(end, y), weight, StrokeCap.Round)
                }
            }
        })
}
