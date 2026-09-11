// TEST-ONLY external type substitute. Never add to app sources.
@file:Suppress("UNUSED_PARAMETER")
package com.luma.downloader
import android.content.Context
import com.luma.downloader.data.UiSettings
import com.luma.downloader.auth.*
import kotlinx.coroutines.flow.MutableStateFlow
class SettingsStore {val flow=MutableStateFlow(UiSettings())}
class AppGraph {
 val settings=SettingsStore();val vault=SessionVault();val bili=BiliAuth()
 fun load() {}
 companion object {fun get(context:Context)=AppGraph()}
}
