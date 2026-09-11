#!/usr/bin/env bash
set -euo pipefail
if [ -n "${DEBUG_KEYSTORE_BASE64:-}" ]; then
  mkdir -p "$HOME/.android"
  printf '%s' "$DEBUG_KEYSTORE_BASE64" | base64 --decode > "$HOME/.android/debug.keystore"
  chmod 600 "$HOME/.android/debug.keystore"
  keytool -list \
    -keystore "$HOME/.android/debug.keystore" \
    -storepass android \
    -alias androiddebugkey >/dev/null
  echo "Stable debug keystore restored."
else
  echo "DEBUG_KEYSTORE_BASE64 is not configured; Gradle will use a temporary debug key."
fi

