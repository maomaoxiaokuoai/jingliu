package com.luma.downloader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.luma.downloader.data.*

/** Content is deliberately neutral. Accent is reserved for actionable controls. */
data class LumaPalette(
    val background: Color, val surface: Color, val group: Color,
    val ink: Color, val muted: Color, val line: Color, val accent: Color, val dark: Boolean,
)
fun paletteFor(dark: Boolean, accent: AccentStyle = AccentStyle.BLUE): LumaPalette {
    val tint = when(accent) {
        AccentStyle.BLUE -> if(dark) Color(0xFF0A84FF) else Color(0xFF007AFF)
        AccentStyle.GRAPHITE -> if(dark) Color(0xFFB6B6BF) else Color(0xFF54545D)
        AccentStyle.GREEN -> if(dark) Color(0xFF54BD7C) else Color(0xFF278451)
        AccentStyle.ROSE -> if(dark) Color(0xFFFF7192) else Color(0xFFC34770)
        AccentStyle.VIOLET -> if(dark) Color(0xFFBB98FF) else Color(0xFF7C58B1)
        AccentStyle.AMBER -> if(dark) Color(0xFFFFB04F) else Color(0xFFB86B12)
    }
    return if(dark) LumaPalette(Color(0xFF000000), Color(0xFF1C1C1E), Color(0xFF1C1C1E),
        Color(0xFFF5F5F7),Color(0xFF98989F),Color(0xFF38383A),tint,true)
    else LumaPalette(Color(0xFFF2F2F7),Color.White,Color.White,Color(0xFF1C1C1E),
        Color(0xFF737379),Color(0xFFDDDDDF),tint,false)
}
val LocalLumaPalette = staticCompositionLocalOf { paletteFor(false) }
val LocalAppearance = staticCompositionLocalOf { Appearance() }
@Composable fun LumaTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val dark = appearance.theme == ThemeStyle.NIGHT ||
        (appearance.theme == ThemeStyle.SYSTEM && isSystemInDarkTheme())
    val base = remember(dark,appearance.accent) { paletteFor(dark,appearance.accent) }
    val contrast=LocalUiSettings.current.enabled("increaseContrast")
    val p=if(contrast)base.copy(muted=if(dark)Color(0xFFCACAD0)else Color(0xFF55555C),
        line=if(dark)Color(0xFF77777D)else Color(0xFF929298))else base
    val scheme = if(dark) darkColorScheme(primary=p.accent,background=p.background,
        surface=p.surface,onSurface=p.ink,onBackground=p.ink,onPrimary=Color.White)
    else lightColorScheme(primary=p.accent,background=p.background,surface=p.surface,
        onSurface=p.ink,onBackground=p.ink,onPrimary=Color.White)
    CompositionLocalProvider(LocalLumaPalette provides p,LocalAppearance provides appearance,LocalContentColor provides p.ink) {
        MaterialTheme(colorScheme=scheme,typography=Typography(
            headlineLarge=TextStyle(fontSize=34.sp,lineHeight=41.sp,fontWeight=FontWeight.Bold),
            titleLarge=TextStyle(fontSize=22.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold),
            bodyLarge=TextStyle(fontSize=17.sp,lineHeight=24.sp),
            bodyMedium=TextStyle(fontSize=15.sp,lineHeight=21.sp),
            labelLarge=TextStyle(fontSize=15.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium)),content=content)
    }
}
