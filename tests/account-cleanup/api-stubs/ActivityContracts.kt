// TEST-ONLY external API substitute; not Android implementation or device validation.
@file:Suppress("UNUSED_PARAMETER")
package androidx.activity.result.contract
import android.content.Intent
import androidx.activity.result.ActivityResult
abstract class ActivityResultContract<I,O>
object ActivityResultContracts {class StartActivityForResult:ActivityResultContract<Intent,ActivityResult>()}
