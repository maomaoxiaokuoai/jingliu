#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C.UTF-8
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${1:-$ROOT/.host-checks/browser080-ui}";mkdir -p "$OUT"
KH="$(cd "$(dirname "$(command -v kotlinc)")/.." && pwd)";CORO="$KH/lib/kotlinx-coroutines-core-jvm.jar"
A="$ROOT/app/src/main/java/com/luma/downloader"
kotlinc "$ROOT"/core/src/main/kotlin/com/luma/core/*.kt "$ROOT"/tests/browser080/api-stubs/*.kt \
 "$A/auth/SessionVault.kt" "$A/auth/BiliAuth.kt" "$A/auth/BrowserSessionSaver.kt" "$A/auth/SessionChecks.kt" \
 "$A/auth/BrowserCookieStore.kt" "$A/auth/EmbeddedLoginActivity.kt" "$A/ui/AccountScreen.kt" \
 -cp "$CORO" -Xjdk-release=17 -d "$OUT/ui.jar" > "$OUT/compile.log" 2>&1
printf 'PASS: actual changed Activity/AccountPanel/CookieStore/Saver/Vault/Checks compiled with external Android/Compose/AppGraph/VM type substitutes. Not Android Gradle or device verification.\n' | tee "$OUT/result.log"
