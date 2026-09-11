#!/usr/bin/env bash
# Local JVM type check with explicit Android/Compose substitutes, NOT Android build.
set -euo pipefail
export LC_ALL=C.UTF-8
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TEST="$ROOT/tests/progress-signature-fix"
OUT="${1:-$ROOT/.host-checks/progress-signature-fix}"
mkdir -p "$OUT"
KH="$(cd "$(dirname "$(command -v kotlinc)")/.." && pwd)"
CORO="$KH/lib/kotlinx-coroutines-core-jvm.jar"
A="$ROOT/app/src/main/java/com/luma/downloader"
compile_file() {
 kotlinc "$ROOT"/core/src/main/kotlin/com/luma/core/*.kt "$TEST"/api-stubs/*.kt \
 "$A/auth/SessionVault.kt" "$A/auth/BiliAuth.kt" "$A/auth/BrowserSessionSaver.kt" "$A/auth/SessionChecks.kt" \
 "$A/auth/BrowserCookieStore.kt" "$A/ui/AccountScreen.kt" \
 "$1" -cp "$CORO" -Xjdk-release=17 -d "$2"
}
set +e
compile_file "$TEST/baseline/EmbeddedLoginActivity.kt" "$OUT/before.jar" > "$OUT/before-compile.log" 2>&1
BEFORE=$?
set -e
printf '%s
' "$BEFORE" > "$OUT/before-compile.exit"
if [ "$BEFORE" -eq 0 ]; then echo 'ERROR: regression did not reproduce baseline failure'; exit 1; fi
if ! grep -q 'setProgress(I)V' "$OUT/before-compile.log"; then cat "$OUT/before-compile.log"; echo 'ERROR: wrong baseline failure'; exit 1; fi
compile_file "$A/auth/EmbeddedLoginActivity.kt" "$OUT/after.jar" > "$OUT/after-compile.log" 2>&1
printf '0
' > "$OUT/after-compile.exit"
javap -p -s -classpath "$OUT/after.jar" com.luma.downloader.auth.EmbeddedLoginActivity > "$OUT/jvm-signatures.txt"
grep -q 'setPageLoadProgress(int)' "$OUT/jvm-signatures.txt"
if grep -q 'setProgress(int)' "$OUT/jvm-signatures.txt"; then echo 'ERROR: colliding declared setter remains'; exit 1; fi
echo 'PASS: baseline setProgress(I)V failure reproduced; fixed Activity compiles with signature-aware substitutes; renamed setter confirmed. NOT Android build.' | tee "$OUT/result.log"
