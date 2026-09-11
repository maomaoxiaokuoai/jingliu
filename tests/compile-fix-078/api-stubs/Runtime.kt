// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package androidx.compose.runtime
import kotlin.reflect.KProperty
import kotlinx.coroutines.flow.StateFlow
class MutableState<T>(var value:T)
fun <T> mutableStateOf(value:T)=MutableState(value)
operator fun <T> MutableState<T>.getValue(receiver:Any?,property:KProperty<*>):T=value
operator fun <T> MutableState<T>.setValue(receiver:Any?,property:KProperty<*>,new:T) {value=new}
class ProvidedValue<T>(val value:T)
class ProvidableCompositionLocal<T>(val value:T) {infix fun provides(value:T)=ProvidedValue(value)}
fun CompositionLocalProvider(vararg values:ProvidedValue<*>,content:()->Unit) {}
fun <T> StateFlow<T>.collectAsState()=MutableState(value)
