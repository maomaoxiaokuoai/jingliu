// TEST-ONLY external API substitute; not Android implementation or device validation.
@file:Suppress("UNUSED_PARAMETER")
package androidx.lifecycle.compose
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.MutableState
fun <T> StateFlow<T>.collectAsStateWithLifecycle()=MutableState(value)
