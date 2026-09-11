// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package android.view
import android.content.Context
open class View(val context:Context)
class Window {fun addFlags(flags:Int) {};fun clearFlags(flags:Int) {}}
class WindowManager {class LayoutParams {companion object {const val FLAG_SECURE=8192}}}
