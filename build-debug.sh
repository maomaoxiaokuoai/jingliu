#!/bin/sh
set -eu
cd -- "$(dirname -- "$0")"
sh ./gradlew :core:test :app:assembleDebug --stacktrace --console=plain
printf '\nAPK: app/build/outputs/apk/debug/app-debug.apk\n'
