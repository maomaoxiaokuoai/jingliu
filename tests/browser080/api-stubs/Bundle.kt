// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package android.os
class Bundle
object SystemClock {fun elapsedRealtime():Long=0}
