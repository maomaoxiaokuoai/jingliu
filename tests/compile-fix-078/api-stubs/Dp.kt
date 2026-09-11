// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package androidx.compose.ui.unit
class Dp(val value:Float)
val Int.dp get()=Dp(toFloat())
