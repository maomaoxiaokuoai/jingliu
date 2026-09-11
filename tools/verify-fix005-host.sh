#!/bin/bash
set -eu
export LC_ALL=C.utf8
R="$(cd "$(dirname "$0")/.." && pwd)"
O="$R/tests/fix-005"
B="$R/.host-checks"
D="$R/app/src/main/java/com/luma/downloader/data"
mkdir -p "$O" "$B"
cd "$R"
kotlinc core/src/main/kotlin/com/luma/core/*.kt core/src/test/kotlin/com/luma/core/CoreChecks.kt core/src/test/kotlin/com/luma/core/QrProtocolChecks.kt core/src/test/kotlin/com/luma/core/AccountAccessChecks.kt auth-bridge/src/main/kotlin/com/luma/bridge/*.kt auth-bridge/src/test/kotlin/com/luma/bridge/BridgeChecks.kt -Xjdk-release=17 -include-runtime -d "$B/all-core.jar" > "$O/core-bridge-compile.log" 2>&1
java --add-modules jdk.httpserver -cp "$B/all-core.jar" com.luma.core.CoreChecksKt > "$O/core.log" 2>&1
java -cp "$B/all-core.jar" com.luma.core.QrProtocolChecks > "$O/qr-protocol.log" 2>&1
java -cp "$B/all-core.jar" com.luma.core.AccountAccessChecks > "$O/access-policy.log" 2>&1
java --add-modules jdk.httpserver -cp "$B/all-core.jar" com.luma.bridge.BridgeChecks > "$O/bridge.log" 2>&1
kotlinc "$D/UiSettings.kt" "$D/Appearance.kt" "$D/MotionPolicy.kt" "$D/MaterialPolicy.kt" "$D/MenuPolicy.kt" "$D/GlassPolicy.kt" "$D/GrainPixels.kt" "$D/OverlayPolicy.kt" app/src/test/java/com/luma/downloader/PolicyChecks.kt app/src/test/java/com/luma/downloader/MenuPolicyChecks.kt app/src/test/java/com/luma/downloader/OverlayLifecycleChecks.kt -Xjdk-release=17 -include-runtime -d "$B/policy.jar" > "$O/policy-compile.log" 2>&1
java -cp "$B/policy.jar" com.luma.downloader.PolicyChecksKt > "$O/policy.log" 2>&1
java -cp "$B/policy.jar" com.luma.downloader.MenuPolicyChecks > "$O/menu.log" 2>&1
java -cp "$B/policy.jar" com.luma.downloader.OverlayLifecycleChecks > "$O/overlay.log" 2>&1
python3 tools/check_render_graph.py > "$O/render-contract.log" 2>&1
python3 tools/check_source.py > "$O/source.log" 2>&1
python3 tools/check_fix005.py > "$O/source-contracts.log" 2>&1
printf 'All host suites completed. Not an Android build.\n' 
