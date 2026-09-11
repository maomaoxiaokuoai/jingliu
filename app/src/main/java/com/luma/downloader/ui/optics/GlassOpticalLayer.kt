package com.luma.downloader.ui.optics

import android.graphics.RenderEffect
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.luma.downloader.data.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Replays the backing RenderNode into a bounded padded offscreen layer. Foreground is not filtered.
 * API 33+: AGSL lens + blur. API 31/32: RenderEffect blur. Older/software: opaque fallback.
 */
@Composable
fun opticalBacking(source: GlassSource?, plan: GlassPlan, role: GlassRole, settings: UiSettings,
                   radius: Dp, fallback: Color, tintColor: Color, motion:()->Float): Modifier {
    val destination = rememberGraphicsLayer()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var rootPosition by remember{mutableStateOf(Offset.Zero)}
    val hardware = LocalView.current.isHardwareAccelerated
    return Modifier.onGloballyPositioned { coordinates = it;rootPosition=it.positionInRoot() }.drawWithCache {
        val width = size.width.coerceAtLeast(1f); val height = size.height.coerceAtLeast(1f)
        val lens = LensPolicy.plan(settings, role, Build.VERSION.SDK_INT, hardware, plan.enabled,
            min(width,height), density)
        val r = radius.toPx().coerceIn(0f, min(width,height)/2f)
        val blur = plan.blur * density
        // Gaussian reads around the glass boundary. Capture a margin so corners don't smear black.
        val pad = ceil(blur * 2f + lens.depth * 1.25f + 2f).toInt().coerceAtLeast(2)
        val recordingSize = IntSize((width.toInt()+pad*2).coerceAtLeast(1), (height.toInt()+pad*2).coerceAtLeast(1))
        val effect = if(lens.tier != OpticalTier.SOLID) runCatching {
            if(Build.VERSION.SDK_INT>=33 && lens.tier==OpticalTier.LENS && !OpticalRuntime.shaderDisabled) Api33.effect(width,height,pad.toFloat(),r,lens,blur)
            else if(Build.VERSION.SDK_INT>=31) Api31.saturatedBlur(blur,lens.saturation) else null
        }.recoverCatching { error ->
            OpticalRuntime.report(error)
            if(Build.VERSION.SDK_INT>=31)Api31.saturatedBlur(blur,lens.saturation) else null
        }.getOrNull() else null
        destination.renderEffect = effect?.asComposeRenderEffect()
        val matrix = Matrix()
        onDrawBehind {
            // Subscribe to geometry and ancestor animation without reading it in composition.
            @Suppress("UNUSED_VARIABLE") val frame=motion()+rootPosition.x+rootPosition.y
            drawRect(fallback)
            val from=source?.coordinates;val to=coordinates
            // Observing source changes here invalidates only drawing, not the composable tree.
            val revision=source?.revision ?: 0L
            if(lens.tier!=OpticalTier.SOLID && source!=null && revision>0 && source.recorded &&
                from?.isAttached==true && to?.isAttached==true && (effect!=null || blur==0f)) {
                val origin=to.localPositionOf(from, Offset.Zero)
                val axisX=to.localPositionOf(from, Offset(1f,0f))-origin
                val axisY=to.localPositionOf(from, Offset(0f,1f))-origin
                matrix.reset()
                matrix[0,0]=axisX.x;matrix[0,1]=axisX.y
                matrix[1,0]=axisY.x;matrix[1,1]=axisY.y
                matrix[3,0]=origin.x+pad;matrix[3,1]=origin.y+pad
                destination.record(size=recordingSize) {
                    drawRect(fallback)
                    withTransform({ transform(matrix) }) { drawLayer(source.layer) }
                }
                translate(-pad.toFloat(),-pad.toFloat()) { drawLayer(destination) }
                drawRect(fallback,alpha=plan.tint)
                if(tintColor!=Color.Unspecified)drawRect(tintColor)
            }
        }
    }
}

@RequiresApi(31)
private object Api31 {
    fun saturatedBlur(radius: Float, saturation: Float):RenderEffect {
        val color=RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(ColorMatrix().apply{setSaturation(saturation)}))
        val blur=blur(radius)
        return if(blur!=null)RenderEffect.createChainEffect(color,blur) else color
    }
    fun blur(radius: Float): RenderEffect? = if(radius>0f)
        RenderEffect.createBlurEffect(radius,radius,Shader.TileMode.CLAMP) else null
}
@RequiresApi(33)
private object Api33 {
    private data class Key(val width:Float,val height:Float,val pad:Float,val radius:Float,val lens:LensPlan,val blur:Float)
    private val effects=object:LinkedHashMap<Key,RenderEffect>(48,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<Key,RenderEffect>):Boolean = size>48
    }
    @Synchronized
    fun effect(width:Float,height:Float,pad:Float,radius:Float,lens:LensPlan,blur:Float):RenderEffect {
        val key=Key(width,height,pad,radius,lens,blur)
        effects[key]?.let{return it}
        val shader=RuntimeShader(LiquidShader.SOURCE)
        shader.setFloatUniform("glassSize",width,height)
        shader.setFloatUniform("padding",pad)
        shader.setFloatUniform("radius",radius)
        shader.setFloatUniform("depth",lens.depth)
        shader.setFloatUniform("bevel",lens.bevel)
        shader.setFloatUniform("dispersion",lens.dispersion)
        shader.setFloatUniform("saturation",lens.saturation)
        val refracted=RenderEffect.createRuntimeShaderEffect(shader,"scene")
        val blurred=Api31.blur(blur)
        return (if(blurred!=null)RenderEffect.createChainEffect(refracted,blurred) else refracted).also{effects[key]=it}
    }
}

object OpticalRuntime {
    var shaderDisabled by mutableStateOf(false)
        private set
    private val reported=java.util.concurrent.atomic.AtomicBoolean(false)
    fun report(error:Throwable) {
        if(reported.compareAndSet(false,true)) {
            shaderDisabled=true
            android.util.Log.w("JingliuGlass", "Optical effect initialization failed; retrying blur, then readable solid fallback. ${error.javaClass.simpleName}")
        }
    }
}
