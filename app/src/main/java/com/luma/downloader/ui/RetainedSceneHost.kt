package com.luma.downloader.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import com.luma.downloader.data.*
import com.luma.downloader.ui.optics.LocalGlassMotion

/** A logical page can remain composed while it is not visible or interactive. */
val LocalSceneActive=compositionLocalOf { true }

/** Stable page identities, no AnimatedContent disposal and no waiting for an outgoing animation.
 * Only placement/layer properties read frame state. Hidden pages are NOT placed or drawn.
 * The platform Transition retargets from its current values/velocities on rapid taps.
 * Precompose the four small top-level roots once; LazyColumn still composes only its viewport. */
@Composable fun <K:Any> RetainedSceneHost(
    keys:List<K>, selected:K, distancePx:Float, channel:MotionChannel=MotionChannel.TAB,
    modifier:Modifier=Modifier, precompose:Boolean=true,
    content:@Composable (K)->Unit,
) {
    require(selected in keys && keys.distinct().size==keys.size)
    val s=LocalUiSettings.current;val mode=LocalAppearance.current.motion
    val parentActive=LocalSceneActive.current
    val parentMotion=LocalGlassMotion.current
    val holder=rememberSaveableStateHolder()
    val transition=updateTransition(selected,label="retained-scenes")
    val selectedIndex=keys.indexOf(selected)
    // Index order stays stable even during an interrupted three-way transition.
    val positions=keys.mapIndexed { index,item -> key(item) {
        transition.animateFloat(transitionSpec={s.motionSpec(channel,mode)},label="scene-offset-$index") {target ->
            if(mode==MotionMode.OFF)0f else ScenePolicy.offset(index,keys.indexOf(target),distancePx)
        }
    }}
    val weights=keys.mapIndexed { index,item -> key(item) {
        transition.animateFloat(transitionSpec={
            if(mode==MotionMode.OFF)snap() else tween(180,easing=LinearEasing)
        },label="scene-visibility-$index") {target -> if(target==item)1f else 0f}
    }}
    val visited=remember {mutableStateMapOf<K,Boolean>()}
    // First appearance is composed immediately, not after a delayed effect. SideEffect only
    // retains the identity for future visits; it never runs on frame-level position changes.
    SideEffect {if(!precompose)visited[selected]=true}
    val included=keys.filter{precompose || visited[it]==true || it==selected || it==transition.currentState}
    val frameWeights=remember(keys){FloatArray(keys.size)}
    Layout(modifier=modifier,content={
        included.forEach { item -> key(item) {
            val active=parentActive && item==selected
            Box(Modifier.fillMaxSize().testTag("scene-$item")
                .suppressPaneInput(!active).hideBehindOverlay(!active)) {
                CompositionLocalProvider(LocalSceneActive provides active, LocalGlassMotion provides {parentMotion()+positions[keys.indexOf(item)].value+weights[keys.indexOf(item)].value}) {
                    holder.SaveableStateProvider(item.toString()) {content(item)}
                }
            }
        }}
    }) {measurables,constraints ->
        val places=measurables.map{it.measure(constraints)}
        layout(constraints.maxWidth,constraints.maxHeight) {
            for(i in keys.indices)frameWeights[i]=weights[i].value
            included.forEachIndexed { child,item ->
                val index=keys.indexOf(item)
                if(ScenePolicy.draw(frameWeights[index],index==selectedIndex)) {
                    places[child].placeWithLayer(0,0,zIndex=if(index==selectedIndex)1f else 0f) {
                        translationX=positions[index].value
                        alpha=ScenePolicy.targetOnTopAlpha(index,selectedIndex,frameWeights)
                        clip=false
                    }
                }
            }
        }
    }
}

/** Keep a view tree but skip its GPU work when fully hidden; no zero-sized intermediate layout. */
@Composable fun RetainedPane(
    modifier:Modifier=Modifier, visible:()->Boolean, translation:()->Float={0f},
    content:@Composable ()->Unit,
) {
    val parentMotion=LocalGlassMotion.current
    Layout(modifier=Modifier.fillMaxSize(),content={Box(modifier.fillMaxSize()){CompositionLocalProvider(LocalGlassMotion provides {parentMotion()+translation()}){content()}}}) {nodes,c ->
        val child=nodes.single().measure(c)
        layout(c.maxWidth,c.maxHeight) {
            if(visible())child.placeWithLayer(0,0) {translationX=translation();clip=false}
        }
    }
}
