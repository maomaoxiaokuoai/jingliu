package com.luma.downloader.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luma.downloader.data.*
import com.luma.downloader.ui.optics.*

// Legacy Local names intentionally kept to avoid breaking unrelated business/UI call sites.
// Their type is now GlassSource, not HazeState; no Haze renderer is active.
val LocalContentHaze=staticCompositionLocalOf<GlassSource?>{null}
val LocalBackdropHaze=staticCompositionLocalOf<GlassSource?>{null}
val LocalControlHaze=staticCompositionLocalOf<GlassSource?>{null}
// Only read by a portal rendered outside the page capture, never by the page being captured.
val LocalOverlayGlass=staticCompositionLocalOf<GlassSource?>{null}
val GlassRenderingKey=SemanticsPropertyKey<String>("GlassRendering")
var SemanticsPropertyReceiver.glassRendering by GlassRenderingKey

@Composable fun GlassSurface(
    modifier:Modifier=Modifier, radius:Dp=32.dp, role:GlassRole=GlassRole.NAVIGATION,
    tint:Color=Color.Unspecified, fallback:Color=Color.Unspecified,
    materialOutput:GlassSource?=null, sourceOverride:GlassSource?=null, pressed:Float=0f, pressProgress:(()->Float)?=null, lightPosition:(()->Offset)?=null, motionSample:()->Float={0f},
    content:@Composable BoxScope.()->Unit,
) {
    val p=LocalLumaPalette.current;val s=LocalUiSettings.current
    val source=sourceOverride ?: when(role) {
        GlassRole.MENU->LocalOverlayGlass.current ?: LocalContentHaze.current ?: LocalBackdropHaze.current
        GlassRole.GROUP->LocalBackdropHaze.current
        GlassRole.BUTTON,GlassRole.FIELD,GlassRole.THUMB->LocalControlHaze.current ?: LocalBackdropHaze.current
        else ->LocalContentHaze.current ?: LocalBackdropHaze.current
    }
    val supported=Build.VERSION.SDK_INT>=31 && LocalView.current.isHardwareAccelerated
    val plan=remember(s,role,supported,source!=null){GlassPolicy.plan(s,role,supported,source!=null)}
    val shape=remember(radius){RoundedCornerShape(radius)}
    val fallbackColor=if(fallback.isSpecified)fallback else p.group
    val state=rememberUpdatedState(pressed)
    val reader=remember(pressProgress){pressProgress ?: {state.value}}
    val ancestorMotion=LocalGlassMotion.current
    val motion=rememberUpdatedState(motionSample)
    val optical=opticalBacking(source,plan,role,s,radius,fallbackColor,tint) {ancestorMotion()+motion.value()}
    Box(modifier.shadow(plan.shadow.dp,shape,clip=false).clip(shape).semantics {
        glassRendering="${role.name}:${if(!plan.enabled)"solid" else if(Build.VERSION.SDK_INT>=33 && !OpticalRuntime.shaderDisabled && s.text("material") in setOf("glass","regular") && s.number("refraction")>0f)"lens" else "blur"}"
    }) {
        Box(Modifier.matchParentSize().then(if(materialOutput!=null)Modifier.captureGlass(materialOutput) else Modifier).then(optical).glassFinish(plan,radius,p.dark,reader,lightPosition))
        content()
    }
}

/** Static, deterministic texture. No bitmaps allocated per frame; no idle animation loop. */
private object GrainTexture {
    val image:ImageBitmap by lazy {
        val pixels=GrainPixels.create(64)
        Bitmap.createBitmap(pixels,64,64,Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
private fun Modifier.glassFinish(plan:GlassPlan,radius:Dp,dark:Boolean,pressed:()->Float,lightPosition:(()->Offset)?)=drawWithCache {
    val corner=CornerRadius(radius.toPx(),radius.toPx())
    val stroke=Stroke(.8.dp.toPx())
    val edge=Brush.linearGradient(listOf(
        Color.White.copy(alpha=plan.rim), Color.White.copy(alpha=plan.rim*.20f),
        Color.Black.copy(alpha=plan.rim*(if(dark).26f else .10f)), Color.White.copy(alpha=plan.rim*.5f)),
        start=Offset.Zero,end=Offset(size.width,size.height))
    val sheen=Brush.verticalGradient(listOf(Color.White.copy(alpha=plan.rim*.25f),Color.Transparent,
        Color.White.copy(alpha=plan.rim*.04f)))
    var cachedPoint=Offset(.4f,.15f)
    var touch=Brush.radialGradient(listOf(Color.White.copy(alpha=.22f),Color.Transparent),
        center=Offset(size.width*.4f,size.height*.15f),radius=size.width.coerceAtLeast(1f))
    val noise=if(plan.noise>0f)ShaderBrush(ImageShader(GrainTexture.image,TileMode.Repeated,TileMode.Repeated)) else null
    onDrawWithContent {
        drawContent()
        if(plan.enabled) {
            drawRect(sheen)
            if(noise!=null)drawRect(noise,alpha=plan.noise)
            val strength=pressed().coerceIn(0f,1f)
            if(strength>0f) {
                val point=lightPosition?.invoke()?:cachedPoint
                if(point!=cachedPoint){cachedPoint=point;touch=Brush.radialGradient(listOf(Color.White.copy(alpha=.22f),Color.Transparent),
                    center=Offset(size.width*point.x,size.height*point.y),radius=size.width.coerceAtLeast(1f))}
                drawRect(touch,alpha=strength)
            }
            val inset=.5.dp.toPx()
            drawRoundRect(edge,Offset(inset,inset),Size((size.width-inset*2).coerceAtLeast(0f),(size.height-inset*2).coerceAtLeast(0f)),corner,style=stroke)
        } else {
            val strength=pressed().coerceIn(0f,1f)
            if(strength>0f)drawRect(Color.White,alpha=strength*.12f)
        }
    }
}

/** Quiet pearl backdrop. Variation makes translucent surfaces visible without moving light blobs. */
@Composable fun GlassBackdrop(modifier:Modifier=Modifier) {
    val p=LocalLumaPalette.current;val s=LocalUiSettings.current
    val active=s.text("material")!="solid" && !s.enabled("reduceTransparency") && GlassRole.entries.any{s.enabled(it.setting)}
    Box(modifier.background(p.background).drawWithCache {
        val base=if(p.dark)Color(0xFF181D26) else Color(0xFFDDE6F0)
        val soft=Brush.linearGradient(listOf(p.background,base.copy(alpha=if(active).72f else 0f),p.background),
            start=Offset(-size.width*.1f,0f),end=Offset(size.width,size.height*.82f))
        val band=Brush.linearGradient(listOf(Color.Transparent,p.accent.copy(alpha=if(active) .045f else 0f),Color.Transparent),
            start=Offset(0f,size.height*.16f),end=Offset(size.width,size.height*.58f))
        onDrawBehind {drawRect(soft);drawRect(band)}
    })
}
