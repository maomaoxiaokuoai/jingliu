package com.luma.downloader.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.core.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.luma.downloader.auth.EmbeddedLoginActivity
import com.luma.downloader.data.LumaViewModel
import com.luma.downloader.engine.MediaHttp

/** Compact account panel. Every real platform supports explicit file/paste import.
 * Only a platform name is restorable; plaintext credentials never enter saved UI state. */
@Composable
fun AccountPanel(vm: LumaViewModel) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val health by vm.sessionHealth.collectAsStateWithLifecycle()
    var platformName by rememberSaveable { mutableStateOf(Platform.BILIBILI.name) }
    val platform = Platform.valueOf(platformName)
    var expanded by remember { mutableStateOf(false) }
    var logoutDialog by remember(platform) { mutableStateOf(false) }
    // Capture the platform at launch, not whatever is selected when the picker returns.
    var fileTargetName by rememberSaveable { mutableStateOf<String?>(null) }
    var pasteTarget by remember { mutableStateOf<Platform?>(null) }
    val context = LocalContext.current
    val settings = LocalUiSettings.current
    val palette = LocalLumaPalette.current
    val account = accounts.firstOrNull { it.platform == platform }
    val desktopAvailable = BrowserSessionPolicy.embeddedAllowed(platform)
    val check = health[platform]?.takeIf { it.revision == account?.revision }
    val active = LocalSceneActive.current
    LaunchedEffect(platform, account?.revision, active) { if (active) vm.checkAccount(platform) }

    val embeddedLogin = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Only the existing WebView save/verification path may publish success.
        if (result.resultCode == Activity.RESULT_OK) {
            MediaHttp.clearImageMemory()
            vm.notice(result.data?.getStringExtra("status") ?: "已保存本机会话")
        }
    }

    val cookieFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val targetName = fileTargetName
        fileTargetName = null
        if (uri != null) {
            val target = Platform.entries.firstOrNull { it.name == targetName && it != Platform.DIRECT }
            if (target != null) vm.importCookieFile(target, uri)
            else vm.notice("请重新选择平台后导入")
        }
    }

    fun openCookieFile() {
        if (vm.accountBusy || fileTargetName != null) return
        fileTargetName = platform.name
        try {
            // Some exporters label .txt as application/octet-stream. Validate the contents,
            // not the provider's MIME guess; the existing reader enforces the 1 MB cap.
            cookieFile.launch(arrayOf("*/*"))
        } catch (_: Exception) {
            fileTargetName = null
            vm.notice("无法打开文件选择器，请使用粘贴 Cookie")
        }
    }

    fun openDesktopLogin() {
        embeddedLogin.launch(
            Intent(context, EmbeddedLoginActivity::class.java)
                .putExtra("platform", platform.name)
                .putExtra("desktop", true)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        GroupCard {
            Box {
                SettingsLine(
                    "选择平台", platform.title,
                    onClick = { expanded = true },
                    trailing = { PlatformIcon(platform) }
                )
                SpringDropdownMenu(expanded, { expanded = false }, title = "选择平台") {
                    val entries = Platform.entries.filter { it != Platform.DIRECT }
                    entries.forEachIndexed { index, entry ->
                        SpringMenuItem(
                            selected = platform == entry,
                            leadingIcon = { PlatformIcon(entry, 28.dp) },
                            onClick = { platformName = entry.name; expanded = false }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(entry.title)
                                Text(
                                    if (accounts.any { it.platform == entry }) "已保存本机会话" else "尚未连接",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.muted
                                )
                            }
                        }
                        if (index != entries.lastIndex) SpringMenuDivider()
                    }
                }
            }
        }

        Text("下载会话", style = MaterialTheme.typography.titleMedium)
        GroupCard {
            Row(
                Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (account != null && account.avatar.isNotBlank() && !settings.enabled("hideProfile")) {
                    RemoteImage(account.avatar, "账号头像", Modifier.size(48.dp).clip(CircleShape))
                } else {
                    PlatformIcon(platform, 46.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        if (account == null) "尚未登录"
                        else if (settings.enabled("hideProfile")) "已隐藏账号资料"
                        else account.name.ifBlank { "已保存本机会话" },
                        color = palette.ink,
                        fontSize = 18.sp
                    )
                    Note(check?.code?.label ?: account?.stateLabel ?: "未连接")
                    if (account != null) {
                        val expiry = LoginCookiePolicy.expiry(platform, account.cookies)
                        val known = expiry.nextExpirySeconds?.let {
                            runCatching { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(it)) }.getOrNull()
                        }
                        Note(if (known != null) "${if (expiry.allExpired) "已于" else "已知到期："}$known${if (expiry.hasUnknown) " · 部分期限未知" else ""}"
                            else "到期：未知（Cookie 未提供）")
                        check?.let { info ->
                            val time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(info.checkedAt))
                            Note("检查于 $time")
                        }
                    }
                    if (account != null && platform == Platform.BILIBILI && !settings.enabled("hideProfile")) {
                        Note("UID ${account.uid.ifBlank { "未获取" }} · ${account.vip}")
                    }
                }
            }
            if (account != null) {
                InsetDivider()
                GlassTextButton(
                    onClick = { vm.checkAccount(platform, force = true) },
                    enabled = !vm.accountBusy,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) { Text(if (platform in setOf(Platform.BILIBILI, Platform.DOUYIN)) "检查登录状态" else "检查有效期") }
            }
        }

        run {
            GroupCard {
                if (platform == Platform.BILIBILI) {
                    SettingsLine(
                        "B站扫码登录",
                        "扫码后保存会话",
                        Icons.Outlined.QrCode2,
                        onClick = { vm.startQr(Platform.BILIBILI) }
                    )
                    InsetDivider()
                }
                run {
                    SettingsLine(
                        "内置浏览器登录",
                        if (desktopAvailable) "自己登录 · 自动保存 Cookie" else "此平台限制内置登录，查看可用方式",
                        Icons.Outlined.Computer,
                        onClick = { openDesktopLogin() }
                    )
                }
            }
        }

        // This group is deliberately outside the QR/WebView condition: YouTube and
        // every other listed platform still have a working manual session entry.
        GroupCard {
            SettingsLine(
                "导入 Cookie 文件", "Netscape cookies.txt", Icons.Outlined.FileOpen,
                onClick = { openCookieFile() }
            )
            InsetDivider()
            SettingsLine(
                "粘贴 Cookie", "Cookie 请求头", Icons.Outlined.Key,
                onClick = {
                    if (!vm.accountBusy && fileTargetName == null) pasteTarget = platform
                }
            )
        }

        if (vm.accountBusy) GlassLinearProgressIndicator(Modifier.fillMaxWidth())
        if (account != null) {
            GlassTextButton(onClick = { logoutDialog = true }) {
                Text("删除本机会话", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    pasteTarget?.let { target ->
        key(target) {
            CookieImportDialog(
                platform = target,
                onDismiss = { pasteTarget = null },
                onImport = { value ->
                    // Close the secure editing surface before starting asynchronous import.
                    pasteTarget = null
                    vm.importCookies(target, value)
                },
                onNotice = vm::notice
            )
        }
    }

    if (logoutDialog) {
        GlassAlertDialog(
            onDismissRequest = { logoutDialog = false },
            title = { Text("删除此平台的本机会话？") },
            text = { Text("不会注销平台账号。已启动的请求可能持有内存中的会话；要立即停止使用，请先暂停该平台的任务。") },
            confirmButton = {
                GlassTextButton(onClick = { vm.logout(platform); logoutDialog = false }) { Text("删除会话") }
            },
            dismissButton = { GlassTextButton(onClick = { logoutDialog = false }) { Text("取消") } }
        )
    }
}


@Composable
private fun CookieImportDialog(
    platform: Platform,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    onNotice: (String) -> Unit
) {
    // Never rememberSaveable: Cookie values must not enter Activity state or disk logs.
    var value by remember { mutableStateOf("") }
    val context = LocalContext.current
    val maxCharacters = 1024 * 1024

    fun acceptText(text: String) {
        if (text.length > maxCharacters) {
            // Do not silently truncate a credential and then claim a successful import.
            onNotice("Cookie 内容过大，请导入不超过 1 MB 的文件")
        } else {
            value = text
        }
    }

    fun paste() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            // Only accept an actual text payload; do not dereference arbitrary clipboard URIs.
            val text = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).text?.toString()
            } else null
            if (text.isNullOrBlank()) onNotice("剪贴板没有 Cookie 文本")
            else acceptText(text)
        } catch (_: Exception) {
            onNotice("无法读取剪贴板，请在输入框中长按粘贴")
        }
    }

    GlassAlertDialog(
        secure = true,
        onDismissRequest = { value = ""; onDismiss() },
        title = { Text("导入 ${platform.title} Cookie") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Note("仅导入自己的 Cookie，本机加密保存。")
                GlassTextField(
                    value = value,
                    onValueChange = { acceptText(it) },
                    placeholder = "Cookie 请求头或 Netscape 文本",
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 7,
                    visualTransformation = PasswordVisualTransformation()
                )
                GlassTextButton(onClick = { paste() }) { Text("从剪贴板粘贴") }
            }
        },
        confirmButton = {
            GlassTextButton(
                enabled = value.isNotBlank(),
                onClick = {
                    val text = value
                    value = ""
                    onImport(text)
                }
            ) { Text("加密保存") }
        },
        dismissButton = {
            GlassTextButton(onClick = { value = ""; onDismiss() }) { Text("取消") }
        }
    )
}
