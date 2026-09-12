package com.luma.downloader.data

import kotlin.math.abs

/** Pure-JVM checks for 0.9.1 fusion/gravity math. No Android dependency. */
object LiquidOpticsRegression {
    @JvmStatic
    fun main(args: Array<String>) {
        var groups = 0
        var assertions = 0
        fun expect(ok: Boolean, message: String) {
            assertions++
            check(ok) { message }
        }
        fun near(a: Float, b: Float, message: String) = expect(abs(a - b) < .004f, "$message: $a != $b")
        fun group(name: String, body: () -> Unit) {
            body(); groups++; println("PASS $name")
        }
        group("smin glues close shapes") {
            val glued = LiquidFusionMath.smin(4f, 6f, 12f)
            expect(glued < 4f, "close pills must dip below min")
            near(LiquidFusionMath.smin(0f, 100f, 8f), 0f, "far pills stay separate")
            near(LiquidFusionMath.smin(5f, 5f, 0f), 5f, "zero k falls back to min")
        }
        group("fusion factor is bounded") {
            near(LiquidFusionMath.fusionFactor(0f, 40f, 16f), 1f, "overlap fuses")
            expect(LiquidFusionMath.fusionFactor(200f, 40f, 16f) == 0f, "far pills separate")
            for (d in 0..120) {
                val f = LiquidFusionMath.fusionFactor(d.toFloat(), 48f, 20f)
                expect(f.isFinite() && f in 0f..1f, "fusion bounds at $d")
            }
        }
        group("inverse power tail decays") {
            near(LiquidFusionMath.inversePowerTail(0f, 20f), 1f, "rim is strongest")
            expect(LiquidFusionMath.inversePowerTail(20f, 20f) < 0.05f, "one bevel inside fades")
            for (i in 0..60) {
                val t = LiquidFusionMath.inversePowerTail(i.toFloat(), 20f)
                expect(t.isFinite() && t in 0f..1f, "tail bounds at $i")
            }
        }
        group("arc bevel eases inward") {
            near(LiquidFusionMath.arcBevel(0f, 20f), 1f, "rim arc")
            expect(LiquidFusionMath.arcBevel(20f, 20f) == 0f, "inner edge")
            expect(LiquidFusionMath.arcBevel(5f, 20f) > LiquidFusionMath.inversePowerTail(5f, 20f) * 0.4f, "arc stays visible")
        }
        group("touch falloff is local") {
            near(LiquidFusionMath.touchFalloff(0f, 60f), 1f, "finger center")
            expect(LiquidFusionMath.touchFalloff(200f, 60f) < 0.01f, "far field fades")
            expect(LiquidFusionMath.touchFalloff(Float.NaN, 60f) == 0f, "NaN safe")
        }
        group("edge soft maps to pixels") {
            for (p in listOf(0f, 25f, 55f, 100f)) {
                val px = LiquidFusionMath.edgeSoftPx(p)
                expect(px.isFinite() && px in 0.5f..4f, "soft px at $p")
            }
            expect(LiquidFusionMath.edgeSoftPx(Float.NaN) == 1f, "NaN fallback")
        }
        group("gravity light stays in glass") {
            for (ax in listOf(-9f, -4f, 0f, 4f, 9f)) for (ay in listOf(-9f, 0f, 9f)) {
                val (x, y) = LiquidFusionMath.gravityToLight(ax, ay, 2f, true)
                expect(x in 0.05f..0.95f && y in 0.05f..0.9f, "light bounds $ax,$ay")
            }
            val (fx, fy) = LiquidFusionMath.gravityToLight(0.2f, -0.3f, 9.6f, true)
            near(fx, 0.35f, "flat x parks")
            near(fy, 0.15f, "flat y parks")
            val (ix, iy) = LiquidFusionMath.gravityToLight(Float.NaN, 0f, 0f, true)
            near(ix, 0.35f, "invalid x")
            near(iy, 0.15f, "invalid y")
        }
        println("RESULT: $groups groups, $assertions assertions passed. Pure Kotlin only; Android UI not exercised.")
    }
}
