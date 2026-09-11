// TEST-ONLY external API substitute; not Android implementation or device validation.
@file:Suppress("UNUSED_PARAMETER")
package androidx.compose.ui.platform
import androidx.compose.runtime.ProvidableCompositionLocal
import android.content.Context
import androidx.compose.ui.Modifier
val LocalContext=ProvidableCompositionLocal(Context())
fun Modifier.testTag(tag:String)=this
