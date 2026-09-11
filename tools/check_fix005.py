"""Source contracts and compiled JVM bytecode, NOT Gradle model resolution/Android/device verification."""
from pathlib import Path
import zipfile,json,re,struct,subprocess
R=Path(__file__).resolve().parents[1]; O=R/'tests/fix-005';O.mkdir(parents=True,exist_ok=True)
rows=[]
def check(name,value):
    rows.append({'check':name,'passed':bool(value)})
    assert value,name
A=R/'app/src/main/java/com/luma/downloader'
settings=(R/'settings.gradle.kts').read_text()
bridge=(R/'auth-bridge/build.gradle.kts').read_text()
core=(R/'core/build.gradle.kts').read_text()
qr=(A/'ui/LoginQrDialog.kt').read_text()
account=(A/'ui/AccountScreen.kt').read_text()
exporter=(A/'auth/LoginQrImageStore.kt').read_text()
manifest=(R/'app/src/main/AndroidManifest.xml').read_text()
check('server inclusion is explicit opt-in', 'if (includeAuthBridge == "true") include(":auth-bridge")' in settings)
check('default Android modules remain app and core','include(":app", ":core")' in settings)
check('no mandatory exact JDK17 search in bridge','jvmToolchain(' not in bridge and 'toolchain {' not in bridge)
check('bridge Java compile release is 17','options.release.set(17)' in bridge)
check('bridge Kotlin bytecode target is 17','JvmTarget.JVM_17' in bridge and '-Xjdk-release=17' in bridge)
check('core release and Kotlin target agree','options.release.set(17)' in core and 'JvmTarget.JVM_17' in core)
check('Android app does not depend on optional server','project(":auth-bridge")' not in (R/'app/build.gradle.kts').read_text())
check('QR dialog allows screenshots explicitly','GlassAlertDialog(secure=false' in qr)
check('Cookie editor remains protected','GlassAlertDialog(secure=true' in account and 'PasswordVisualTransformation()' in account)
check('does not globally clear secure flag','clearFlags(' not in qr and 'FLAG_SECURE' not in (A/'MainActivity.kt').read_text())
check('only raw code is rendered/exportable','it.kind==QrDisplay.CODE' in qr and 'QrPresentationPolicy.canExport' in qr)
check('official page not converted into a made-up QR','remote?.kind==QrDisplay.OFFICIAL_PAGE' in qr and 'OfficialQrPage' in qr)
check('user confirms gallery export','确认保存' in qr and '相册云备份' in qr)
check('save writes PNG with pending publication','"image/png"' in exporter and 'IS_PENDING,1' in exporter and 'IS_PENDING,0' in exporter)
check('save checks cancellation and live guard','currentCoroutineContext().ensureActive()' in exporter and exporter.count('guard.requireValid()')>=4)
check('save cleans its created URI on failure','NonCancellable+Dispatchers.IO' in exporter and 'created?.let' in exporter)
check('no broad gallery permission added','WRITE_EXTERNAL_STORAGE' not in manifest and 'READ_MEDIA_IMAGES' not in manifest)
check('native launch described as launch only','App · 仅启动' in account and '仅打开 App' in account)
check('identity authorization is not called Cookie login','开放平台身份授权' in account and '扫码授权登录' not in account)
check('no actual private bridge properties included',not (R/'qr-auth.properties').exists())
# Optional actual host compilation evidence, not generated or assumed.
jar=R/'.host-checks/all-core.jar'
bytecode={}
if jar.exists():
    with zipfile.ZipFile(jar) as z:
        bytecode={n:struct.unpack('>H',z.read(n)[6:8])[0] for n in z.namelist() if n.endswith('.class') and n.startswith('com/luma/')}
    check('actual compiled application/test bytecode is Java17 (61)',bool(bytecode) and set(bytecode.values())=={61})
(O/'source-contracts.json').write_text(json.dumps({'scope':__doc__,'checks':rows,'compiled_luma_classes':len(bytecode)},ensure_ascii=False,indent=2)+'\n')
print(f'Fix005 contracts: {len(rows)} passed; compiled Luma classes={len(bytecode)}. Not an Android or Gradle build.')
