// TEST-ONLY external API substitute; not Android implementation or device validation.
@file:Suppress("UNUSED_PARAMETER")
package androidx.compose.runtime
import kotlin.reflect.KProperty
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE) annotation class Composable
class MutableState<T>(var value:T)
fun <T> mutableStateOf(value:T)=MutableState(value)
fun mutableLongStateOf(value:Long)=MutableState(value)
fun mutableIntStateOf(value:Int)=MutableState(value)
operator fun <T> MutableState<T>.getValue(receiver:Any?,property:KProperty<*>):T=value
operator fun <T> MutableState<T>.setValue(receiver:Any?,property:KProperty<*>,new:T) {value=new}
class ProvidedValue<T>(val value:T)
class ProvidableCompositionLocal<T>(val value:T) {val current:T get()=value;infix fun provides(value:T)=ProvidedValue(value)}
fun CompositionLocalProvider(vararg values:ProvidedValue<*>,content:@Composable ()->Unit) {}
fun <T> StateFlow<T>.collectAsState()=MutableState(value)
@Composable fun <T> remember(vararg keys:Any?,calculation:()->T):T=calculation()
@Composable fun rememberCoroutineScope():CoroutineScope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
@Composable fun LaunchedEffect(vararg keys:Any?,block:suspend CoroutineScope.()->Unit) {}
@Composable fun SideEffect(effect:()->Unit) {}
class DisposableEffectResult
class DisposableEffectScope {fun onDispose(block:()->Unit)=DisposableEffectResult()}
@Composable fun DisposableEffect(vararg keys:Any?,effect:DisposableEffectScope.()->DisposableEffectResult) {}
@Composable fun key(vararg keys:Any?,block:@Composable ()->Unit) {}
