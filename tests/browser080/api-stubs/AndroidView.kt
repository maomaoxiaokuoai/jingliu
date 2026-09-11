// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package androidx.compose.ui.viewinterop
import android.content.Context
import android.view.View
import androidx.compose.ui.Modifier
// Public overload type contracts only: no Compose compiler or Android rendering.
fun <T:View> AndroidView(factory:(Context)->T,modifier:Modifier=Modifier,update:(T)->Unit={}) {}
fun <T:View> AndroidView(factory:(Context)->T,modifier:Modifier=Modifier,onReset:((T)->Unit)?,onRelease:(T)->Unit={},update:(T)->Unit={}) {}
