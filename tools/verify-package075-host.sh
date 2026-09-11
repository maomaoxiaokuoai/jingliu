#!/bin/bash
set -euo pipefail
export LC_ALL=C.utf8
R="$(cd "$(dirname "$0")/.." && pwd)"
O="$R/tests/package-0.7.5"
B="$(mktemp -d)"
trap 'rm -rf "$B"' EXIT
D="$R/app/src/main/java/com/luma/downloader/data"
KOTLIN_HOME_REAL="${KOTLIN_HOME:-$(cd "$(dirname "$(command -v kotlinc)")/.." && pwd)}"
KOTLIN_LIB="$KOTLIN_HOME_REAL/lib"
mkdir -p "$O" "$B"
cd "$R"
kotlinc core/src/main/kotlin/com/luma/core/*.kt core/src/test/kotlin/com/luma/core/CoreChecks.kt core/src/test/kotlin/com/luma/core/QrProtocolChecks.kt core/src/test/kotlin/com/luma/core/AccountAccessChecks.kt auth-bridge/src/main/kotlin/com/luma/bridge/*.kt auth-bridge/src/test/kotlin/com/luma/bridge/BridgeChecks.kt -Xjdk-release=17 -include-runtime -d "$B/core.jar" > "$O/core-compile.log" 2>&1
java --add-modules jdk.httpserver -cp "$B/core.jar" com.luma.core.CoreChecksKt > "$O/core.log" 2>&1
java -cp "$B/core.jar" com.luma.core.QrProtocolChecks > "$O/qr.log" 2>&1
java -cp "$B/core.jar" com.luma.core.AccountAccessChecks > "$O/account-access.log" 2>&1
java --add-modules jdk.httpserver -cp "$B/core.jar" com.luma.bridge.BridgeChecks > "$O/auth-bridge.log" 2>&1
kotlinc "$D/UiSettings.kt" "$D/Appearance.kt" "$D/MotionPolicy.kt" "$D/MaterialPolicy.kt" "$D/MenuPolicy.kt" "$D/GlassPolicy.kt" "$D/GrainPixels.kt" "$D/OverlayPolicy.kt" app/src/test/java/com/luma/downloader/PolicyChecks.kt app/src/test/java/com/luma/downloader/MenuPolicyChecks.kt app/src/test/java/com/luma/downloader/OverlayLifecycleChecks.kt -Xjdk-release=17 -include-runtime -d "$B/policy.jar" > "$O/policy-compile.log" 2>&1
java -cp "$B/policy.jar" com.luma.downloader.PolicyChecksKt > "$O/policy.log" 2>&1
java -cp "$B/policy.jar" com.luma.downloader.MenuPolicyChecks > "$O/menu.log" 2>&1
java -cp "$B/policy.jar" com.luma.downloader.OverlayLifecycleChecks > "$O/overlay.log" 2>&1
kotlinc tools/KotlinSyntaxCheck.kt -cp "$KOTLIN_LIB/kotlin-compiler.jar" -d "$B/syntax.jar" > "$O/syntax-compile.log" 2>&1
java -Dfile.encoding=UTF-8 -cp "$B/syntax.jar:$KOTLIN_LIB/*" KotlinSyntaxCheckKt "$R" > "$O/syntax.log" 2>&1
python3 tools/check_source.py > "$O/source.log" 2>&1
printf 'Host checks completed. No Android Gradle build or device verification.\n'
