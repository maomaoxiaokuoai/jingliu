package com.luma.downloader.data

/** Rendering is a pure plan: tests can distinguish stored preference from effective appearance. */
enum class GlassRole(val setting: String) {
    NAVIGATION("glassNav"), BUTTON("glassButtons"), MENU("glassMenus"),
    GROUP("glassCards"), FIELD("glassFields"), THUMB("glassSwitches")
}
data class GlassPlan(val enabled: Boolean, val blur: Float, val tint: Float, val rim: Float,
    val shadow: Float, val noise: Float, val reason: String = "")
object GlassPolicy {
    fun plan(s: UiSettings, role: GlassRole, supported: Boolean = true, sourceAvailable: Boolean = true): GlassPlan {
        val reason = when {
            !s.enabled(role.setting) -> "此区域使用实色"
            s.enabled("reduceTransparency") -> "减少透明度已开启"
            s.text("material") == "solid" -> "全局材质为实色"
            !supported -> "系统不支持背景采样模糊"
            !sourceAvailable -> "等待背景采样"
            role == GlassRole.BUTTON && s.text("buttonStyle") in setOf("solid", "outline") -> "主按钮使用实色或轮廓"
            else -> ""
        }
        if(reason.isNotEmpty()) return GlassPlan(false,0f,1f,0f,0f,0f,reason)
        val basic = MaterialPolicy.plan(s)
        // Text-dense menus always retain enough diffusion and tint to hide sharp underlying words.
        val menu = role == GlassRole.MENU
        val efficient = s.text("performance") == "efficient"
        val blur = when { menu -> basic.blur.coerceAtLeast(if(efficient)12f else 18f)
            role == GlassRole.THUMB -> basic.blur.coerceAtMost(12f); else -> basic.blur }
        val tint = when(role) {
            GlassRole.MENU -> if(efficient).80f+basic.tint*.16f else .64f+basic.tint*.30f
            GlassRole.GROUP -> (.15f+basic.tint*.74f).coerceIn(.30f,.94f)
            GlassRole.BUTTON -> basic.tint.coerceIn(.18f,.88f)
            GlassRole.THUMB -> .50f+basic.tint*.36f
            else -> basic.tint.coerceIn(.22f,.94f)
        }.let { if(s.enabled("increaseContrast")) it.coerceAtLeast(.82f) else it }
        val shadow = basic.shadow * when(role) { GlassRole.GROUP -> .45f; GlassRole.THUMB -> .38f; GlassRole.FIELD -> .35f; else -> 1f }
        return GlassPlan(true,blur,tint,basic.rim,shadow,basic.noise)
    }
    fun explanation(s: UiSettings, key: String, supported: Boolean, sdk:Int=33): String? {
        if(key=="grainStrength" && !s.enabled("grain"))return "先开启细腻颗粒"
        val materialKeys=setOf("blur","transparency","highlight","shadow","grain","grainStrength","refraction","lensBevel","dispersion","saturation")
        if(key !in materialKeys && key !in GlassRole.entries.map{it.setting})return null
        if(s.enabled("reduceTransparency"))return "减少透明度已开启，玻璃效果暂不使用"
        if(s.text("material")=="solid")return "全局材质为实色，可在材质菜单重新启用玻璃"
        if(!supported)return "当前系统使用实色回退"
        if(key in materialKeys && GlassRole.entries.none{s.enabled(it.setting)})return "所有玻璃区域均已关闭"
        if(key in setOf("refraction","lensBevel","dispersion") && sdk<33)return "当前系统使用模糊；镜片折射需要 Android 13 及以上"
        if(key in setOf("refraction","lensBevel","dispersion") && s.text("material")=="frost")return "磨砂模式不使用镜片折射"
        if(key in setOf("lensBevel","dispersion") && s.number("refraction")==0f)return "先提高边缘折射强度"
        return null
    }
}

enum class VisualResetScope { GLASS, ALL_VISUAL }
object VisualDefaults {
    const val SCHEMA=3
    const val SCHEMA_KEY="_visualSchema"
    fun reset(s:UiSettings,scope:VisualResetScope):UiSettings {
        val sections=if(scope==VisualResetScope.GLASS)setOf("glass","buttons") else setOf("appearance","glass","buttons","motion")
        return s.copy(values=s.values+SettingsCatalog.all.filter{it.section in sections}.associate{it.key to it.default})
    }
    /** User requested new defaults. Update only old shipped defaults once, never every launch.
     * Accessibility, custom alternatives, accounts and business preferences are preserved. */
    fun migrate(raw:Map<String,String>):UiSettings {
        val clean=raw.filter{SettingsCatalog.valid(it.key,it.value)}.toMutableMap()
        if((raw[SCHEMA_KEY]?.toIntOrNull()?:0)<SCHEMA) {
            val legacyDefaults=mapOf("material" to "regular","buttonStyle" to "solid","glassCards" to "false", "transparency" to "64", "highlight" to "48", "shadow" to "8")
            legacyDefaults.forEach{(key,old)->if(clean[key]==null||clean[key]==old)clean[key]=SettingsCatalog.byKey.getValue(key).default}
        }
        return UiSettings(clean)
    }
    fun persisted(s:UiSettings):Map<String,String> = s.values + (SCHEMA_KEY to SCHEMA.toString())
}

/** Keep the explicit button-style picker and its glass toggle from contradicting one another. */
object SettingActions {
    fun update(s:UiSettings,key:String,value:String):UiSettings {
        if(!SettingsCatalog.valid(key,value))return s
        val next=s.withValue(key,value)
        return when {
            key=="buttonStyle" -> next.withValue("glassButtons",(value in setOf("glass","tinted")).toString())
            key=="glassButtons" && value=="true" && s.text("buttonStyle") in setOf("solid","outline") -> next.withValue("buttonStyle","tinted")
            else -> next
        }
    }
}
