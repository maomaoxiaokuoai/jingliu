package com.luma.downloader.ui

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.repeatOnLifecycle
import com.luma.downloader.data.*
import com.luma.downloader.ui.optics.rememberGlassSource
import com.luma.downloader.ui.optics.captureGlass

@Composable fun ShiliuApp(vm: LumaViewModel, requestNotifications: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val haze = rememberGlassSource()
    val backdrop = rememberGlassSource()
    val overlays=remember { GlassOverlayState() }
    val storageError by vm.graph.settings.errors.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val focus=LocalFocusManager.current
    LaunchedEffect(vm.page){focus.clearFocus()}
    var systemReduced by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_RESUME) systemReduced = !ValueAnimator.areAnimatorsEnabled()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val appearance = settings.appearance(systemReduced)
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(storageError) { storageError?.let { snackbar.showSnackbar(it) } }
    CompositionLocalProvider(LocalUiSettings provides settings, LocalContentHaze provides haze, LocalOverlayGlass provides haze, LocalBackdropHaze provides backdrop, LocalGlassOverlay provides overlays,
        LocalDensity provides Density(density.density, density.fontScale * settings.number("fontScale") / 100f)) {
        LumaTheme(appearance) {
            val p = LocalLumaPalette.current
            BoxWithConstraints(Modifier.fillMaxSize().background(p.background).statusBarsPadding()) {
                val wide = maxWidth >= 840.dp && maxHeight >= 480.dp
                // The material backdrop spans both the phone and the full tablet rail/detail window.
                GlassBackdrop(Modifier.matchParentSize().captureGlass(backdrop))
                if(!vm.ready) {
                    Column(Modifier.align(Alignment.Center).padding(30.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if(vm.loadError.isBlank()) { GlassCircularProgressIndicator(); Text("正在读取本机数据…") }
                        else { Text(vm.loadError); GlassTextButton(onClick = { vm.initialize() }) { Text("重新读取") } }
                    }
                } else {
                    BackHandler(enabled = vm.page != AppPage.PARSE && !(vm.page == AppPage.SETTINGS && vm.section != "home")) { vm.page = AppPage.PARSE }
                    Row(Modifier.fillMaxSize().hideBehindOverlay(overlays.visible)) {
                        if(wide) TabletNavigation(vm)
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            Box(Modifier.fillMaxSize().captureGlass(haze)) {
                            CompositionLocalProvider(LocalContentHaze provides backdrop) {
                            RetainedSceneHost(keys=AppPage.entries.toList(),selected=vm.page,
                                distancePx=if(settings.text("tabTransition")=="fade")0f else with(density){settings.number("tabDistance").dp.toPx()},
                                modifier=Modifier.fillMaxSize()) {page->
                                Box(Modifier.fillMaxSize()) {
                                    GlassBackdrop(Modifier.matchParentSize())
                                    when(page) {
                                        AppPage.PARSE -> ParseScreen(vm, wide, requestNotifications)
                                        AppPage.DOWNLOADS -> ObservedDownloads(vm, wide, files=false)
                                        AppPage.LIBRARY -> ObservedDownloads(vm, wide, files=true)
                                        AppPage.SETTINGS -> SettingsScreen(vm, wide)
                                    }
                                }
                            }
                            }
                            }
                            if(!wide) PhoneNavigation(vm,
                                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 22.dp, vertical = 10.dp))
                        }
                    }
                    QrDialog(vm)
                }
                GlassOverlayHost(overlays,Modifier.matchParentSize())
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if(wide) 16.dp else 90.dp), snackbar={GlassSnackbar(it)})
            }
        }
    }
}
private val pageIcons = listOf(Icons.Outlined.Link, Icons.Outlined.FileDownload, Icons.Outlined.Folder, Icons.Outlined.Settings)
@Composable private fun TabletNavigation(vm: LumaViewModel) {
    val p = LocalLumaPalette.current
    GlassSurface(Modifier.width(94.dp).fillMaxHeight(), radius=0.dp, sourceOverride=LocalBackdropHaze.current) {
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("镜流", color = p.ink, modifier = Modifier.padding(bottom = 12.dp))
        AppPage.entries.forEachIndexed { i, page ->
            val selected = page == vm.page
            GlassSurface(Modifier.fillMaxWidth(),radius=18.dp,role=GlassRole.NAVIGATION,sourceOverride=LocalBackdropHaze.current,
                tint=if(selected)p.accent.copy(alpha=.12f) else Color.Unspecified) {
            Column(Modifier.fillMaxWidth()
                .elasticPress().testTag("tab-${page.name}").selectable(selected, role = Role.Tab, interactionSource=remember{MutableInteractionSource()}, indication=null, onClick = { vm.page = page }).padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(pageIcons[i], null, tint = if(selected) p.accent else p.muted)
                Text(page.title, fontSize = 12.sp, color = if(selected) p.accent else p.muted)
            }
            }
        }
    }
    }
}
@Composable private fun PhoneNavigation(vm: LumaViewModel, modifier: Modifier = Modifier) {
    val s = LocalUiSettings.current; val p = LocalLumaPalette.current; val mode = LocalAppearance.current.motion
    val count by vm.activeTaskCount.collectAsStateWithLifecycle()
    val dp=LocalDensity.current
    val collapsed = s.enabled("navCollapse") && vm.contentScrolled
    val height = animateDpAsState(if(collapsed) 58.dp else 70.dp, s.motionSpec(MotionChannel.CONTROL, mode), label = "nav-height")
    GlassSurface(modifier.widthIn(max = 520.dp).fillMaxWidth().layout {measurable,constraints->
        val px=height.value.roundToPx().coerceIn(constraints.minHeight,constraints.maxHeight)
        val child=measurable.measure(constraints.copy(minHeight=px,maxHeight=px))
        layout(child.width,px){child.place(0,0)}
    }, radius = 36.dp) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp)) {
            val cell = maxWidth / 4
            val offset by animateDpAsState(cell * vm.page.ordinal,
                if(s.enabled("tabMorph")) s.motionSpec(MotionChannel.INDICATOR, mode) else snap(), label = "nav-position")
            GlassSurface(Modifier.offset {IntOffset(with(dp){offset.roundToPx()},0)}.width(cell).fillMaxHeight(),radius=36.dp,role=GlassRole.NAVIGATION,tint=p.accent.copy(alpha=.055f),motionSample={offset.value}) {}
            Row(Modifier.fillMaxSize()) {
                AppPage.entries.forEachIndexed { i, page ->
                    val selected = vm.page == page
                    Column(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).elasticPress()
                        .testTag("tab-${page.name}").selectable(selected, role = Role.Tab, interactionSource=remember{MutableInteractionSource()}, indication=null, onClick = { vm.page = page }),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        BadgedBox(badge = { if(page == AppPage.DOWNLOADS && count > 0) GlassBadge(count.toString()) }) {
                            Icon(pageIcons[i], null, Modifier.size(23.dp), tint = if(selected) p.accent else p.muted)
                        }
                        if(!collapsed) Text(page.title, fontSize = 11.sp, color = if(selected) p.accent else p.muted)
                    }
                }
            }
        }
    }
}

/** Hidden retained pages do not collect every network progress tick. File lists only observe completed files. */
@Composable private fun ObservedDownloads(vm:LumaViewModel,wide:Boolean,files:Boolean) {
    val active=LocalSceneActive.current
    val owner=LocalLifecycleOwner.current
    val flow=remember(vm,files){vm.tasks.map {all->if(files)all.filter{it.stage==TransferStage.COMPLETE} else all}.distinctUntilChanged().flowOn(Dispatchers.Default)}
    var cached by remember(vm,files){mutableStateOf(if(files)vm.tasks.value.filter{it.stage==TransferStage.COMPLETE} else vm.tasks.value)}
    LaunchedEffect(flow,active,owner){if(active)owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){flow.collect{cached=it}}}
    DownloadsScreen(vm,wide,cached,files)
}
