package com.luma.downloader.data

enum class AppPage(val title: String) { PARSE("解析"), DOWNLOADS("下载"), LIBRARY("文件"), SETTINGS("设置") }
enum class ThemeStyle { FROST, NIGHT, SYSTEM }
enum class AccentStyle { BLUE, GRAPHITE, GREEN, AMBER, ROSE, VIOLET }
enum class GlassMaterial { LIQUID, FROSTED, SOLID }
enum class MotionMode { SPRING, GENTLE, OFF }
enum class InformationDensity { COMPACT, DETAILED }
data class Appearance(
    val theme: ThemeStyle = ThemeStyle.FROST,
    val material: GlassMaterial = GlassMaterial.LIQUID,
    val motion: MotionMode = MotionMode.SPRING,
    val density: InformationDensity = InformationDensity.DETAILED,
    val accent: AccentStyle = AccentStyle.BLUE,
)
fun UiSettings.appearance(systemReduced: Boolean = false): Appearance = Appearance(
    theme = when(text("theme")) { "dark" -> ThemeStyle.NIGHT; "auto" -> ThemeStyle.SYSTEM; else -> ThemeStyle.FROST },
    material = when(text("material")) { "solid" -> GlassMaterial.SOLID; "frost" -> GlassMaterial.FROSTED; else -> GlassMaterial.LIQUID },
    motion = if(systemReduced || enabled("reduceMotion") || text("motion") == "reduce") MotionMode.OFF else if(text("motion") == "soft") MotionMode.GENTLE else MotionMode.SPRING,
    density = if(text("density") == "compact") InformationDensity.COMPACT else InformationDensity.DETAILED,
    accent = when(text("accent")) { "graphite" -> AccentStyle.GRAPHITE; "green" -> AccentStyle.GREEN; "orange" -> AccentStyle.AMBER; "rose" -> AccentStyle.ROSE; "violet" -> AccentStyle.VIOLET; else -> AccentStyle.BLUE },
)
