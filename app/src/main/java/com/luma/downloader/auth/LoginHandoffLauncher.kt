package com.luma.downloader.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.luma.core.AuthorizationPhase

sealed interface HandoffResult {
    data object Opened: HandoffResult
    data class Failed(val message:String):HandoffResult
}

/** No CookieManager, SessionVault, JS, exported callback or untrusted intent extras.
 * Browser redirects are owned by the real platform/browser; no forged grant dialog. */
object LoginHandoffLauncher {
    fun open(context:Context,mode:LoginHandoffMode,ticket:QrTicket,phase:AuthorizationPhase):HandoffResult {
        val target=try {LoginHandoffPolicy.bili(ticket.url,ticket.key,ticket.expiresAt,phase,System.currentTimeMillis())}
            catch(_:Exception){return HandoffResult.Failed("当前二维码不支持跳转或已经过期。请重新申请，或使用扫码。")}
        return try {
            when(mode) {
                LoginHandoffMode.NATIVE_APP -> {
                    val view=Intent(Intent.ACTION_VIEW,Uri.parse(target.nativeUrl)).setPackage(target.packageName)
                    view.addCategory(Intent.CATEGORY_BROWSABLE)
                    if(view.resolveActivity(context.packageManager)==null)return HandoffResult.Failed("当前 B站版本未提供可用的确认页跳转。请保存此二维码，在 B站扫一扫中识别。")
                    if(context !is Activity)view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(view)
                }
                LoginHandoffMode.EXTERNAL_BROWSER -> {
                    // Resolve generic browsers against a neutral HTTPS origin. Do not let this
                    // first hop silently resolve to Bili or another specialised app-link handler.
                    val probe=Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE)
                    val packages=context.packageManager.queryIntentActivities(probe,PackageManager.MATCH_DEFAULT_ONLY)
                        .mapNotNull{it.activityInfo?.packageName}.filter{it!=context.packageName&&it!=target.packageName}.distinct()
                    if(packages.isEmpty())return HandoffResult.Failed("未找到可打开 HTTPS 的浏览器。可继续保存二维码扫码。")
                    val chosen=probe.resolveActivity(context.packageManager)?.packageName?.takeIf{it in packages}
                    fun browser(name:String)=Intent(Intent.ACTION_VIEW,Uri.parse(target.officialUrl)).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(name)
                    val view=if(chosen!=null)browser(chosen) else Intent.createChooser(browser(packages.first()),"选择浏览器确认当前登录")
                        .putExtra(Intent.EXTRA_INITIAL_INTENTS,packages.drop(1).map(::browser).toTypedArray())
                    if(context !is Activity)view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(view)
                }
            }
            HandoffResult.Opened
        }catch(_:android.content.ActivityNotFoundException){HandoffResult.Failed("系统没有找到对应的处理程序；本机会话未改变。")}
        catch(_:SecurityException){HandoffResult.Failed("系统拒绝了此跳转；本机会话未改变。")}
    }
}
