package com.luma.downloader.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.luma.core.Platform

sealed interface NativeLaunchResult {
    data class Opened(val packageName:String):NativeLaunchResult
    data object NotInstalled:NativeLaunchResult
    data object Unavailable:NativeLaunchResult
}

/** Explicit installed-app launch only: no web fallback, no fabricated SDK intent or login callback.
 * This object has no reference to SessionVault and cannot mark an account authenticated. */
object NativeAppLauncher {
    fun installedPackage(context:Context,platform:Platform):String? =
        NativeAppPolicy.firstInstalled(platform){name->
            runCatching{context.packageManager.getLaunchIntentForPackage(name)?.component?.packageName==name}.getOrDefault(false)
        }
    fun open(context:Context,platform:Platform):NativeLaunchResult {
        val names=NativeAppPolicy.target(platform)?.packageNames?:return NativeLaunchResult.Unavailable
        var sawLaunchable=false
        for(name in names) {
            val launch=runCatching{context.packageManager.getLaunchIntentForPackage(name)}.getOrNull()?:continue
            if(launch.component?.packageName!=name)continue
            sawLaunchable=true
            try {
                launch.setPackage(name)
                if(context !is Activity)launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return NativeLaunchResult.Opened(name)
            } catch(_:android.content.ActivityNotFoundException) { /* Try another official regional variant. */ }
              catch(_:SecurityException) { return NativeLaunchResult.Unavailable }
        }
        return if(sawLaunchable)NativeLaunchResult.Unavailable else NativeLaunchResult.NotInstalled
    }
}
