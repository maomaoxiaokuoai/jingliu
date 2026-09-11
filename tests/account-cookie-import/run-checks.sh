#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C.utf8
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$ROOT/tests/account-cookie-import"
OUT="${JINGLIU_COOKIE_CHECK_OUT:-$ROOT/.host-checks/account-cookie-import}"
mkdir -p "$OUT"
KH="$(cd "$(dirname "$(command -v kotlinc)")/.." && pwd)"
CORO="$KH/lib/kotlinx-coroutines-core-jvm.jar"
kotlinc "$ROOT"/core/src/main/kotlin/com/luma/core/*.kt  "$HERE/CookieImportChecks.kt"  "$ROOT/core/src/test/kotlin/com/luma/core/AccountAccessChecks.kt"  -Xjdk-release=17 -include-runtime -d "$OUT/core.jar" > "$OUT/core-compile.log" 2>&1
java -cp "$OUT/core.jar" com.luma.core.CookieImportChecks > "$OUT/cookie-checks.log" 2>&1
java -cp "$OUT/core.jar" com.luma.core.AccountAccessChecks > "$OUT/qr-policy.log" 2>&1
kotlinc "$HERE"/api-stubs/*.kt  "$ROOT/app/src/main/java/com/luma/downloader/ui/AccountScreen.kt"  "$ROOT/app/src/main/java/com/luma/downloader/ui/LoginQrDialog.kt"  "$ROOT/app/src/main/java/com/luma/downloader/auth/EmbeddedLoginActivity.kt"  -cp "$OUT/core.jar:$CORO" -Xjdk-release=17 -d "$OUT/ui-typecheck.jar" > "$OUT/ui-typecheck.log" 2>&1
printf 'PASS: two changed complete UI files plus unchanged browser type-check using explicit API substitutes; not an Android build.\n' > "$OUT/typecheck-result.log"
python3 "$HERE/check_routes.py" > "$OUT/routes.log"
echo 'Host checks completed. Android build and actual account/picker/clipboard/storage remain unverified.'
