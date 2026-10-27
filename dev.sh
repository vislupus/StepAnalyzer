#!/usr/bin/env bash
# One-command developer workflow for Step Analyzer (Linux / macOS / Git Bash on Windows).
#
#   ./dev.sh run        build the debug APK, install it on the connected phone and launch it
#   ./dev.sh build      build a release APK (app/build/outputs/apk/release/app-release.apk)
#   ./dev.sh install    install the already built debug APK without rebuilding
#   ./dev.sh test       run the algorithm unit tests of the :core module (no Android SDK needed)
#   ./dev.sh analyze FILE.csv [FILE.gpx]   run the analysis on your PC from the terminal
#   ./dev.sh logs       stream the app's logcat output
#   ./dev.sh studio     open the project in Android Studio (needs `studio` on your PATH)
#   ./dev.sh clean      delete build outputs
#
# Hot reload: open the project in Android Studio, run the app once, then keep editing
# Composables - "Live Edit" (Android Studio Settings > Editor > Live Edit) pushes UI changes
# to the phone without a rebuild; Ctrl+F10 ("Apply Changes") handles everything else.
set -euo pipefail
cd "$(dirname "$0")"

APP_ID="com.vislupus.stepanalyzer"
GRADLEW="./gradlew"
[ -x "$GRADLEW" ] || GRADLEW="gradle"

adb_cmd() {
  if command -v adb >/dev/null 2>&1; then adb "$@";
  elif [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then "$ANDROID_HOME/platform-tools/adb" "$@";
  elif [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]; then "$ANDROID_SDK_ROOT/platform-tools/adb" "$@";
  else echo "adb not found - add <sdk>/platform-tools to PATH or set ANDROID_HOME" >&2; exit 1; fi
}

case "${1:-help}" in
  run)
    $GRADLEW :app:installDebug
    adb_cmd shell am start -n "$APP_ID/.MainActivity"
    ;;
  build)
    $GRADLEW :app:assembleRelease
    echo
    echo "APK: app/build/outputs/apk/release/app-release.apk"
    ;;
  install)
    adb_cmd install -r app/build/outputs/apk/debug/app-debug.apk
    adb_cmd shell am start -n "$APP_ID/.MainActivity"
    ;;
  test)
    $GRADLEW -PcoreOnly :core:test
    ;;
  analyze)
    shift
    $GRADLEW -PcoreOnly -q :core:run --args="$*"
    ;;
  logs)
    adb_cmd logcat -v time --pid="$(adb_cmd shell pidof -s "$APP_ID" | tr -d '\r')"
    ;;
  studio)
    if command -v studio >/dev/null 2>&1; then studio . ; else echo "Android Studio launcher 'studio' not on PATH; open this folder from Android Studio instead." ; fi
    ;;
  clean)
    $GRADLEW clean
    ;;
  *)
    sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
    ;;
esac
