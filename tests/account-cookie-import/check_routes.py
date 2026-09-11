"""Static UI wiring checks, not Compose rendering or event simulation."""
from pathlib import Path
import re
root = Path(__file__).resolve().parents[2]
a = (root/'app/src/main/java/com/luma/downloader/ui/AccountScreen.kt').read_text()
q = (root/'app/src/main/java/com/luma/downloader/ui/LoginQrDialog.kt').read_text()
v = (root/'app/src/main/java/com/luma/downloader/data/LumaViewModel.kt').read_text()
vault = (root/'app/src/main/java/com/luma/downloader/auth/SessionVault.kt').read_text()
passed = 0

def case(label, condition):
    global passed
    assert condition, label
    passed += 1
    print('PASS', label)

case('boxed QR paragraphs removed', '保存后手动打开 B站扫一扫' not in q and '点击保存即将此临时登录二维码' not in q)
case('boxed current-session row removed', 'SettingsLine("使用当前会话"' not in a)
case('boxed browser footnote removed', '二维码是否提供及如何确认' not in a)
case('unconditional Cookie group outside QR/WebView block', a.index('// This group is deliberately outside') > a.index('if (platform == Platform.BILIBILI || desktopAvailable)') and '\n        }\n\n        // This group' in a)
case('file import connected to existing VM', 'vm.importCookieFile(target, uri)' in a)
case('paste import connected to existing VM', 'vm.importCookies(target, value)' in a)
case('picker target set before launch and cleared on callback', a.index('fileTargetName = platform.name') < a.index('cookieFile.launch') and 'val targetName = fileTargetName\n        fileTargetName = null' in a)
case('picker cancellation never imports', 'if (uri != null)' in a and 'target != null' in a)
case('uses Android document contract', 'ActivityResultContracts.OpenDocument()' in a)
case('Cookie values not rememberSaveable', 'var value by remember { mutableStateOf("") }' in a and 'var value by rememberSaveable' not in a)
case('paste surface remains secure', 'secure = true' in a[a.index('private fun CookieImportDialog'):])
case('clipboard read only in explicit paste action', a.count('.primaryClip') == 1 and 'TextButton(onClick = { paste() })' in a)
case('no URI dereference for clipboard content', 'clip.getItemAt(0).text?.toString()' in a and '.coerceToText' not in a)
case('overlarge text rejected without truncation', 'if (text.length > maxCharacters)' in a and '.take(128' not in a)
case('save/cancel clears editable Cookie', 'val text = value\n                    value = ""\n                    onImport(text)' in a and 'value = ""; onDismiss()' in a)
case('Bili direct QR and desktop remain', 'vm.startQr(Platform.BILIBILI)' in a and '.putExtra("desktop", true)' in a)
case('no restored app/identity/mobile browser entries', all(x not in a for x in ['LoginHandoffLauncher', 'NativeAppLauncher', '系统浏览器登录', '手机版', '其他账号操作', '开放平台身份授权']))
case('QR still one action and export guards', q.count('OutlinedButton(') == 1 and 'QrPresentationPolicy.canExport' in q and 'exportGuard?.revoke()' in q and 'onDismissRequest = vm::closeQr' in q)
case('persistent encryption not replaced', 'AES/GCM/NoPadding' in vault and 'atomicText(file,' in vault and 'AndroidKeyStore' in vault)
case('Bili actual verification reused', 'graph.bili.verify(draft, token)' in v and 'graph.vault.save(account)' in v)
case('existing file read cap remains', 'out.size() + n <= 1024 * 1024' in v)
print(f'Static route checks: {passed} passed; not Android UI tests.')
