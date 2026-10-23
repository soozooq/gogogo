#!/usr/bin/env bash
# CI/development only. Dedicated cloud AVD; no physical phone or system target app.
set -euo pipefail
spec_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
: "${CONTAINER_RESEARCH_WORKDIR:?Use an external research workdir}"
: "${ANDROID_AVD_HOME:?Use a dedicated research AVD directory}"
: "${ANDROID_HOME:?Set the SDK path}"
research_abi=${1:?Specify x86_64 or arm64-v8a}
case "$research_abi" in
    x86_64) test "$(uname -m)" = x86_64 ;;
    arm64-v8a) case "$(uname -m)" in arm64|aarch64) ;; *) exit 2 ;; esac ;;
    *) exit 2 ;;
esac
mkdir -p "$CONTAINER_RESEARCH_WORKDIR/evidence" "$ANDROID_AVD_HOME"
uname -a > "$CONTAINER_RESEARCH_WORKDIR/evidence/host-platform.txt"
sdkmanager --sdk_root="$ANDROID_HOME" 'emulator' 'platform-tools' "system-images;android-33;default;$research_abi"
printf 'no\n' | avdmanager create avd --name api33-research-check --package "system-images;android-33;default;$research_abi"
research_adb="$ANDROID_HOME/platform-tools/adb"
"$ANDROID_HOME/emulator/emulator" -version > "$CONTAINER_RESEARCH_WORKDIR/evidence/emulator-version.txt" 2>&1
"$ANDROID_HOME/emulator/emulator" -avd api33-research-check -port 5580 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -accel off -memory 1536 -cores 2 -no-snapshot-load -no-snapshot-save > "$CONTAINER_RESEARCH_WORKDIR/evidence/emulator.log" 2>&1 &
research_emulator_pid=$!
trap 'kill "$research_emulator_pid" 2>/dev/null || true' EXIT
research_deadline=$((SECONDS + 1200))
until [ "$("$research_adb" -s emulator-5580 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
    if ! kill -0 "$research_emulator_pid" 2>/dev/null; then
        cat "$CONTAINER_RESEARCH_WORKDIR/evidence/emulator.log"
        exit 1
    fi
    if [ "$SECONDS" -ge "$research_deadline" ]; then
        echo 'Boot timed out; no runtime success claimed.'
        exit 1
    fi
    sleep 5
done
python3 "$spec_root/tools/run-cloud-check.py" --sdk "$ANDROID_HOME" --serial emulator-5580 --label "$research_abi-ordinary-entry" --third-party-location --ordinary-entry
python3 "$spec_root/tools/run-cloud-check.py" --sdk "$ANDROID_HOME" --serial emulator-5580 --label "$research_abi-calculator"
