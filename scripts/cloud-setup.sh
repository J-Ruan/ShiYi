#!/usr/bin/env bash
set -euo pipefail
# Use the existing checkout: each cloud task is already isolated.
cd /workspace/ShiYi
TASK_TOOLS=/workspace/tooling
mkdir -p "$TASK_TOOLS"
export ANDROID_HOME="$TASK_TOOLS/android-sdk"
export GRADLE_USER_HOME="$TASK_TOOLS/gradle-home"
export JAVA_HOME="$TASK_TOOLS/jdk-21.0.8"
python3 - <<'PY'
import pathlib, urllib.request, hashlib, zipfile, tarfile
root = pathlib.Path('/workspace/tooling')
def download(url, filename, expected, algorithm='sha256'):
    dst = root / filename
    if not dst.exists(): urllib.request.urlretrieve(url, dst)
    h = hashlib.new(algorithm)
    with dst.open('rb') as f:
        while block := f.read(1024 * 1024): h.update(block)
    if h.hexdigest() != expected: raise RuntimeError('Checksum mismatch: ' + filename)
    return dst
if not (root / 'jdk-21.0.8/bin/javac').exists():
    f = download('https://download.oracle.com/java/21/archive/jdk-21.0.8_linux-x64_bin.tar.gz', 'jdk-21.0.8.tar.gz', 'd87272944278713fc7a120cf024d2818d136b5debc750aa17045e3c6f045b867')
    with tarfile.open(f) as archive: archive.extractall(root, filter='data')
if not (root / 'android-sdk/cmdline-tools/latest/bin/sdkmanager').exists():
    f = download('https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip', 'commandlinetools-linux-16111833_latest.zip', 'e025545c62a8e64c7559119566a569fb1dec5f60', 'sha1')
    target = root / 'android-sdk/cmdline-tools/latest'
    with zipfile.ZipFile(f) as archive:
        for info in archive.infolist():
            if info.is_dir(): continue
            path = target / pathlib.Path(info.filename).relative_to('cmdline-tools')
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(archive.read(info))
            path.chmod((info.external_attr >> 16) & 0o777 or 0o644)
PY
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
mkdir -p "$GRADLE_USER_HOME"
# Session proxy and public CA trust only. Never copy authentication values.
python3 - <<'PY'
import os, pathlib, urllib.parse
p = urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY', ''))
lines = []
if p.hostname:
    for protocol in ('http', 'https'):
        lines += [f'systemProp.{protocol}.proxyHost={p.hostname}', f'systemProp.{protocol}.proxyPort={p.port or 8080}', f'systemProp.{protocol}.nonProxyHosts=localhost|127.*']
if pathlib.Path('/etc/ssl/certs/java/cacerts').exists(): lines += ['systemProp.javax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts']
# Keep task-specific settings in a separate Gradle user home.
path = pathlib.Path('/workspace/tooling/gradle-home/gradle.properties')
old = path.read_text() if path.exists() else ''
keys = {s.split('=', 1)[0] for s in lines}
kept = [s for s in old.splitlines() if s.split('=', 1)[0] not in keys]
path.write_text('\n'.join(kept + lines) + '\n')
PY
# Android SDK licenses apply to build tools only; app data never leaves the device.
if [[ ! -d "$ANDROID_HOME/platforms/android-35" || ! -d "$ANDROID_HOME/build-tools/35.0.0" ]]; then
    set +o pipefail
    yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses > "$TASK_TOOLS/sdk-licenses.log" 2>&1
    license_status=${PIPESTATUS[1]}
    set -o pipefail
    [[ "$license_status" == 0 ]]
    "$ANDROID_HOME/cmdline-tools/latest/bin/android" --no-metrics --sdk="$ANDROID_HOME" sdk install 'platforms;android-35' 'build-tools;35.0.0' platform-tools
fi
if [[ ! -f local.properties ]]; then printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties; fi
./gradlew testDebugUnitTest lint assembleDebug
