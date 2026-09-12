package com.luma.downloader.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luma.core.Platform
import com.luma.downloader.R
import com.luma.downloader.data.*
import com.luma.downloader.ui.optics.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState

@Composable fun PlatformIcon(platform: Platform, size: Dp = 32.dp) {
    val resource = when(platform) {
        Platform.BILIBILI -> R.drawable.platform_bilibili; Platform.DOUYIN -> R.drawable.platform_douyin
        Platform.KUAISHOU -> R.drawable.platform_kuaishou; Platform.XIAOHONGSHU -> R.drawable.platform_xiaohongshu
        Platform.YOUTUBE -> R.drawable.platform_youtube; Platform.TIKTOK -> R.drawable.platform_tiktok
        Platform.X -> R.drawable.platform_x; Platform.DIRECT -> null
    }
    if(resource != null) Image(painterResource(resource), platform.title, Modifier.size(size).clip(RoundedCornerShape(size * .23f)))
    else Icon(Icons.Outlined.Link, platform.title, Modifier.size(size), tint = LocalLumaPalette.current.muted)
}
@Composable fun RemoteImage(url: String, description: String?, modifier: Modifier = Modifier, referer: String = "", fit: Boolean = false,
    headers:Map<String,String> = emptyMap(), backups:List<String> = emptyList()) {
    val context=LocalContext.current
    val candidates=remember(url,backups){com.luma.core.MediaRequestPolicy.candidates(url,backups)}
    var index by remember(candidates){mutableIntStateOf(0)}
    var failed by remember(candidates){mutableStateOf(false)}
    var retry by remember(candidates){mutableIntStateOf(0)}
    val current=candidates.getOrNull(index).orEmpty()
    val request=remember(context,current,referer,headers,retry){ImageRequest.Builder(context).data(current)
        .apply {if(current.isNotBlank())com.luma.core.MediaRequestPolicy.headers(current,referer,headers).forEach{(k,v)->addHeader(k,v)}}
        .crossfade(true).build()}
    Box(modifier.background(LocalLumaPalette.current.muted.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Image, null, Modifier.size(26.dp), tint = LocalLumaPalette.current.muted.copy(alpha = .45f))
        if(current.isNotBlank()) key(current,retry) { AsyncImage(model=request,
            imageLoader=com.luma.downloader.engine.MediaHttp.imageLoader(context),contentDescription=description,
            modifier=Modifier.fillMaxSize(),contentScale=if(fit)ContentScale.Fit else ContentScale.Crop,
            onSuccess={failed=false},onError={if(index+1<candidates.size){index++;failed=false}else failed=true}) }
        if(failed) GlassTextButton(onClick={failed=false;index=0;retry++}) {Text("重试图片",fontSize=12.sp)}
    }
}
@Composable fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val s = LocalUiSettings.current
    val backing = rememberGlassSource()
    GlassSurface(modifier.fillMaxWidth(),radius=s.number("corner").dp,role=GlassRole.GROUP,materialOutput=backing) {
        CompositionLocalProvider(LocalControlHaze provides backing) { Column(Modifier.fillMaxWidth(),content=content) }
    }
}

@Composable fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = LocalLumaPalette.current.muted)
}
@Composable fun SectionLabel(text: String) { Text(text, color = LocalLumaPalette.current.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 2.dp)) }
@Composable fun SettingsLine(title: String, detail: String = "", icon: ImageVector? = null, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    val p = LocalLumaPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(Modifier.fillMaxWidth().background(if(pressed) p.ink.copy(alpha=.045f) else Color.Transparent)
        .then(if(onClick == null) Modifier else Modifier.clickable(interactionSource=interaction,indication=null,onClick=onClick))
        .padding(horizontal = 18.dp, vertical = if(LocalUiSettings.current.text("density") == "compact") 12.dp else 17.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if(icon != null) Icon(icon, null, Modifier.size(23.dp), tint = p.muted)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = p.ink)
            if(detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = p.muted)
        }
        if(trailing != null) trailing() else if(onClick != null) Icon(Icons.Outlined.ChevronRight, null, tint = p.muted, modifier = Modifier.size(18.dp))
    }
}
@Composable fun InsetDivider() { HorizontalDivider(Modifier.padding(start = 18.dp), thickness = .5.dp, color = LocalLumaPalette.current.line) }
@Composable fun PageList(vm: LumaViewModel, wide: Boolean, title: String, actions: @Composable RowScope.() -> Unit = {}, content: LazyListScope.() -> Unit) {
    val p = LocalLumaPalette.current; val state = rememberLazyListState()
    val settings = LocalUiSettings.current
    val scrolled by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 40 } }
    val owner=when(title){"解析"->AppPage.PARSE;"下载"->AppPage.DOWNLOADS;"文件"->AppPage.LIBRARY;else->AppPage.SETTINGS}
    LaunchedEffect(scrolled,vm.page) { if(vm.page==owner)vm.contentScrolled = scrolled }
    Column(Modifier.fillMaxSize()) {
        GlassSurface(Modifier.fillMaxWidth(),radius=0.dp,role=GlassRole.NAVIGATION,sourceOverride=LocalBackdropHaze.current) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = p.ink)
            actions()
        }
        }
        val (topFade, bottomFade) = rememberScrollEdgeAlphas(state)
        val fadeEnabled = settings.enabled("scrollFade") && !settings.enabled("reduceTransparency") &&
            settings.text("material") != "solid"
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()
            .scrollEdgeFade(fadeEnabled, topFade, bottomFade, p.background),
            contentPadding = PaddingValues(start = if(wide) 32.dp else 20.dp, end = if(wide) 32.dp else 20.dp,
                top = 10.dp, bottom = if(wide) 32.dp else 148.dp),
            verticalArrangement = Arrangement.spacedBy(LocalUiSettings.current.number("groupGap").dp), content = content)
    }
}
@Composable fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 46.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(42.dp), tint = LocalLumaPalette.current.muted.copy(alpha = .65f))
        Text(title, style = MaterialTheme.typography.titleLarge, color = LocalLumaPalette.current.ink)
        Note(subtitle)
    }
}
