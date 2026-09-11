#!/usr/bin/env python3
"""Source contracts only, not an Android UI execution test."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
R=Path(__file__).resolve().parents[2]
A=(R/'app/src/main/java/com/luma/downloader/ui/AccountScreen.kt').read_text()
Q=(R/'app/src/main/java/com/luma/downloader/ui/LoginQrDialog.kt').read_text()
B=(R/'app/src/main/java/com/luma/downloader/auth/EmbeddedLoginActivity.kt').read_text()
H=(R/'app/src/main/java/com/luma/downloader/ui/GlassOverlayHost.kt').read_text()
checks=[]
def check(label, condition):
    assert condition, label
    checks.append(label)
    print('PASS '+label)

check('account has only Bili QR and desktop login call sites',
      'vm.startQr(Platform.BILIBILI)' in A and 'onClick = { openDesktopLogin() }' in A and A.count('SettingsLine(')==4)
check('no other-account, SDK identity or app-launch entries',all(s not in A for s in ['其他账号操作','开放平台身份授权','NativeAppLauncher','startBiliHandoff','authorizedProfiles','officialQr','仅启动']))
check('removed mobile, external-browser, cookie-import and connection-help entries',all(s not in A for s in ['系统浏览器登录','内置浏览器登录 · 手机版','fileImport','importCookies','CookieImportDialog','connectionHelp']))
check('desktop intent is always true and respects existing supported-platform guard',
      '.putExtra("desktop", true)' in A and 'BrowserSessionPolicy.embeddedAllowed(platform)' in A and 'if (!desktopAvailable) return' in A)
check('existing account flow, Bili verification, deletion confirmation remain',
      'vm.accounts.collectAsStateWithLifecycle()' in A and 'onClick = vm::verifyBili' in A and 'vm.logout(platform)' in A and 'dismissButton =' in A)
check('activity-result success still requires RESULT_OK',
      'if (result.resultCode == Activity.RESULT_OK)' in A)
check('QR contains exactly one button declaration',
      len(re.findall(r'\b(?:OutlinedButton|TextButton|Button|IconButton)\s*\(',Q))==1 and 'OutlinedButton(' in Q)
check('no QR refresh, handoff, native launch or browser page actions',
      all(s not in Q for s in ['refreshQr','launchHandoff','NativeAppLauncher','LoginHandoff','OfficialQrPage','打开官方 App','App 确认','浏览器确认']))
check('QR dismiss works via unchanged overlay outside/back and closeQr',
      'onDismissRequest = vm::closeQr' in Q and 'BackHandler {entry.close()}' in H)
check('QR keeps screenshot allowance, expiry and revocation safeguards',
      'secure = false' in Q and 'QrPresentationPolicy.canExport' in Q and 'onDispose { exportGuard?.revoke() }' in Q and 'exportGuard?.phase(vm.qrPhase)' in Q)
check('QR only writes real existing bitmap with explicit user action, never auto-save',
      'onClick = { save() }' in Q and 'LoginQrImageStore.png(bitmap, guard)' in Q and 'LoginQrImageStore.saveToGallery(context, bytes, guard)' in Q)
check('QR export is Android29 guarded and repeat-save disabled',
      'Build.VERSION.SDK_INT < 29) return' in Q and 'canExport && !saving && !saved && Build.VERSION.SDK_INT >= 29' in Q)
check('desktop WebView uses fixed UA and wide layout',
      'settings.userAgentString = BrowserSessionPolicy.DESKTOP_AGENT' in B and 'settings.useWideViewPort = true' in B and 'settings.loadWithOverviewMode = true' in B)
check('desktop WebView cannot switch to mobile or launch another app/browser',
      all(s not in B for s in ['openExternal','launchApp','pendingApp','setAgent','切手机版','切电脑版','startActivity(']))
check('WebView still uses named AndroidView factory and modifier',
      re.search(r'AndroidView<WebView>\(\s*factory\s*=',B) is not None and 'modifier = Modifier.padding(padding).fillMaxSize()' in B)
check('WebView preserves platform restriction and explicit Cookie save confirmation',
      'BrowserSessionPolicy.embeddedAllowed(platform)' in B and 'BrowserSessionPolicy.allowed(platform, url)' in B and 'confirmSave = true' in B and 'saveSession()' in B)
check('WebView still checks Bili identity and account revision before writing',
      'graph.bili.verify(account, token)' in B and 'check(graph.vault.get(platform)?.revision == savedRevision)' in B and 'check(graph.vault.save(account, savedRevision))' in B)
check('no adjacent else/string regression in modified sources',
      all(not re.search(r'"else\b|\belse"',s) for s in [A,Q,B]))
xmls=list((R/'app/src').rglob('*.xml'))
for p in xmls:ET.parse(p)
check('application resource/manifest XML remains parseable', bool(xmls))
print(f'RESULT: {len(checks)} source-contract checks; not UI/device verification')
