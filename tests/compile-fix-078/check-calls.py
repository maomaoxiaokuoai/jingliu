#!/usr/bin/env python3
"""A narrow source regression guard. Not an Android compiler."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[2]
found=[]
for source in (root/'app/src/main').rglob('*.kt'):
    text=source.read_text(encoding='utf-8')
    for match in re.finditer(r'\bAndroidView(?:\s*<[^>]+>)?\s*\(',text):
        first=text[match.end():].lstrip()
        assert re.match(r'(?:factory|modifier)\s*=',first),f'Name AndroidView arguments: {source}'
        found.append(str(source.relative_to(root)))
    for number,line in enumerate(text.splitlines(),1):
        assert not re.search(r'"else\b|\belse"',line),f'Adjacent else/string literal: {source}:{number}'
assert len(found)==3, f'Unexpected AndroidView call count: {len(found)}; review new usages.'
print('PASS: all 3 production AndroidView calls start with named arguments.')
print('PASS: no adjacent else/string-literal patterns in app/src/main Kotlin sources.')
print('Source-pattern checks only; not runtime or Android UI validation.')
