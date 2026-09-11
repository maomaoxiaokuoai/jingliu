// TEST-ONLY external API substitute; not Android implementation or device validation.
@file:Suppress("UNUSED_PARAMETER")
package com.luma.downloader.ui
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.*
import com.luma.downloader.data.*
import com.luma.core.Platform
class Palette {val muted=Color();val ink=Color()}
val LocalUiSettings=ProvidableCompositionLocal(UiSettings())
val LocalLumaPalette=ProvidableCompositionLocal(Palette())
fun LumaTheme(appearance:Appearance,content:()->Unit) {}
fun Note(text:String) {}
fun GroupCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {}
fun PlatformIcon(platform:Platform,size:Dp=Dp(32f)) {}
fun RemoteImage(url:String,description:String?,modifier:Modifier=Modifier) {}
fun SettingsLine(title:String,detail:String="",icon:ImageVector?=null,onClick:(()->Unit)?=null,trailing:(@Composable ()->Unit)?=null) {}
fun InsetDivider() {}
fun SpringDropdownMenu(expanded:Boolean,onDismiss:()->Unit,title:String,content:ColumnScope.()->Unit) {}
fun SpringMenuItem(selected:Boolean,leadingIcon:(@Composable ()->Unit)?=null,onClick:()->Unit,content:RowScope.()->Unit) {}
fun SpringMenuDivider() {}
fun GlassAlertDialog(onDismissRequest:()->Unit,confirmButton:@Composable ()->Unit,title:(@Composable ()->Unit)?=null,text:(@Composable ()->Unit)?=null,dismissButton:(@Composable ()->Unit)?=null,secure:Boolean=false) {}

fun GlassTextField(value:String,onValueChange:(String)->Unit,placeholder:String,modifier:Modifier=Modifier,minLines:Int=1,maxLines:Int=4,visualTransformation:androidx.compose.ui.text.input.VisualTransformation) {}

val LocalSceneActive=ProvidableCompositionLocal(true)
