package com.luma.downloader
import com.luma.downloader.data.*

/** JVM-only policy assertions, not a Compose rendering test. */
object PolicyChecks {
    fun run(): Int {
        var count = 0
        fun ok(value: Boolean, name: String) { check(value) { name }; count++ }
        val base = UiSettings()
        ok(SettingsCatalog.all.map { it.key }.distinct().size == SettingsCatalog.all.size, "unique settings keys")
        SettingsCatalog.all.forEach { spec ->
            ok(SettingsCatalog.valid(spec.key, spec.default), "valid default: ${spec.key}")
            when(spec.kind) {
                SettingKind.RANGE -> {
                    ok(!SettingsCatalog.valid(spec.key, "NaN"), "nonfinite range rejected")
                    ok(!SettingsCatalog.valid(spec.key, (spec.maximum + spec.step).toString()), "upper bound")
                    ok(MotionPolicy.snapRange(spec, spec.minimum - 100) == spec.minimum, "range clamp")
                }
                SettingKind.CHOICE -> ok(!SettingsCatalog.valid(spec.key, "__missing__"), "unknown choice")
                SettingKind.TOGGLE -> ok(!SettingsCatalog.valid(spec.key, "1"), "strict boolean")
            }
        }
        ok(base.withValue("missing", "anything") == base, "unknown keys rejected")
        ok(base.appearance(true).motion == MotionMode.OFF, "system reduce motion")
        ok(base.withValue("reduceMotion", "true").appearance().motion == MotionMode.OFF, "app reduce motion")
        ok(MaterialPolicy.plan(base, supported = false).opaque, "old Android fallback")
        ok(MaterialPolicy.plan(base.withValue("material", "solid")).blur == 0f, "solid is not blurred")
        ok(MaterialPolicy.plan(base.withValue("reduceTransparency", "true")).opaque, "transparency accessibility")
        ok(MaterialPolicy.plan(base.withValue("performance", "efficient").withValue("blur", "36")).blur == 12f, "performance cap")
        ok(MaterialPolicy.plan(base.withValue("transparency", "90")).tint < MaterialPolicy.plan(base.withValue("transparency", "15")).tint, "opacity slider affects rendering")
        MotionChannel.entries.forEach { channel ->
            val t = MotionPolicy.tuning(base, channel)
            ok(t.stiffness.isFinite() && t.stiffness > 0 && t.dampingRatio > 0, "valid physical spring")
            ok(MotionPolicy.tuning(base, channel, reduced = true).instant, "reduce every channel")
        }
        val customized = base.withValue("theme", "dark").withValue("concurrency", "5").withValue("bounce", "0")
        val preset = MotionPolicy.preset(customized)
        ok(preset.text("theme") == "dark" && preset.number("concurrency") == 5f && preset.number("bounce") == 64f, "motion reset preserves business settings")
        ok(!MotionPolicy.backCommits(200f, 400f, 600f, cancelled = true), "cancelled gesture not committed")
        ok(MotionPolicy.backCommits(200f, 400f, 0f), "distance return")
        ok(!MotionPolicy.backCommits(20f, 400f, 0f), "short return cancels")
        println("Policy regression: $count assertions passed (JVM-only; no Android rendering verification).")
        return count
    }
}
fun main() { PolicyChecks.run() }
