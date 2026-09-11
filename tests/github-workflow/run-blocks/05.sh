#!/usr/bin/env bash
set -euo pipefail
mkdir -p build-logs
set -o pipefail
gradle \
  --no-daemon \
  --stacktrace \
  --console=plain \
  :core:test \
  :app:testDebugUnitTest 2>&1 | tee build-logs/unit-tests.log

