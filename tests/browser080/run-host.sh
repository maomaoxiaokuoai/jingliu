#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C.UTF-8
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${1:-$ROOT/.host-checks/browser080}"
mkdir -p "$OUT"
KH="$(cd "$(dirname "$(command -v kotlinc)")/.." && pwd)"
CORO="$KH/lib/kotlinx-coroutines-core-jvm.jar"
A="$ROOT/app/src/main/java/com/luma/downloader/auth"
kotlinc "$ROOT"/core/src/main/kotlin/com/luma/core/*.kt \
 "$ROOT"/tests/browser080/key-stubs/*.kt \
 "$A/SessionVault.kt" "$A/BiliAuth.kt" "$A/BrowserSessionSaver.kt" "$A/SessionChecks.kt" \
 "$ROOT/tests/browser080/Browser080Checks.kt" -cp "$CORO" -Xjdk-release=17 -include-runtime -d "$OUT/host-checks.jar" > "$OUT/compile.log" 2>&1
java -cp "$OUT/host-checks.jar:$CORO" com.luma.downloader.auth.Browser080Checks | tee "$OUT/checks.log"
