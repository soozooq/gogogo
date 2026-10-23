#!/usr/bin/env python3
"""Dedicated API30 official-translation emulator only. Anonymous startup, no account actions."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--root', default=os.environ.get('CONTAINER_RESEARCH_WORKDIR'))
p.add_argument('--sdk', default=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME'))
p.add_argument('--serial', default='emulator-5580')
p.add_argument('--label', required=True)
p.add_argument('--target', choices=['wechat', 'douyin'], required=True)
a = p.parse_args()
if not a.root or not a.sdk or not re.fullmatch(r'emulator-\d+', a.serial):
    raise SystemExit('Dedicated emulator SDK/workdir required')
if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_-]{0,63}', a.label):
    raise SystemExit('Invalid label')
spec = Path(__file__).resolve().parents[1]
work = Path(a.root).resolve()
evidence = work / 'evidence' / a.label
evidence.mkdir(parents=True, exist_ok=False)
adb = [str(Path(a.sdk) / 'platform-tools/adb'), '-s', a.serial]

def run(args, timeout=60, check=True):
    r = subprocess.run(adb + args, capture_output=True, timeout=timeout)
    if check and r.returncode:
        raise RuntimeError(r.stderr.decode(errors='replace')[-1500:])
    return r

def shell(command, timeout=30, check=True):
    return run(['shell', command], timeout, check).stdout.decode(errors='replace').strip()

if shell('getprop ro.kernel.qemu') != '1' or shell('getprop ro.build.version.sdk') != '30':
    raise SystemExit('Only the dedicated API30 image is accepted; no target result')
run(['unroot'], check=False)
for _ in range(20):
    if shell('id -u', check=False) == '2000': break
    time.sleep(1)
if shell('id -u') != '2000' or shell('getenforce') != 'Enforcing':
    raise SystemExit('Ordinary shell + SELinux required')
props = {k: shell('getprop ' + k) for k in ['sys.boot_completed', 'ro.build.fingerprint',
    'ro.product.cpu.abilist', 'ro.dalvik.vm.native.bridge']}
(evidence / 'image.json').write_text(json.dumps(props, indent=2) + '\n')
if props['sys.boot_completed'] != '1' or 'arm64-v8a' not in props['ro.product.cpu.abilist'].split(',') \
        or props['ro.dalvik.vm.native.bridge'] in ['', '0']:
    raise SystemExit('Actual image is not ready with ARM64 translation; no target result')
readiness = json.loads((spec / 'evidence/official-target-apk-readiness.json').read_text())
entry = next(x for x in readiness['targets'] if x['source_key'] == a.target)
pkg = entry['package']
if shell('pm path ' + pkg, check=False):
    raise SystemExit('System package already exists; preserved, use a fresh cloud AVD')
filename = 'weixin-8.0.79-arm64.apk' if a.target == 'wechat' else 'douyin-40.8.0.apk'
apk = work / 'targets' / filename
if not apk.is_file() or apk.stat().st_size != entry['size']:
    raise SystemExit('Pinned original input missing')
with apk.open('rb') as f:
    sha = hashlib.file_digest(f, 'sha256').hexdigest()
if sha != entry['sha256']:
    raise SystemExit('Original APK digest mismatch; preserved')
host = work / 'probe-host-build/outputs/apk/debug/probeHost-debug.apk'
test = work / 'probe-host-build/outputs/apk/androidTest/debug/probeHost-debug-androidTest.apk'
if not host.is_file() or not test.is_file(): raise SystemExit('Build the standalone host and driver first')
inputs = {'scope': 'OFFICIAL_ANDROID11_TRANSLATION_STARTUP_ONLY_NOT_LOCATION_OR_VIVO',
    'target': entry, 'image': props,
    'artifacts': {str(x.relative_to(work)): hashlib.sha256(x.read_bytes()).hexdigest() for x in [host, test, apk]},
    'research_source_sha256': {str(x.relative_to(spec)): hashlib.sha256(x.read_bytes()).hexdigest()
        for folder in ['tools', 'probe-host', 'patches'] for x in sorted((spec / folder).rglob('*'))
        if x.is_file() and x.suffix in ['.py', '.sh', '.java', '.xml', '.gradle', '.patch']},
    'input_manifests_sha256': {str(x.relative_to(spec)): hashlib.sha256(x.read_bytes()).hexdigest()
        for x in [spec / 'sources.json', spec / 'evidence/official-target-apk-readiness.json']},
    'terms_accepted': False, 'login_attempted': False, 'messages_sent': False, 'location_enabled': False}
(evidence / 'case-inputs.json').write_text(json.dumps(inputs, indent=2) + '\n')
result = {'scope': inputs['scope'], 'baseline': 'NOT_RUN', 'container': 'NOT_RUN',
    'location_business': 'NOT_TESTED', 'accounts_used': False}
created_control = False

def capture(prefix):
    run(['shell', 'rm', '-f', '/data/local/tmp/gogogo-target-window.xml'], check=False)
    try:
        run(['shell', 'uiautomator', 'dump', '/data/local/tmp/gogogo-target-window.xml'], timeout=15, check=False)
    except subprocess.TimeoutExpired:
        return b''
    xml = run(['exec-out', 'cat', '/data/local/tmp/gogogo-target-window.xml'], check=False).stdout
    if xml.lstrip().startswith(b'<?xml'):
        (evidence / (prefix + '-window.xml')).write_bytes(xml)
    screen = run(['exec-out', 'screencap', '-p'], check=False).stdout
    if screen.startswith(b'\x89PNG\r\n\x1a\n'):
        (evidence / (prefix + '-screen.png')).write_bytes(screen)
    return xml

def anonymous_stage(text):
    s = '\n'.join(text).lower()
    if a.target == 'wechat':
        if ('log in' in s or '登录' in s) and ('sign up' in s or '注册' in s): return 'WELCOME_WITHOUT_LOGIN'
        if ('微信' in s or 'wechat' in s) and ('隐私' in s or 'privacy' in s) and ('同意' in s or 'agree' in s): return 'PRIVACY_WITHOUT_ACCEPTANCE'
    elif ('隐私' in s or 'privacy' in s) and ('同意' in s or 'agree' in s):
        return 'PRIVACY_WITHOUT_ACCEPTANCE'
    return None

try:
    shell('input keyevent KEYCODE_WAKEUP'); shell('wm dismiss-keyguard', check=False)
    run(['logcat', '-c'])
    result['baseline'] = 'INSTALL_PENDING_OR_TIMED_OUT_UNKNOWN'
    installed = run(['install', str(apk)], timeout=240, check=False)
    (evidence / 'baseline-install.txt').write_bytes(installed.stdout + installed.stderr)
    if installed.returncode or b'Success' not in installed.stdout:
        result['baseline'] = 'INSTALL_FAILED_NO_STARTUP_CONCLUSION'
        raise RuntimeError('Ordinary installed control failed; no virtual comparison')
    created_control = True
    resolved = shell('cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p ' + pkg)
    component = resolved.splitlines()[-1]
    if not re.fullmatch(re.escape(pkg) + r'/[A-Za-z0-9_.$]+', component):
        raise RuntimeError('No ordinary launcher component')
    shell('am start -n ' + component)
    deadline = time.monotonic() + 120
    stage = None; text = []
    while time.monotonic() < deadline:
        xml = capture('baseline')
        try:
            text = [x.get('text') for x in ET.fromstring(xml).iter('node')
                    if x.get('package') == pkg and x.get('text')]
        except ET.ParseError: text = []
        stage = anonymous_stage(text)
        if stage: break
        time.sleep(2)
    result['baseline'] = stage or 'STARTUP_UI_NOT_REACHED_WITHIN_BOUND_UNKNOWN_CAUSE'
    (evidence / 'baseline-visible-text.json').write_text(json.dumps(text, ensure_ascii=False, indent=2) + '\n')
    (evidence / 'baseline-crash-log.txt').write_bytes(run(['logcat', '-d', '-b', 'crash'], check=False).stdout)
    removed = run(['uninstall', pkg], timeout=120, check=False)
    created_control = not (removed.returncode == 0 and b'Success' in removed.stdout)
    if created_control or shell('pm path ' + pkg, check=False): raise RuntimeError('Owned control cleanup incomplete')
    if not stage: raise RuntimeError('No baseline UI confirmation; do not classify virtual compatibility')
    # Request the platform's supported ARM64 ABI for our unchanged multi-ABI host,
    # so host and ARM-only guest run under the same official Native Bridge.
    run(['install', '-r', '--abi', 'arm64-v8a', str(host)], timeout=180)
    host_package = shell('dumpsys package com.soozooq.tianwaitian.containerresearch')
    (evidence / 'host-package.txt').write_text(host_package + '\n')
    if 'primaryCpuAbi=arm64-v8a' not in host_package:
        raise RuntimeError('Platform did not select the ARM64 host ABI; no mixed-ISA conclusion')
    result['host_primary_cpu_abi'] = 'arm64-v8a'
    run(['install', '-r', str(test)], timeout=180)
    run(['push', str(apk), '/data/local/tmp/gogogo-official-target.apk'], timeout=180)
    run(['shell', 'run-as', 'com.soozooq.tianwaitian.containerresearch', 'rm', '-rf', 'files/target-startup-' + a.target])
    run(['logcat', '-c'])
    test_result = run(['shell', 'am', 'instrument', '-w', '-r', '-e', 'target', a.target, '-e', 'class',
        'com.soozooq.tianwaitian.containerresearch.OfficialTargetStartupInstrumentedTest#pinnedOfficialTargetColdStartup',
        'com.soozooq.tianwaitian.containerresearch.test/androidx.test.runner.AndroidJUnitRunner'], timeout=420, check=False)
    transcript = test_result.stdout + test_result.stderr
    (evidence / 'container-junit.txt').write_bytes(transcript)
    for name in ['result.json', 'window.xml', 'screen.png']:
        path = 'files/target-startup-' + a.target + '/' + name
        if run(['shell', 'run-as', 'com.soozooq.tianwaitian.containerresearch', 'test', '-f', path], check=False).returncode == 0:
            data = run(['exec-out', 'run-as', 'com.soozooq.tianwaitian.containerresearch', 'cat', path]).stdout
            (evidence / ('container-' + name)).write_bytes(data)
    (evidence / 'container-crash-log.txt').write_bytes(run(['logcat', '-d', '-b', 'crash'], check=False).stdout)
    local = evidence / 'container-result.json'
    passed = test_result.returncode == 0 and b'OK (1 test)' in transcript and b'FAILURES!!!' not in transcript \
        and local.is_file() and json.loads(local.read_text()).get('passed') is True
    result['container'] = 'ANONYMOUS_STARTUP_UI_CONFIRMED' if passed else 'STARTUP_NOT_CONFIRMED_SEE_EVIDENCE'
    result['passed'] = passed
except Exception as error:
    result['error'] = type(error).__name__ + ': ' + str(error)[:1500]
    result['passed'] = False
finally:
    try:
        if created_control or shell('pm path ' + pkg, check=False):
            run(['uninstall', pkg], timeout=120, check=False)
        result['system_package_after_cleanup'] = bool(shell('pm path ' + pkg, check=False))
    except Exception as error:
        result['system_package_after_cleanup'] = 'UNKNOWN'
        result['cleanup_error'] = type(error).__name__ + ': ' + str(error)[:1500]
        result['passed'] = False
    (evidence / 'case-result.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2))
raise SystemExit(0 if result.get('passed') else 1)
