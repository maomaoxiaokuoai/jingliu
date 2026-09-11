#!/usr/bin/env python3
"""Offline structural checks; explicitly NOT an Android compiler or emulator."""
from pathlib import Path
import json, re, hashlib, subprocess, xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
checks = []
def check(name, value):
    checks.append({'name': name, 'passed': bool(value)})
    if not value: raise AssertionError(name)
required = ['settings.gradle.kts','build.gradle.kts','app/build.gradle.kts','core/build.gradle.kts',
 'app/src/main/AndroidManifest.xml','setup-wrapper.bat','setup-wrapper.ps1','setup-wrapper.sh',
 'gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.properties','README.md','LICENSE']
for path in required: check(f'file: {path}', (root/path).is_file() and (root/path).stat().st_size > 0)
kt = list((root/'app/src/main').rglob('*.kt')) + list((root/'core/src/main').rglob('*.kt'))
text = '\n'.join(p.read_text() for p in kt)
check('no TODO()/NotImplementedError business stubs', not re.search(r'\b(?:TODO|NotImplementedError)\s*\(', text))
check('no UI demo task arrays', not re.search(r'\b(?:demoMedia|demoTasks|parseDemo|tickDemo|simulateLogin)\b', text))
check('no font files', not any(root.rglob('*.ttf')) and not any(root.rglob('*.otf')))
check('no app secret/signing inputs', not any(root.rglob('*.jks')) and not any(root.rglob('cookies.txt')))
check('no shipped machine-specific SDK config', not (root/'local.properties').exists())
resources = root/'app/src/main/res'
xml_files = list(resources.rglob('*.xml')) + [root/'app/src/main/AndroidManifest.xml']
for p in xml_files: ET.parse(p)
check(f'{len(xml_files)} XML files parse', True)
resource_ids = {}
for p in resources.rglob('*'):
    if p.is_file() and not p.parent.name.startswith('values'):
        resource_ids.setdefault(p.parent.name.split('-')[0],set()).add(p.stem)
for values_file in resources.glob('values*/*.xml'):
    for node in ET.parse(values_file).getroot():
        if node.get('name'):
            resource_ids.setdefault(node.tag if node.tag != 'item' else node.get('type', ''), set()).add(node.get('name'))
for kind, name in set(re.findall(r'\bR\.(\w+)\.(\w+)', text)):
    check(f'R.{kind}.{name} exists', name in resource_ids.get(kind,set()))
for p in xml_files:
    for kind, name in re.findall(r'@(?:(?:\+)?)(drawable|mipmap|xml)/(\w+)', p.read_text()):
        check(f'XML ref {kind}/{name}', name in resource_ids.get(kind,set()))
manifest = ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
ns = '{http://schemas.android.com/apk/res/android}'
for e in manifest.iter():
    name = e.get(ns+'name','')
    if name.startswith('.'):
        cls = name.rsplit('.',1)[-1]
        check(f'Manifest class {cls}', bool(re.search(r'\bclass\s+'+re.escape(cls)+r'\b', text)))
schema = (root/'app/src/main/java/com/luma/downloader/data/UiSettings.kt').read_text()
keys = set(re.findall(r'SettingSpec\("([^"]+)"',schema))
for key in sorted(set(re.findall(r'\.(?:enabled|number|text)\("([^"]+)"\)',text))):
    check(f'known setting {key}', key in keys)
for script in ('gradlew','setup-wrapper.sh','build-debug.sh'):
    result = subprocess.run(['sh','-n',str(root/script)],capture_output=True,text=True)
    check(f'shell syntax {script}', result.returncode == 0)
report={'scope':'Offline structure/XML/settings refs only; no Android type-check or live platform validation',
 'main_kotlin_files':len(kt),'assertions':len(checks),'checks':checks}
(root/'tests/source-checks.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(f'Source structure: {len(checks)} checks passed; {len(kt)} production Kotlin files. NOT a Gradle build.')
