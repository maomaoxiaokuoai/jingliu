package com.luma.downloader.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.luma.core.*
import com.luma.downloader.data.*

@Composable fun DownloadsScreen(vm: LumaViewModel, wide: Boolean, all: List<DownloadTask>, files: Boolean) {
    var deleteId by remember { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    val p = LocalLumaPalette.current; val context = LocalContext.current
    val list = remember(all,files,search) { all.filter { (!files || it.stage == TransferStage.COMPLETE) && (search.isBlank() || it.title.contains(search, true) || it.fileName.contains(search, true)) } }
    if(deleteId != null&&LocalSceneActive.current) GlassAlertDialog(onDismissRequest = { deleteId = null }, title = { Text("删除任务和本机文件？") },
        text = { Text("将先停止写入，再删除此任务的临时片段、音视频源文件、合并文件和已导出的文件。删除不可撤销，不会操作平台上的原作品。") },
        confirmButton = { GlassTextButton(onClick = { deleteId?.let(vm::delete); deleteId = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { GlassTextButton(onClick = { deleteId = null }) { Text("取消") } })
    PageList(vm, wide, if(files) "文件" else "下载", actions = {
        if(!files && all.any { it.stage.isActive || it.stage == TransferStage.QUEUED }) GlassTextButton(onClick = { vm.pauseAll() }) { Text("全部暂停") }
    }) {
        if(files) item(key = "search") { GlassSearchField(search, { search = it }, Modifier.fillMaxWidth()) }
        if(list.isEmpty()) item(key = "empty") { EmptyState(if(files) Icons.Outlined.Folder else Icons.Outlined.FileDownload,
            if(search.isNotBlank()) "没有匹配的文件" else if(files) "还没有保存的文件" else "还没有下载任务", if(search.isNotBlank()) "尝试其他名称，或清空搜索查看全部文件。" else if(files) "完成下载的文件会出现在这里。" else "解析链接，选择画质或图片后添加下载。") }
        items(list, key = { it.id }) { task ->
            GroupCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlatformIcon(task.platform, 26.dp)
                        Text(task.title, Modifier.weight(1f), color = p.ink, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Note(task.formatLabel)
                    Note("引擎："+ParseEngine.from(task.resolvedEngine.ifBlank{task.parserEngine}).title)
                    Text(task.stage.title, color = if(task.stage in setOf(TransferStage.FAILED, TransferStage.DELETE_FAILED)) MaterialTheme.colorScheme.error else p.accent, fontSize = 14.sp)
                    if(task.stage.isActive || task.stage == TransferStage.QUEUED || task.stage == TransferStage.PAUSED) {
                        val fraction = task.fraction
                        if(fraction != null && task.stage !in setOf(TransferStage.MERGING, TransferStage.RESOLVING)) GlassLinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        else if(task.stage.isActive) GlassLinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("${sizeText(task.downloaded)} / ${sizeText(task.total)}" + if(task.stage.isActive && task.speed > 0) " · ${sizeText(task.speed.toLong())}/s" else "", color = p.muted, fontSize = 13.sp)
                    }
                    if(task.message.isNotBlank()) Note(task.message)
                    if(task.failureDetails.isNotBlank()) GlassTextButton(onClick={
                        val clipboard=context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("镜流脱敏诊断",task.failureDetails))
                        vm.notice("已复制脱敏诊断，不包含 Cookie 或签名地址")
                    }){Text("复制脱敏诊断")}
                    if(task.fileName.isNotBlank()) Note(task.fileName)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        if(task.stage == TransferStage.COMPLETE) GlassTextButton(onClick = {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(task.finalUri), task.mime.ifBlank { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                            catch(_: Exception) { vm.notice("文件不存在、权限失效或没有可打开此类型的应用") }
                        }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp)); Text("打开") }
                        if(task.stage == TransferStage.COMPLETE && task.finalUri.isNotBlank()) GlassTextButton(onClick = {
                            try {
                                val uri = Uri.parse(task.finalUri)
                                val share = Intent(Intent.ACTION_SEND).setType(task.mime.ifBlank { "*/*" })
                                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                share.clipData = android.content.ClipData.newRawUri("下载文件", uri)
                                context.startActivity(Intent.createChooser(share, "分享文件"))
                            } catch(_: Exception) { vm.notice("文件不可用或没有接收分享的应用") }
                        }) { Text("分享") }
                        if(task.stage == TransferStage.PAUSED || task.stage == TransferStage.FAILED) GlassTextButton(onClick = { vm.resume(task.id) }) { Text("继续 / 重试") }
                        if(task.stage.isActive || task.stage == TransferStage.QUEUED) GlassTextButton(onClick = { vm.pause(task.id) }) { Text("暂停") }
                        if(task.stage != TransferStage.DELETING) GlassTextButton(onClick = { deleteId = task.id }) { Text(if(task.stage == TransferStage.DELETE_FAILED) "重试删除" else "删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
