package com.luma.downloader.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import com.luma.downloader.data.*
import com.luma.downloader.ui.optics.LocalGlassMotion

val LocalSceneActive = compositionLocalOf { true }

/** Retain page identities and scroll state. A dock gesture may supply continuousPosition:
 * in that mode at most two adjacent pages are placed, at full opacity, following the finger.
 * Ordinary settings subroutes retain the previous interruptible spring/fade path. */
@Composable
fun <K : Any> RetainedSceneHost(
    keys: List<K>, selected: K, distancePx: Float, channel: MotionChannel = MotionChannel.TAB,
    modifier: Modifier = Modifier, precompose: Boolean = true,
    continuousPosition: (() -> Float)? = null,
    content: @Composable (K) -> Unit,
) {
    require(selected in keys && keys.distinct().size == keys.size)
    val s = LocalUiSettings.current
    val mode = LocalAppearance.current.motion
    val parentActive = LocalSceneActive.current
    val parentMotion = LocalGlassMotion.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val livePosition by rememberUpdatedState(continuousPosition)
    val holder = rememberSaveableStateHolder()
    val transition = updateTransition(selected, label = "retained-scenes")
    val selectedIndex = keys.indexOf(selected)
    val positions = keys.mapIndexed { index, item -> key(item) {
        transition.animateFloat(transitionSpec = { s.motionSpec(channel, mode) }, label = "scene-offset-$index") { target ->
            if (mode == MotionMode.OFF) 0f else ScenePolicy.offset(index, keys.indexOf(target), distancePx)
        }
    } }
    val weights = keys.mapIndexed { index, item -> key(item) {
        transition.animateFloat(transitionSpec = {
            if (mode == MotionMode.OFF) snap() else tween(180, easing = LinearEasing)
        }, label = "scene-visibility-$index") { target -> if (target == item) 1f else 0f }
    } }
    val visited = remember { mutableStateMapOf<K, Boolean>() }
    SideEffect { if (!precompose) visited[selected] = true }
    val included = keys.filter { precompose || visited[it] == true || it == selected || it == transition.currentState }
    val frameWeights = remember(keys) { FloatArray(keys.size) }
    Layout(modifier = if (continuousPosition != null) modifier.clipToBounds() else modifier, content = {
        included.forEach { item -> key(item) {
            val active = parentActive && item == selected
            val index = keys.indexOf(item)
            Box(Modifier.fillMaxSize().testTag("scene-$item")
                .suppressPaneInput(!active).hideBehindOverlay(!active)) {
                CompositionLocalProvider(LocalSceneActive provides active,
                    LocalGlassMotion provides {
                        parentMotion() + (livePosition?.invoke() ?: (positions[index].value + weights[index].value))
                    }) {
                    holder.SaveableStateProvider(item.toString()) { content(item) }
                }
            }
        } }
    }) { measurables, constraints ->
        val places = measurables.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val continuous = livePosition?.invoke()
            if (continuous != null && continuous.isFinite()) {
                val position = continuous.coerceIn(0f, keys.lastIndex.toFloat())
                included.forEachIndexed { child, item ->
                    val index = keys.indexOf(item)
                    if (LiquidGestureMath.pageVisible(index, position)) {
                        places[child].placeWithLayer(0, 0, zIndex = if (index == selectedIndex) 1f else 0f) {
                            translationX = LiquidGestureMath.pageOffset(index, position, constraints.maxWidth.toFloat(), rtl)
                            alpha = 1f
                            clip = false
                        }
                    }
                }
            } else {
                for (i in keys.indices) frameWeights[i] = weights[i].value
                included.forEachIndexed { child, item ->
                    val index = keys.indexOf(item)
                    if (ScenePolicy.draw(frameWeights[index], index == selectedIndex)) {
                        places[child].placeWithLayer(0, 0, zIndex = if (index == selectedIndex) 1f else 0f) {
                            translationX = positions[index].value
                            alpha = ScenePolicy.targetOnTopAlpha(index, selectedIndex, frameWeights)
                            clip = false
                        }
                    }
                }
            }
        }
    }
}

/** Keep a view tree but skip drawing and placement when fully hidden. */
@Composable
fun RetainedPane(
    modifier: Modifier = Modifier, visible: () -> Boolean, translation: () -> Float = { 0f },
    content: @Composable () -> Unit,
) {
    val parentMotion = LocalGlassMotion.current
    Layout(modifier = Modifier.fillMaxSize(), content = {
        Box(modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalGlassMotion provides { parentMotion() + translation() }) { content() }
        }
    }) { nodes, constraints ->
        val child = nodes.single().measure(constraints)
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (visible()) child.placeWithLayer(0, 0) { translationX = translation(); clip = false }
        }
    }
}
