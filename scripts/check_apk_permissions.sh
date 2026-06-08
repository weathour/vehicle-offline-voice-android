#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/env.sh"
cd "$ROOT_DIR"

APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK" ]]; then
  echo "APK not found, building debug package first..."
  bash scripts/build_debug.sh
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

if grep -q 'android.permission.INTERNET' <<<"$PERMISSIONS"; then
  echo "ERROR: APK must not request android.permission.INTERNET" >&2
  exit 1
fi

for required in \
  'android.permission.RECORD_AUDIO' \
  'android.permission.FOREGROUND_SERVICE' \
  'android.permission.POST_NOTIFICATIONS'; do
  if ! grep -q "$required" <<<"$PERMISSIONS"; then
    echo "ERROR: APK is missing required permission: $required" >&2
    exit 1
  fi
done

MERGED_MANIFEST="app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml"
if [[ ! -f "$MERGED_MANIFEST" ]]; then
  MERGED_MANIFEST="app/src/main/AndroidManifest.xml"
fi
if ! grep -q 'android:foregroundServiceType="microphone"' "$MERGED_MANIFEST"; then
  echo "ERROR: VoiceForegroundService must declare android:foregroundServiceType=\"microphone\"" >&2
  exit 1
fi

echo "APK permission policy OK: no INTERNET, required local voice permissions present, microphone foreground service type declared."
