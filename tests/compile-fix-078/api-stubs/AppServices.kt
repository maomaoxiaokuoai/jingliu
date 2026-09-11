// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package com.luma.downloader.auth
import android.content.Context
import com.luma.core.*
import java.util.UUID
data class Account(val platform:Platform,val cookies:List<Cookie>,val refreshToken:String="",val pendingConfirm:String="",
    val uid:String="",val name:String="",val avatar:String="",val vip:String="未获取",val checked:Long=0,val rejected:Boolean=false,
    val revision:String=UUID.randomUUID().toString()) {
    val stateLabel:String get()=when {rejected->"登录已失效";checked>0&&System.currentTimeMillis()-checked<24*60*60*1000L->"会话已验证";checked>0->"待重新校验";else->"Cookie 已保存 · 未验证身份"}
    fun json():Map<String,Any?> = mapOf("platform" to platform.name,"cookies" to cookies.map{it.json()},"refreshToken" to refreshToken,"pendingConfirm" to pendingConfirm,"uid" to uid,"name" to name,"avatar" to avatar,"vip" to vip,"checked" to checked,"rejected" to rejected,"revision" to revision)
    companion object {fun from(v:Any?)=Account(Platform.valueOf(v.s("platform")),v.list("cookies").map{Cookie.from(it)},v.s("refreshToken"),v.s("pendingConfirm"),v.s("uid"),v.s("name"),v.s("avatar"),v.s("vip").ifBlank{"未获取"},v.n("checked")?:0,v.at("rejected").truth(),v.s("revision").ifBlank{UUID.randomUUID().toString()})}
}


// Account above copied from production; service bodies below are compile-only.
class SessionVault {
 fun get(platform:Platform):Account?=null
 fun save(account:Account,expectedRevision:String?=null):Boolean=false
}
class BiliAuth {fun verify(account:Account,token:CancelToken):Account=account}
object NativeAppLauncher {fun installedPackage(context:Context,platform:Platform):String?=null}
