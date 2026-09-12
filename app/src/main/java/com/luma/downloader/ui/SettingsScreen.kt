package com.luma.downloader.ui

import android.provider.Settings
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.flow.collect
import com.luma.downloader.data.*
import android.os.Build
import com.luma.downloader.BuildConfig
import kotlinx.coroutines.CancellationException

private val sectionNames = linkedMapOf("accounts" to "平台账号", "appearance" to "外观与排版", "glass" to "液态玻璃",
    "buttons" to "按钮与控件", "motion" to "动态效果", "downloads" to "解析与下载", "privacy" to "隐私与会话",
    "accessibility" to "辅助功能", "engine" to "解析引擎", "about" to "关于镜流")
private fun sectionIcon(key: String) = when(key) {
    "accounts" -> Icons.Outlined.AccountCircle; "appearance" -> Icons.Outlined.Contrast; "glass" -> Icons.Outlined.WaterDrop
    "buttons" -> Icons.Outlined.TouchApp; "motion" -> Icons.Outlined.Animation; "downloads" -> Icons.Outlined.FileDownload
    "privacy" -> Icons.Outlined.Shield; "accessibility" -> Icons.Outlined.AccessibilityNew; "engine" -> Icons.Outlined.Tune; else -> Icons.Outlined.Info
}
@Composable fun SettingsScreen(vm: LumaViewModel, wide: Boolean) {
    val s = LocalUiSettings.current; val mode = LocalAppearance.current.motion
    val holder = rememberSaveableStateHolder()
    val predictive = remember { Animatable(0f) }
    var skipReturn by remember { mutableStateOf(false) }
    val route = vm.section
    val pageActive=LocalSceneActive.current
    BackHandler(enabled = pageActive && route != "home" && (!s.enabled("swipeBack") || wide)) { vm.section = "home" }
    PredictiveBackHandler(enabled = pageActive && route != "home" && s.enabled("swipeBack") && !wide) { progress ->
        try {
            progress.collect { predictive.snapTo(it.progress.coerceIn(0f,1f)) }
            if(predictive.value>0f)predictive.animateTo(1f,tween(90))
            skipReturn = predictive.value > 0f
            vm.section = "home"
            predictive.snapTo(0f)
        } catch(_: CancellationException) {
            // A cancelled gesture must return smoothly, not jump a partially moved layer to zero.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {predictive.animateTo(0f,s.motionSpec(MotionChannel.PAGE,mode))}
        }
    }
    Row(Modifier.fillMaxSize()) {
        if(wide) Column(Modifier.width(230.dp).fillMaxHeight().padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("设置", fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
            sectionNames.forEach { (key, title) ->
                GlassSurface(Modifier.fillMaxWidth().clickable { vm.section = key },radius=12.dp,role=GlassRole.NAVIGATION,sourceOverride=LocalBackdropHaze.current,
                    tint=if(route==key)LocalLumaPalette.current.accent.copy(alpha=.1f) else androidx.compose.ui.graphics.Color.Unspecified) {
                    Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Icon(sectionIcon(key),null,Modifier.size(20.dp));Text(title)
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            if(wide) {
                RetainedSceneHost(keys=listOf("home")+sectionNames.keys,selected=route,distancePx=24f,
                    channel=MotionChannel.PAGE,precompose=false,modifier=Modifier.fillMaxSize()) {section->
                    Box(Modifier.fillMaxSize()) {GlassBackdrop(Modifier.matchParentSize());SettingsPane(vm,section,true)}
                }
            } else {
                var lastDetail by rememberSaveable {mutableStateOf("appearance")}
                SideEffect {if(route!="home"&&lastDetail!=route)lastDetail=route}
                val stack=updateTransition(route!="home",label="settings-stack")
                val reveal by stack.animateFloat(transitionSpec={
                    if(mode==MotionMode.OFF||skipReturn)snap() else s.motionSpec(MotionChannel.PAGE,mode)
                },label="detail-reveal") {opened->if(opened)1f else 0f}
                fun amount():Float=when {
                    skipReturn&&route=="home"->0f
                    predictive.value>0f->1f-predictive.value
                    else ->reveal
                }.coerceIn(0f,1f)
                // Keep home and its scroller alive under the pushed page. Predictive back never
                // reveals an empty backdrop and then remounts home on the final frame.
                RetainedPane(Modifier.fillMaxSize().suppressPaneInput(route!="home").hideBehindOverlay(route!="home"),
                    visible={amount()<.99999f || route=="home"},translation={-widthPx*.24f*amount()}) {
                    GlassBackdrop(Modifier.matchParentSize())
                    CompositionLocalProvider(LocalSceneActive provides (pageActive&&route=="home")){holder.SaveableStateProvider("home") {SettingsPane(vm,"home",false)}}
                }
                // The last visited pane stays mounted even after returning. Revisiting it does
                // not allocate a new list, set of sliders, or Haze effects on the final back frame.
                val detail=if(route!="home")route else lastDetail
                RetainedPane(Modifier.fillMaxSize().suppressPaneInput(route=="home").hideBehindOverlay(route=="home"),
                    visible={amount()>0.00001f || route!="home"},translation={widthPx*(1f-amount())}) {
                    GlassBackdrop(Modifier.matchParentSize())
                    CompositionLocalProvider(LocalSceneActive provides (pageActive&&route!="home")) {
                        holder.SaveableStateProvider(detail) {SettingsPane(vm,detail,false)}
                    }
                }

            }
            LaunchedEffect(route) {if(route!="home")skipReturn=false}
        }
    }
}
@Composable private fun SettingsPane(vm: LumaViewModel, section: String, wide: Boolean) {
    val s = LocalUiSettings.current; val p = LocalLumaPalette.current; val context = LocalContext.current
    var resetConfirm by remember { mutableStateOf(false) }
    val paneActive=LocalSceneActive.current
    val state = rememberLazyListState()
    val scrolled by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 40 } }
    LaunchedEffect(scrolled,vm.page,vm.section,paneActive) {
        val active=vm.section
        if(paneActive&&vm.page==AppPage.SETTINGS && active==section)vm.contentScrolled = scrolled
    }
    if(resetConfirm&&paneActive) GlassAlertDialog(onDismissRequest={resetConfirm=false},title={Text("恢复外观与动效默认值？")},
        text={Text("恢复主题、字号、玻璃、按钮和动效。不会改变账号、下载任务、画质和文件，也不会关闭辅助功能中的减少动态效果或减少透明度。")},
        confirmButton={GlassTextButton(onClick={resetConfirm=false;vm.restoreVisual(VisualResetScope.ALL_VISUAL)}){Text("恢复默认")}},
        dismissButton={GlassTextButton(onClick={resetConfirm=false}){Text("取消")}})
    Column(Modifier.fillMaxSize()) {
        GlassSurface(Modifier.fillMaxWidth(),radius=0.dp,role=GlassRole.NAVIGATION,sourceOverride=LocalBackdropHaze.current) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if(section != "home" && !wide) GlassIconButton(onClick = { vm.section = "home" },modifier=Modifier.testTag("settings-back")) { Icon(Icons.Outlined.ChevronLeft, "返回设置") }
            Text(sectionNames[section] ?: "设置", modifier = Modifier.padding(start = if(section == "home") 12.dp else 0.dp),
                color = p.ink, fontSize = if(section == "home") 28.sp else 22.sp, fontWeight = FontWeight.Bold)
        }
        }
        val (topFade, bottomFade) = rememberScrollEdgeAlphas(state)
        val fadeEnabled = s.enabled("scrollFade") && !s.enabled("reduceTransparency") &&
            s.text("material") != "solid"
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()
            .scrollEdgeFade(fadeEnabled, topFade, bottomFade, p.background),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = if(wide) 32.dp else 152.dp),
            verticalArrangement = Arrangement.spacedBy(s.number("groupGap").dp)) {
            if(section == "home") {
                item { GroupCard { SettingsLine("平台账号", "B站、抖音、快手、小红书、YouTube、TikTok、X", Icons.Outlined.AccountCircle, onClick = { vm.section = "accounts" }) } }
                listOf("个性化" to listOf("appearance", "glass", "buttons", "motion"), "使用偏好" to listOf("downloads", "privacy", "accessibility"), "管理" to listOf("engine", "about")).forEach { (label, keys) ->
                    item(key = label) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { SectionLabel(label); GroupCard { keys.forEachIndexed { i, key ->
                        SettingsLine(sectionNames.getValue(key), icon = sectionIcon(key), onClick = { vm.section = key }); if(i != keys.lastIndex) InsetDivider()
                    } } } }
                }
                item { GlassTextButton(onClick={resetConfirm=true}){Text("恢复外观与动效默认配置…")} }
                item { Note("镜流 · 本机解析与下载\n外观设置只保存在此设备。") }
            } else if(section == "accounts") item { AccountPanel(vm) }
            else if(section == "engine") {
                item { GroupCard { Column(Modifier.padding(18.dp)) { ParserEngineControl(vm) } } }
                item { GroupCard { SettingsLine("解析组件", vm.engineStatus); InsetDivider(); SettingsLine("国内平台", "自动模式仅在本机使用原生与通用提取器。parse-video-py 选中后也在手机本地执行，无需服务器；B站也使用所选引擎；本地 Python 上游仅首分P，不支持时明确提示，不自动切换原生。") } }
                item { LiquidActionButton(onClick = vm::updateEngine, enabled = !vm.engineBusy, modifier = Modifier.fillMaxWidth()) { Text(if(vm.engineBusy) "正在更新…" else "更新通用解析引擎") } }
                item { Note("更新需要连接上游发布服务器，不代表国内下载本身依赖该服务器。引擎更新失败不会删除下载文件。") }
            } else if(section == "about") {
                item { GroupCard { SettingsLine("镜流", "GlassFlow · ${BuildConfig.VERSION_NAME} · Kotlin / Jetpack Compose"); InsetDivider(); SettingsLine("存储位置", "Android 10 及以上：Download/镜流；旧 Download/拾流 文件保留原位置。Android 8–9：应用专属下载目录，可从文件页打开 / 分享。"); InsetDivider(); SettingsLine("使用边界", "只保存你拥有或获准下载的内容，不绕过 DRM、付费或账号访问限制。") } }
                item { Note("开源依赖和许可见源码根目录 THIRD_PARTY_NOTICES.md。平台接口可能变动；未提供的统计数据不会显示为 0。") }
            } else {
                if(section == "glass") item {
                    LiquidActionButton(onClick={vm.restoreVisual(VisualResetScope.GLASS)},modifier=Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Restore,null); Spacer(Modifier.width(8.dp));Text("恢复液态玻璃默认值")
                    }
                }
                // 0.9.1 live preview uses the production renderer so Clear/Regular/Frost
                // and tinted medium match what buttons/cards/menus actually show.
                if(section == "glass") item {
                    GroupCard { Column(Modifier.padding(18.dp)) { GlassMaterialPreview() } }
                }
                if(section == "buttons") item {
                    GroupCard { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.Text("有色玻璃介质 · 当前强调色", color = p.muted, fontSize = 12.sp)
                        TintedMediumPreview()
                    } }
                }
                if(section == "motion") item { GlassTextButton(onClick=vm::defaultMotion) {Text("恢复灵动弹簧默认值")} }
                val specs = SettingsCatalog.active.filter { it.section == section }
                // Each module has a bounded material layer. A single offscreen layer containing
                // dozens of sliders can exceed GPU texture limits and defeat LazyColumn reuse.
                items(specs, key = { it.key }) { spec ->
                    GroupCard { SettingControl(vm, spec) }
                }
                if(section == "appearance") item {GlassTextButton(onClick={resetConfirm=true}){Text("恢复外观与动效默认配置…")}}
                if(section == "downloads") item { Note("并发和 Wi-Fi 限制会影响真实下载。任务进入视频、音频、合并、保存各阶段时分别展示进度；未知大小不伪造百分比。") }
                if(section == "privacy") item { Note("自动维护目前仅用于 B站：需要扫码获取的可用刷新令牌。其他平台在 Cookie 失效后需要重新登录或导入。") }
                if(section == "glass") item {
                    val fallback=GlassPolicy.explanation(s,"blur",Build.VERSION.SDK_INT>=31 && LocalView.current.isHardwareAccelerated,Build.VERSION.SDK_INT)
                    if(com.luma.downloader.ui.optics.OpticalRuntime.shaderDisabled)Note("此设备的镜片着色器未能初始化，当前使用模糊或实色回退。")
                    Note(fallback ?: "Android 13 及以上使用镜片折射与色散；Android 12 使用模糊。文字、二维码不做光学变形。各区域可独立关闭玻璃。")
                }
                if(section == "accessibility") item { GlassTextButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                        .onFailure { vm.notice("当前系统未能打开应用设置，请从系统设置进入镜流。") }
                }) { Text("打开系统权限与通知设置") } }
            }
        }
    }
}
@Composable private fun SettingControl(vm: LumaViewModel, spec: SettingSpec) {
    val settings = LocalUiSettings.current
    val value = settings.text(spec.key)
    val opticalUnavailable=spec.key in setOf("refraction","lensBevel","dispersion") && com.luma.downloader.ui.optics.OpticalRuntime.shaderDisabled
    val availability=if(opticalUnavailable) "镜片着色器当前不可用" else GlassPolicy.explanation(settings,spec.key,Build.VERSION.SDK_INT>=31 && LocalView.current.isHardwareAccelerated,Build.VERSION.SDK_INT)
    when(spec.kind) {
        SettingKind.TOGGLE -> SettingsLine(spec.title,detail=availability.orEmpty(), trailing = { LiquidSwitch(settings.enabled(spec.key), { vm.setSetting(spec.key, it.toString()) }, spec.title) })
        SettingKind.CHOICE -> {
            var expanded by remember { mutableStateOf(false) }
            Box { SettingsLine(spec.title, onClick = { expanded = true }, trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(spec.options.firstOrNull { it.first == value }?.second.orEmpty(), color = LocalLumaPalette.current.muted, fontSize = 14.sp); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = LocalLumaPalette.current.muted) }
            })
                SpringDropdownMenu(expanded, { expanded = false }, title = spec.title) {
                    spec.options.forEachIndexed { index, (key, title) ->
                        SpringMenuItem(selected = value == key, onClick = { vm.setSetting(spec.key, key); expanded = false }) { Text(title) }
                        if (index != spec.options.lastIndex) SpringMenuDivider()
                    }
                }
            }
        }
        SettingKind.RANGE -> {
            val enabled = availability==null
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(spec.title, Modifier.weight(1f)); Text("${settings.number(spec.key).toInt()} ${spec.unit}", color = LocalLumaPalette.current.muted)
                }
                LiquidSlider(settings.number(spec.key), onValueChange = { vm.setSetting(spec.key, MotionPolicy.snapRange(spec, it).toString()) },
                    valueRange = spec.minimum..spec.maximum, steps = ((spec.maximum - spec.minimum) / spec.step).toInt() - 1, enabled = enabled, description = spec.title)
                availability?.let {Note(it)}
                if(spec.key=="blur" && settings.text("performance")=="efficient")Note("省资源模式实际模糊最多 12 dp；菜单会增加底色遮蔽。")
            }
        }
    }
}
