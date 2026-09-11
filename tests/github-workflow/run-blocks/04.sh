#!/usr/bin/env bash
set -euo pipefail
mkdir -p build-logs
set -o pipefail
gradle \
  --no-daemon \
  --stacktrace \
  --console=plain \
  :app:assembleDebug 2>&1 | tee build-logs/assemble-debug.log

