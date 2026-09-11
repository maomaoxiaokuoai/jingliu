#!/usr/bin/env bash
# Host compilation/contract checks only. No Android/Compose/Keystore/real platform validation.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${JINGLIU_CHECK_OUT:-$ROOT/.host-checks/package-0.7.6}"
mkdir -p "$OUT"
export LC_ALL="${LC_ALL:-C.UTF-8}"
command -v java >/dev/null
command -v kotlinc >/dev/null
command -v python3 >/dev/null
KOTLIN_HOME="${KOTLIN_HOME:-$(cd "$(dirname "$(command -v kotlinc)")/.." && pwd)}"
COROUTINES="${KOTLIN_COROUTINES_JAR:-$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar}"
[ -f "$COROUTINES" ] || { echo 'Set KOTLIN_COROUTINES_JAR to a local kotlinx-coroutines-core-jvm.jar'; exit 2; }
C="$ROOT/core/src/main/kotlin/com/luma/core"
CT="$ROOT/core/src/test/kotlin/com/luma/core"
B="$ROOT/auth-bridge/src/main/kotlin/com/luma/bridge"
BT="$ROOT/auth-bridge/src/test/kotlin/com/luma/bridge"
D="$ROOT/app/src/main/java/com/luma/downloader/data"
AT="$ROOT/app/src/test/java/com/luma/downloader"
F="$ROOT/tests/package-0.7.6/fixtures"
kotlinc "$C"/*.kt "$B"/*.kt "$CT/CoreChecks.kt" "$CT/QrProtocolChecks.kt" "$CT/AccountAccessChecks.kt" "$CT/DownloadPackagingChecks.kt" "$BT/BridgeChecks.kt" -Xjdk-release=17 -include-runtime -d "$OUT/core.jar" >"$OUT/core-compile.log" 2>&1
for cls in com.luma.core.CoreChecksKt com.luma.core.QrProtocolChecks com.luma.core.AccountAccessChecks com.luma.core.DownloadPackagingChecks com.luma.bridge.BridgeChecks; do
  java -Dfile.encoding=UTF-8 --add-modules jdk.httpserver -cp "$OUT/core.jar" "$cls" | tee "$OUT/${cls##*.}.log"
done
kotlinc "$D/UiSettings.kt" "$D/Appearance.kt" "$D/MotionPolicy.kt" "$D/MaterialPolicy.kt" "$D/GlassPolicy.kt" "$D/MenuPolicy.kt" "$D/OverlayPolicy.kt" "$AT/PolicyChecks.kt" "$AT/MenuPolicyChecks.kt" "$AT/OverlayLifecycleChecks.kt" -Xjdk-release=17 -include-runtime -d "$OUT/policy.jar" >"$OUT/policy-compile.log" 2>&1
for cls in com.luma.downloader.PolicyChecksKt com.luma.downloader.MenuPolicyChecks com.luma.downloader.OverlayLifecycleChecks; do
  java -Dfile.encoding=UTF-8 -cp "$OUT/policy.jar" "$cls" | tee "$OUT/${cls##*.}.log"
done
kotlinc "$C"/*.kt "$D/TaskStore.kt" "$ROOT/app/src/main/java/com/luma/downloader/engine/ExtractorRouter.kt" "$F"/*.kt -cp "$COROUTINES" -Xjdk-release=17 -include-runtime -d "$OUT/wiring.jar" >"$OUT/wiring-compile.log" 2>&1
java -Dfile.encoding=UTF-8 -cp "$OUT/wiring.jar:$COROUTINES" package076.WiringChecks | tee "$OUT/wiring.log"
(cd "$ROOT/parse-video-service"; PYTHONDONTWRITEBYTECODE=1 python3 -m unittest -v test_server) 2>&1 | tee "$OUT/service.log"
python3 "$ROOT/tools/check_source.py" | tee "$OUT/structure.log"
echo 'Host checks complete. No APK or live-platform claims are implied.'
