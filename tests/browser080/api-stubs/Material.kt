// TEST-ONLY external API substitute; not Android implementation or device validation.
@file:Suppress("UNUSED_PARAMETER")
package androidx.compose.material3
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
@RequiresOptIn annotation class ExperimentalMaterial3Api
class TextStyle
class Typography {val labelSmall=TextStyle();val bodySmall=TextStyle();val titleMedium=TextStyle();val headlineSmall=TextStyle()}
class Colors {val error=Color()}
object MaterialTheme {operator fun invoke(content:()->Unit) {} ;val typography=Typography();val colorScheme=Colors()}
fun Text(text:String,modifier:Modifier=Modifier,style:TextStyle=TextStyle(),color:Color=Color(),fontSize:TextUnit=TextUnit()) {}
fun TextButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:RowScope.()->Unit) {}
fun OutlinedButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:RowScope.()->Unit) {}
fun Button(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:RowScope.()->Unit) {}
fun CircularProgressIndicator(modifier:Modifier=Modifier) {}
fun LinearProgressIndicator(modifier:Modifier=Modifier) {}
fun Scaffold(topBar:()->Unit={},bottomBar:()->Unit={},content:(PaddingValues)->Unit) {}
fun TopAppBar(title:()->Unit,navigationIcon:()->Unit={},actions:RowScope.()->Unit={}) {}
fun AlertDialog(onDismissRequest:()->Unit,confirmButton:()->Unit,dismissButton:(()->Unit)?=null,title:(()->Unit)?=null,text:(()->Unit)?=null) {}

fun Surface(modifier:Modifier=Modifier,content:()->Unit) {}
fun FilterChip(selected:Boolean,onClick:()->Unit,label:()->Unit) {}
fun Switch(checked:Boolean,onCheckedChange:(Boolean)->Unit) {}
fun LinearProgressIndicator(progress:()->Float,modifier:Modifier=Modifier) {}
