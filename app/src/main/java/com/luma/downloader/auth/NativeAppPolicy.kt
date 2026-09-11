package com.luma.downloader.auth

import com.luma.core.Platform

/** Package allowlist only. An installed app is NOT an OAuth provider or an authenticated session. */
data class NativeAppTarget(val platform:Platform,val packageNames:List<String>,val authorizationNote:String)
object NativeAppPolicy {
    val targets=listOf(
        NativeAppTarget(Platform.BILIBILI,listOf("tv.danmaku.bili"),"B站开放平台应用凭证与官方授权接入尚未配置；现有扫码登录仍可独立使用。"),
        NativeAppTarget(Platform.DOUYIN,listOf("com.ss.android.ugc.aweme"),"正式 App 授权需要抖音 OpenSDK、Client Key、签名登记、回调和服务端令牌交换。"),
        NativeAppTarget(Platform.KUAISHOU,listOf("com.smile.gifmaker"),"正式 App 授权需要快手 OpenSDK、appId、签名登记与授权回调。"),
        NativeAppTarget(Platform.XIAOHONGSHU,listOf("com.xingin.xhs"),"正式 App 授权需要小红书应用标识和已批准的账号授权 SDK 配置。"),
        NativeAppTarget(Platform.YOUTUBE,listOf("com.google.android.youtube"),"YouTube 数据授权由 Google 账号授权服务提供，不是打开 YouTube 就返回 Cookie。"),
        NativeAppTarget(Platform.TIKTOK,listOf("com.zhiliaoapp.musically","com.ss.android.ugc.trill"),"正式 App 授权需要 TikTok Login Kit、Client Key、PKCE 与已登记的回调。"),
        NativeAppTarget(Platform.X,listOf("com.twitter.android"),"X 的正式数据授权使用 OAuth 2.0 + PKCE，需要 Client ID 和回调；不能读取 X App 私有 Cookie。"),
    )
    fun target(platform:Platform):NativeAppTarget?=targets.firstOrNull{it.platform==platform}
    fun firstInstalled(platform:Platform,isLaunchable:(String)->Boolean):String?=target(platform)?.packageNames?.firstOrNull(isLaunchable)
}
