#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"
cd "$ROOT_DIR"
APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
  ./gradlew :app:assembleDebug
fi
adb devices
adb install -r "$APK"
