@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.luma.downloader.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.luma.downloader.data.*
import com.luma.downloader.ui.optics.*

/** One material/action path for primary, text, destructive, icon and toolbar actions.
 * TextButton is a visual size variant, not unstyled Material text on an opaque card.
 */
@Composable internal fun GlassButtonBody(
    onClick:()->Unit, modifier:Modifier, enabled:Boolean, compact:Boolean,
    outline:Boolean=false, iconOnly:Boolean=false, contentPadding:PaddingValues?=null,
    content:@Composable RowScope.()->Unit,
) {
    val s=LocalUiSettings.current;val p=LocalLumaPalette.current;val mode=LocalAppearance.current.motion
    val source=remember{MutableInteractionSource()}
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    var hitSize by remember{mutableStateOf(IntSize.Zero)}
    var lightPoint by remember{mutableStateOf(Offset(.4f,.15f))}
    val hover by source.collectIsHoveredAsState()
    val haptic=LocalHapticFeedback.current
    val amount=animateFloatAsState(if(pressed&&enabled&&mode!=MotionMode.OFF)s.number("press")/100f else 0f,
        s.motionSpec(if(pressed)MotionChannel.PRESS else MotionChannel.BUTTON,mode),label="glass-button-press")
    val light=animateFloatAsState(if(enabled&&(pressed||focused||hover)&&s.enabled("pressLight"))1f else 0f,
        s.motionSpec(MotionChannel.FADE,mode),label="glass-button-light")
    val radius=if(iconOnly||compact||s.text("buttonShape")=="capsule")100.dp else s.number("corner").dp
    val shape=RoundedCornerShape(radius)
    val tinted=s.text("buttonStyle")=="tinted"
    val glass=s.enabled("glassButtons")&&!s.enabled("reduceTransparency")&&s.text("material")!="solid"&&s.text("buttonStyle") !in setOf("solid","outline")
    val foreground=if(!enabled)p.muted else if(glass||outline||compact)p.accent else Color.White
    val fill=if(!glass&&!outline&&!compact)p.accent else p.group
    val size=if(iconOnly)44.dp else if(compact)42.dp else s.number("buttonHeight").dp
    val outer=modifier.heightIn(min=size).then(if(iconOnly)Modifier.width(44.dp) else Modifier)
        .graphicsLayer { val press=amount.value;scaleX=1f-press*.026f;scaleY=1f-press*.026f }
        .clip(shape).onSizeChanged{hitSize=it}
        .hoverable(source,enabled)
        .pointerInput(s.enabled("pointerLight"),enabled){
            if(s.enabled("pointerLight")&&enabled)awaitPointerEventScope {
                while(true){val event=awaitPointerEvent(PointerEventPass.Initial);val change=event.changes.firstOrNull()
                    if(change!=null&&hitSize.width>0&&hitSize.height>0)lightPoint=Offset((change.position.x/hitSize.width).coerceIn(0f,1f),(change.position.y/hitSize.height).coerceIn(0f,1f))
                }
            }
        }
        .then(if(outline)Modifier.border(.7.dp,p.accent.copy(alpha=.30f),shape) else Modifier)
        .clickable(enabled=enabled,interactionSource=source,indication=null,role=Role.Button) {
            if(s.enabled("haptics"))haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    GlassSurface(outer,radius,GlassRole.BUTTON,
        tint=if(tinted)p.accent.copy(alpha=if(enabled).10f else .035f) else Color.Unspecified,
        fallback=fill,pressProgress={light.value},lightPosition={lightPoint},motionSample={amount.value}) {
        CompositionLocalProvider(LocalContentColor provides foreground) {
            Row(Modifier.align(Alignment.Center).heightIn(min=size).then(if(iconOnly)Modifier.fillMaxWidth() else Modifier)
                .padding(contentPadding ?: PaddingValues(horizontal=if(iconOnly)0.dp else if(compact)14.dp else 18.dp,vertical=if(iconOnly)0.dp else 8.dp)),
                horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically,content=content)
        }
    }
}

@Composable fun GlassTextButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,contentPadding:PaddingValues?=null,
    content:@Composable RowScope.()->Unit) = GlassButtonBody(onClick,modifier,enabled,true,contentPadding=contentPadding,content=content)
@Composable fun GlassOutlinedButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    content:@Composable RowScope.()->Unit) = GlassButtonBody(onClick,modifier,enabled,true,outline=true,content=content)
@Composable fun GlassIconButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    content:@Composable ()->Unit) = GlassButtonBody(onClick,modifier,enabled,true,iconOnly=true){content()}

@Composable fun GlassFilterChip(selected:Boolean,onClick:()->Unit,modifier:Modifier=Modifier,
    enabled:Boolean=true,label:@Composable ()->Unit) {
    val p=LocalLumaPalette.current
    GlassSurface(modifier.heightIn(min=42.dp).selectable(selected,enabled,Role.RadioButton,onClick),
        radius=21.dp,role=GlassRole.BUTTON,tint=if(selected)p.accent.copy(alpha=.15f) else Color.Unspecified) {
        Row(Modifier.padding(horizontal=13.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            if(selected)Icon(Icons.Outlined.Check,null,Modifier.size(16.dp),tint=p.accent)
            CompositionLocalProvider(LocalContentColor provides if(selected)p.accent else p.ink){label()}
        }
    }
}

@Composable fun GlassRadioButton(selected:Boolean,onClick:(()->Unit)?,modifier:Modifier=Modifier,enabled:Boolean=true) {
    val p=LocalLumaPalette.current
    val input=if(onClick!=null)Modifier.selectable(selected,enabled,Role.RadioButton,onClick) else Modifier
    Box(modifier.size(44.dp).then(input),contentAlignment=Alignment.Center) {
        GlassSurface(Modifier.size(24.dp),radius=12.dp,role=GlassRole.THUMB,
            tint=if(selected)p.accent.copy(alpha=.15f) else Color.Unspecified) {
            Canvas(Modifier.fillMaxSize()){
                drawCircle((if(selected)p.accent else p.muted).copy(alpha=if(enabled).8f else .3f),radius=size.minDimension*.40f,style=Stroke(1.5.dp.toPx()))
                if(selected)drawCircle(p.accent.copy(alpha=if(enabled)1f else .3f),radius=size.minDimension*.21f)
            }
        }
    }
}

@Composable fun GlassSelectionMark(selected:Boolean,modifier:Modifier=Modifier,source:GlassSource?=null) {
    val p=LocalLumaPalette.current
    GlassSurface(modifier.size(28.dp),radius=14.dp,role=GlassRole.THUMB,sourceOverride=source,tint=if(selected)p.accent.copy(alpha=.12f) else Color.Unspecified) {
        if(selected)Icon(Icons.Outlined.Check,null,Modifier.align(Alignment.Center).size(18.dp),tint=p.accent)
        else Canvas(Modifier.fillMaxSize()){drawCircle(p.muted.copy(alpha=.55f),size.minDimension*.34f,style=Stroke(1.3.dp.toPx()))}
    }
}

@Composable fun GlassLinearProgressIndicator(modifier:Modifier=Modifier) = GlassProgressBar(null,modifier)
@Composable fun GlassLinearProgressIndicator(progress:()->Float,modifier:Modifier=Modifier) = GlassProgressBar(progress,modifier)
@Composable private fun GlassProgressBar(progress:(()->Float)?,modifier:Modifier) {
    val p=LocalLumaPalette.current;val mode=LocalAppearance.current.motion
    val phase=if(progress==null&&mode!=MotionMode.OFF) {
        val transition=rememberInfiniteTransition(label="active-progress")
        transition.animateFloat(-.3f,1.1f,infiniteRepeatable(tween(1000,easing=LinearEasing)),label="indeterminate")
    } else remember{mutableFloatStateOf(.3f)}
    GlassSurface(modifier.height(10.dp).semantics {progressBarRangeInfo=if(progress==null)ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(progress().takeIf{it.isFinite()}?.coerceIn(0f,1f)?:0f,0f..1f)},
        radius=5.dp,role=GlassRole.THUMB) {
        Canvas(Modifier.fillMaxSize()) {
            val fraction=progress?.invoke()?.takeIf{it.isFinite()}?.coerceIn(0f,1f)
            val start=if(fraction!=null)0f else phase.value.coerceAtLeast(0f)
            val end=fraction ?: (phase.value+.30f).coerceIn(0f,1f)
            if(end>start)drawRoundRect(Brush.verticalGradient(listOf(p.accent.copy(alpha=.38f),p.accent.copy(alpha=.88f))),
                topLeft=Offset(size.width*start,0f),size=androidx.compose.ui.geometry.Size(size.width*(end-start),size.height),cornerRadius=androidx.compose.ui.geometry.CornerRadius(size.height/2))
        }
    }
}

@Composable fun GlassCircularProgressIndicator(modifier:Modifier=Modifier,strokeWidth:Dp=2.2.dp) {
    val p=LocalLumaPalette.current;val reduced=LocalAppearance.current.motion==MotionMode.OFF
    val angle=if(!reduced){val t=rememberInfiniteTransition(label="busy-glass");t.animateFloat(0f,360f,infiniteRepeatable(tween(950,easing=LinearEasing)),label="rotation")} else remember{mutableFloatStateOf(0f)}
    GlassSurface(modifier.size(38.dp).semantics{progressBarRangeInfo=ProgressBarRangeInfo.Indeterminate},radius=19.dp,role=GlassRole.THUMB) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)){drawArc(p.accent,angle.value,235f,false,style=Stroke(strokeWidth.toPx(),cap=StrokeCap.Round))}
    }
}

@Composable fun GlassTopAppBar(title:@Composable ()->Unit,modifier:Modifier=Modifier,
    navigationIcon:@Composable ()->Unit={},actions:@Composable RowScope.()->Unit={}) {
    GlassSurface(modifier.fillMaxWidth(),radius=0.dp,role=GlassRole.NAVIGATION) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min=64.dp).padding(horizontal=12.dp,vertical=6.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            navigationIcon()
            Box(Modifier.weight(1f)){ProvideTextStyle(MaterialTheme.typography.titleLarge){title()}}
            actions()
        }
    }
}

@Composable fun GlassSnackbar(data:SnackbarData) {
    GlassSurface(Modifier.padding(horizontal=16.dp).fillMaxWidth().semantics{liveRegion=LiveRegionMode.Polite},radius=22.dp,role=GlassRole.MENU) {
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(data.visuals.message,Modifier.weight(1f),color=LocalLumaPalette.current.ink)
            data.visuals.actionLabel?.let{label->GlassTextButton(onClick={data.performAction()}){Text(label)}}
            if(data.visuals.withDismissAction)GlassIconButton({data.dismiss()}){Icon(Icons.Outlined.Close,"关闭提示")}
        }
    }
}

/** Own browser/player chrome uses this host. WebView pages and video SurfaceView pixels remain
 * platform/player content, not captured from private pages into a shader or relabelled as app UI.
 */
@Composable fun GlassWindow(content:@Composable ()->Unit) {
    val source=rememberGlassSource();val overlays=remember{GlassOverlayState()}
    CompositionLocalProvider(LocalBackdropHaze provides source,LocalContentHaze provides source,
        LocalControlHaze provides source,LocalOverlayGlass provides source,LocalGlassOverlay provides overlays) {
        Box(Modifier.fillMaxSize().background(LocalLumaPalette.current.background)) {
            GlassBackdrop(Modifier.matchParentSize().captureGlass(source))
            content()
            GlassOverlayHost(overlays,Modifier.matchParentSize())
        }
    }
}

@Composable fun GlassBadge(label:String) {
    val p=LocalLumaPalette.current
    GlassSurface(Modifier.defaultMinSize(18.dp,18.dp),radius=12.dp,role=GlassRole.THUMB,tint=p.accent.copy(alpha=.42f),fallback=p.accent) {
        Text(label,Modifier.align(Alignment.Center).padding(horizontal=4.dp),fontSize=10.sp,color=p.ink)
    }
}
