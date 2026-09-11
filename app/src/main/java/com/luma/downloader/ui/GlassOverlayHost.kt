package com.luma.downloader.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import com.luma.downloader.data.*
import com.luma.downloader.ui.optics.LocalGlassMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

val LocalGlassOverlay=staticCompositionLocalOf<GlassOverlayState>{error("Missing root GlassOverlayHost")}
internal val LocalOverlayInput=staticCompositionLocalOf{true}
internal val LocalOverlaySelect=staticCompositionLocalOf<((()->Unit)->Unit)?>{null}

/** A single Activity window: opening a menu must not create a new Android Popup/Window. */
@Stable class GlassOverlayState {
    internal var current by mutableStateOf<GlassOverlayEntry?>(null)
    val visible:Boolean get()=current!=null
    internal fun show(entry:GlassOverlayEntry){if(current!==entry)current?.dispose();current=entry}
    internal fun remove(entry:GlassOverlayEntry){if(current===entry)current=null}
}

internal enum class OverlayKind { MENU, DIALOG }
internal class GlassOverlayEntry(
    val host:GlassOverlayState,
    val scope:CoroutineScope,
    val kind:OverlayKind,
    val locals:()->CompositionLocalContext,
    val title:()->String,
    val secure:Boolean,
    val settings:()->UiSettings,
    val mode:()->MotionMode,
    val dismiss:()->Unit,
    val body:@Composable ()->Unit,
) {
    var anchor by mutableStateOf(Rect.Zero)
    var accepting by mutableStateOf(false)
    val scale=Animatable(0f)
    val opacity=Animatable(0f)
    private val lifecycle=OverlayLifecycle()
    private var job:Job?=null
    fun open() {
        val ticket=lifecycle.open();accepting=true;host.show(this);job?.cancel()
        job=scope.launch {
            // Root sources already exist in the same window. Do not queue two silent frames
            // before responding to a tap; the alpha animation starts on the next normal frame.
            val policy=settings();val motion=mode()
            val fade=if(motion==MotionMode.OFF)0 else 140
            launch {opacity.animateTo(1f,if(fade==0)snap()else tween(fade,easing=LinearEasing))}
            scale.animateTo(1f,policy.motionSpec(MotionChannel.MENU,motion))
            lifecycle.opened(ticket)
        }
    }
    fun close(action:(()->Unit)?=null) {
        val ticket=lifecycle.close()?:return
        accepting=false;job?.cancel()
        job=scope.launch {
            val motion=mode();val policy=settings()
            launch {scale.animateTo(0f,policy.motionSpec(MotionChannel.MENU,motion))}
            // Alpha is monotonic, not an under-damped spring. No fade out/in pulse at settling.
            opacity.animateTo(0f,if(motion==MotionMode.OFF)snap()else tween(110,easing=LinearEasing))
            if(lifecycle.closed(ticket) && host.current===this@GlassOverlayEntry) {
                host.remove(this@GlassOverlayEntry)
                dismiss()
                action?.invoke() // e.g. theme/material changes happen after the old menu is invisible.
            }
        }
    }
    fun dispose(){job?.cancel();lifecycle.dispose();accepting=false;host.remove(this)}
}

/** Pointer and accessibility ownership stay with the overlay until its closing fade has finished. */
fun Modifier.hideBehindOverlay(hidden:Boolean):Modifier = if(!hidden)this else
    this.focusProperties{canFocus=false}.clearAndSetSemantics{}

/** Prevent clicks from reaching a visible but inactive outgoing/underlying pane. */
fun Modifier.suppressPaneInput(blocked:Boolean):Modifier = if(!blocked)this else pointerInput(Unit) {
    awaitPointerEventScope {while(true) {
        val event=awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach{it.consume()}
    }}
}

@Composable fun GlassOverlayHost(state:GlassOverlayState,modifier:Modifier=Modifier) {
    var origin by remember {mutableStateOf(Offset.Zero)}
    Box(modifier.onGloballyPositioned {origin=it.positionInRoot()}) {
        val entry=state.current?:return@Box
        key(entry) {
            val interactions=remember {MutableInteractionSource()}
            val requester=remember {FocusRequester()}
            BackHandler {entry.close()}
            val activity=LocalContext.current.findActivity()
            DisposableEffect(entry,activity) {
                val alreadySecure=(activity?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE)?:0)!=0
                if(entry.secure)activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                onDispose {if(entry.secure&&!alreadySecure)activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)}
            }
            LaunchedEffect(entry) {withFrameNanos{};requester.requestFocus()}
            val dp=LocalDensity.current
            val gutter=with(dp){14.dp.roundToPx()}
            val margin=with(dp){10.dp.roundToPx()}
            val gap=with(dp){6.dp.roundToPx()}
            val preferred=with(dp){(if(entry.kind==OverlayKind.MENU)340.dp else 420.dp).roundToPx()}
            val windowPadding=WindowInsets.ime.union(WindowInsets.navigationBars).asPaddingValues()
            Box(Modifier.fillMaxSize().drawBehind {if(entry.kind==OverlayKind.DIALOG)drawRect(Color.Black,alpha=.22f*entry.opacity.value.coerceIn(0f,1f))}
                .clickable(interactionSource=interactions,indication=null){entry.close()})
            SubcomposeLayout(Modifier.fillMaxSize().padding(windowPadding)
                .focusRequester(requester).focusable().semantics{paneTitle=entry.title()}
                .testTag("glass-overlay-host")) {constraints ->
                val width=constraints.maxWidth;val height=constraints.maxHeight
                val availableWidth=(width-2*(margin+gutter)).coerceAtLeast(1)
                val availableHeight=(height-2*(margin+gutter)).coerceAtLeast(1)
                val popupWidth=minOf(preferred,availableWidth)+gutter*2
                val popupConstraints=Constraints(minWidth=popupWidth,maxWidth=popupWidth,maxHeight=availableHeight+2*gutter)
                val placeable=subcompose(entry) {
                    CompositionLocalProvider(entry.locals()) {
                        CompositionLocalProvider(LocalGlassMotion provides {entry.scale.value+entry.opacity.value}, LocalOverlayInput provides entry.accepting,
                            LocalOverlaySelect provides {action->entry.close(action)}) {
                            Box(Modifier.padding(14.dp)) {entry.body()}
                        }
                    }
                }.single().measure(popupConstraints)
                val placement=if(entry.kind==OverlayKind.MENU)MenuPolicy.place(
                    MenuAnchor((entry.anchor.left-origin.x).toInt(),(entry.anchor.top-origin.y).toInt(),
                        (entry.anchor.right-origin.x).toInt(),(entry.anchor.bottom-origin.y).toInt()),
                    width,height,placeable.width,placeable.height,layoutDirection==LayoutDirection.Rtl,margin,gap,gutter)
                else MenuPlacement((width-placeable.width)/2,(height-placeable.height)/2,false,.5f,.5f)
                layout(width,height) {
                    placeable.placeWithLayer(placement.x,placement.y) {
                        val frame=MenuPolicy.frame(entry.scale.value,entry.mode()!=MotionMode.OFF&&entry.settings().enabled("menuMorph"))
                        alpha=entry.opacity.value.coerceIn(0f,1f)
                        scaleX=frame.scale;scaleY=frame.scale
                        transformOrigin=TransformOrigin(placement.pivotX,placement.pivotY)
                    }
                }
            }
        }
    }
}
private tailrec fun Context.findActivity():Activity?=when(this){is Activity->this;is ContextWrapper->baseContext.findActivity();else->null}
