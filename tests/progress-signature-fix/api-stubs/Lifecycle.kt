// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package androidx.lifecycle
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
val ComponentActivity.lifecycleScope:CoroutineScope get()=CoroutineScope(Dispatchers.Default)

class Lifecycle {enum class State {STARTED}}
suspend fun ComponentActivity.repeatOnLifecycle(state:Lifecycle.State,block:suspend CoroutineScope.()->Unit) {kotlinx.coroutines.coroutineScope(block)}
