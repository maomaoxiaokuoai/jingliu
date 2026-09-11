#!/bin/sh
# Project launcher: the official Wrapper JAR and distribution are checksum-verified.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
sh "$ROOT/setup-wrapper.sh"
if [ -n "${JAVA_HOME:-}" ]; then JAVA="$JAVA_HOME/bin/java"; else JAVA=java; fi
exec "$JAVA" -Dorg.gradle.appname=gradlew -classpath "$ROOT/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
