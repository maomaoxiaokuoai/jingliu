// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package androidx.lifecycle
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
val ComponentActivity.lifecycleScope:CoroutineScope get()=CoroutineScope(Dispatchers.Default)
