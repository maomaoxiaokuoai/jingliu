package com.luma.downloader.data

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 0.9.1 liquid-suite pure optics math.
 *
 * Kyant0/AndroidLiquidGlass (Apache-2.0, commit 65ab177) contributes the layered
 * dock/lens separation, arc-tinted bevel and velocity stretch model.
 * QWEA0/Liquid-Glass-Android (MIT, commit 73e2253) contributes inverse-power edge
 * refraction, per-corner SDF, smooth-min dual-shape fusion, press bulge and
 * saturation-protected sampling.
 *
 * This file is the Jingliu-side reimplementation: pure Kotlin, no Android or
 * Compose dependency, so it is covered by JVM unit tests. The AGSL in
 * [com.luma.downloader.ui.optics.LiquidShader] mirrors these curves on GPU.
 */
object LiquidFusionMath {
    /** Polynomial smooth-min for dual-glass adhesion (QWEA fusion). k in px, >= 0. */
    fun smin(a: Float, b: Float, k: Float): Float {
        if (!a.isFinite() || !b.isFinite() || !k.isFinite() || k <= 0f) return minOf(
            if (a.isFinite()) a else Float.MAX_VALUE,
            if (b.isFinite()) b else Float.MAX_VALUE,
        )
        val h = ((k - abs(a - b)) / k).coerceIn(0f, 1f)
        return minOf(a, b) - h * h * k * 0.25f
    }

    /** Fusion amount 0..1 from pill distance: 1 = fully glued, 0 = separate. */
    fun fusionFactor(distance: Float, radiusSum: Float, softness: Float): Float {
        if (!distance.isFinite() || !radiusSum.isFinite() || !softness.isFinite()) return 0f
        if (radiusSum <= 0f || softness <= 0f) return 0f
        return ((radiusSum + softness - distance) / softness).coerceIn(0f, 1f)
    }

    /** Inverse-power edge tail (QWEA): strong at rim, ~0 one bevel inside. */
    fun inversePowerTail(inside: Float, bevel: Float): Float {
        if (!inside.isFinite() || !bevel.isFinite()) return 0f
        val band = maxOf(bevel, 1f)
        val fall = 1f / ((1f + inside / maxOf(band * 0.25f, 0.5f)) *
            (1f + inside / maxOf(band * 0.25f, 0.5f)))
        val cut = 1f - smoothstep(band * 0.65f, band, inside)
        return (fall * cut).coerceIn(0f, 1f)
    }

    /** Kyant-style circular-arc bevel: 1 at rim, easing to 0 at [bevel] inside. */
    fun arcBevel(inside: Float, bevel: Float): Float {
        if (!inside.isFinite() || !bevel.isFinite() || bevel <= 0f) return 0f
        val t = (inside / bevel).coerceIn(0f, 1f)
        // 1 - t^1.6 approximates a circular lens arc without trig on CPU.
        return (1f - Math.pow(t.toDouble(), 1.6).toFloat()).coerceIn(0f, 1f)
    }

    /** Local touch bulge falloff: 1 at finger, ~0 beyond ~2.2 sigma. */
    fun touchFalloff(distance: Float, sigma: Float): Float {
        if (!distance.isFinite() || !sigma.isFinite() || sigma <= 0f) return 0f
        if (distance < 0f) return 1f
        val z = distance / sigma
        return exp(-0.5f * z * z).coerceIn(0f, 1f)
    }

    /** Edge-soft mask width in px from 0..100 setting. */
    fun edgeSoftPx(percent: Float): Float {
        if (!percent.isFinite()) return 1f
        return (0.5f + percent.coerceIn(0f, 100f) / 100f * 3.5f).coerceIn(0.5f, 4f)
    }

    /**
     * Map accelerometer (m/s^2, device frame) to a 0..1 glass highlight point.
     * Handles portrait/landscape remap, flat-device fallback and low-pass inputs.
     * Pure function so sensor filtering stays testable; Android listener lives in
     * GravityLight.kt.
     */
    fun gravityToLight(
        ax: Float, ay: Float, az: Float,
        portrait: Boolean = true,
        flatThreshold: Float = 3.0f,
    ): Pair<Float, Float> {
        if (!ax.isFinite() || !ay.isFinite() || !az.isFinite()) return 0.35f to 0.15f
        val flat = abs(az) > 9.0f - flatThreshold * 0.2f && sqrt(ax * ax + ay * ay) < flatThreshold
        if (flat) return 0.35f to 0.15f
        val nx = (if (portrait) -ax else ay) / 9.81f
        val ny = (if (portrait) ay else ax) / 9.81f
        val x = (0.5f + (nx * 0.28f)).coerceIn(0.05f, 0.95f)
        val y = (0.18f + (ny * 0.22f)).coerceIn(0.05f, 0.9f)
        return x to y
    }

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (!x.isFinite() || !edge0.isFinite() || !edge1.isFinite() || edge0 == edge1) return 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
