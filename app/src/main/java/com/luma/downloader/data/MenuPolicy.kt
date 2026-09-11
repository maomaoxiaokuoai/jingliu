package com.luma.downloader.data

/** Pure menu geometry/presentation rules; no Android or Compose types. */
data class MenuAnchor(val left: Int, val top: Int, val right: Int, val bottom: Int)
data class MenuPlacement(val x: Int, val y: Int, val above: Boolean, val pivotX: Float, val pivotY: Float)
data class MenuFrame(val alpha: Float, val scale: Float)
data class MenuSurfacePlan(val baseAlpha: Float, val decorated: Boolean, val rimAlpha: Float, val shadowDp: Float)

object MenuPolicy {
    /** Source-backed menu blur, with an opaque fallback if source/API support is absent. */
    fun surface(s: UiSettings, supported: Boolean = true, sourceAvailable: Boolean = true): MenuSurfacePlan {
        val plan=GlassPolicy.plan(s,GlassRole.MENU,supported,sourceAvailable)
        return MenuSurfacePlan(plan.tint,plan.enabled,plan.rim,plan.shadow)
    }

    fun frame(progress: Float, morph: Boolean): MenuFrame {
        val finite = progress.takeIf { it.isFinite() } ?: 1f
        return MenuFrame(finite.coerceIn(0f, 1f), if (morph) (.94f + .06f * finite).coerceIn(.92f, 1.02f) else 1f)
    }

    /** Popup size includes a transparent gutter reserved for the one outer shadow. All units are pixels. */
    fun place(
        anchor: MenuAnchor, windowWidth: Int, windowHeight: Int,
        popupWidth: Int, popupHeight: Int, rtl: Boolean,
        margin: Int, gap: Int, shadowInset: Int,
    ): MenuPlacement {
        val w = popupWidth.coerceAtLeast(1)
        val h = popupHeight.coerceAtLeast(1)
        val ww = windowWidth.coerceAtLeast(1)
        val wh = windowHeight.coerceAtLeast(1)
        val mx = margin.coerceAtLeast(0).coerceAtMost(((ww - w) / 2).coerceAtLeast(0))
        val my = margin.coerceAtLeast(0).coerceAtMost(((wh - h) / 2).coerceAtLeast(0))
        val inset = shadowInset.coerceIn(0, minOf(w, h) / 2)
        val offset = gap.coerceAtLeast(0)
        val xIdeal = if (rtl) anchor.left - inset else anchor.right - w + inset
        val belowY = anchor.bottom + offset - inset
        val aboveY = anchor.top - offset - h + inset
        val belowFits = belowY >= my && belowY + h <= wh - my
        val aboveFits = aboveY >= my && aboveY + h <= wh - my
        val above = when {
            belowFits -> false
            aboveFits -> true
            else -> anchor.top > wh - anchor.bottom
        }
        val x = xIdeal.coerceIn(mx, (ww - w - mx).coerceAtLeast(mx))
        val y = (if (above) aboveY else belowY).coerceIn(my, (wh - h - my).coerceAtLeast(my))
        val centerX = (anchor.left.toDouble() + anchor.right.toDouble()) / 2.0
        return MenuPlacement(
            x, y, above,
            ((centerX - x) / w).toFloat().coerceIn(0f, 1f),
            (if (above) 1f - inset.toFloat() / h else inset.toFloat() / h).coerceIn(0f, 1f),
        )
    }
}
