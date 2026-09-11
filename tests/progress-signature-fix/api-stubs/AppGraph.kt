// TEST-ONLY AppGraph boundaries; actual account, vault, probe and saver types are compiled.
package com.luma.downloader
import android.content.Context
import com.luma.downloader.auth.*
import com.luma.downloader.data.UiSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
class SettingsStore {val flow=MutableStateFlow(UiSettings())}
class AppGraph(context:Context) {
 val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 val settings=SettingsStore();val vault=SessionVault(context);val bili=BiliAuth(vault)
 val sessionChecks=SessionChecks(vault,scope){a,t->bili.verify(a,t)}
 fun load() {}
 companion object {fun get(context:Context)=AppGraph(context)}
}
