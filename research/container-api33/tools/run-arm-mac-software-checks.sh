#!/usr/bin/env bash
# Explicit research opt-in. Full ARM64 software guest, never an app Native Bridge.
set -euo pipefail
spec_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
: "${CONTAINER_RESEARCH_WORKDIR:?Use an external research workdir}"
: "${ANDROID_AVD_HOME:?Use a dedicated research AVD directory}"
: "${ANDROID_HOME:?Set the SDK path}"
test "$(uname -s)" = Darwin
test "$(uname -m)" = arm64
mkdir -p "$CONTAINER_RESEARCH_WORKDIR/evidence" "$ANDROID_AVD_HOME"
sdkmanager --sdk_root="$ANDROID_HOME" 'emulator' 'platform-tools'
research_backend="$ANDROID_HOME/emulator/qemu/darwin-aarch64/qemu-system-aarch64-headless"
test -x "$research_backend"
"$research_backend" -qemu -accel help > "$CONTAINER_RESEARCH_WORKDIR/evidence/arm64-software-accelerators.txt" 2>&1
if ! python3 -c 'import re,sys; sys.exit(0 if re.search(r"\btcg\b",open(sys.argv[1]).read()) else 1)' "$CONTAINER_RESEARCH_WORKDIR/evidence/arm64-software-accelerators.txt"; then
    echo 'This official binary does not advertise TCG; no OS or app runtime result.'
    exit 1
fi
sdkmanager --sdk_root="$ANDROID_HOME" 'system-images;android-33;default;arm64-v8a'
printf 'no\n' | avdmanager create avd --name api33-arm64-software --package 'system-images;android-33;default;arm64-v8a'
python3 - "$ANDROID_AVD_HOME/api33-arm64-software.avd/config.ini" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
values = {'disk.dataPartition.size': '6G', 'hw.lcd.width': '320',
          'hw.lcd.height': '640', 'hw.lcd.density': '160',
          'hw.camera.back': 'none', 'hw.camera.front': 'none'}
lines = [s for s in p.read_text().splitlines() if s.partition('=')[0].strip() not in values]
p.write_text('\n'.join(lines + [k + '=' + v for k, v in values.items()]) + '\n')
PY
# The ARM glue can add -enable-hvf even for -accel off. Explicitly select the
# software accelerator in the final QEMU machine options; leave the OS untouched.
"$ANDROID_HOME/emulator/emulator" -avd api33-arm64-software -port 5580 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -accel off -memory 2048 -cores 2 -no-snapshot-load -no-snapshot-save -show-kernel -verbose -qemu -machine accel=tcg -cpu max > "$CONTAINER_RESEARCH_WORKDIR/evidence/arm64-software-emulator.log" 2>&1 &
research_emulator_pid=$!
trap 'kill "$research_emulator_pid" 2>/dev/null || true' EXIT
python3 "$spec_root/tools/qualify-arm64-software-boot.py" --sdk "$ANDROID_HOME" --serial emulator-5580 --emulator-pid "$research_emulator_pid" --output "$CONTAINER_RESEARCH_WORKDIR/evidence/arm64-software-boot"
python3 "$spec_root/tools/run-cloud-check.py" --sdk "$ANDROID_HOME" --serial emulator-5580 --label arm64-software-ordinary-entry --third-party-location --ordinary-entry
python3 "$spec_root/tools/run-cloud-check.py" --sdk "$ANDROID_HOME" --serial emulator-5580 --label arm64-software-calculator
