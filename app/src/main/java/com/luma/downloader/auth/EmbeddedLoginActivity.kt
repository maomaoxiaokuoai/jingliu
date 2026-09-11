@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.luma.downloader.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.luma.core.*
import com.luma.downloader.AppGraph
import com.luma.downloader.data.appearance
import com.luma.downloader.ui.*
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

/** Browser-local, explicitly selected own-account login. No JS bridge, passwords, external browser
 * cookies, synthetic authorization, SSL bypass or OAuth user-agent disguise. */
class EmbeddedLoginActivity : ComponentActivity() {
    companion object { private val leased = AtomicBoolean(false) }
    private var ownsLease = false
    private lateinit var platform: Platform
    private lateinit var graph: AppGraph
    private lateinit var saver: BrowserSessionSaver
    private lateinit var gate: BrowserCaptureGate
    private var browser: WebView? = null
    private var store: BrowserCookieStore? = null
    private var expected: BrowserSessionSaver.Snapshot? = null
    private val token = CancelToken()
    private val visited = linkedSetOf<String>()
    private var status by mutableStateOf("正在打开官方页面")
    private var current by mutableStateOf("")
    private var ready by mutableStateOf(false)
    private var pageVisible by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var pageLoadProgress by mutableIntStateOf(0)
    private var autoSave by mutableStateOf(true)
    private var desktop by mutableStateOf(true)
    private var lightweight by mutableStateOf(false)
    private var showOptions by mutableStateOf(false)
    private var screenshots by mutableStateOf(false)
    private var confirmScreenshot by mutableStateOf(false)
    private var userInteracted = false
    private var firstSample = true
    private var savedAny = false
    private var openAt = 0L
    private var captureJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        platform = runCatching { Platform.valueOf(intent.getStringExtra("platform").orEmpty()) }
            .getOrNull() ?: run { finish(); return }
        graph = AppGraph.get(this)
        if (!BrowserSessionPolicy.embeddedAllowed(platform)) {
            setContent {
                val savedSettings by graph.settings.flow.collectAsState()
                CompositionLocalProvider(LocalUiSettings provides savedSettings) { LumaTheme(savedSettings.appearance()) { GlassWindow {
                    Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("${platform.title} 登录", style = MaterialTheme.typography.headlineSmall)
                        Text("此平台不支持内置浏览器登录。请返回账号页，导入你自己的 cookies.txt 或粘贴 Cookie。镜流不能自动读取系统浏览器的 Cookie。")
                        LiquidActionButton(onClick = { finish() }) { Text("返回账号页") }
                    }
                } } }
            }
            return
        }
        ownsLease = leased.compareAndSet(false, true)
        if (!ownsLease) { finish(); return }
        saver = BrowserSessionSaver(graph.vault)
        gate = BrowserCaptureGate(platform)
        current = BrowserSessionPolicy.entry(platform)
        desktop = intent.getBooleanExtra("desktop", true)
        lightweight = platform == Platform.KUAISHOU
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { BrowserScreen() }
        captureJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    delay(1500)
                    if (ready && pageVisible && autoSave && !busy) capture(false)
                    if (ready && !pageVisible && openAt > 0 && SystemClock.elapsedRealtime() - openAt > 20_000) {
                        status = "页面响应较慢，可刷新或在显示选项中切换手机版"
                    }
                }
            }
        }
    }

    @Composable private fun BrowserScreen() {
        val settings by graph.settings.flow.collectAsState()
        CompositionLocalProvider(LocalUiSettings provides settings) {
            LumaTheme(settings.appearance()) {
                GlassWindow {
                BackHandler { if (browser?.canGoBack() == true) browser?.goBack() else closeBrowser() }
                Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    topBar = {
                        GlassTopAppBar(
                            title = { Column {
                                Text(platform.title + if (desktop) " · 电脑版" else " · 手机版")
                                Text(runCatching { URI(current).host }.getOrDefault("") ?: "",
                                    style = MaterialTheme.typography.labelSmall)
                            } },
                            navigationIcon = { GlassTextButton(onClick = { closeBrowser() }) { Text("关闭") } },
                            actions = {
                                GlassTextButton(onClick = { showOptions = !showOptions }) { Text("显示") }
                                GlassTextButton(onClick = { browser?.reload() }, enabled = ready && !busy) { Text("刷新") }
                            }
                        )
                    },
                    bottomBar = {
                        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (showOptions) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GlassFilterChip(selected = desktop, onClick = { changeMode(true) }, label = { Text("电脑版") })
                                    GlassFilterChip(selected = !desktop, onClick = { changeMode(false) }, label = { Text("手机版") })
                                    if (platform == Platform.KUAISHOU) GlassFilterChip(selected = lightweight,
                                        onClick = { lightweight = !lightweight; browser?.reload() }, label = { Text("轻量加载") })
                                }
                            }
                            Note(status)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text("自动保存 Cookie", Modifier.weight(1f))
                                LiquidSwitch(checked = autoSave, onCheckedChange = { autoSave = it }, description = "自动保存 Cookie")
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassTextButton(onClick = {
                                    if (screenshots) { screenshots = false; window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
                                    else confirmScreenshot = true
                                }, modifier = Modifier.weight(1f)) { Text(if (screenshots) "恢复保护" else "截图二维码") }
                                LiquidActionButton(onClick = { lifecycleScope.launch { capture(true) } },
                                    enabled = ready && pageVisible && !busy, modifier = Modifier.weight(1f)) {
                                    Text(if (busy) "保存中…" else "保存并返回")
                                }
                            }
                        }
                    }
                ) { padding ->
                    Column(Modifier.padding(padding).fillMaxSize()) {
                        if (pageLoadProgress in 1..99) GlassLinearProgressIndicator(progress = { pageLoadProgress / 100f }, modifier = Modifier.fillMaxWidth())
                        AndroidView<WebView>(
                            factory = { _ -> createBrowser().also { browser = it; prepare(it) } },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }
                }
                if (confirmScreenshot) GlassAlertDialog(onDismissRequest = { confirmScreenshot = false },
                    title = { Text("允许本次截图？") }, text = { Text("页面可能包含账号或二维码，请勿分享。") },
                    confirmButton = { GlassTextButton(onClick = { confirmScreenshot = false; screenshots = true;
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }) { Text("允许") } },
                    dismissButton = { GlassTextButton(onClick = { confirmScreenshot = false }) { Text("取消") } })
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun createBrowser(): WebView {
        val view = WebView(this)
        store = BrowserCookieStore.attach(view, platform) // before settings/loadUrl: stable per-platform profile
        view.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            allowFileAccess = false; allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW; safeBrowsingEnabled = true
            setSupportMultipleWindows(false); javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = if (desktop) BrowserSessionPolicy.desktopAgent(WebSettings.getDefaultUserAgent(this@EmbeddedLoginActivity))
                else WebSettings.getDefaultUserAgent(this@EmbeddedLoginActivity)
            useWideViewPort = desktop; loadWithOverviewMode = desktop
        }
        store!!.manager.setAcceptCookie(true)
        store!!.manager.setAcceptThirdPartyCookies(view, false)
        view.setOnTouchListener { _, e -> if (e.actionMasked == MotionEvent.ACTION_UP) userInteracted = true; false }
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                if (BrowserSessionPolicy.allowed(platform, request.url.toString())) return false
                status = "已阻止外部跳转，请使用此官方页面的登录方式"
                return true
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                pageVisible = false; pageLoadProgress = 0; openAt = SystemClock.elapsedRealtime()
                if (BrowserSessionPolicy.allowed(platform, url)) { current = url; addVisited(url); status = "正在加载官方页面" }
            }
            override fun onPageCommitVisible(view: WebView, url: String) { visible(url) }
            override fun onPageFinished(view: WebView, url: String) { visible(url) }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) { pageVisible = false; status = "页面未能加载，请刷新或切换显示模式" }
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                // No custom fetching/interception of JS, cookies, QR images or captcha traffic.
                return if (platform == Platform.KUAISHOU && BrowserSessionPolicy.skipVideoInLogin(
                        request.url.toString(), request.isForMainFrame, lightweight))
                    WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0))) else null
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, value: Int) { pageLoadProgress = value.coerceIn(0, 100) }
        }
        view.setDownloadListener { _, _, _, _, _ -> status = "请返回解析页下载文件" }
        return view
    }
    private fun visible(url: String) {
        if (!ready || !BrowserSessionPolicy.allowed(platform, url)) return
        current = url; addVisited(url); pageVisible = true
        if (!savedAny) status = "在官方页面登录，检测到新会话后自动保存到本机"
    }
    private fun addVisited(url: String) {
        visited.add(url)
        while (visited.size > 12) visited.remove(visited.first())
    }
    private fun prepare(view: WebView) {
        lifecycleScope.launch {
            try {
                val start = withContext(Dispatchers.IO) { graph.load(); saver.initial(platform) }
                expected = start
                val account = start.account
                val cookies = if (account != null && !account.rejected &&
                    SessionHealthPolicy.local(platform, account.cookies) != SessionHealthCode.EXPIRED) account.cookies else emptyList()
                gate.baseline(cookies)
                withTimeout(12_000) { store!!.restore(cookies) }
                token.check(); ready = true; view.loadUrl(current)
            } catch (e: TimeoutCancellationException) { if (!isFinishing) status = "浏览器准备超时，请关闭后重试" }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { status = "浏览器或安全存储初始化失败，旧账号保留" }
        }
    }
    private suspend fun capture(manual: Boolean) {
        if (busy || !ready || !pageVisible || token.cancelled) return
        val old = expected ?: return
        val store = store ?: return
        busy = true
        try {
            val observed = store.snapshot(platform, visited.toList(), old.account?.cookies.orEmpty())
            token.check()
            val take = if (manual) true else gate.consider(observed, SystemClock.elapsedRealtime(), userInteracted, firstSample)
            firstSample = false
            if (!take) return
            if (observed.isEmpty() || (!manual && LoginCookiePolicy.active(platform, observed).isEmpty())) {
                if (manual) status = "页面尚未产生可保存的 Cookie，请完成登录后重试"
                return
            }
            val result = withContext(Dispatchers.IO) { saver.save(platform, old, observed, token) }
            expected = result; savedAny = true; gate.markSaved(observed)
            status = if (manual) "Cookie 已保存 · 在线状态另行检查" else "Cookie 已自动保存 · 在线状态另行检查"
            setResult(Activity.RESULT_OK, Intent().putExtra("platform", platform.name).putExtra("status", "Cookie 已保存"))
            graph.scope.launch(Dispatchers.IO) { graph.sessionChecks.request(platform, true) }
            if (manual) finish()
        } catch (e: CancellationException) { throw e }
        catch (_: BrowserAccountChanged) { autoSave = false; status = "账号已在别处修改，请关闭后重新进入" }
        catch (_: Exception) { status = "本次未能保存，旧会话保留；可手动重试" }
        finally { busy = false }
    }
    private fun changeMode(pc: Boolean) {
        if (desktop == pc || busy) return
        desktop = pc; showOptions = false
        browser?.settings?.apply {
            userAgentString = if (pc) BrowserSessionPolicy.desktopAgent(WebSettings.getDefaultUserAgent(this@EmbeddedLoginActivity))
                else WebSettings.getDefaultUserAgent(this@EmbeddedLoginActivity)
            useWideViewPort = pc; loadWithOverviewMode = pc
        }
        browser?.reload() // same cookie store and same login attempt, not a new WebView
    }
    private fun closeBrowser() { finish() }
    override fun onDestroy() {
        token.cancel(); captureJob?.cancel()
        if (ownsLease) {
            browser?.apply { stopLoading(); onPause(); removeAllViews(); destroy() }; browser = null
            // Keep static resource caches for next entry. Temporary cookie copies are erased;
            // the canonical, durable account remains AES-GCM protected in SessionVault.
            val s = store
            if (s == null) leased.set(false) else {
                s.clearSiteStorage() // remove localStorage/session artifacts, NOT the HTTP resource cache
                s.eraseTemporary { leased.set(false) }
            }
        }
        super.onDestroy()
    }
}
