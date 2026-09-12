package com.luma.downloader.data

import kotlin.math.abs

/** No Android or JUnit dependency: the same assertions run via kotlinc locally and JUnit in CI. */
object LiquidGestureRegression {
    @JvmStatic
    fun main(args: Array<String>) {
        var groups = 0
        var assertions = 0
        fun expect(ok: Boolean, message: String) {
            assertions++
            check(ok) { message }
        }
        fun near(a: Float, b: Float, message: String) = expect(abs(a - b) < .003f, "$message: $a != $b")
        fun group(name: String, body: () -> Unit) {
            body(); groups++; println("PASS $name")
        }
        group("LTR tab centers") {
            for (count in 1..8) for (width in listOf(1f, 320f, 1080f)) for (tab in 0 until count)
                near(LiquidGestureMath.tabFraction((tab + .5f) * width / count, width, count), tab.toFloat(), "center")
        }
        group("RTL tab centers") {
            for (count in 1..8) for (width in listOf(1f, 320f, 1080f)) for (tab in 0 until count)
                near(LiquidGestureMath.tabFraction(width - (tab + .5f) * width / count, width, count, true), tab.toFloat(), "RTL center")
        }
        group("finger follows continuously") {
            for (step in 50..350) near(LiquidGestureMath.tabFraction(step.toFloat(), 400f, 4), step / 100f - .5f, "fraction")
        }
        group("offscreen finger clamps") {
            for (x in listOf(-10000f, -1f, 0f)) near(LiquidGestureMath.tabFraction(x, 400f, 4), 0f, "left bound")
            for (x in listOf(400f, 10000f)) near(LiquidGestureMath.tabFraction(x, 400f, 4), 3f, "right bound")
        }
        group("RTL mirror equivalence") {
            for (x in -20..420) near(LiquidGestureMath.tabFraction(x.toFloat(), 400f, 4, true),
                LiquidGestureMath.tabFraction(400f - x, 400f, 4), "mirror")
        }
        group("invalid geometry cannot yield NaN") {
            for (x in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY))
                near(LiquidGestureMath.tabFraction(x, 400f, 4), 0f, "invalid x")
            for (width in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY))
                near(LiquidGestureMath.tabFraction(25f, width, 4), 0f, "invalid width")
        }
        group("nearest tab and crossing boundaries") {
            expect(LiquidGestureMath.nearestTab(.499f, 4) == 0, "before crossing")
            expect(LiquidGestureMath.nearestTab(.5f, 4) == 1, "crossing")
            expect(LiquidGestureMath.nearestTab(2.5f, 4) == 3, "last crossing")
            expect(LiquidGestureMath.nearestTab(99f, 4) == 3, "upper clamp")
            expect(LiquidGestureMath.nearestTab(-99f, 4) == 0, "lower clamp")
            expect(LiquidGestureMath.nearestTab(Float.NaN, 4) == 0, "NaN selection")
        }
        group("invalid counts rejected") {
            expect(runCatching { LiquidGestureMath.tabFraction(0f, 100f, 0) }.isFailure, "zero count")
            expect(runCatching { LiquidGestureMath.nearestTab(0f, -1) }.isFailure, "negative count")
        }
        group("cancelled switch never commits") {
            for (checked in listOf(false, true)) for (fraction in listOf(-1f, 0f, .2f, .8f, 1f, Float.NaN))
                for (velocity in listOf(-20f, 0f, 20f, Float.NaN))
                    expect(LiquidGestureMath.switchTarget(fraction, velocity, false, checked) == checked, "cancel changed value")
        }
        group("slow switch uses midpoint") {
            expect(!LiquidGestureMath.switchTarget(.49f, 0f, true, true), "low fraction")
            expect(LiquidGestureMath.switchTarget(.5f, 0f, true, false), "midpoint")
            expect(LiquidGestureMath.switchTarget(.9f, 0f, true, false), "high fraction")
        }
        group("fast switch respects fling direction") {
            expect(LiquidGestureMath.switchTarget(.1f, 9f, true, false), "fling on")
            expect(!LiquidGestureMath.switchTarget(.9f, -9f, true, true), "fling off")
            expect(!LiquidGestureMath.switchTarget(.1f, 8f, true, true), "threshold is exclusive")
        }
        group("bad velocity is harmless") {
            for (v in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                expect(LiquidGestureMath.switchTarget(.8f, v, true, false), "invalid speed")
                near(LiquidGestureMath.stretchX(v), 1f, "invalid X stretch")
                near(LiquidGestureMath.stretchY(v), 1f, "invalid Y stretch")
            }
        }
        group("stretch is finite and bounded") {
            for (velocity in -10000..10000) {
                val x = LiquidGestureMath.stretchX(velocity.toFloat())
                val y = LiquidGestureMath.stretchY(velocity.toFloat())
                expect(x.isFinite() && x in 1f..1.137f, "stretchX out of bounds")
                expect(y.isFinite() && y in .919f..1f, "stretchY out of bounds")
            }
        }
        group("at most two retained pages draw") {
            for (i in 0..3000) {
                val position = i / 1000f
                val visible = (0..3).filter { LiquidGestureMath.pageVisible(it, position) }
                expect(visible.size in 1..2, "blank page or excessive layers at $position")
                if (visible.size == 2) expect(visible[1] - visible[0] == 1, "not adjacent")
            }
        }
        group("adjacent pages cover the viewport without a gap") {
            for (i in 1..2999) {
                val position = i / 1000f
                val visible = (0..3).filter { LiquidGestureMath.pageVisible(it, position) }
                if (visible.size == 2) {
                    val left = LiquidGestureMath.pageOffset(visible[0], position, 1080f)
                    val right = LiquidGestureMath.pageOffset(visible[1], position, 1080f)
                    near(left + 1080f, right, "page seam")
                    expect(left <= 0f && right >= 0f, "viewport coverage")
                }
            }
        }
        group("RTL page motion is mirrored") {
            for (i in 0..300) for (page in 0..3) near(
                LiquidGestureMath.pageOffset(page, i / 100f, 720f, true),
                -LiquidGestureMath.pageOffset(page, i / 100f, 720f), "RTL offset")
        }
        group("invalid page geometry falls back safely") {
            expect(!LiquidGestureMath.pageVisible(1, Float.NaN), "invalid page visible")
            near(LiquidGestureMath.pageOffset(1, Float.NaN, 1080f), 0f, "invalid position")
            near(LiquidGestureMath.pageOffset(1, 0f, Float.NaN), 0f, "invalid width")
        }
        println("RESULT: $groups groups, $assertions assertions passed. Pure Kotlin only; Android UI not exercised.")
    }
}
