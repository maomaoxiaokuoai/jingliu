package com.luma.downloader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luma.core.*
import com.luma.downloader.data.LumaViewModel

@Composable fun EnginePicker(selected:String,enabled:Boolean=true,onSelect:(String)->Unit) {
    var expanded by remember{mutableStateOf(false)}
    Box {
        Row(Modifier.fillMaxWidth().clickable(enabled=enabled){expanded=true}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)){Note("解析引擎");Text(ParseEngine.from(selected).title)}
            Icon(Icons.Outlined.ExpandMore,null)
        }
        SpringDropdownMenu(expanded,{expanded=false},title="解析引擎") {
            ParseEngine.entries.forEachIndexed{index,engine->
                SpringMenuItem(selected=engine.id==selected,onClick={expanded=false;onSelect(engine.id)}){Text(engine.title)}
                if(index<ParseEngine.entries.lastIndex)SpringMenuDivider()
            }
        }
    }
}
@Composable fun ParserEngineControl(vm:LumaViewModel) {
    val selected=LocalUiSettings.current.text("parserEngine")
    Column {
        EnginePicker(selected,!vm.parsing&&!vm.enqueuing,vm::changeParser)
        if(selected==ParseEngine.PARSE_VIDEO_PY.id) {
            Note("手机本地解析，无需填写地址、部署服务器或安装外部 Python。B站使用所选上游实现，不静默切换；YouTube / TikTok 请选 yt-dlp。")
            Note(vm.localParserStatus)
            GlassTextButton(onClick=vm::checkLocalParser,enabled=!vm.localParserChecking&&!vm.parsing&&!vm.enqueuing) {
                Text(if(vm.localParserChecking)"正在检查本地组件…" else "检查本地解析组件")
            }
        }
    }
}
@Composable fun BatchDownloadDialog(vm:LumaViewModel,requestNotifications:()->Unit) {
    val sample=vm.batchSample
    GlassAlertDialog(onDismissRequest={if(!vm.enqueuing)vm.closeBatch()},title={Text("合集下载 · 选择画质")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("已选 ${vm.batchEntries.size} 项")
            // Engine is bound to the parsing request. This dialog changes QUALITY only.
            if(vm.batchBusy){GlassLinearProgressIndicator(Modifier.fillMaxWidth());Note("正在读取已选第一项的实际画质…")}
            if(vm.batchError.isNotBlank()) {
                Text(vm.batchError,color=MaterialTheme.colorScheme.error)
                GlassTextButton(onClick=vm::loadBatchSample,enabled=!vm.batchBusy){Text("重新读取画质")}
            }
            if(sample!=null) {
                Note("以下档位来自：${sample.title}")
                BatchQuality.options(sample).forEach{option->
                    Row(Modifier.fillMaxWidth().clickable(enabled=!vm.enqueuing){vm.batchQuality=option.key}.padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
                        GlassRadioButton(selected=vm.batchQuality==option.key,onClick={vm.batchQuality=option.key},enabled=!vm.enqueuing)
                        Column(Modifier.weight(1f)){Text(option.title);option.size?.let{Note("首项"+(if(it.estimated)"预计 " else " ")+sizeText(it.bytes))}}
                    }
                }
                Note("其余项目在下载前逐项重新解析。选固定档位时，未提供该档位就提示失败，不偷偷降级。最高可用则每集独立选最高档；上面不是整个合集的大小。")
            }
        }
    },confirmButton={GlassTextButton(enabled=sample!=null&&!vm.batchBusy&&!vm.enqueuing,onClick={requestNotifications();vm.confirmBatch()}){Text(if(vm.enqueuing)"正在添加…" else "确认下载")}},
      dismissButton={GlassTextButton(enabled=!vm.enqueuing,onClick=vm::closeBatch){Text("取消")}})
}
