#!/bin/sh
# Small project-authored bootstrap; it downloads the OFFICIAL wrapper, not a custom JAR.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
EXPECTED=2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046
DIR="$ROOT/gradle/wrapper"
TARGET="$DIR/gradle-wrapper.jar"
mkdir -p "$DIR"
hash() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}';
  elif command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | awk '{print $1}';
  else echo 'sha256sum or shasum is required.' >&2; return 1; fi
}
valid() { [ -f "$1" ] && [ "$(hash "$1")" = "$EXPECTED" ]; }
if valid "$TARGET"; then echo 'Official Gradle 8.11.1 wrapper verified.'; exit 0; fi
TEMP=$(mktemp "$DIR/wrapper.XXXXXX")
trap 'rm -f "$TEMP"' EXIT HUP INT TERM
if [ "$#" -gt 0 ]; then
  cp -- "$1" "$TEMP"
else
  command -v curl >/dev/null 2>&1 || { echo 'curl is required, or supply an official local JAR path.' >&2; exit 1; }
  for url in \
    'https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar' \
    'https://github.com/gradle/gradle/raw/refs/tags/v8.11.1/gradle/wrapper/gradle-wrapper.jar'; do
    echo "Downloading official Gradle wrapper: $url"
    if curl --proto '=https' --proto-redir '=https' --fail --location --connect-timeout 15 --max-time 60 --output "$TEMP" "$url"; then
      if valid "$TEMP"; then break; fi
    fi
  done
fi
valid "$TEMP" || { echo 'Wrapper download or SHA-256 verification failed. See README.md; never disable verification.' >&2; exit 1; }
mv -f -- "$TEMP" "$TARGET"
echo 'Official Gradle 8.11.1 wrapper installed and verified.'
