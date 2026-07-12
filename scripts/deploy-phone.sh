#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
APPLICATION_ID="net.sagberg.kartoffel"
ACTIVITY="$APPLICATION_ID/.MainActivity"

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    echo "Usage: scripts/deploy-phone.sh"
    echo "Builds, installs, and launches the latest debug version on the connected phone."
    exit 0
fi

if [[ $# -ne 0 ]]; then
    echo "Unexpected argument: $1" >&2
    echo "Run scripts/deploy-phone.sh --help for usage." >&2
    exit 2
fi

if command -v adb >/dev/null 2>&1; then
    ADB="$(command -v adb)"
else
    SDK_DIR="$(sed -n 's/^sdk\.dir=//p' "$ROOT_DIR/local.properties" 2>/dev/null | tail -1)"
    ADB="${SDK_DIR:-}/platform-tools/adb"
    if [[ ! -x "$ADB" ]]; then
        echo "adb was not found. Add it to PATH or set sdk.dir in local.properties." >&2
        exit 1
    fi
fi

DEVICES=()
while IFS= read -r serial; do
    DEVICES+=("$serial")
done < <(
    "$ADB" devices | awk '
        NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1 }
    '
)

if [[ ${#DEVICES[@]} -eq 0 ]]; then
    echo "No authorized physical Android device found." >&2
    echo "Connect the phone, enable USB debugging, and accept its authorization prompt." >&2
    exit 1
fi

if [[ ${#DEVICES[@]} -gt 1 ]]; then
    echo "More than one physical Android device is connected:" >&2
    printf '  %s\n' "${DEVICES[@]}" >&2
    echo "Disconnect all but the phone you want to deploy to." >&2
    exit 1
fi

SERIAL="${DEVICES[0]}"

echo "Building Kartoffel..."
"$ROOT_DIR/gradlew" -p "$ROOT_DIR" :app:assembleDebug

echo "Installing on $SERIAL..."
"$ADB" -s "$SERIAL" install -r -t "$APK_PATH"

echo "Launching Kartoffel..."
"$ADB" -s "$SERIAL" shell am start -n "$ACTIVITY" >/dev/null

echo "Deployed successfully."
