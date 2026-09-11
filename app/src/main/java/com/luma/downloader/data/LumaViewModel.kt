package com.luma.downloader.data

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luma.core.*
import com.luma.downloader.AppGraph
import com.luma.downloader.auth.*
import com.luma.downloader.download.humanError
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

class LumaViewModel(application: Application) : AndroidViewModel(application) {
    val graph = AppGraph.get(application)
    val settings = graph.settings.flow
    val tasks = graph.tasks.flow
    // Byte progress does not invalidate the app root or every navigation item.
    val activeTaskCount = tasks.map { list -> list.count { it.stage.isActive || it.stage == TransferStage.QUEUED } }
        .distinctUntilChanged().flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val accounts = graph.vault.accounts
    val sessionHealth = graph.sessionChecks.flow
    var qrPlatform by mutableStateOf(Platform.BILIBILI); private set
    var qrPhase by mutableStateOf(AuthorizationPhase.WAITING); private set
    private val qrGate=QrAttemptGate()
    private var qrToken:CancelToken?=null
    private val notices = Channel<String>(Channel.BUFFERED)
    val messages = notices.receiveAsFlow()
    var ready by mutableStateOf(false); private set
    var loadError by mutableStateOf(""); private set
    var page by mutableStateOf(AppPage.PARSE)
    var section by mutableStateOf("home")
    var contentScrolled by mutableStateOf(false)
    var link by mutableStateOf("")
    var media by mutableStateOf<Media?>(null); private set
    var formatId by mutableStateOf("")
    var selected by mutableStateOf<Set<String>>(emptySet()); private set
    var parsing by mutableStateOf(false); private set
    var enqueuing by mutableStateOf(false); private set
    var parseError by mutableStateOf(""); private set
    var engineStatus by mutableStateOf("内置解析引擎 · 按需初始化"); private set
    var engineBusy by mutableStateOf(false); private set
    var accountBusy by mutableStateOf(false); private set
    var qrOpen by mutableStateOf(false); private set
    var qrTicket by mutableStateOf<QrTicket?>(null); private set
    var qrLoading by mutableStateOf(false); private set
    var qrMessage by mutableStateOf(""); private set
    private var parseJob: Job? = null
    private var qrJob: Job? = null
    private var parseSerial = 0
    private var parsedEngine = "auto"
    var localParserStatus by mutableStateOf("组件随 APK 内置；首次解析时加载"); private set
    var localParserChecking by mutableStateOf(false); private set
    var batchOpen by mutableStateOf(false); private set
    var batchEntries by mutableStateOf<List<MediaEntry>>(emptyList()); private set
    var batchEngine by mutableStateOf("auto"); private set
    var batchQuality by mutableStateOf("best")
    var batchSample by mutableStateOf<Media?>(null); private set
    var batchBusy by mutableStateOf(false); private set
    var batchError by mutableStateOf(""); private set
    private var batchJob:Job?=null
    private var batchSerial=0
    private var batchPlatform=Platform.DIRECT
    init { initialize() }
    fun initialize() = viewModelScope.launch {
        loadError = ""
        try { withContext(Dispatchers.IO) { graph.load(); graph.queue.recover(); graph.startMaintenance() }; ready = true; graph.sessionChecks.checkSaved()
        }
        catch(e: CancellationException) { throw e }
        catch(_: Exception) { loadError = "本机设置、任务记录或加密账号读取失败。原文件未被删除；请查看编译说明中的恢复方法。" }
    }
    fun notice(message: String) { notices.trySend(message) }
    fun setSetting(key: String, value: String) { if(key=="parserEngine")changeParser(value)else graph.settings.set(key, value) }
    fun restoreVisual(scope: VisualResetScope) { graph.settings.restore(scope); notice(if(scope==VisualResetScope.GLASS) "已恢复玻璃与控件默认值；账号和下载设置未改变" else "已恢复外观与动效默认值；账号和下载设置未改变") }
    fun defaultMotion() { graph.settings.applyMotionPreset() }
    fun flushSettings() = viewModelScope.launch(Dispatchers.IO) { graph.settings.flush() }
    fun toggle(id: String) { selected = Selection.toggle(selected, id) }
    fun selectAll() { selected = Selection.all(selected, media?.entries?.map { it.id }?.toSet().orEmpty()) }
    fun cancelParse() { parseSerial++; parseJob?.cancel(); parsing = false; if(!enqueuing)closeBatch() }
    fun parse(more: Boolean = false) {
        if(enqueuing)return
        closeBatch()
        val old = media
        val engine=if(more)parsedEngine else graph.settings.current().text("parserEngine")
        if(more && (old?.nextOffset == null || parsing)) return
        val input = if(more) old!!.url else link
        val url = try { UrlPolicy.normalize(input) } catch(_: Exception) { parseError = "请粘贴完整的 HTTPS 视频、图集或合集分享链接"; return }
        parseJob?.cancel(); val serial = ++parseSerial
        parsing = true; parseError = ""
        if(!more) { media = null; formatId = ""; selected = emptySet() }
        parseJob = viewModelScope.launch {
            try {
                val next = cancellable { token -> graph.extractors.parse(url, token, offset = if(more) old!!.nextOffset!! else 0,engine=engine) }
                if(serial != parseSerial) return@launch
                media = if(more) old!!.copy(entries = (old.entries + next.entries).distinctBy { it.id }, nextOffset = next.nextOffset, totalEntries = next.totalEntries ?: old.totalEntries) else next
                if(!more) {parsedEngine=engine;formatId = choose(next, graph.settings.current().text("defaultQuality"))?.id.orEmpty()}
                if(next.formats.isEmpty() && next.entries.isEmpty()) parseError = "平台没有返回可以下载的媒体。请检查登录、权限与链接类型。"
            } catch(e: CancellationException) { throw e }
              catch(e: Exception) { if(serial == parseSerial) parseError = humanError(e) }
            finally { if(serial == parseSerial) parsing = false }
        }
    }
    fun selectedFormat(): Format? = media?.formats?.firstOrNull { it.id == formatId }
    fun enqueue() {
        if(enqueuing || parsing) return
        val m = media ?: return
        if(m.kind==Kind.PLAYLIST){
            val chosen=m.entries.filter{it.id in selected}
            if(chosen.isEmpty()){notice("先选择要下载的项目，或点击全选");return}
            batchEntries=chosen;batchPlatform=m.platform;batchEngine=ParsedEngineBinding(parsedEngine).taskEngine();batchOpen=true;batchQuality="best";loadBatchSample();return
        }
        val policy = graph.settings.current().text("defaultQuality")
        val items: List<DownloadTask> = when(m.kind) {
            Kind.GALLERY -> m.entries.filter { it.id in selected && it.image }.map { item ->
                DownloadTask(source = m.url, title = "${m.title} · ${item.title}", platform = m.platform,
                    imageId = item.id, thumbnail = item.url, formatId = "image", formatLabel = "原图",parserEngine=parsedEngine) }
            Kind.PLAYLIST -> m.entries.filter { it.id in selected }.map { item ->
                DownloadTask(source = item.url, title = item.title, platform = m.platform,
                    thumbnail = item.thumbnail, formatId = "policy:$policy", formatLabel = "按偏好选取实际可用画质",parserEngine=parsedEngine) }
            else -> {
                val f = selectedFormat() ?: return
                if(f.drm) { notice("不支持受 DRM 保护的资源"); return }
                listOf(DownloadTask(source = m.url, title = m.title, platform = m.platform,
                    thumbnail = m.thumbnail, formatId = f.id, formatLabel = f.display, total = f.size(m.duration).bytes,parserEngine=parsedEngine))
            }
        }
        if(items.isEmpty()) { notice("先选择要下载的项目，或点击全选"); return }
        enqueuing = true
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { graph.queue.add(items) }; page = AppPage.DOWNLOADS; notice("已添加 ${items.size} 个真实下载任务") }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { notice(humanError(e)) }
            finally { enqueuing = false }
        }
    }
    fun changeParser(value:String) {
        if(enqueuing||!SettingsCatalog.valid("parserEngine",value))return
        if(graph.settings.current().text("parserEngine")==value)return
        cancelParse();graph.settings.set("parserEngine",value)
        media=null;formatId="";selected=emptySet();parseError=""
    }
    fun checkLocalParser() {
        if(localParserChecking||parsing||enqueuing)return
        localParserChecking=true
        viewModelScope.launch {
            try {localParserStatus=cancellable{token->graph.localParser.selfTest(token)}}
            catch(e:CancellationException){throw e}
            catch(e:Exception){localParserStatus=humanError(e)}
            finally{localParserChecking=false}
        }
    }
    fun closeBatch() {
        if(enqueuing)return
        batchSerial++;batchJob?.cancel();batchOpen=false;batchBusy=false;batchSample=null;batchError="";batchEntries=emptyList()
    }
    fun loadBatchSample() {
        if(!batchOpen||enqueuing)return
        val entry=batchEntries.firstOrNull()?:return
        batchJob?.cancel();val serial=++batchSerial;val engine=batchEngine
        batchBusy=true;batchError="";batchSample=null
        batchJob=viewModelScope.launch {
            try {
                val sample=cancellable{token->graph.extractors.parse(entry.url,token,one=true,engine=engine)}
                if(serial!=batchSerial||!batchOpen)return@launch
                if(sample.formats.none{!it.drm})throw PlatformError("首项没有返回可用视频/音频画质，请检查链接或切换引擎","QUALITY_UNAVAILABLE")
                batchSample=sample
                if(BatchQuality.options(sample).none{it.key==batchQuality})batchQuality="best"
            }catch(e:CancellationException){throw e}
             catch(e:Exception){if(serial==batchSerial)batchError=humanError(e)}
            finally{if(serial==batchSerial)batchBusy=false}
        }
    }
    fun confirmBatch() {
        if(enqueuing||batchBusy||!batchOpen)return
        val sample=batchSample?:return
        val option=BatchQuality.options(sample).firstOrNull{it.key==batchQuality}?:return
        if(BatchQuality.select(sample,option.key)==null){batchError="所选档位不再可用，请重新读取画质";return}
        val engine=batchEngine
        val items=batchEntries.map{entry->DownloadTask(source=entry.url,title=entry.title,platform=batchPlatform,
            thumbnail=entry.thumbnail,formatId="policy:${option.key}",formatLabel=option.title,parserEngine=engine)}
        if(items.isEmpty())return
        enqueuing=true
        viewModelScope.launch {
            try {withContext(Dispatchers.IO){graph.queue.add(items)};batchOpen=false;batchSample=null;batchEntries=emptyList();page=AppPage.DOWNLOADS;notice("已添加 ${items.size} 项，按每项实际画质下载")}
            catch(e:CancellationException){throw e}
            catch(e:Exception){batchError=humanError(e)}
            finally{enqueuing=false}
        }
    }
    fun pause(id: String) = operation { graph.queue.pause(id) }
    fun resume(id: String) = operation { graph.queue.resume(id) }
    fun delete(id: String) = operation { graph.queue.delete(id) }
    fun pauseAll() = operation { graph.tasks.all().filter { it.stage.isActive || it.stage == TransferStage.QUEUED }.forEach { graph.queue.pause(it.id) } }
    fun logout(platform: Platform) = operation {
        graph.vault.delete(platform); graph.sessionChecks.clear(platform)
        com.luma.downloader.engine.MediaHttp.clearImageMemory()
    }
    fun checkAccount(platform: Platform, force: Boolean = false) { if (ready) graph.scope.launch { graph.sessionChecks.request(platform, force) } }
    fun checkSavedAccounts() { if (ready) graph.sessionChecks.checkSaved() }
    fun importCookies(platform: Platform, text: String) {
        if(accountBusy) return
        accountBusy = true
        viewModelScope.launch {
            try {
                cancellable { token ->
                    val previous = graph.vault.get(platform)
                    val draft = Account(platform, CookieCodec.parse(text, platform))
                    val local = SessionHealthPolicy.local(platform, draft.cookies)
                    require(local !in setOf(SessionHealthCode.EXPIRED, SessionHealthCode.MISSING)) { "会话不完整或已过期" }
                    val account = if (platform == Platform.BILIBILI) {
                        try { graph.bili.verify(draft, token) }
                        catch (e: LoginRejected) { throw e }
                        catch (e: Exception) { token.check(); draft }
                    } else draft
                    token.check()
                    check(graph.vault.saveIfUnchanged(previous, account)) { "账号已在别处修改" }
                    if (account.checked > 0) graph.sessionChecks.rememberVerified(account)
                    else graph.sessionChecks.request(platform)

                }
                notice("Cookie 已加密持久保存；检查结果见账号卡片")
            } catch(e: CancellationException) { throw e }
              catch(e: LoginRejected) { notice("B站 Cookie 已失效；未覆盖原来的账号") }
              catch(_: Exception) { notice("未导入：Cookie 格式不正确、校验失败或安全存储不可用；原账号保留") }
            finally { accountBusy = false }
        }
    }
    fun importCookieFile(platform: Platform, uri: Uri) = viewModelScope.launch {
        try {
            val text = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while(true) { val n = stream.read(buffer); if(n < 0) break; require(out.size() + n <= 1024 * 1024); out.write(buffer, 0, n) }
                    out.toString("UTF-8")
                } ?: error("File unavailable")
            }
            importCookies(platform, text)
        } catch(e: CancellationException) { throw e }
          catch(_: Exception) { notice("无法读取 Cookie 文件，或文件超过 1 MB") }
    }
    fun startQr() = startQr(Platform.BILIBILI)
    fun startQr(platform: Platform) {
        if (platform != Platform.BILIBILI) return
        closeQr(); qrPlatform = platform
        val generation = qrGate.start()
        qrOpen = true; qrLoading = true; qrPhase = AuthorizationPhase.WAITING
        qrMessage = "正在向 B站申请二维码…"
        val token = CancelToken(); qrToken = token
        qrJob = viewModelScope.launch {
            try {
                val original = withContext(Dispatchers.IO) { synchronized(graph.vault) { graph.vault.get(platform) to graph.vault.epoch(platform) } }
                val ticket = withContext(Dispatchers.IO) { graph.bili.newQr(token) }
                if (!qrGate.accepts(generation)) return@launch
                qrTicket = ticket; qrLoading = false
                while (isActive && qrGate.accepts(generation) && System.currentTimeMillis() < ticket.expiresAt) {
                    val result = withContext(Dispatchers.IO) { graph.bili.poll(ticket, token) }
                    if (!qrGate.accepts(generation)) return@launch
                    qrMessage = result.text
                    qrPhase = when (result.code) {
                        86090L -> AuthorizationPhase.SCANNED
                        86038L -> AuthorizationPhase.EXPIRED
                        0L -> AuthorizationPhase.VERIFYING
                        else -> AuthorizationPhase.WAITING
                    }
                    val account = result.account
                    if (account != null) {
                        val saved = withContext(Dispatchers.IO) {
                            qrGate.commit(generation) {
                                token.check()
                                // Credential revision, not verification timestamps, defines account replacement.
                                synchronized(graph.vault) {
                                    if (graph.vault.get(platform)?.revision != original.first?.revision || graph.vault.epoch(platform) != original.second) false
                                    else graph.vault.save(account)
                                }
                            }
                        }
                        if (saved == true) {
                            graph.sessionChecks.rememberVerified(account)
                            qrPhase = AuthorizationPhase.CONFIRMED; qrOpen = false; qrTicket = null
                            notice("B站会话已确认并持久保存")
                        } else { qrPhase = AuthorizationPhase.FAILED; qrMessage = "账号已修改，未覆盖新会话，请关闭后重试" }
                        return@launch
                    }
                    if (result.code == 86038L) break
                    delay(2000)
                }
                if (qrGate.accepts(generation)) { qrPhase = AuthorizationPhase.EXPIRED; qrMessage = "二维码已过期，请关闭后重新进入扫码登录" }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (qrGate.accepts(generation)) { qrPhase = AuthorizationPhase.FAILED; qrMessage = "扫码申请或校验未完成，原会话已保留；请关闭后重试" }
            } finally { if (qrGate.accepts(generation)) qrLoading = false; token.cancel() }
        }
    }
    fun closeQr() {
        qrToken?.cancel(); qrGate.cancel(); qrJob?.cancel()
        qrOpen = false; qrTicket = null; qrLoading = false
    }
    fun updateEngine() {
        if(engineBusy) return
        engineBusy = true
        viewModelScope.launch {
            try { engineStatus = withContext(Dispatchers.IO) { graph.runtime.update(); "解析引擎：${graph.runtime.version()}" } }
            catch(e: CancellationException) { throw e }
            catch(_: Exception) { engineStatus = "更新失败，仍保留已安装的引擎；检查网络后重试" }
            finally { engineBusy = false }
        }
    }
    private fun operation(block: () -> Unit) = viewModelScope.launch {
        try { withContext(Dispatchers.IO) { block() } }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { notice(humanError(e)) }
    }
    private suspend fun <T> cancellable(block: (CancelToken) -> T): T = coroutineScope {
        val token = CancelToken()
        val watcher = launch(Dispatchers.Default) { try { awaitCancellation() } finally { token.cancel() } }
        try { withContext(Dispatchers.IO) { block(token) } } finally { token.cancel();watcher.cancel() }
    }
    private fun choose(m: Media, policy: String): Format? = when(policy) {
        "audio" -> audioBest(m.formats)
        "1080p", "720p" -> m.copy(formats = m.formats.filter { it.video && it.height in 1..policy.removeSuffix("p").toInt() }).best ?: m.best
        else -> m.best
    }
    override fun onCleared() { batchSerial++;batchJob?.cancel();parseJob?.cancel(); qrToken?.cancel(); qrGate.cancel(); qrJob?.cancel(); super.onCleared() }
}
