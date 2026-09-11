package com.luma.downloader.data

import android.content.Context
import com.luma.core.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** UI changes synchronously; disk I/O uses a separate lock and never holds the UI state lock. */
class SettingsStore(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.noBackupFilesDir, "settings-v7.json")
    private val state = MutableStateFlow(UiSettings())
    val flow = state.asStateFlow()
    private val errorState = MutableStateFlow<String?>(null)
    val errors = errorState.asStateFlow()
    private val diskLock=Any()
    @Volatile private var loaded=false
    private var writer:Job?=null
    @Synchronized fun load() {
        if(loaded)return
        val raw=if(file.exists())Json.parse(file.readText()).obj().mapValues{it.value.str()}else emptyMap()
        val migrated=VisualDefaults.migrate(raw).let { if(raw["_retrySchema"]!="1")it.withValue("retry","10")else it }
        // load() is invoked on IO. Failure does not silently overwrite an unreadable file.
        atomicText(file,Json.stringify(VisualDefaults.persisted(migrated)+( "_retrySchema" to "1")))
        state.value=migrated;loaded=true
    }
    fun current():UiSettings=state.value
    @Synchronized fun set(key:String,value:String) {
        check(loaded){"设置正在加载"}
        if(!SettingsCatalog.valid(key,value))return
        val next=SettingActions.update(state.value,key,value)
        if(next==state.value)return // snapped slider ticks often repeat the same value
        state.value=next;scheduleWrite()
    }
    @Synchronized fun applyMotionPreset(){state.value=MotionPolicy.preset(state.value);scheduleWrite()}
    @Synchronized fun restore(scope:VisualResetScope){check(loaded);state.value=VisualDefaults.reset(state.value,scope);scheduleWrite()}
    private fun scheduleWrite(){writer?.cancel();writer=scope.launch(Dispatchers.IO){delay(180);flush()}}
    fun flush() { if(!loaded)return
        synchronized(diskLock) {
            try {atomicText(file,Json.stringify(VisualDefaults.persisted(state.value)+( "_retrySchema" to "1")));errorState.value=null}
            catch(_:Exception){errorState.value="外观已在本次运行生效，但无法写入存储。请检查可用空间。"}
        }
    }
}
