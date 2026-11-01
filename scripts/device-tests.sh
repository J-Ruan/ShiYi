#!/usr/bin/env bash
set -euo pipefail
# Tests themselves refuse an installation containing personal records or settings.
status=0
./gradlew connectedDebugAndroidTest || status=$?
# UTP may already have copied outputs and uninstalled the test application.
if python3 scripts/collect-screenshots.py; then exit "$status"; fi
target=app/build/outputs/connected_android_test_additional_output/synthetic/screenshots-110
mkdir -p "$target"
archive=$(mktemp)
trap 'rm -f "$archive"' EXIT
if adb exec-out run-as com.shiyi.memory tar -C files/screenshots-110 -cf - . > "$archive"; then
  python3 - "$archive" "$target" <<'PY'
import pathlib, sys, tarfile
names = set('home memories detail editor profile calendar collections statistics settings backup pdf wishes anniversaries search storage trash dark dark-editor'.split())
with tarfile.open(sys.argv[1]) as source:
    for member in source.getmembers():
        name = pathlib.PurePosixPath(member.name).name
        if member.isfile() and name.removesuffix('.png') in names and name.endswith('.png'):
            data = source.extractfile(member).read()
            pathlib.Path(sys.argv[2], name).write_bytes(data)
PY
else
  if [[ "$status" == 0 ]]; then status=1; fi
fi
exit "$status"
