// TEST-ONLY external API substitute; not Android implementation or device validation.
@file:Suppress("UNUSED_PARAMETER")
package androidx.compose.ui.unit
class Dp(val value:Float)
class TextUnit
val Int.dp get()=Dp(toFloat())
val Int.sp get()=TextUnit()
