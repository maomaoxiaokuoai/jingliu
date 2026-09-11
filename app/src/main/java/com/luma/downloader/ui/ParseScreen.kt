package com.luma.downloader.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.luma.core.*
import com.luma.downloader.data.*
import com.luma.downloader.preview.MediaPreview

@Composable fun ParseScreen(vm: LumaViewModel, wide: Boolean, requestNotifications: () -> Unit) {
    val p = LocalLumaPalette.current; val s = LocalUiSettings.current; val context = LocalContext.current
    var confirmPaste by remember { mutableStateOf(false) }
    val paste = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        vm.link = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.take(32768).orEmpty()
        if(vm.link.isBlank()) vm.notice("剪贴板里没有文字")
    }
    if(confirmPaste) GlassAlertDialog(onDismissRequest = { confirmPaste = false }, title = { Text("读取剪贴板？") },
        text = { Text("只在本次点击后读取分享文字。不会自动监视或上传剪贴板。") },
        confirmButton = { GlassTextButton(onClick = { confirmPaste = false; paste() }) { Text("读取") } },
        dismissButton = { GlassTextButton(onClick = { confirmPaste = false }) { Text("取消") } })
    if(vm.batchOpen&&LocalSceneActive.current)BatchDownloadDialog(vm,requestNotifications)
    val m = vm.media
    val galleryRows=remember(m?.entries,wide){m?.entries.orEmpty().chunked(if(wide)4 else 2)}
    val sortedFormats=remember(m?.formats){m?.formats.orEmpty().sortedWith(compareByDescending<Format>{it.video}.thenByDescending{it.height}.thenByDescending{it.bitrate?:0})}
    PageList(vm, wide, "解析", actions = { Text("镜流", color = p.muted, fontSize = 15.sp) }) {
        item(key = "input") {
            GroupCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ParserEngineControl(vm)
                    GlassTextField(value=vm.link,onValueChange={vm.link=it.take(32768)},
                        placeholder="粘贴视频、图集或合集分享链接",modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=4,
                        imeAction=androidx.compose.ui.text.input.ImeAction.Go,onSubmit={if(vm.link.isNotBlank()&&!vm.parsing)vm.parse()},
                        trailingIcon={GlassIconButton(onClick={if(s.enabled("clipboardPrompt"))confirmPaste=true else paste()}){Icon(Icons.Outlined.ContentPaste,"粘贴")}})
                    if(vm.parsing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlassCircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("正在读取平台返回的数据…", Modifier.weight(1f).padding(start = 10.dp), color = p.muted)
                            GlassTextButton(onClick = vm::cancelParse) { Text("取消") }
                        }
                    } else LiquidActionButton(onClick = { vm.parse() }, modifier = Modifier.fillMaxWidth(), enabled = vm.link.isNotBlank()) {
                        Icon(Icons.Outlined.Link, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("开始解析")
                    }
                }
            }
        }
        if(vm.parseError.isNotBlank()) item(key = "error") { GroupCard { Text(vm.parseError, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.error) } }
        if(m == null && !vm.parsing && vm.parseError.isBlank()) item(key = "empty") {
            EmptyState(Icons.Outlined.Link, "把影像留在本机", "从平台分享至镜流，或粘贴链接后解析。只下载你有权保存的内容。")
        }
        if(m != null) {
            item(key = "metadata-${m.id}") { MediaDetails(m) }
            if(m.kind == Kind.GALLERY || m.kind == Kind.PLAYLIST) {
                item(key = "selection") {
                    GroupCard {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if(m.kind == Kind.GALLERY) "图集 · ${m.entries.size} 张" else "合集 · 已加载 ${m.entries.size} 项", color = p.ink)
                                Note("已选 ${vm.selected.size} 项${m.totalEntries?.let { " / 共 $it 项" }.orEmpty()}")
                            }
                            GlassTextButton(onClick = vm::selectAll) { Text(if(m.entries.isNotEmpty() && vm.selected.size == m.entries.size) "取消全选" else "全选") }
                            GlassTextButton(enabled = vm.selected.isNotEmpty() && !vm.enqueuing && !vm.parsing, onClick = { if(m.kind!=Kind.PLAYLIST)requestNotifications(); vm.enqueue() }) { Text("下载") }
                        }
                    }
                }
                if(m.kind == Kind.GALLERY) items(galleryRows, key = { "images-${it.first().id}" }) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { entry ->
                            val selected = entry.id in vm.selected
                            GlassSurface(Modifier.weight(1f).toggleableForImage(selected) { vm.toggle(entry.id) },radius=16.dp,role=GlassRole.GROUP) {
                            Column {
                                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                    RemoteImage(entry.thumbnail.ifBlank{entry.url}, entry.title, Modifier.fillMaxSize(), m.url, headers=m.headers, backups=entry.backupUrls)
                                    GlassSelectionMark(selected,Modifier.align(Alignment.TopEnd).padding(8.dp)
                                        .semantics {contentDescription=if(selected) "已选中" else "未选中"})
                                }
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = p.ink)
                                    GlassTextButton(onClick={runCatching{MediaPreview.image(context,m,entry)}.onFailure{vm.notice("无法打开图片预览，请重新解析")}},contentPadding=PaddingValues(0.dp)){Text("预览",fontSize=12.sp)}
                                    if(selected) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = p.accent)
                                }
                            }
                            }
                        }
                        repeat((if(wide) 4 else 2) - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                } else items(m.entries, key = { "entry-${it.id}" }) { entry ->
                    val selected = entry.id in vm.selected
                    GroupCard(Modifier.toggleableForImage(selected) { vm.toggle(entry.id) }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RemoteImage(entry.thumbnail, null, Modifier.size(72.dp, 48.dp).clip(RoundedCornerShape(8.dp)))
                            Text(entry.title, Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis, color = p.ink)
                            GlassSelectionMark(selected)
                        }
                    }
                }
                if(m.nextOffset != null) item(key = "more") { GlassTextButton(onClick = { vm.parse(more = true) }, enabled = !vm.parsing, modifier = Modifier.fillMaxWidth()) { Text("继续读取后面的项目") } }
            } else {
                item(key = "quality") {
                    GroupCard {
                        SettingsLine("画质与预计大小", "默认优先当前账号实际可用的最高画质")
                        sortedFormats.forEach { f ->
                            val estimate = f.size(m.duration)
                            Row(Modifier.fillMaxWidth().clickable { vm.formatId = f.id }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassRadioButton(selected = f.id == vm.formatId, onClick = { vm.formatId = f.id })
                                Column(Modifier.weight(1f)) {
                                    Text(f.display, color = p.ink, fontWeight = if(f.id == vm.formatId) FontWeight.Medium else FontWeight.Normal)
                                    Note("${f.extension.uppercase()}${if(f.audioUrl.isNotBlank()) " · 视频 + 音频，下载后无损合并" else ""}")
                                }
                                Text((if(estimate.estimated && estimate.bytes != null) "约 " else "") + sizeText(estimate.bytes), color = p.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                item(key = "preview") {
                    GlassTextButton(onClick={runCatching{MediaPreview.video(context,m,vm.selectedFormat())}.onFailure{vm.notice("无法打开视频预览，请重新解析")}},enabled=vm.selectedFormat()!=null,modifier=Modifier.fillMaxWidth()) {Text("预览所选视频 / 音频")}
                }
                item(key = "download") {
                    LiquidActionButton(onClick = { requestNotifications(); vm.enqueue() }, enabled = !vm.parsing && !vm.enqueuing && vm.selectedFormat() != null, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(8.dp)); Text(if(vm.enqueuing) "正在建立任务…" else "下载所选画质")
                    }
                }
            }
            if(m.notice.isNotBlank()) item(key = "notice") { Note(m.notice) }
        }
    }
}
private fun Modifier.toggleableForImage(selected: Boolean, action: () -> Unit): Modifier = this
    .semantics { role = Role.Checkbox; toggleableState = if(selected) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off }
    .clickable(onClick = action)

@Composable private fun MediaDetails(m: Media) {
    val p = LocalLumaPalette.current; val s = LocalUiSettings.current
    var expanded by rememberSaveable(m.id) { mutableStateOf(false) }
    GroupCard {
        if(m.thumbnail.isNotBlank()) RemoteImage(m.thumbnail, "媒体封面", Modifier.fillMaxWidth().aspectRatio(if(m.kind == Kind.GALLERY) 2f else 16f / 9f), m.url, headers=m.headers)
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlatformIcon(m.platform, 22.dp); Note(m.platform.title)
            }
            SelectionContainer { Text(m.title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = p.ink, maxLines = if(expanded) Int.MAX_VALUE else s.text("titleLines").toInt(), overflow = TextOverflow.Ellipsis) }
            if(!s.enabled("hideProfile")) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if(m.author.avatar.isNotBlank()) RemoteImage(m.author.avatar, "作者头像", Modifier.size(36.dp).clip(CircleShape))
                Column {
                    Text(m.author.name.ifBlank { "作者未提供" }, color = p.ink)
                    Note("粉丝 ${countText(m.author.followers)}${m.duration?.let { " · ${it.toInt() / 60}:${(it.toInt() % 60).toString().padStart(2, '0')}" }.orEmpty()}")
                }
            }
            if(m.description.isNotBlank()) {
                SelectionContainer { Text(m.description, maxLines = if(expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, color = p.muted, style = MaterialTheme.typography.bodyMedium) }
                GlassTextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text(if(expanded) "收起简介" else "展开完整标题与简介") }
            }
            if(s.enabled("stats")) {
                val entries = listOf("播放" to m.stats.views, "点赞" to m.stats.likes, "收藏" to m.stats.favorites, "评论" to m.stats.comments, "转发" to m.stats.shares)
                entries.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (name, value) -> Column(Modifier.weight(1f)) { Text(countText(value), color = p.ink, fontSize = 15.sp); Text(name, color = p.muted, fontSize = 12.sp) } }
                } }
            }
        }
    }
}
