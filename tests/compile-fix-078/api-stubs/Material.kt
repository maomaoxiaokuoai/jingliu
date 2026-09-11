// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package androidx.compose.material3
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
@RequiresOptIn annotation class ExperimentalMaterial3Api
class TextStyle
class Typography {val labelSmall=TextStyle()}
object MaterialTheme {val typography=Typography()}
fun Text(text:String,modifier:Modifier=Modifier,style:TextStyle=TextStyle()) {}
fun TextButton(onClick:()->Unit,modifier:Modifier=Modifier,content:RowScope.()->Unit) {}
fun Button(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:RowScope.()->Unit) {}
fun Scaffold(topBar:()->Unit={},bottomBar:()->Unit={},content:(PaddingValues)->Unit) {}
fun TopAppBar(title:()->Unit,navigationIcon:()->Unit={},actions:RowScope.()->Unit={}) {}
fun AlertDialog(onDismissRequest:()->Unit,confirmButton:()->Unit,dismissButton:(()->Unit)?=null,title:(()->Unit)?=null,text:(()->Unit)?=null) {}
