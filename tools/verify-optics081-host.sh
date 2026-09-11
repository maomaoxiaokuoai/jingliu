#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C.UTF-8
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${JINGLIU_CHECK_OUT:-$ROOT/.host-checks/optics081}"
TMP="$OUT/artifacts"
mkdir -p "$OUT" "$TMP"
D="$ROOT/app/src/main/java/com/luma/downloader/data"
T="$ROOT/app/src/test/java/com/luma/downloader"
kotlinc "$D"/{UiSettings,Appearance,MotionPolicy,MaterialPolicy,GlassPolicy,LensPolicy,MenuPolicy,OverlayPolicy}.kt "$T"/{PolicyChecks,MenuPolicyChecks,OverlayLifecycleChecks,LensPolicyChecks}.kt -Xjdk-release=17 -include-runtime -d "$TMP/policy.jar" > "$OUT/policy-compile.log" 2>&1
for cls in PolicyChecksKt MenuPolicyChecks OverlayLifecycleChecks LensPolicyChecks; do
 java -cp "$TMP/policy.jar" "com.luma.downloader.$cls" > "$OUT/$cls.log" 2>&1
done
KOTLIN_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v kotlinc)")")")
kotlinc "$ROOT/tools/KotlinSyntaxCheck.kt" -cp "$KOTLIN_HOME/lib/kotlin-compiler.jar" -d "$TMP/syntax.jar" > "$OUT/syntax-compile.log" 2>&1
java -cp "$TMP/syntax.jar:$KOTLIN_HOME/lib/*" KotlinSyntaxCheckKt "$ROOT" > "$OUT/syntax.log" 2>&1
kotlinc "$ROOT"/core/src/main/kotlin/com/luma/core/*.kt "$ROOT"/core/src/test/kotlin/com/luma/core/*Checks.kt -Xjdk-release=17 -include-runtime -d "$TMP/core.jar" > "$OUT/core-compile.log" 2>&1
for cls in CoreChecksKt DownloadPackagingChecks LocalParserChecks AccountAccessChecks QrProtocolChecks Package078Checks; do
 java --add-modules jdk.httpserver -cp "$TMP/core.jar" "com.luma.core.$cls" > "$OUT/$cls.log" 2>&1
done
python "$ROOT/tools/check_source.py" > "$OUT/structure.log" 2>&1
cp "$ROOT/tests/source-checks.json" "$OUT/source-checks.json"
echo 'Current checks completed; no Android SDK or GPU tests.'
