#!/usr/bin/env bash
set -euo pipefail
apk="app/build/outputs/apk/debug/app-debug.apk"
test -s "$apk"
mkdir -p artifacts
output="artifacts/Jingliu-debug-${GITHUB_RUN_NUMBER}.apk"
cp "$apk" "$output"
sha256sum "$output" > "${output}.sha256"
{
  echo "commit=${GITHUB_SHA}"
  echo "run_number=${GITHUB_RUN_NUMBER}"
  echo "java=$(java -version 2>&1 | head -n 1)"
  echo "python=$(python --version 2>&1)"
  echo "gradle=$(gradle --version | awk '/^Gradle / { print $2; exit }')"
  echo "apk_sha256=$(sha256sum "$output" | awk '{print $1}')"
} > artifacts/build-info.txt

