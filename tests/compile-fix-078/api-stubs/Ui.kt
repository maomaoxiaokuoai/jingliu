// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package com.luma.downloader.ui
import androidx.compose.runtime.ProvidableCompositionLocal
import com.luma.downloader.data.*
val LocalUiSettings=ProvidableCompositionLocal(UiSettings())
fun LumaTheme(appearance:Appearance,content:()->Unit) {}
fun Note(text:String) {}
