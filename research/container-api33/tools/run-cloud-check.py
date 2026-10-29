#!/usr/bin/env python3
"""Run only on this research emulator; never accepts a physical-device serial."""
import argparse
import atexit
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

parser = argparse.ArgumentParser()
parser.add_argument('--sdk', default=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME'))
parser.add_argument('--root', default=os.environ.get('CONTAINER_RESEARCH_WORKDIR'))
parser.add_argument('--serial', default='emulator-5580')
parser.add_argument('--api', choices=['32', '33'], default='33')
mode = parser.add_mutually_exclusive_group()
mode.add_argument('--installed-baseline', action='store_true')
mode.add_argument('--location-contract', action='store_true')
mode.add_argument('--radio-contract', action='store_true')
mode.add_argument('--guest-location', action='store_true')
mode.add_argument('--startup-contract', action='store_true')
mode.add_argument('--third-party-location', action='store_true')
parser.add_argument('--ordinary-entry', action='store_true', help='Use the real import/coordinate launcher with --third-party-location')
parser.add_argument('--label', default='latest')
args = parser.parse_args()
contract_case = args.location_contract or args.radio_contract
if args.ordinary_entry and not args.third_party_location:
    raise SystemExit('--ordinary-entry requires --third-party-location')
if args.api != '33' and (contract_case or args.guest_location or args.startup_contract):
    raise SystemExit('The owned location contract cases remain API33 only')
if not args.sdk or not args.root:
    raise SystemExit('Specify --sdk/--root or ANDROID_SDK_ROOT/CONTAINER_RESEARCH_WORKDIR')
if not re.fullmatch(r'emulator-\d+', args.serial):
    raise SystemExit('Only a dedicated emulator is permitted')
root = Path(args.root).resolve()
if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_-]{0,63}', args.label):
    raise SystemExit('Invalid evidence label')
evidence = root / 'evidence' / args.label
evidence.mkdir(parents=True, exist_ok=False)
adb = [str(Path(args.sdk) / 'platform-tools/adb'), '-s', args.serial]

def run(arguments, timeout=60, check=True):
    result = subprocess.run(adb + arguments, capture_output=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(result.stderr.decode(errors='replace')[:1500])
    return result

def shell(command, timeout=30, check=True):
    return run(['shell', command], timeout, check).stdout.decode(errors='replace').strip()

deadline = time.monotonic() + 1200
last_notice = 0
while time.monotonic() < deadline:
    try:
        if shell('getprop sys.boot_completed', timeout=10, check=False) == '1':
            break
    except subprocess.TimeoutExpired:
        pass
    if time.monotonic() - last_notice >= 30:
        print('Waiting for software-emulated Android API ' + args.api + ' boot', flush=True)
        last_notice = time.monotonic()
    time.sleep(2)
else:
    raise SystemExit('Android did not boot; no runtime result')
if shell('getprop ro.kernel.qemu') != '1' or shell('getprop ro.build.version.sdk') != args.api:
    raise SystemExit('Not the explicitly selected research emulator API')
run(['unroot'], check=False)
for _ in range(30):
    try:
        if shell('id -u', timeout=5, check=False) == '2000':
            break
    except subprocess.TimeoutExpired:
        pass
    time.sleep(1)
else:
    raise SystemExit('Ordinary adb shell UID unavailable')
host = root / 'probe-host-build/outputs/apk/debug/probeHost-debug.apk'
test = root / 'probe-host-build/outputs/apk/androidTest/debug/probeHost-debug-androidTest.apk'
guest = root / 'guest/calculator-10-foss-release.apk'
guest_package = 'org.fossify.math'
if args.guest_location or args.startup_contract:
    guest = root / 'location-consumer-build/outputs/apk/debug/locationConsumer-debug.apk'
    guest_package = 'com.soozooq.tianwaitian.locationconsumer'
if args.third_party_location:
    guest = root / 'guest/osmdroidRelease-v3.10.6.apk'
    guest_package = 'com.android.gpstest.osmdroid'
inputs = (host, test) if contract_case else (host, test, guest)
if not all(p.is_file() for p in inputs):
    raise SystemExit('Build artifacts missing')
if shell('getenforce') != 'Enforcing':
    raise SystemExit('SELinux must remain enforcing')
metadata = {
    'started_at_utc': datetime.now(timezone.utc).isoformat(),
    'scope': 'CLOUD_EMULATOR_ONLY_NOT_VIVO_OR_WECHAT',
    'case': 'owned_startup_framework_contract' if args.startup_contract else
        'ordinary_import_coordinate_entry' if args.ordinary_entry else 'official_gpstest_location_ui' if args.third_party_location else
        'owned_guest_location_real_process' if args.guest_location else
        'controlled_radio_proxy_contract' if args.radio_contract else
        'controlled_location_contract' if args.location_contract else
        ('system_installed_control' if args.installed_baseline else 'container_uninstalled_guest'),
    'serial': args.serial, 'api': int(args.api), 'abi': shell('getprop ro.product.cpu.abi'),
    'shell_uid': shell('id -u'), 'selinux': shell('getenforce'),
    'artifacts': {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs},
}
spec = Path(__file__).resolve().parents[1]
source_inputs = [spec / 'sources.json', spec / 'evidence/official-target-apk-readiness.json',
    *sorted((spec / 'patches').glob('*.patch')),
    *sorted((spec / 'tools').glob('*.*')), *sorted((spec / 'probe-host').rglob('*.java')),
    spec / 'probe-host/build.gradle', spec / 'probe-host/src/main/AndroidManifest.xml']
source_inputs += [*sorted((spec / 'licenses').glob('*.txt')),
    *sorted((spec / 'licenses').glob('*provenance.json')),
    spec / 'THIRD_PARTY_NOTICES.md', spec / 'CI_SOURCE_AND_RELINK.md']
source_inputs += sorted((spec / 'probe-host/src/main/res').rglob('*.xml'))
source_inputs += sorted((spec / 'probe-host/src/androidTest').rglob('*.xml'))
source_inputs += [*sorted((spec / 'location-consumer').rglob('*.java')),
    *sorted((spec / 'location-consumer').rglob('*.xml')), spec / 'location-consumer/build.gradle']
metadata['research_source_sha256'] = {
    str(p.relative_to(spec)): hashlib.sha256(p.read_bytes()).hexdigest() for p in source_inputs
    if p.is_file() and '__pycache__' not in p.parts}
(evidence / 'case-inputs.json').write_text(json.dumps(metadata, indent=2) + '\n')
if args.guest_location or args.startup_contract:
    subprocess.run([sys.executable, str(spec / 'tools/inspect-location-consumer.py'),
        '--sdk', args.sdk, '--apk', str(guest), '--output', str(evidence / 'consumer-artifact.json')], check=True)
if args.third_party_location:
    subprocess.run([sys.executable, str(spec / 'tools/inspect-third-party-apk.py'),
        '--sdk', args.sdk, '--apk', str(guest), '--output', str(evidence / 'third-party-artifact.json')], check=True)
if shell('pm path ' + guest_package, check=False):
    raise SystemExit('Guest already installed in system; use a fresh research emulator')
print('Installing standalone host/test; guest remains uninstalled in the system', flush=True)
for apk in (host, test):
    print(run(['install', '-r', str(apk)], timeout=180).stdout.decode(errors='replace'), flush=True)
if args.third_party_location:
    # Dedicated emulator + own research package only. Test grants through normal system UI.
    research_package = 'com.soozooq.tianwaitian.containerresearch'
    shell('am force-stop ' + research_package)
    permission_reset = {}
    for permission in ('android.permission.ACCESS_FINE_LOCATION', 'android.permission.ACCESS_COARSE_LOCATION'):
        revoked = run(['shell', 'pm', 'revoke', research_package, permission], check=False)
        flags = run(['shell', 'pm', 'clear-permission-flags', research_package, permission,
                     'user-set', 'user-fixed'], check=False)
        permission_reset[permission] = {'revoke_exit': revoked.returncode,
            'clear_flags_exit': flags.returncode}
    (evidence / 'permission-precondition.json').write_text(json.dumps(permission_reset, indent=2) + '\n')
control_installed = False

def remove_control_guest():
    global control_installed
    if not control_installed:
        return True
    cleanup = run(['uninstall', 'org.fossify.math'], timeout=120, check=False)
    removed = cleanup.returncode == 0 and b'Success' in cleanup.stdout
    (evidence / 'control-cleanup.json').write_text(json.dumps({'removed': removed}) + '\n')
    if removed:
        control_installed = False
    return removed

def emergency_cleanup():
    try:
        remove_control_guest()
    except Exception as error:
        (evidence / 'control-cleanup-error.txt').write_text(type(error).__name__ + '\n')

atexit.register(emergency_cleanup)
if args.installed_baseline:
    print('Installing guest only for the ordinary system-app control', flush=True)
    print(run(['install', str(guest)], timeout=180).stdout.decode(errors='replace'), flush=True)
    control_installed = True
shell('settings put global window_animation_scale 0')
shell('settings put global transition_animation_scale 0')
shell('settings put global animator_duration_scale 0')
shell('input keyevent KEYCODE_WAKEUP')
shell('wm dismiss-keyguard', check=False)
if not contract_case:
    run(['push', str(guest), '/data/local/tmp/gogogo-official-gpstest.apk' if args.third_party_location
        else '/data/local/tmp/gogogo-location-consumer.apk' if args.guest_location or args.startup_contract
        else '/data/local/tmp/gogogo-research-guest.apk'], timeout=120)
if args.ordinary_entry:
    # A fresh boot may report boot_completed before shared storage is usable.
    # Wake/dismiss the ordinary keyguard first; never chmod or bypass storage checks.
    storage_deadline = time.monotonic() + 90
    ready = False
    while time.monotonic() < storage_deadline:
        created = run(['shell', 'mkdir', '-p', '/sdcard/Download'], check=False)
        writable = run(['shell', 'test', '-w', '/sdcard/Download'], check=False)
        if created.returncode == 0 and writable.returncode == 0:
            ready = True
            break
        time.sleep(2)
    (evidence / 'storage-precondition.json').write_text(json.dumps({
        'ready': ready, 'shell_uid': shell('id -u'),
        'scope': 'DEDICATED_EMULATOR_DOWNLOAD_DIRECTORY_ONLY_NO_PERMISSION_BYPASS',
    }, indent=2) + '\n')
    if not ready:
        raise SystemExit('Shared Download directory is not ready; no import/UI result')
    run(['push', str(guest), '/sdcard/Download/GPSTest-3.10.6.apk'], timeout=120)
run(['logcat', '-c'])
folder = 'startup-framework-evidence' if args.startup_contract else 'third-party-location-evidence' if args.third_party_location else \
    'guest-location-evidence' if args.guest_location else 'runtime-evidence'
# Only this dedicated emulator's own test-output directory; never reuse a prior screenshot/report.
run(['shell', 'run-as', 'com.soozooq.tianwaitian.containerresearch',
     'rm', '-rf', 'files/' + folder])
test_class = ('com.soozooq.tianwaitian.containerresearch.StartupFrameworkInstrumentedTest' if args.startup_contract else
    'com.soozooq.tianwaitian.containerresearch.ThirdPartyLocationInstrumentedTest#' +
    ('ordinaryImportCoordinateAndStopEntry' if args.ordinary_entry else
     'officialGpsTestReceivesChangedTargetThroughItsNormalUi')
    if args.third_party_location else 'com.soozooq.tianwaitian.containerresearch.GuestLocationInstrumentedTest'
    if args.guest_location else 'top.niunaijun.bcore.fake.service.LocationContractInstrumentedTest'
    if args.location_contract else 'top.niunaijun.bcore.fake.service.RadioPrivacyContractInstrumentedTest'
    if args.radio_contract else 'com.soozooq.tianwaitian.containerresearch.ExternalGuestInstrumentedTest#' +
    ('installedCalculatorControl' if args.installed_baseline else 'calculatorRunsUninstalledAndRetainsResultAfterHome'))
print('Running official GPSTest permission + coordinate UI check' if args.third_party_location
    else 'Running public Android location calls in a real owned Guest process' if args.guest_location
    else 'Running controlled public-API33 location contracts' if args.location_contract
    else 'Running controlled API33 radio privacy proxy contracts' if args.radio_contract
    else 'Running self-owned Guest framework startup contracts' if args.startup_contract
    else 'Running real third-party UI and return-from-home check', flush=True)
instrumentation = ['shell', 'am', 'instrument', '-w', '-r', '-e', 'api', args.api, '-e', 'class', test_class]
if args.guest_location:
    instrumentation += ['-e', 'consumer_sha256', hashlib.sha256(guest.read_bytes()).hexdigest()]
if args.startup_contract:
    instrumentation += ['-e', 'guest_sha256', hashlib.sha256(guest.read_bytes()).hexdigest()]
result = run(instrumentation + [
    'com.soozooq.tianwaitian.containerresearch.test/androidx.test.runner.AndroidJUnitRunner'],
    timeout=300, check=False)
text = result.stdout.decode(errors='replace') + result.stderr.decode(errors='replace')
(evidence / 'cloud-instrumentation.txt').write_text(text)
for relative in (() if contract_case else
        ('result.json',) if args.startup_contract else
        ('window.xml', 'screen.png', 'entry.png', 'result.json', 'last-visible-text.json',
         'failure-window.xml', 'failure-screen.png') if args.third_party_location else
        ('window.xml', 'screen.png', 'result.json', 'stages.json', 'last-guest-report.json') if args.guest_location
        else ('window.xml', 'screen.png', 'installed-import.json', 'installed-import-ui-state.json') if args.installed_baseline
        else ('window.xml', 'screen.png')):
    exists = run(['shell', 'run-as', 'com.soozooq.tianwaitian.containerresearch',
        'test', '-f', 'files/' + folder + '/' + relative], check=False).returncode == 0
    if not exists:
        if relative == 'screen.png':
            screen = run(['exec-out', 'screencap', '-p'], check=False)
            if screen.returncode == 0 and screen.stdout.startswith(b'\x89PNG\r\n\x1a\n'):
                (evidence / 'cloud-screen.png').write_bytes(screen.stdout)
        continue
    data = run(['exec-out', 'run-as', 'com.soozooq.tianwaitian.containerresearch', 'cat',
        'files/' + folder + '/' + relative], check=False)
    if data.returncode == 0:
        (evidence / ('cloud-' + relative)).write_bytes(data.stdout)
    elif relative == 'screen.png':
        screen = run(['exec-out', 'screencap', '-p'], check=False)
        if screen.returncode == 0:
            (evidence / 'cloud-screen.png').write_bytes(screen.stdout)
(evidence / 'cloud-logcat.txt').write_bytes(run(['logcat', '-d', '-v', 'threadtime'], check=False).stdout)
(evidence / 'cloud-activities.txt').write_text(shell('dumpsys activity activities', check=False))
(evidence / 'cloud-guest-system-path.txt').write_text(shell('pm path ' + guest_package, check=False))
cleanup_ok = remove_control_guest()
expected_result = 'OK (7 tests)' if args.location_contract else 'OK (19 tests)' if args.radio_contract else 'OK (1 test)'
passed = result.returncode == 0 and expected_result in text and not re.search(
    r'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed', text)
system_guest_after_cleanup = bool(shell('pm path ' + guest_package, check=False))
passed = passed and cleanup_ok and not system_guest_after_cleanup
if args.guest_location or args.third_party_location or args.startup_contract:
    guest_result = evidence / 'cloud-result.json'
    passed = passed and guest_result.is_file() and json.loads(guest_result.read_text()).get('passed') is True
if args.guest_location and passed:
    observed = json.loads(guest_result.read_text())
    passed = observed.get('scope') == 'OWNED_GUEST_REAL_PROCESS_NOT_THIRD_PARTY_OR_WECHAT' \
        and observed.get('report_source') == 'OWN_GUEST_ATOMIC_PRIVATE_FILE' \
        and observed.get('consumer_sha256') == hashlib.sha256(guest.read_bytes()).hexdigest() \
        and observed.get('host_pid') != observed.get('guest_pid') \
        and {s.get('name') for s in observed.get('stages', [])} == {
            'TARGET_A', 'TARGET_B', 'UNREGISTERED_STABLE', 'CURRENT_CANCELLED'}
installed_import = None
if args.installed_baseline:
    imported = evidence / 'cloud-installed-import.json'
    installed_import = json.loads(imported.read_text()) if imported.is_file() else {}
    passed = passed and installed_import.get('scope') == 'INSTALLED_PUBLIC_APK_IMPORT_NOT_ACCOUNT_DATA_OR_TARGET_LOCATION' \
        and installed_import.get('package') == guest_package and all(installed_import.get(key) is True for key in [
            'passed', 'ordinary_import_button_used', 'original_uid_path_version_preserved',
            'original_signatures_verified', 'temporary_apk_removed', 'virtual_copy_removed'])
(evidence / 'case-result.json').write_text(json.dumps({
    'passed': passed, 'control_removed': cleanup_ok,
    'installed_public_apk_import': installed_import,
    'guest_system_installed_after_cleanup': system_guest_after_cleanup,
    'wechat_result': 'NOT_TESTED',
    'location_replacement': 'OFFICIAL_GPSTEST_UI_ONLY' if args.third_party_location else
        'OWNED_GUEST_REAL_PROCESS_ONLY' if args.guest_location else
        'CONTROLLED_PUBLIC_API33_TEST_ONLY' if args.location_contract else
        'NOT_TESTED_RADIO_PROXY_ONLY' if args.radio_contract else 'DISABLED',
    'radio_privacy': 'CONTROLLED_PROXY_ONLY' if args.radio_contract else 'NOT_TESTED',
    'guest_signer_metadata': 'PASS' if passed and not contract_case
        and not args.installed_baseline and not args.guest_location and not args.third_party_location
        and not args.startup_contract else 'NOT_TESTED',
    'startup_framework': 'OWN_GUEST_CREATION_ONLY_NO_PENDING_INTENT_DELIVERY'
        if args.startup_contract else 'NOT_TESTED',
}, indent=2) + '\n')
(evidence / 'cloud-runtime-check.exit').write_text('0\n' if passed else '1\n')
print(text[-5000:], flush=True)
raise SystemExit(0 if passed else 1)
