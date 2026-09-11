#!/usr/bin/env bash
# Isolated Kotlin type-check, NOT an Android or Compose compilation.
# External API substitutes are in api-stubs; production core and coroutines are real.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$ROOT/tests/compile-fix-078"
OUT="${JINGLIU_COMPILE_FIX_OUT:-$ROOT/.host-checks/compile-fix-078}"
mkdir -p "$OUT/before"
KOTLIN_HOME="$(cd "$(dirname "$(command -v kotlinc)")/.." && pwd)"
CORO="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
[ -f "$CORO" ] || { echo 'The local Kotlin distribution must include kotlinx-coroutines-core-jvm.jar'; exit 2; }
cp "$HERE/EmbeddedLoginActivity.original.txt" "$OUT/before/EmbeddedLoginActivity.kt"
kotlinc "$ROOT"/core/src/main/kotlin/com/luma/core/*.kt -Xjdk-release=17 -d "$OUT/core.jar" > "$OUT/core-compile.log" 2>&1
set +e
kotlinc "$HERE"/api-stubs/*.kt "$OUT/before/EmbeddedLoginActivity.kt" -cp "$OUT/core.jar:$CORO" -Xjdk-release=17 -d "$OUT/before.jar" > "$OUT/before.log" 2>&1
BEFORE=$?
set -e
[ "$BEFORE" -ne 0 ] || { echo 'Original source unexpectedly compiled; inspect the negative test.'; exit 3; }
grep -Eq 'type mismatch|argument.*already passed' "$OUT/before.log" || { echo 'Original failed for a different reason; inspect before.log'; exit 4; }
kotlinc "$HERE"/api-stubs/*.kt "$ROOT/app/src/main/java/com/luma/downloader/auth/EmbeddedLoginActivity.kt" -cp "$OUT/core.jar:$CORO" -Xjdk-release=17 -d "$OUT/fixed.jar" > "$OUT/fixed.log" 2>&1
echo 'PASS: original parameter binding failure reproduced; fixed Activity type-check passed.'
echo 'This does NOT resolve actual Android/Compose artifacts or build an APK.'
