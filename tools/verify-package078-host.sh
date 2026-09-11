#!/usr/bin/env bash
# Real JVM/file/network tests; Android and upstream parser objects are substituted only where documented.
set -euo pipefail
export LC_ALL="${LC_ALL:-C.UTF-8}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${JINGLIU_CHECK_OUT:-$ROOT/.host-checks/package078}"
mkdir -p "$OUT"
C="$ROOT/core/src/main/kotlin/com/luma/core";T="$ROOT/core/src/test/kotlin/com/luma/core"
kotlinc "$C"/*.kt "$T"/*Checks.kt -Xjdk-release=17 -include-runtime -d "$OUT/core.jar" > "$OUT/core-compile.log" 2>&1
for cls in CoreChecksKt DownloadPackagingChecks LocalParserChecks AccountAccessChecks QrProtocolChecks Package078Checks; do
 java --add-modules jdk.httpserver -cp "$OUT/core.jar" "com.luma.core.$cls" | tee "$OUT/$cls.log"
done
D="$ROOT/app/src/main/java/com/luma/downloader/data";AT="$ROOT/app/src/test/java/com/luma/downloader"
kotlinc "$D"/{UiSettings,Appearance,MotionPolicy,MaterialPolicy,GlassPolicy,MenuPolicy,OverlayPolicy}.kt "$AT"/{PolicyChecks,MenuPolicyChecks,OverlayLifecycleChecks}.kt -Xjdk-release=17 -include-runtime -d "$OUT/policy.jar" > "$OUT/policy-compile.log" 2>&1
for cls in PolicyChecksKt MenuPolicyChecks OverlayLifecycleChecks; do
 java -cp "$OUT/policy.jar" "com.luma.downloader.$cls" | tee "$OUT/$cls.log"
done
PYTHONDONTWRITEBYTECODE=1 python3 "$ROOT/tests/local077/test_local_adapter.py" 2>&1 | tee "$OUT/python-baseline.log"
PYTHONDONTWRITEBYTECODE=1 python3 "$ROOT/tests/package-0.7.8/test_media_compat.py" 2>&1 | tee "$OUT/python-media.log"
python3 "$ROOT/tools/check_source.py" | tee "$OUT/structure.log"
echo 'Current host checks complete. Android compile, Media3, WebView, device frames and live sites are not tested by this script.'
