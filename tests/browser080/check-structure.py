"""Source contracts, not UI execution, device timings or a Kotlin/Gradle type checker."""
from pathlib import Path
import re, xml.etree.ElementTree as ET
R=Path(__file__).resolve().parents[2];A=R/'app/src/main/java/com/luma/downloader'
activity=(A/'auth/EmbeddedLoginActivity.kt').read_text();store=(A/'auth/BrowserCookieStore.kt').read_text();panel=(A/'ui/AccountScreen.kt').read_text();checks=(A/'auth/SessionChecks.kt').read_text()
passed=0
def ok(name,v):
 global passed
 assert v,name
 passed+=1;print('PASS',name)
ok('all platform panels offer a browser entry independent of Bili QR', '内置浏览器登录' in panel and 'if (platform == Platform.BILIBILI)' in panel)
ok('cookie file/paste actions still wired to current view model','vm.importCookieFile(target, uri)' in panel and 'vm.importCookies(target, value)' in panel)
ok('AndroidView uses factory and modifier named args', 'AndroidView<WebView>(' in activity and 'factory =' in activity and 'modifier = Modifier.weight' in activity)
ok('new browser does not clear HTTP cache on entry or exit','clearCache(' not in activity+store)
ok('first visible frame does not wait for full page completion','onPageCommitVisible' in activity)
ok('lightweight Kuaishou loading is actually switchable','lightweight = !lightweight' in activity and 'skipVideoInLogin(' in activity)
ok('no JS credential/password interception','addJavascriptInterface' not in activity and 'evaluateJavascript' not in activity)
ok('browser follows lifecycle and reads only when foreground/visible','repeatOnLifecycle(Lifecycle.State.STARTED)' in activity and 'ready && pageVisible && autoSave' in activity)
ok('Cookie metadata and multi-profile each use feature detection','WebViewFeature.GET_COOKIE_INFO' in store and 'WebViewFeature.MULTI_PROFILE' in store)
ok('WebView temporary cookies and site storage cleared, encrypted canonical account remains','s.clearSiteStorage()' in activity and 's.eraseTemporary' in activity)
ok('Bili and Douyin network checks explicit, other sites remain local','Platform.BILIBILI, Platform.DOUYIN' in checks and 'verifyDouyin' in checks)
ok('each check separates HTTP failures from explicit logout','SelfSessionRejected' in checks and 'SessionHealthCode.UNCONFIRMED' in checks)
ok('saved-account status check hooked to foreground','vm.checkSavedAccounts()' in (A/'MainActivity.kt').read_text())
ok('both parse router and browser save request a real account check','sessionChecks::beforeUse' in (A/'AppGraph.kt').read_text() and 'sessionChecks.request(platform, true)' in activity)
ok('hidden account page does not trigger checks','if (active) vm.checkAccount(platform)' in panel)
ok('QR identity still uses cancel gate and new account epoch','qrGate.commit(generation)' in (A/'data/LumaViewModel.kt').read_text() and 'graph.vault.epoch(platform) != original.second' in (A/'data/LumaViewModel.kt').read_text())
xmls=list((R/'app/src').rglob('*.xml'))
for p in xmls:ET.parse(p)
ok('all Android XML parsed',bool(xmls))
main=(R/'app/src/main/AndroidManifest.xml').read_text()
ok('embedded activity remains unexported', bool(re.search(r'<activity\b[^>]*android:name="\.auth\.EmbeddedLoginActivity"[^>]*android:exported="false"',main)))
print(f'Browser source contracts: {passed} checks passed; not Android build or visual tests.')
