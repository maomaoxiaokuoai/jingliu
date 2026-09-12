package com.luma.downloader.data

import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure geometry shared by pointer input and regression tests. No platform or Compose dependency. */
object LiquidGestureMath {
    fun tabFraction(x: Float, width: Float, count: Int, rtl: Boolean = false): Float {
        require(count > 0)
        if (!x.isFinite() || !width.isFinite() || width <= 0f) return 0f
        val physical = if (rtl) width - x else x
        return (physical / width * count - .5f).coerceIn(0f, (count - 1).toFloat())
    }
    fun nearestTab(fraction: Float, count: Int): Int {
        require(count > 0)
        return (if (fraction.isFinite()) fraction else 0f).roundToInt().coerceIn(0, count - 1)
    }
    fun switchTarget(fraction: Float, velocity: Float, completed: Boolean, checked: Boolean): Boolean {
        if (!completed) return checked
        val speed = if (velocity.isFinite()) velocity else 0f
        return if (abs(speed) > 8f) speed > 0f else (if (fraction.isFinite()) fraction else 0f) >= .5f
    }
    fun stretchX(velocity: Float): Float {
        val speed = if (velocity.isFinite()) abs(velocity) else 0f
        return 1f / (1f - (speed * .012f).coerceIn(0f, .12f))
    }
    fun stretchY(velocity: Float): Float {
        val speed = if (velocity.isFinite()) abs(velocity) else 0f
        return 1f - (speed * .006f).coerceIn(0f, .08f)
    }
    fun pageVisible(index: Int, position: Float): Boolean =
        position.isFinite() && kotlin.math.abs(index - position) < 1f

    fun pageOffset(index: Int, position: Float, width: Float, rtl: Boolean = false): Float {
        if (!position.isFinite() || !width.isFinite() || width < 0f) return 0f
        return (index - position) * width * if (rtl) -1f else 1f
    }
}
