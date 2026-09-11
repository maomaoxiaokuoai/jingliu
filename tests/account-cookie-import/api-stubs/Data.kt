// TEST-ONLY external API substitute; not Android implementation or device validation.
@file:Suppress("UNUSED_PARAMETER")
package com.luma.downloader.data
import kotlinx.coroutines.flow.MutableStateFlow
import com.luma.core.*
import com.luma.downloader.auth.Account
import com.luma.downloader.auth.QrTicket
class UiSettings {fun enabled(key:String)=false}
class Appearance
fun UiSettings.appearance()=Appearance()
class LumaViewModel {
 val accounts=MutableStateFlow<List<Account>>(emptyList())
 val qrOpen=false
 val qrPlatform=Platform.BILIBILI
 val qrTicket:QrTicket?=null
 val qrPhase=AuthorizationPhase.WAITING
 val qrLoading=false
 val qrMessage=""
 val accountBusy=false
 fun notice(message:String){}
 fun verifyBili(){}
 fun logout(platform:Platform){}
 fun startQr(platform:Platform){}
 fun closeQr(){}
 fun importCookies(platform:Platform,text:String){}
 fun importCookieFile(platform:Platform,uri:android.net.Uri){}
}
