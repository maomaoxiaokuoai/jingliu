// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package androidx.activity
import android.app.Activity
open class ComponentActivity:Activity()
fun ComponentActivity.enableEdgeToEdge() {}
