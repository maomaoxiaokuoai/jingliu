// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package android.app
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Window
open class Activity:Context() {
 val isFinishing=false
 val intent=Intent();val window=Window()
 open fun onCreate(savedInstanceState:Bundle?) {}
 open fun onDestroy() {}
 // Mirrors Activity's final setProgress(int); omitted in the earlier substitute.
 fun setProgress(progress:Int) {}
 fun finish() {}
 fun setResult(code:Int,intent:Intent) {}
 companion object {const val RESULT_OK=-1}
}
