#!/usr/bin/env bash
set -euo pipefail
python_bin="$(python -c 'import sys; print(sys.executable)')"
printf 'buildPython=%s\n' "$python_bin" > python-build.properties
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties
test -f settings.gradle.kts
test -f app/build.gradle.kts
test -f python-runtime-requirements.txt

