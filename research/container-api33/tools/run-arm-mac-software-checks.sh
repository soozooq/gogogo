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
research_probe_exit=0
# The launcher normally supplies these paths. A bare backend needs the same SDK
# libraries even when it only lists accelerators (before any AVD is created).
env ANDROID_EMULATOR_LAUNCHER_DIR="$ANDROID_HOME/emulator" DYLD_LIBRARY_PATH="$ANDROID_HOME/emulator/lib64:$ANDROID_HOME/emulator/lib64/qt/lib${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}" "$research_backend" -qemu -accel help > "$CONTAINER_RESEARCH_WORKDIR/evidence/arm64-software-accelerators.txt" 2>&1 || research_probe_exit=$?
python3 - "$CONTAINER_RESEARCH_WORKDIR/evidence" "$research_probe_exit" <<'PY'
import json, re, sys
from pathlib import Path
root = Path(sys.argv[1])
status = int(sys.argv[2])
text = (root / 'arm64-software-accelerators.txt').read_text(errors='replace')
tcg = status == 0 and bool(re.search(r'\btcg\b', text))
result = {'scope': 'CAPABILITY_COMMAND_ONLY_NOT_OS_OR_APP_RUNTIME', 'exit': status,
          'command_completed': status == 0, 'tcg_advertised': tcg,
          'state': 'COMMAND_FAILED' if status else 'TCG_ADVERTISED' if tcg else 'TCG_NOT_ADVERTISED'}
(root / 'arm64-software-capability.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2))
raise SystemExit(0 if tcg else 1)
PY
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
# Request TCG explicitly, but do not infer the selected accelerator from this flag.
# SDK 37.2.12 still initialized HVF on the tested Mac runner (see the recorded
# selection failure). Only the actual boot gate permits subsequent app checks.
# Do not repeat this opt-in on an unchanged runner/SDK merely to increase counts.
"$ANDROID_HOME/emulator/emulator" -avd api33-arm64-software -port 5580 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -accel off -memory 2048 -cores 2 -no-snapshot-load -no-snapshot-save -show-kernel -verbose -qemu -machine accel=tcg -cpu max > "$CONTAINER_RESEARCH_WORKDIR/evidence/arm64-software-emulator.log" 2>&1 &
research_emulator_pid=$!
trap 'kill "$research_emulator_pid" 2>/dev/null || true' EXIT
python3 "$spec_root/tools/qualify-arm64-software-boot.py" --sdk "$ANDROID_HOME" --serial emulator-5580 --emulator-pid "$research_emulator_pid" --output "$CONTAINER_RESEARCH_WORKDIR/evidence/arm64-software-boot"
python3 "$spec_root/tools/run-cloud-check.py" --sdk "$ANDROID_HOME" --serial emulator-5580 --label arm64-software-ordinary-entry --third-party-location --ordinary-entry
python3 "$spec_root/tools/run-cloud-check.py" --sdk "$ANDROID_HOME" --serial emulator-5580 --label arm64-software-calculator
