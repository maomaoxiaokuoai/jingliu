#!/usr/bin/env bash
# Local Kotlin type check + actual core QR safety policy, not Android validation.
set -euo pipefail
export LC_ALL=C.utf8
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$ROOT/tests/account-cleanup"
OUT="${JINGLIU_ACCOUNT_CHECK_OUT:-$ROOT/.host-checks/account-cleanup}"
mkdir -p "$OUT"
KH="$(cd "$(dirname "$(command -v kotlinc)")/.." && pwd)"
CORO="$KH/lib/kotlinx-coroutines-core-jvm.jar"
kotlinc "$ROOT"/core/src/main/kotlin/com/luma/core/*.kt "$ROOT/core/src/test/kotlin/com/luma/core/AccountAccessChecks.kt" -Xjdk-release=17 -include-runtime -d "$OUT/core.jar" > "$OUT/core-compile.log" 2>&1
java -cp "$OUT/core.jar" com.luma.core.AccountAccessChecks > "$OUT/qr-policy.log" 2>&1
kotlinc "$HERE"/api-stubs/*.kt \
 "$ROOT/app/src/main/java/com/luma/downloader/ui/AccountScreen.kt" \
 "$ROOT/app/src/main/java/com/luma/downloader/ui/LoginQrDialog.kt" \
 "$ROOT/app/src/main/java/com/luma/downloader/auth/EmbeddedLoginActivity.kt" \
 -cp "$OUT/core.jar:$CORO" -Xjdk-release=17 -d "$OUT/ui-typecheck.jar" > "$OUT/ui-typecheck.log" 2>&1
printf 'PASS: three modified files type-check against EXPLICIT external API substitutes. Not an Android build.\n'
