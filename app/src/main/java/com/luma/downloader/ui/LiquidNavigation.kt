package com.luma.downloader.ui

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luma.downloader.data.*
import com.luma.downloader.ui.optics.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Shared continuous position; pages read it during placement, not in every label's composition. */
@Stable
class LiquidTabMotion(initialIndex: Int) {
    private var presentation: State<Float> by mutableStateOf<State<Float>>(mutableFloatStateOf(initialIndex.toFloat()))
    fun value(): Float = presentation.value
    internal fun bind(state: State<Float>) { if (presentation !== state) presentation = state }
}

/** Compose adaptation of Kyant Catalog's layered bottom tabs.
 * Base scene (page + dock + labels) -> movable lens. The scene never contains that lens.
 * A held finger preserves refraction while crossing tabs, and a cancellation restores the start tab.
 */
@Composable
fun LiquidNavigationDock(
    selected: AppPage, onSelected: (AppPage) -> Unit, activeDownloads: Int, collapsed: Boolean,
    motion: LiquidTabMotion, modifier: Modifier = Modifier,
) {
    val s = LocalUiSettings.current
    val p = LocalLumaPalette.current
    val mode = LocalAppearance.current.motion
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val tabs = AppPage.entries
    val icons = remember { listOf(Icons.Outlined.Link, Icons.Outlined.FileDownload, Icons.Outlined.Folder, Icons.Outlined.Settings) }
    val position = remember { Animatable(selected.ordinal.toFloat()) }
    var direct by remember { mutableFloatStateOf(selected.ordinal.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var downPressed by remember { mutableStateOf(false) }
    var gestureVelocity by remember { mutableFloatStateOf(0f) }
    var releaseVelocity by remember { mutableFloatStateOf(0f) }
    var releaseSerial by remember { mutableIntStateOf(0) }
    var releaseJob by remember { mutableStateOf<Job?>(null) }
    var light by remember { mutableStateOf(Offset(.5f, .2f)) }
    val currentSelected by rememberUpdatedState(selected)
    val currentSelect by rememberUpdatedState(onSelected)
    val haptic = LocalHapticFeedback.current
    val haptics by rememberUpdatedState(s.enabled("haptics"))
    val scope = rememberCoroutineScope()
    val display = remember { derivedStateOf { if (dragging) direct else position.value } }
    SideEffect { motion.bind(display) }
    LaunchedEffect(selected, dragging, releaseSerial, s.text("motion"), s.text("duration"), s.text("bounce"), s.enabled("tabMorph"), mode) {
        if (!dragging) {
            val speed = releaseVelocity
            releaseVelocity = 0f
            position.animateTo(selected.ordinal.toFloat(),
                if (s.enabled("tabMorph")) s.motionSpec(MotionChannel.INDICATOR, mode) else snap(),
                initialVelocity = speed)
        }
    }
    val pressure = animateFloatAsState(
        if ((downPressed || dragging || position.isRunning) && mode != MotionMode.OFF) 1f else 0f,
        s.motionSpec(MotionChannel.LIFT, mode), label = "dock-pressure")
    val velocity = animateFloatAsState(if (dragging) gestureVelocity else 0f,
        s.motionSpec(MotionChannel.LIFT, mode), label = "dock-stretch")
    val height by animateDpAsState(if (collapsed) 58.dp else 70.dp,
        s.motionSpec(MotionChannel.CONTROL, mode), label = "dock-height")
    val lift = (s.number("controlLift") / 100f - 1f).coerceIn(0f, .35f)
    val source = rememberGlassSource()
    val hardware = LocalView.current.isHardwareAccelerated
    val optical = GlassPolicy.plan(s, GlassRole.NAVIGATION, Build.VERSION.SDK_INT >= 31 && hardware,
        LocalContentHaze.current != null || LocalBackdropHaze.current != null).enabled
    BoxWithConstraints(modifier.widthIn(max = 520.dp).fillMaxWidth().height(height)) {
        val inset = 5.dp
        val cell = (maxWidth - inset * 2) / tabs.size
        val insetPx = with(density) { inset.toPx() }
        val cellPx = with(density) { cell.toPx() }
        val innerWidth = cellPx * tabs.size
        fun fraction(x: Float): Float = LiquidGestureMath.tabFraction(x - insetPx, innerWidth, tabs.size, rtl)
        fun translation(): Float {
            val logical = display.value.coerceIn(-.06f, tabs.lastIndex + .06f)
            return insetPx + (if (rtl) tabs.lastIndex - logical else logical) * cellPx
        }
        val gestures = Modifier.pointerInput(innerWidth, rtl) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val initialTab = currentSelected
                downPressed = true
                light = Offset((down.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f), .25f)
                var didDrag = false
                var complete = false
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                try {
                    val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (start != null && innerWidth > 0f) {
                        releaseJob?.cancel()
                        direct = fraction(start.position.x)
                        dragging = true
                        didDrag = true
                        var hovered = LiquidGestureMath.nearestTab(direct, tabs.size)
                        if (tabs[hovered] != currentSelected) currentSelect(tabs[hovered])
                        tracker.addPosition(start.uptimeMillis, start.position)
                        complete = horizontalDrag(start.id) { event ->
                            tracker.addPosition(event.uptimeMillis, event.position)
                            gestureVelocity = (tracker.calculateVelocity().x / cellPx * if (rtl) -1f else 1f).coerceIn(-16f, 16f)
                            direct = fraction(event.position.x)
                            light = Offset((event.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                (event.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f))
                            val next = LiquidGestureMath.nearestTab(direct, tabs.size)
                            if (next != hovered) {
                                hovered = next
                                if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentSelect(tabs[next])
                            }
                            event.consume()
                        }
                    }
                } finally {
                    downPressed = false
                    if (didDrag) {
                        val finalPosition = direct
                        val target = if (complete) tabs[LiquidGestureMath.nearestTab(finalPosition, tabs.size)] else initialTab
                        val speed = if (complete) gestureVelocity else 0f
                        currentSelect(target)
                        releaseJob = scope.launch {
                            position.snapTo(finalPosition)
                            releaseVelocity = speed
                            dragging = false
                            gestureVelocity = 0f
                            releaseSerial++
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().then(gestures)) {
            Box(Modifier.fillMaxSize().captureGlass(source)) {
                GlassSurface(Modifier.fillMaxSize(), radius = 36.dp, observePress = false,
                    pressProgress = { pressure.value * .25f }, lightPosition = { light }) {}
                if (!optical) Box(Modifier.padding(vertical = inset).graphicsLayer { translationX = translation() }
                    .width(cell).fillMaxHeight().clip(CircleShape).background(p.accent.copy(alpha = .14f)))
                Row(Modifier.fillMaxSize().padding(inset)) {
                    tabs.forEachIndexed { index, page ->
                        val isSelected = selected == page
                        Column(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                            .testTag("tab-${page.name}")
                            .selectable(isSelected, role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() }, indication = null,
                                onClick = { currentSelect(page) }),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            BadgedBox(badge = {
                                if (page == AppPage.DOWNLOADS && activeDownloads > 0) GlassBadge(activeDownloads.toString())
                            }) {
                                Icon(icons[index], null, Modifier.size(23.dp), tint = if (isSelected) p.accent else p.muted)
                            }
                            if (!collapsed) Text(page.title, fontSize = 11.sp, color = if (isSelected) p.accent else p.muted)
                        }
                    }
                }
            }
            // No input or accessibility duplicate on this optical overlay. The underlying tabs
            // remain real selectable controls; the parent gesture spans all four hit targets.
            // 0.9.1: finger position drives touch bulge; neighboring-pill distance drives
            // smooth-min fusion so the lens appears to glue while crossing tabs.
            if (optical) GlassSurface(
                Modifier.padding(vertical = inset).graphicsLayer {
                    translationX = translation()
                    val grow = 1f + lift * pressure.value
                    scaleX = grow * LiquidGestureMath.stretchX(velocity.value * pressure.value)
                    scaleY = grow * LiquidGestureMath.stretchY(velocity.value * pressure.value)
                }.width(cell).fillMaxHeight().clearAndSetSemantics {},
                radius = 36.dp, role = GlassRole.NAVIGATION, sourceOverride = source,
                tint = p.accent.copy(alpha = .025f), lensOnly = true, observePress = false,
                pressProgress = { pressure.value }, lightPosition = { light },
                touchPoint = { light }, touchStrength = { pressure.value * 0.85f },
                fuseCenter = { Offset(0.5f, 0.5f) },
                fuseStrength = {
                    // Fuse while the finger sits between pills; rest = crisp single lens.
                    val frac = display.value
                    val nearest = LiquidGestureMath.nearestTab(frac, tabs.size).toFloat()
                    val between = kotlin.math.abs(frac - nearest).coerceIn(0f, 0.5f) * 2f
                    (pressure.value * (1f - between)).coerceIn(0f, 1f) * 0.7f
                },
                motionSample = { display.value + pressure.value + velocity.value },
            ) {}
        }
    }
}
