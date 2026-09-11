// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package androidx.compose.foundation.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
class PaddingValues
interface RowScope {fun Modifier.weight(weight:Float,fill:Boolean=true):Modifier=this}
interface ColumnScope
fun Row(modifier:Modifier=Modifier,content:RowScope.()->Unit) {}
fun Column(modifier:Modifier=Modifier,content:ColumnScope.()->Unit) {}
fun Modifier.padding(paddingValues:PaddingValues):Modifier=this
fun Modifier.padding(all:Dp):Modifier=this
fun Modifier.fillMaxSize(fraction:Float=1f):Modifier=this
fun Modifier.fillMaxWidth(fraction:Float=1f):Modifier=this
fun Modifier.navigationBarsPadding():Modifier=this
