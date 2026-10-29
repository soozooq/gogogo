#!/usr/bin/env bash
# CI only: the official Android 12L image's ARM translation, never a phone.
set -euo pipefail
spec_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
: "${CONTAINER_RESEARCH_WORKDIR:?Use an external research workdir}"
: "${ANDROID_AVD_HOME:?Use a dedicated research AVD directory}"
: "${ANDROID_HOME:?Set the SDK path}"
test "$(uname -s)" = Linux
test "$(uname -m)" = x86_64
mkdir -p "$CONTAINER_RESEARCH_WORKDIR/evidence" "$ANDROID_AVD_HOME"

# Original target APKs remain external research inputs; never embed or upload them.
python3 - "$spec_root" "$CONTAINER_RESEARCH_WORKDIR" <<'PY'
import hashlib, json, subprocess, sys, tempfile
from pathlib import Path
spec, work = map(Path, sys.argv[1:])
pins = json.loads((spec / 'evidence/official-target-apk-readiness.json').read_text())
directory = work / 'targets'
directory.mkdir(exist_ok=True)
for entry in pins['targets']:
    key = entry['source_key']
    filename = {'wechat': 'weixin-8.0.79-arm64.apk', 'douyin': 'douyin-40.8.0.apk'}[key]
    target = directory / filename
    def approved(path):
        if path.stat().st_size != entry['size']: return False
        digest = hashlib.sha256()
        with path.open('rb') as source:
            for block in iter(lambda: source.read(1024 * 1024), b''): digest.update(block)
        return digest.hexdigest() == entry['sha256']
    if target.exists():
        if not approved(target): raise SystemExit('Existing target input differs; preserved')
        continue
    with tempfile.NamedTemporaryFile(dir=directory, prefix='download-', delete=False) as out:
        temporary = Path(out.name)
    try:
        subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error', '--retry', '2',
            '--max-time', '600', '--max-filesize', str(entry['size']), '--output', str(temporary),
            pins['source'][key]['url']], check=True)
        if not approved(temporary): raise SystemExit('Original target size/SHA mismatch')
        temporary.rename(target)
    finally:
        temporary.unlink(missing_ok=True)
PY
sdkmanager --sdk_root="$ANDROID_HOME" 'emulator' 'platform-tools' 'system-images;android-32;google_apis;x86_64'
printf 'no\n' | avdmanager create avd --name api32-official-targets --package 'system-images;android-32;google_apis;x86_64'
"$ANDROID_HOME/emulator/emulator" -no-window -accel-check > "$CONTAINER_RESEARCH_WORKDIR/evidence/target-acceleration-check.txt" 2>&1
research_adb="$ANDROID_HOME/platform-tools/adb"
"$ANDROID_HOME/emulator/emulator" -avd api32-official-targets -port 5580 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -accel on -memory 2048 -cores 2 -no-snapshot-load -no-snapshot-save > "$CONTAINER_RESEARCH_WORKDIR/evidence/target-emulator.log" 2>&1 &
research_emulator_pid=$!
trap 'kill "$research_emulator_pid" 2>/dev/null || true' EXIT
research_deadline=$((SECONDS + 600))
until [ "$("$research_adb" -s emulator-5580 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
    kill -0 "$research_emulator_pid" 2>/dev/null || { cat "$CONTAINER_RESEARCH_WORKDIR/evidence/target-emulator.log"; exit 1; }
    [ "$SECONDS" -lt "$research_deadline" ] || { echo 'No boot result within bound'; exit 1; }
    sleep 3
done
research_failed=0
for research_target in wechat douyin; do
    if ! python3 "$spec_root/tools/run-target-startup-check.py" --sdk "$ANDROID_HOME" --serial emulator-5580 --label "api32-$research_target-startup" --target "$research_target" --api 32; then
        research_failed=1
    fi
done
exit "$research_failed"
