#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"
echo "ROOT_DIR=$ROOT_DIR"
echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_HOME=$ANDROID_HOME"
echo
java -version
javac -version
echo
sdkmanager --version
echo
adb version | head -3
echo
sdkmanager --sdk_root="$ANDROID_HOME" --list_installed
echo
if [ -f "$ROOT_DIR/gradlew" ]; then
  "$ROOT_DIR/gradlew" --version | sed -n '1,12p'
else
  echo "gradlew: not generated yet"
fi
