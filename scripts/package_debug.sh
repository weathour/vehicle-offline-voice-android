#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/env.sh"
cd "$ROOT_DIR"

./gradlew :app:assembleDebug
bash scripts/check_apk_permissions.sh
APK="app/build/outputs/apk/debug/app-debug.apk"
ls -lh "$APK"
echo "Debug APK ready: $APK"
