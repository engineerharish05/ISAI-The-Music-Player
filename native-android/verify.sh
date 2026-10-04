#!/usr/bin/env bash
set -euo pipefail

echo "== ISAI native verification =="
gradle --version

echo "== Clean =="
gradle clean

echo "== Unit tests =="
gradle testDebugUnitTest

echo "== Lint =="
gradle lintDebug

echo "== Debug APK =="
gradle assembleDebug

echo "== Release APK =="
gradle assembleRelease

test -f app/build/outputs/apk/debug/app-debug.apk
test -f app/build/outputs/apk/release/app-release-unsigned.apk

echo "ISAI verification completed successfully."
