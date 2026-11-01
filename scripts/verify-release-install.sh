#!/usr/bin/env bash
set -euo pipefail
# Only called on a fresh CI emulator. Never uninstall a user's application.
apk=$(find release-assets -name '*-release.apk' -print -quit)
adb install "$apk"
adb shell am start -W -n com.shiyi.memory/.MainActivity
sleep 5
adb shell pidof com.shiyi.memory
adb shell uiautomator dump /sdcard/release-ui.xml
adb pull /sdcard/release-ui.xml "$RUNNER_TEMP/release-ui.xml"
python3 - "$RUNNER_TEMP/release-ui.xml" <<'PY'
import sys
text=open(sys.argv[1]).read()
assert '拾忆' in text and '无法读取' not in text and '无法读取回忆' not in text
PY
