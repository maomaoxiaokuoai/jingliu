@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.luma.downloader.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.*
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.luma.downloader.data.*
import com.luma.downloader.ui.optics.*

val LocalUiSettings = compositionLocalOf { UiSettings() }

/** All glass buttons sample a material-only backing; the button never samples its own label. */
@Composable fun LiquidActionButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    content:@Composable RowScope.()->Unit) {
    GlassButtonBody(onClick,modifier,enabled,compact=false) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically,content=content)
    }
}

/** Tap/keyboard semantics are supplied by toggleable; cancelled drags never change the value. */
@Composable fun LiquidSwitch(checked:Boolean,onCheckedChange:(Boolean)->Unit,description:String) {
    val s=LocalUiSettings.current;val p=LocalLumaPalette.current;val motion=LocalAppearance.current.motion
    val rail=rememberGlassSource()
    val dpScale=LocalDensity.current.density
    val travel=with(LocalDensity.current){20.dp.toPx()}
    val offset=remember{Animatable(if(checked)travel else 0f)}
    var dragging by remember{mutableStateOf(false)}
    var direct by remember{mutableFloatStateOf(0f)}
    var releaseVelocity by remember{mutableFloatStateOf(0f)}
    var releaseSerial by remember{mutableIntStateOf(0)}
    var releaseJob by remember{mutableStateOf<Job?>(null)}
    val scope=rememberCoroutineScope()
    val currentChecked by rememberUpdatedState(checked)
    val change by rememberUpdatedState(onCheckedChange)
    val haptic=LocalHapticFeedback.current
    val source=remember{MutableInteractionSource()}
    val pressed by source.collectIsPressedAsState()
    val setChecked:(Boolean)->Unit={value->
        if(value!=currentChecked){if(s.enabled("haptics"))haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);change(value)}
    }
    val currentSet by rememberUpdatedState(setChecked)
    LaunchedEffect(checked,dragging,releaseSerial,travel,s.text("motion"),s.text("duration"),s.text("bounce"),motion) {
        if(!dragging) {
            val velocity=if(releaseVelocity!=0f)releaseVelocity else offset.velocity
            releaseVelocity=0f
            offset.animateTo(if(checked)travel else 0f,s.motionSpec(MotionChannel.CONTROL,motion),initialVelocity=velocity)
        }
    }
    val lift by animateFloatAsState(if((pressed||dragging)&&motion!=MotionMode.OFF)
        1f+(s.number("controlLift")/100f-1f)*.78f else 1f,
        s.motionSpec(MotionChannel.LIFT,motion),label="switch-lift")
    // Observe per-frame motion only in draw/graphics layers, not the whole switch composition.
    val display:()->Float={if(dragging)direct else offset.value}
    val offTrack=p.muted.copy(alpha=.28f)
    val onTrack=if(s.text("switchColor")=="system"){if(p.dark)Color(0xFF30D158) else Color(0xFF34C759)} else p.accent
    Box(Modifier.size(52.dp,44.dp)
        .toggleable(checked,interactionSource=source,indication=null,role=Role.Switch,onValueChange=setChecked)
        .pointerInput(travel) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,down.position)
                var slop=0f
                val start=awaitHorizontalTouchSlopOrCancellation(down.id){change,over->change.consume();slop=over}
                if(start!=null) {
                    releaseJob?.cancel()
                    direct=(offset.value+slop).coerceIn(0f,travel);dragging=true
                    tracker.addPosition(start.uptimeMillis,start.position)
                    var finished=false
                    try {
                        finished=horizontalDrag(start.id){event->
                            tracker.addPosition(event.uptimeMillis,event.position)
                            direct=(direct+event.positionChange().x).coerceIn(0f,travel)
                            event.consume()
                        }
                    } finally {
                        val velocity=if(finished)tracker.calculateVelocity().x.coerceIn(-1800f*dpScale,1800f*dpScale) else 0f
                        val next=if(kotlin.math.abs(velocity)>500f*dpScale)velocity>0 else direct>travel*.5f
                        releaseJob=scope.launch {
                            offset.snapTo(direct)
                            releaseVelocity=velocity
                            if(finished)currentSet(next)
                            dragging=false;releaseSerial++
                        }
                    }
                }
            }
        }.semantics {contentDescription=description},contentAlignment=Alignment.Center) {
        GlassSurface(Modifier.size(50.dp,30.dp).captureGlass(rail),radius=100.dp,role=GlassRole.THUMB,
            sourceOverride=LocalBackdropHaze.current) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(lerp(offTrack,onTrack,(display()/travel).coerceIn(0f,1f)),alpha=.60f)
            }
        }
        GlassSurface(Modifier.align(Alignment.CenterStart).padding(start=3.dp).graphicsLayer {
            translationX=display().coerceIn(-travel*.075f,travel*1.075f);scaleX=lift;scaleY=lift
        }.size(26.dp),radius=100.dp,role=GlassRole.THUMB,sourceOverride=rail,fallback=Color.White,
            tint=if(checked)p.accent.copy(alpha=.08f) else Color.Unspecified,motionSample={display()+lift},
            pressed=if(s.enabled("pressLight")&&(pressed||dragging)) .65f else 0f) {}

    }
}

/** Native Slider retains keyboard, TalkBack, track tapping and gesture cancellation.
 * Only the visual position/lift are spring-driven; no animation writes back to business state. */
@Composable fun LiquidSlider(value:Float,onValueChange:(Float)->Unit,valueRange:ClosedFloatingPointRange<Float>,
    steps:Int=0,enabled:Boolean=true,description:String="参数",onValueChangeFinished:()->Unit={}) {
    val s=LocalUiSettings.current;val p=LocalLumaPalette.current;val mode=LocalAppearance.current.motion
    val rail=rememberGlassSource()
    val haptic=LocalHapticFeedback.current
    var lastCommitted by remember {mutableFloatStateOf(value)}
    val source=remember{MutableInteractionSource()}
    val pressed by source.collectIsPressedAsState();val dragged by source.collectIsDraggedAsState()
    val active=pressed||dragged
    val display by animateFloatAsState(value,
        if(active||!enabled)snap() else s.motionSpec(MotionChannel.CONTROL,mode),label="range-position")
    val lift by animateFloatAsState(if(active&&enabled&&mode!=MotionMode.OFF)s.number("controlLift")/100f else 1f,
        s.motionSpec(MotionChannel.LIFT,mode),label="range-lift")
    Slider(value=display.coerceIn(valueRange.start,valueRange.endInclusive),onValueChange=onValueChange,
        valueRange=valueRange,steps=steps,enabled=enabled,interactionSource=source,
        onValueChangeFinished={if(value!=lastCommitted&&s.enabled("haptics"))haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);lastCommitted=value;onValueChangeFinished()},
        modifier=Modifier.fillMaxWidth().heightIn(min=44.dp).semantics{contentDescription=description},
        thumb={
            GlassSurface(Modifier.size(28.dp).graphicsLayer{scaleX=lift;scaleY=lift},radius=100.dp,
                role=GlassRole.THUMB,sourceOverride=rail,fallback=Color.White,motionSample={display+lift},
                pressed=if(s.enabled("pressLight")&&active).8f else 0f) {}

        },track={state->
            val fraction=((state.value-valueRange.start)/(valueRange.endInclusive-valueRange.start)).coerceIn(0f,1f)
            Box(Modifier.fillMaxWidth().height(28.dp),contentAlignment=Alignment.Center) {
            GlassSurface(Modifier.fillMaxWidth().height(8.dp).captureGlass(rail),radius=4.dp,role=GlassRole.THUMB,sourceOverride=LocalBackdropHaze.current) {
            Canvas(Modifier.fillMaxSize()) {
                val y=size.height/2;val weight=6.dp.toPx()
                drawLine(p.muted.copy(alpha=if(enabled).18f else .1f),Offset(0f,y),Offset(size.width,y),weight,StrokeCap.Round)
                if(fraction>0)drawLine(p.accent.copy(alpha=if(enabled)1f else .3f),Offset(0f,y),Offset(size.width*fraction,y),weight,StrokeCap.Round)
            }
            }
            }
        })
}
