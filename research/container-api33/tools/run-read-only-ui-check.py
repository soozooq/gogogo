#!/usr/bin/env python3
"""Own moving UI, on a dedicated API33 SDK emulator; never a target-app success."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time

from ci_owned_apks import sources, verify_notices

parser = argparse.ArgumentParser()
parser.add_argument('--sdk', default=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME'))
parser.add_argument('--root', default=os.environ.get('CONTAINER_RESEARCH_WORKDIR'))
parser.add_argument('--serial', required=True)
parser.add_argument('--label', default='read-only-ui-capture')
args = parser.parse_args()
if not args.sdk or not args.root or not re.fullmatch(r'emulator-[0-9]+', args.serial) \
        or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_-]{0,63}', args.label):
    raise SystemExit('Explicit own SDK emulator, build directory and evidence label required')
root = Path(args.root).resolve()
spec = Path(__file__).resolve().parents[1]
out = root / 'evidence' / args.label
out.mkdir(exist_ok=False)
adb = [str(Path(args.sdk) / 'platform-tools/adb'), '-s', args.serial]
host = 'com.soozooq.tianwaitian.containerresearch'
driver = host + '.test'
fixture = 'com.soozooq.tianwaitian.locationconsumer'
inputs = [(host, root / 'probe-host-build/outputs/apk/debug/probeHost-debug.apk'),
          (driver, root / 'probe-host-build/outputs/apk/androidTest/debug/probeHost-debug-androidTest.apk'),
          (fixture, root / 'location-consumer-build/outputs/apk/debug/locationConsumer-debug.apk')]
result = {'scope': 'SELF_OWNED_MOVING_UI_ONLY_NOT_TARGET_STARTUP_OR_LOCATION', 'passed': False,
          'research_source_sha256': sources(spec), 'requested_packages': []}
for path in (spec / 'location-consumer').rglob('*'):
    if path.is_file() and path.suffix in ['.java', '.xml', '.gradle']:
        result['research_source_sha256'][str(path.relative_to(spec))] = hashlib.sha256(path.read_bytes()).hexdigest()


def run(command, timeout=30, check=True):
    value = subprocess.run(adb + command, capture_output=True, timeout=timeout)
    if check and value.returncode:
        raise RuntimeError('Own capture ADB command failed')
    return value


def shell(*command, **kwargs):
    return run(['shell', *command], **kwargs).stdout.decode().strip()


try:
    if shell('getprop', 'ro.kernel.qemu') != '1' or shell('getprop', 'ro.build.version.sdk') != '33' \
            or shell('getprop', 'sys.boot_completed') != '1' or shell('getenforce') != 'Enforcing':
        raise RuntimeError('Fully booted enforcing API33 SDK emulator required')
    run(['unroot'], check=False)
    for _ in range(30):
        if shell('id', '-u', check=False) == '2000': break
        time.sleep(1)
    else:
        raise RuntimeError('Ordinary shell required')
    result['system'] = {'api': 33, 'uid': 2000, 'selinux': 'Enforcing',
                        'abi': shell('getprop', 'ro.product.cpu.abi'), 'serial': args.serial}
    for package, path in inputs:
        if shell('pm', 'path', package, check=False):
            raise RuntimeError('Existing own package preserved')
        if not path.is_file(): raise RuntimeError('Own build input missing')
    for _, path in inputs[:2]: verify_notices(path, spec)
    result['apks'] = {package: {'size': path.stat().st_size,
                              'sha256': hashlib.sha256(path.read_bytes()).hexdigest()} for package, path in inputs}
    for package, path in inputs:
        result['requested_packages'].append(package)
        run(['install', str(path)], timeout=180)
    shell('input', 'keyevent', 'KEYCODE_WAKEUP')
    shell('wm', 'dismiss-keyguard', check=False)
    shell('am', 'start', '-n', fixture + '/.ReadOnlyUiFixtureActivity')
    try:
        old = run(['shell', 'uiautomator', 'dump', '/data/local/tmp/gogogo-own-moving-window.xml'],
                  timeout=25, check=False)
        old_text = old.stdout + old.stderr
        result['old_cli'] = {'returncode': old.returncode,
                            'idle_error_observed': b'could not get idle state' in old_text}
    except subprocess.TimeoutExpired as error:
        old_text = (error.stdout or b'') + (error.stderr or b'')
        result['old_cli'] = {'timed_out': True}
    (out / 'old-cli-ui-dump.txt').write_bytes(old_text)
    capture = run(['shell', 'am', 'instrument', '-w', '-r', '-e', 'target', 'owned-fixture',
                  host + '/com.soozooq.tianwaitian.containerresearch.OriginalUiProbeInstrumentation'],
                  timeout=90, check=False)
    transcript = capture.stdout + capture.stderr
    (out / 'instrumentation.txt').write_bytes(transcript)
    observed = None
    for name in ['result.json', 'window.xml', 'screen.png']:
        path = 'files/original-ui-owned-fixture/' + name
        if run(['shell', 'run-as', driver, 'test', '-f', path], check=False).returncode == 0:
            data = run(['exec-out', 'run-as', driver, 'cat', path]).stdout
            (out / ('observed.json' if name == 'result.json' else name)).write_bytes(data)
            if name == 'result.json': observed = json.loads(data)
    result['observed'] = observed
    result['host_engine_process_absent_after_capture'] = not shell('pidof', host, host + ':black', check=False)
    result['passed'] = capture.returncode == 0 \
        and re.search(rb'^INSTRUMENTATION_CODE:\s*-1\s*$', transcript, re.M) is not None \
        and isinstance(observed, dict) and observed.get('passed') is True \
        and observed.get('scope') == 'SELF_OWNED_UI_CAPTURE_ONLY_NOT_TARGET_STARTUP' \
        and observed.get('package') == fixture and observed.get('root_package') == fixture \
        and observed.get('stage') == 'OWN_UI_VISIBLE' and observed.get('plain_application') is True \
        and observed.get('own_driver_targeting') is True \
        and all(observed.get(key) is False for key in ['terms_accepted', 'login_attempted', 'messages_sent', 'input_events_sent']) \
        and result['host_engine_process_absent_after_capture']
except Exception as error:
    result.update(passed=False, error_class=type(error).__name__)
finally:
    cleanup = {}
    for package in reversed(result['requested_packages']):
        try:
            removed = run(['uninstall', package], timeout=120, check=False)
            cleanup[package] = removed.returncode == 0 and b'Success' in removed.stdout \
                and not shell('pm', 'path', package, check=False)
        except Exception: cleanup[package] = False
    result['owned_package_cleanup'] = cleanup
    result['passed'] = result['passed'] and all(cleanup.values())
    (out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'scope': result['scope'], 'passed': result['passed'], 'error_class': result.get('error_class')}))
raise SystemExit(0 if result['passed'] else 1)
