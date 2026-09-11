// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package androidx.activity.compose
import androidx.activity.ComponentActivity
fun ComponentActivity.setContent(content:()->Unit) {}
fun BackHandler(enabled:Boolean=true,onBack:()->Unit) {}
