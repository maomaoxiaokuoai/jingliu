#!/usr/bin/env bash
# Real JVM/core & policy tests; Python uses explicit upstream fixtures and HTTPX MockTransport.
# This does NOT resolve Android/Chaquopy binaries or assert live-platform success.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${JINGLIU_CHECK_OUT:-$ROOT/.host-checks/local077}"
mkdir -p "$OUT"
export LC_ALL="${LC_ALL:-C.UTF-8}"
C="$ROOT/core/src/main/kotlin/com/luma/core"; T="$ROOT/core/src/test/kotlin/com/luma/core"
kotlinc "$C"/*.kt "$T/CoreChecks.kt" "$T/DownloadPackagingChecks.kt" "$T/LocalParserChecks.kt" -Xjdk-release=17 -include-runtime -d "$OUT/core.jar" > "$OUT/core-compile.log" 2>&1
for cls in CoreChecksKt DownloadPackagingChecks LocalParserChecks; do
 java --add-modules jdk.httpserver -cp "$OUT/core.jar" "com.luma.core.$cls" | tee "$OUT/$cls.log"
done
D="$ROOT/app/src/main/java/com/luma/downloader/data"; AT="$ROOT/app/src/test/java/com/luma/downloader"
kotlinc "$D/UiSettings.kt" "$D/Appearance.kt" "$D/MotionPolicy.kt" "$D/MaterialPolicy.kt" "$D/GlassPolicy.kt" "$D/MenuPolicy.kt" "$D/OverlayPolicy.kt" "$AT/PolicyChecks.kt" "$AT/MenuPolicyChecks.kt" "$AT/OverlayLifecycleChecks.kt" -Xjdk-release=17 -include-runtime -d "$OUT/policy.jar" > "$OUT/policy-compile.log" 2>&1
for cls in PolicyChecksKt MenuPolicyChecks OverlayLifecycleChecks; do
 java -cp "$OUT/policy.jar" "com.luma.downloader.$cls" | tee "$OUT/$cls.log"
done
PYTHONDONTWRITEBYTECODE=1 python3 "$ROOT/tests/local077/test_local_adapter.py" 2>&1 | tee "$OUT/python-adapter.log"
python3 "$ROOT/tools/check_source.py" | tee "$OUT/structure.log"
python3 "$ROOT/tools/check_local077.py" | tee "$OUT/local-structure.log"
echo 'Host checks completed. Android SDK, real library imports, device performance and sites still require separate testing.'
