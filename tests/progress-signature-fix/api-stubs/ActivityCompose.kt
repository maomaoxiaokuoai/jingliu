// TEST-ONLY external API substitute; not Android implementation or device validation.
@file:Suppress("UNUSED_PARAMETER")
package androidx.activity.compose
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
class ManagedActivityResultLauncher<I,O>{fun launch(input:I) {}}
fun ComponentActivity.setContent(content:()->Unit) {}
fun BackHandler(enabled:Boolean=true,onBack:()->Unit) {}
fun <I,O> rememberLauncherForActivityResult(contract:ActivityResultContract<I,O>,onResult:(O)->Unit)=ManagedActivityResultLauncher<I,O>()
