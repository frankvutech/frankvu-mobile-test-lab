#!/usr/bin/env bash
# The container's default command: the same checks CI runs, then the debug APK.
set -euo pipefail
cd /workspace
python3 tools/generate_strings.py --check
python3 tools/generate_test_ids.py --check
cd android
./gradlew --no-daemon qualityCheck assembleDebug
echo "APK: android/app/build/outputs/apk/debug/app-debug.apk"
