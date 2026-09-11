package com.luma.downloader.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.luma.core.*
import com.luma.downloader.auth.LoginQrImageStore
import com.luma.downloader.data.LumaViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bilibili QR has one action: save the current real QR. Back/outside tap still dismisses it.
 * No app launch, browser handoff, refresh button or implicit authorization is added here. */
@Composable
fun QrDialog(vm: LumaViewModel) {
    if (!vm.qrOpen) return
    if (vm.qrPlatform != Platform.BILIBILI) {
        // This account UI no longer exposes open-platform identity authorization.
        LaunchedEffect(vm) { vm.closeQr() }
        return
    }

    val ticket = vm.qrTicket
    val identity = ticket?.key
    val code = ticket?.url
    val expires = ticket?.expiresAt ?: 0L
    val stamp = remember(identity, expires) {
        identity?.let { QrExportStamp(Platform.BILIBILI, it, QrDisplay.CODE, expires) }
    }
    val exportGuard = remember(stamp) { stamp?.let { QrExportGuard(it) } }
    SideEffect { exportGuard?.phase(vm.qrPhase) }
    DisposableEffect(exportGuard) { onDispose { exportGuard?.revoke() } }

    var now by remember(identity) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(identity, expires) {
        now = System.currentTimeMillis()
        while (expires > now) {
            delay(500)
            now = System.currentTimeMillis()
        }
    }
    var bitmap by remember(identity) { mutableStateOf<Bitmap?>(null) }
    var renderingError by remember(identity) { mutableStateOf("") }
    LaunchedEffect(identity, code) {
        bitmap = null
        renderingError = ""
        if (code != null) {
            try {
                bitmap = withContext(Dispatchers.Default) { LoginQrImageStore.bitmap(code) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                renderingError = "二维码无法绘制，请按返回关闭后重新进入。"
            }
        }
    }

    val showCode = QrPresentationPolicy.canShow(vm.qrPhase, expires, now)
    val canExport = stamp != null && bitmap != null &&
        QrPresentationPolicy.canExport(stamp, vm.qrPhase, now)
    GlassAlertDialog(
        secure = false,
        onDismissRequest = vm::closeQr,
        title = { Text("B站扫码登录") },
        text = {
            Column(
                Modifier.fillMaxWidth().testTag("login-qr-capture-allowed"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (showCode) {
                    Box(
                        Modifier.fillMaxWidth().height(280.dp).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        bitmap?.let {
                            Image(
                                it.asImageBitmap(), "B站真实登录二维码",
                                Modifier.fillMaxSize().padding(8.dp)
                            )
                        }
                        if (vm.qrLoading || code != null && bitmap == null && renderingError.isBlank()) {
                            GlassCircularProgressIndicator()
                        }
                    }
                } else if (vm.qrLoading) {
                    GlassCircularProgressIndicator()
                }
                Text(
                    if (expires > 0 && expires <= now && QrPresentationPolicy.active(vm.qrPhase))
                        "二维码已过期，请按返回关闭后重新进入。"
                    else vm.qrMessage.replace("请刷新后重新扫描", "请按返回关闭后重新进入")
                )
                if (showCode) {
                    Note("剩余 ${QrPresentationPolicy.remainingSeconds(expires, now)} 秒 · 可截图")
                }
                if (renderingError.isNotBlank()) Note(renderingError)
            }
        },
        confirmButton = {
            key(identity) {
                LoginQrSaveButton(bitmap, exportGuard, canExport)
            }
        }
    )
}

/** The only action in the QR dialog. Existing guard checks also run inside PNG/file writing. */
@Composable
private fun LoginQrSaveButton(bitmap: Bitmap?, guard: QrExportGuard?, canExport: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }

    fun save() {
        if (saving || saved || !canExport || bitmap == null || guard == null || Build.VERSION.SDK_INT < 29) return
        saving = true
        feedback = ""
        scope.launch {
            try {
                val bytes = LoginQrImageStore.png(bitmap, guard)
                LoginQrImageStore.saveToGallery(context, bytes, guard)
                saved = true
                feedback = "已保存，登录后请删除二维码图片。"
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                feedback = "保存失败，请重试或直接截图。"
            } finally {
                saving = false
            }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassOutlinedButton(
            onClick = { save() },
            enabled = canExport && !saving && !saved && Build.VERSION.SDK_INT >= 29,
            modifier = Modifier.fillMaxWidth().testTag("login-qr-save-only")
        ) {
            Text(if (saving) "正在保存…" else if (saved) "已保存二维码" else "保存二维码")
        }
        if (Build.VERSION.SDK_INT < 29) Note("当前系统请截图保存二维码。")
        if (feedback.isNotBlank()) Note(feedback)
    }
}
