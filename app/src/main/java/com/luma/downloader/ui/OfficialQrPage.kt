package com.luma.downloader.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.luma.core.*
import com.luma.downloader.BuildConfig
import java.net.URI

/** Official QR authorization document only. No ordinary-login replacement or credential extraction.
 * No JS bridge, injected script, mixed content, local-file access or TLS override. */
@SuppressLint("SetJavaScriptEnabled")
@Composable fun OfficialQrPage(ticket:RemoteQrTicket,modifier:Modifier=Modifier,onError:(String)->Unit) {
    require(ticket.kind==QrDisplay.OFFICIAL_PAGE && QrProtocol.displayAllowed(ticket.platform,ticket.kind,ticket.url))
    var web by remember(ticket.session){mutableStateOf<WebView?>(null)}
    val latestError by rememberUpdatedState(onError)
    DisposableEffect(ticket.session){onDispose{web?.apply{stopLoading();loadUrl("about:blank");clearHistory();removeAllViews();destroy()};web=null}}
    AndroidView(modifier=modifier,factory={context->WebView(context).apply {
        web=this
        settings.javaScriptEnabled=true;settings.domStorageEnabled=true
        settings.allowFileAccess=false;settings.allowContentAccess=false
        settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW;settings.safeBrowsingEnabled=true
        settings.javaScriptCanOpenWindowsAutomatically=false;settings.setSupportMultipleWindows(false)
        settings.mediaPlaybackRequiresUserGesture=true
        settings.useWideViewPort=true;settings.loadWithOverviewMode=true
        // These providers document a website QR page. Request that page, not a mobile app redirect.
        settings.userAgentString="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        webChromeClient=WebChromeClient()
        webViewClient=object:WebViewClient(){
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                if(!request.isForMainFrame)return false
                val allowed=runCatching {
                    val u=URI(request.url.toString());val official=URI(ticket.url)
                    val callback=URI(QrProtocol.bridgeBase(BuildConfig.QR_AUTH_BRIDGE_URL))
                    u.scheme=="https" && u.userInfo==null && (u.port==-1||u.port==443) &&
                        (u.host==official.host || u.host==callback.host && u.path=="/callback/${ticket.platform.name}")
                }.getOrDefault(false)
                if(!allowed)latestError("平台要求的跳转超出二维码授权域名，已阻止；可刷新重试")
                return !allowed
            }
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){if(request.isForMainFrame)latestError("官方二维码页面加载失败，请检查网络和应用配置")}
        }
        setDownloadListener{_,_,_,_,_->latestError("此窗口仅用于官方扫码授权，不执行网页文件下载")}
        loadUrl(ticket.url)
    }})
}
