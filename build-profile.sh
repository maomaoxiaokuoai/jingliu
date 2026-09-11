#!/bin/sh
set -eu
cd -- "$(dirname -- "$0")"
sh ./gradlew :app:assembleProfile --console=plain --stacktrace
printf '\nAPK: app/build/outputs/apk/profile/app-profile.apk\n'
