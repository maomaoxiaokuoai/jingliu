package com.luma.downloader.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.luma.core.CancelToken
import com.luma.downloader.data.SettingsStore
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex

class WifiRequired : java.io.IOException("任务在等待可用的 Wi-Fi 网络")
class TransferRegistry(private val context: Context, private val settings: SettingsStore) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val tokens = ConcurrentHashMap<String, CancelToken>()
    private val slots = mutableSetOf<String>()
    fun lock(id: String): Mutex { UUID.fromString(id); return locks.getOrPut(id) { Mutex() } }
    fun folder(id: String): File { UUID.fromString(id); return File(context.noBackupFilesDir, "transfers/$id").apply { mkdirs() } }
    fun register(id: String, token: CancelToken) { tokens[id] = token }
    fun unregister(id: String) { tokens.remove(id); synchronized(slots) { slots.remove(id) } }
    fun cancel(id: String) { tokens[id]?.cancel() }
    fun acquire(id: String, token: CancelToken) {
        while(true) {
            token.check(); ensureNetwork(token)
            val acquired = synchronized(slots) {
                if(slots.size < settings.current().number("concurrency").toInt()) { slots.add(id); true } else false
            }
            if(acquired) return
            Thread.sleep(100)
        }
    }
    fun ensureNetwork(token: CancelToken) {
        token.check()
        if(settings.current().enabled("wifiOnly")) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            if(caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) throw WifiRequired()
        }
    }
}
