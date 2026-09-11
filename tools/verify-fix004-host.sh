#!/usr/bin/env bash
# Dependency-free host checks. Requires kotlinc, JDK17+, Python3, UTF-8 locale.
# NOT an Android build, device test or live OAuth check.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/.host-checks"; mkdir -p "$OUT"
D="$ROOT/app/src/main/java/com/luma/downloader/data"
kotlinc "$ROOT"/core/src/main/kotlin/com/luma/core/*.kt "$ROOT"/core/src/test/kotlin/com/luma/core/CoreChecks.kt -jvm-target 17 -include-runtime -d "$OUT/core.jar"
java --add-modules jdk.httpserver -cp "$OUT/core.jar" com.luma.core.CoreChecksKt
kotlinc "$D/UiSettings.kt" "$D/Appearance.kt" "$D/MotionPolicy.kt" "$D/MaterialPolicy.kt" "$D/MenuPolicy.kt" "$D/GlassPolicy.kt" "$D/GrainPixels.kt" "$D/OverlayPolicy.kt" "$ROOT/app/src/test/java/com/luma/downloader/PolicyChecks.kt" "$ROOT/app/src/test/java/com/luma/downloader/MenuPolicyChecks.kt" "$ROOT/app/src/test/java/com/luma/downloader/OverlayLifecycleChecks.kt" -jvm-target 17 -include-runtime -d "$OUT/policy.jar"
java -cp "$OUT/policy.jar" com.luma.downloader.PolicyChecksKt
java -cp "$OUT/policy.jar" com.luma.downloader.MenuPolicyChecks
java -cp "$OUT/policy.jar" com.luma.downloader.OverlayLifecycleChecks
kotlinc "$ROOT"/core/src/main/kotlin/com/luma/core/*.kt "$ROOT"/auth-bridge/src/main/kotlin/com/luma/bridge/*.kt "$ROOT"/auth-bridge/src/test/kotlin/com/luma/bridge/BridgeChecks.kt "$ROOT"/core/src/test/kotlin/com/luma/core/QrProtocolChecks.kt -jvm-target 17 -include-runtime -d "$OUT/auth-tests.jar"
java -cp "$OUT/auth-tests.jar" com.luma.core.QrProtocolChecks
java --add-modules jdk.httpserver -cp "$OUT/auth-tests.jar" com.luma.bridge.BridgeChecks
python3 "$ROOT/tools/check_render_graph.py"
python3 "$ROOT/tools/check_source.py"
echo 'Host checks completed. Android compilation and real-device verification remain separate.'
