// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package android.net
class Uri private constructor(private val raw:String) {
 val host:String? get()=java.net.URI(raw).host
 val scheme:String? get()=java.net.URI(raw).scheme
 override fun toString()=raw
 companion object {fun parse(raw:String)=Uri(raw)}
}
