#!/usr/bin/env bash
set -euo pipefail
apk=${1:?APK path required}
fail() { printf '::error::%s\n' "$1"; exit 1; }
if [[ -z "${EXPECTED_CERT_SHA256:-}" || -z "${VERSION:-}" ]]; then
  fail 'Expected production certificate fingerprint and version are required'
fi
if [[ ! -s "$apk" ]]; then fail 'Signed release APK was not produced'; fi
verification_dir=$(mktemp -d)
trap 'rm -rf "$verification_dir"' EXIT
build_tools="$ANDROID_HOME/build-tools/35.0.0"
if ! "$build_tools/apksigner" verify --verbose --print-certs "$apk" > "$verification_dir/signature.txt" 2>&1; then
  fail 'APK signature verification failed'
fi
if rg -q 'CN=Android Debug' "$verification_dir/signature.txt"; then
  fail 'Debug certificates cannot be used for a production release'
fi
actual=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$verification_dir/signature.txt" | tr '[:upper:]' '[:lower:]')
expected=$(printf '%s' "$EXPECTED_CERT_SHA256" | tr -d ':' | tr '[:upper:]' '[:lower:]')
if [[ -z "$actual" || "$actual" != "$expected" ]]; then fail 'Production certificate fingerprint mismatch'; fi
"$build_tools/aapt" dump badging "$apk" > "$verification_dir/badging.txt"
if rg -q 'application-debuggable' "$verification_dir/badging.txt"; then fail 'Debuggable APKs cannot be released'; fi
if ! rg -q "name='com.shiyi.memory'" "$verification_dir/badging.txt"; then fail 'Unexpected applicationId'; fi
if ! rg -F -q "versionName='$VERSION'" "$verification_dir/badging.txt"; then fail 'Unexpected application version'; fi
printf 'Production APK signature, identity and version verified\n'
