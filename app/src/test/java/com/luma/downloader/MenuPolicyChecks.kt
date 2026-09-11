package com.luma.downloader

import com.luma.downloader.data.*
import kotlin.random.Random

/** Pure JVM checks only; actual Compose rendering tests live under androidTest. */
object MenuPolicyChecks {
    @JvmStatic fun main(args: Array<String>) { run() }
    fun run(): Int {
        var count = 0
        fun checkCase(name: String, test: () -> Unit) { test(); count++; println("PASS $name") }
        val settings = UiSettings()
        checkCase("source-backed default menu is diffused and tinted") {
            val p = MenuPolicy.surface(settings)
            check(p.baseAlpha in .64f..1f && p.baseAlpha < 1f && p.decorated)
        }
        checkCase("missing blur source always uses an opaque fallback") {
            for (value in 15..90) {
                val p = MenuPolicy.surface(settings.withValue("transparency", value.toString()), sourceAvailable = false)
                check(p.baseAlpha == 1f && !p.decorated)
            }
        }
        checkCase("material modes and old Android keep a readable fallback") {
            for (mode in SettingsCatalog.byKey.getValue("material").options.map { it.first }) {
                val s = settings.withValue("material", mode)
                check(MenuPolicy.surface(s, supported = false).baseAlpha == 1f)
                if (mode == "solid") check(MenuPolicy.surface(s).baseAlpha == 1f)
                else check(MenuPolicy.surface(s).baseAlpha in .64f..1f)
            }
        }
        checkCase("reduce transparency disables sampling and sheen") {
            val p = MenuPolicy.surface(settings.withValue("reduceTransparency", "true"))
            check(!p.decorated && p.baseAlpha == 1f)
        }
        checkCase("menu toggle removes sampling and glass finish") {
            val p = MenuPolicy.surface(settings.withValue("glassMenus", "false"))
            check(!p.decorated && p.baseAlpha == 1f && p.rimAlpha == 0f && p.shadowDp == 0f)
            check(!MenuPolicy.surface(settings.withValue("material", "solid")).decorated)
        }
        checkCase("high contrast and shadow parameters affect actual plans") {
            check(MenuPolicy.surface(settings.withValue("increaseContrast", "true")).rimAlpha >= .6f)
            val spec = SettingsCatalog.byKey.getValue("shadow")
            val a = MenuPolicy.surface(settings.withValue("shadow", spec.minimum.toInt().toString()))
            val b = MenuPolicy.surface(settings.withValue("shadow", spec.maximum.toInt().toString()))
            check(a.shadowDp <= b.shadowDp && a.shadowDp >= 0f && b.shadowDp <= 12f)
        }
        checkCase("spring overshoot cannot produce invalid alpha or scale") {
            for (v in listOf(-100f, -.1f, 0f, .4f, 1f, 1.3f, 100f, Float.NaN, Float.POSITIVE_INFINITY)) {
                val frame = MenuPolicy.frame(v, true)
                check(frame.alpha in 0f..1f && frame.scale in .92f..1.02f)
            }
        }
        checkCase("motion-disabled menus never scale") {
            for (v in listOf(-1f, 0f, .5f, 1f, 1.1f)) check(MenuPolicy.frame(v, false).scale == 1f)
        }
        checkCase("settled menu has exact opacity and scale one") {
            check(MenuPolicy.frame(1f, true) == MenuFrame(1f, 1f))
        }
        fun place(a: MenuAnchor, w: Int = 400, h: Int = 800, pw: Int = 330, ph: Int = 200, rtl: Boolean = false) =
            MenuPolicy.place(a, w, h, pw, ph, rtl, margin = 10, gap = 6, shadowInset = 14)
        checkCase("normal top anchor opens below aligned to content") {
            val p = place(MenuAnchor(20, 100, 380, 160))
            check(!p.above && p.y == 152 && p.x == 60)
        }
        checkCase("bottom title-line picker opens above") {
            val p = place(MenuAnchor(20, 690, 380, 742))
            check(p.above && p.y == 498 && p.y + 200 <= 790)
        }
        checkCase("RTL aligns the leading content edge") {
            val p = place(MenuAnchor(40, 100, 360, 160), rtl = true)
            check(p.x == 26)
        }
        checkCase("tablet popup stays next to anchor instead of left screen edge") {
            val p = place(MenuAnchor(650, 280, 1080, 336), w = 1280, pw = 368)
            check(p.x == 726 && !p.above)
        }
        checkCase("large-font tall popup remains visible and pivots correctly") {
            val p = place(MenuAnchor(24, 350, 368, 404), ph = 730)
            check(p.y >= 10 && p.y + 730 <= 790 && p.pivotX in 0f..1f && p.pivotY in 0f..1f)
        }
        checkCase("tiny window and oversized stale measurements do not throw") {
            for (wh in listOf(0, 1, 40, 100)) {
                val p = place(MenuAnchor(-30, 80, 60, 180), w = wh, h = wh, pw = 340, ph = 600)
                check(p.x >= 0 && p.y >= 0 && p.pivotX.isFinite() && p.pivotY.isFinite())
            }
        }
        checkCase("rotation/keyboard/RTL fuzz invariants over 10000 bounded windows") {
            val random = Random(20260909)
            repeat(10_000) {
                val w = random.nextInt(140, 3000); val h = random.nextInt(120, 2600)
                val pw = random.nextInt(48, minOf(368, w) + 1); val ph = random.nextInt(48, h + 1)
                val x = random.nextInt(0, w); val y = random.nextInt(0, h)
                val p = place(MenuAnchor(x, y, minOf(x + 320, w), minOf(y + 52, h)), w, h, pw, ph, random.nextBoolean())
                check(p.x in 0..w-pw && p.y in 0..h-ph)
                check(p.pivotX in 0f..1f && p.pivotY in 0f..1f)
            }
        }
        println("Menu policy: $count named checks passed. Geometry/logic only; NOT Android rendering.")
        return count
    }
}
