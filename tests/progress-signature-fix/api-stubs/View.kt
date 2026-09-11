// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package android.view
import android.content.Context
open class View(val context:Context) {fun setOnTouchListener(listener:(View,MotionEvent)->Boolean) {}}
class MotionEvent {val actionMasked=0;companion object {const val ACTION_DOWN=0;const val ACTION_UP=1}}
class Window {fun addFlags(flags:Int) {};fun clearFlags(flags:Int) {}}
class WindowManager {class LayoutParams {companion object {const val FLAG_SECURE=8192}}}
