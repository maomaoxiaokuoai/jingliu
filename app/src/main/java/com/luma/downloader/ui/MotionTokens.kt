package com.luma.downloader.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.luma.downloader.data.*

fun <T> UiSettings.motionSpec(channel:MotionChannel,mode:MotionMode):FiniteAnimationSpec<T> {
    val t=MotionPolicy.tuning(this,channel,mode==MotionMode.OFF)
    return if(t.instant)snap() else spring(dampingRatio=t.dampingRatio,stiffness=t.stiffness)
}
/** Does not consume the gesture: clickable remains responsible for semantics and activation. */
fun Modifier.elasticPress(row:Boolean=false):Modifier=composed {
    val settings=LocalUiSettings.current
    val mode=LocalAppearance.current.motion
    var held by remember {mutableStateOf(false)}
    val scale by animateFloatAsState(if(held && !row && mode!=MotionMode.OFF)
        1f-settings.number("press")*(if(row).00013f else .00038f) else 1f,
        settings.motionSpec(if(held)MotionChannel.PRESS else MotionChannel.BUTTON,mode),label="control-compression")
    this.graphicsLayer {scaleX=scale;scaleY=scale}.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed=false)
            held=true
            try {waitForUpOrCancellation()} finally {held=false}
        }
    }
}
