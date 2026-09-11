// TEST-ONLY external API substitute; not Android implementation or device validation.
@file:Suppress("UNUSED_PARAMETER")
package androidx.compose.foundation
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
fun Modifier.background(color:Color)=this
fun Image(bitmap:ImageBitmap,contentDescription:String?,modifier:Modifier=Modifier) {}
