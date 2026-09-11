// TEST-ONLY external API substitute; not Android implementation or device validation.
@file:Suppress("UNUSED_PARAMETER")
package androidx.activity.result
import android.content.Intent
class ActivityResult(val resultCode:Int,val data:Intent?)
