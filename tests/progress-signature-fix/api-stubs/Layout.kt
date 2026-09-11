// TEST-ONLY external API substitute; not Android implementation or device validation.
@file:Suppress("UNUSED_PARAMETER")
package androidx.compose.foundation.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
class PaddingValues
interface RowScope {fun Modifier.weight(weight:Float,fill:Boolean=true):Modifier=this}
interface ColumnScope {fun Modifier.weight(weight:Float,fill:Boolean=true):Modifier=this}
interface BoxScope
object Arrangement {fun spacedBy(d:Dp)=Any()}
fun Row(modifier:Modifier=Modifier,horizontalArrangement:Any=Any(),verticalAlignment:Any=Any(),content:RowScope.()->Unit) {}
fun Column(modifier:Modifier=Modifier,verticalArrangement:Any=Any(),horizontalAlignment:Any=Any(),content:ColumnScope.()->Unit) {}
fun Box(modifier:Modifier=Modifier,contentAlignment:Any=Any(),content:BoxScope.()->Unit) {}
fun Modifier.padding(paddingValues:PaddingValues):Modifier=this
fun Modifier.padding(all:Dp):Modifier=this
fun Modifier.padding(horizontal:Dp,vertical:Dp=Dp(0f)):Modifier=this
fun Modifier.fillMaxSize(fraction:Float=1f):Modifier=this
fun Modifier.fillMaxWidth(fraction:Float=1f):Modifier=this
fun Modifier.navigationBarsPadding():Modifier=this
fun Modifier.height(d:Dp):Modifier=this
fun Modifier.size(d:Dp):Modifier=this
fun Modifier.imePadding():Modifier=this
fun Modifier.safeDrawingPadding():Modifier=this
