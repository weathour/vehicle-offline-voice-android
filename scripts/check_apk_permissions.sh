#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/env.sh"
cd "$ROOT_DIR"

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
if [[ ! -f "$APK" ]]; then
  echo "ERROR: APK not found: $APK" >&2
  exit 1
fi

AAPT="$ANDROID_HOME/build-tools/35.0.0/aapt"
if [[ ! -x "$AAPT" ]]; then
  AAPT="$(find "$ANDROID_HOME/build-tools" -path '*/aapt' -type f | sort -V | tail -n 1)"
fi
if [[ -z "${AAPT:-}" || ! -x "$AAPT" ]]; then
  echo "ERROR: aapt not found under $ANDROID_HOME/build-tools" >&2
  exit 1
fi

PERMISSIONS="$($AAPT dump permissions "$APK")"
echo "$PERMISSIONS"

for required in \
  'android.permission.RECORD_AUDIO' \
  'android.permission.INTERNET' \
  'android.permission.FOREGROUND_SERVICE' \
  'android.permission.FOREGROUND_SERVICE_MICROPHONE' \
  'android.permission.POST_NOTIFICATIONS'; do
  if ! grep -q "$required" <<<"$PERMISSIONS"; then
    echo "ERROR: APK is missing required permission: $required" >&2
    exit 1
  fi
done

if grep -q 'android.permission.REQUEST_INSTALL_PACKAGES' <<<"$PERMISSIONS"; then
  echo "ERROR: APK must not request package installation permission" >&2
  exit 1
fi

MERGED_MANIFEST="app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml"
if [[ ! -f "$MERGED_MANIFEST" ]]; then
  MERGED_MANIFEST="app/src/main/AndroidManifest.xml"
fi
if ! grep -q 'android:foregroundServiceType="microphone"' "$MERGED_MANIFEST"; then
  echo "ERROR: VoiceForegroundService must declare android:foregroundServiceType=\"microphone\"" >&2
  exit 1
fi

echo "APK permission policy OK: only the Redis network and local voice permissions are declared; package installation permission is absent."
