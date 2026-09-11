#!/usr/bin/env python3
"""Source-shape safeguards only, not Android rendering or library execution tests."""
from pathlib import Path
import ast, json, re, xml.etree.ElementTree as ET
R=Path(__file__).resolve().parents[1]
A=R/'app/src/main/java/com/luma/downloader'
checks=[]
def verify(name,value):
    checks.append({'name':name,'passed':bool(value)})
    if not value:raise AssertionError(name)
ui=(A/'ui/ParserEngineControl.kt').read_text()
dialog=ui[ui.index('@Composable fun BatchDownloadDialog'):]
vm=(A/'data/LumaViewModel.kt').read_text()
router=(A/'engine/ExtractorRouter.kt').read_text()
verify('download modal has quality controls, no engine chooser', 'BatchQuality.options' in dialog and all(s not in dialog for s in ['EnginePicker','FilterChip','ParseEngine.entries','changeBatchEngine','配置']))
verify('no download-time engine setter remains','fun changeBatchEngine' not in vm)
verify('batch engine frozen from parsed choice','batchEngine=ParsedEngineBinding(parsedEngine).taskEngine()' in vm and 'val engine=batchEngine' in vm and 'parserEngine=engine' in vm)
verify('normal task uses parsed engine not latest setting','parserEngine=parsedEngine' in vm)
verify('parser selection remains available before parsing','EnginePicker(selected' in ui)
verify('remote credential endpoint removed from active app code',all(s not in '\n'.join(p.read_text() for p in A.rglob('*.kt')) for s in ['ParserServiceStore','ParserServiceConfig','parserServiceOrigin','configureParser']))
verify('explicit local engine routes to local client','return local.parse(url,token)' in router)
verify('Bili specialization honestly labelled','原生 Kotlin' in router and 'Android 原生兼容分支' in ui)
verify('upstream pinned in build inputs','5fcf87256edb5ffcdebf0e4aac2a5a41745da76e' in (R/'python-runtime-requirements.txt').read_text())
verify('Chaquopy version and Python declared','17.0.0' in (R/'build.gradle.kts').read_text() and 'version = "3.11"' in (R/'app/build.gradle.kts').read_text())
verify('Android Python source included', (R/'app/src/main/python/jingliu_local/__init__.py').is_file())
pysource=(R/'app/src/main/python/jingliu_local/__init__.py').read_text()
ast.parse(pysource)
verify('real upstream imports and parser invocation exist','import_module("parse_video_py.parser")' in pysource and 'parser.parse_share_url(source)' in pysource)
verify('no web server or runtime installer in Python adapter',not any(s in pysource for s in ['import fastapi','import uvicorn','pip install','http.server','Flask(']))
verify('library self test is not marked as network verification','"network_tested":False' in pysource)
ns='{http://schemas.android.com/apk/res/android}'
manifest=ET.parse(R/'app/src/main/AndroidManifest.xml').getroot()
service=next(s for s in manifest.find('application').findall('service') if s.get(ns+'name')=='.engine.local.LocalParseVideoService')
verify('private same-UID service process, not exported',service.get(ns+'exported')=='false' and service.get(ns+'process')==':parse_video' and service.get(ns+'isolatedProcess')!='true')
service_code=(A/'engine/local/LocalParseVideoService.kt').read_text()
verify('Python starts only in background bound component','workers.execute' in service_code and 'Python.start' not in (A/'ShiliuApplication.kt').read_text())
verify('bounded serialization queue and IPC limits','ArrayBlockingQueue(4)' in service_code and 'LocalParserProtocol.RESULT_BYTES' in service_code)
verify('existing manifest transport used with remux', 'downloadManifest' in (A/'download/DownloadWorker.kt').read_text() and 'remuxManifest' in (A/'download/DownloadWorker.kt').read_text())
report={'scope':'static integration constraints, not build/runtime verification','checks':checks,'passed':len(checks)}
(R/'tests/local077/integration-structure.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(f'{len(checks)} static integration constraints passed; not an Android build.')
