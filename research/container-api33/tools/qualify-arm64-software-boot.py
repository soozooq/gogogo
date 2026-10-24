#!/usr/bin/env python3
"""Dedicated software ARM64 emulator health gate; never a target-app success report."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time

def critical_crash_pids(data):
    """Count native tombstone headers, never service-wait messages or repeats."""
    found = {}
    for pid, process in re.findall(
            rb'pid:\s*(\d+),[^\n]*>>>\s*(system_server|/system/bin/keystore2)\s*<<<', data):
        name = process.decode().rsplit('/', 1)[-1]
        found.setdefault(name, set()).add(int(pid))
    return {name: sorted(pids) for name, pids in found.items()}

p = argparse.ArgumentParser()
p.add_argument('--sdk', required=True)
p.add_argument('--serial', required=True)
p.add_argument('--emulator-pid', type=int, required=True)
p.add_argument('--output', required=True)
p.add_argument('--api', choices=['32', '33'], default='33')
p.add_argument('--console-log', help='This dedicated VM console log; detects native failures before ADB starts')
a = p.parse_args()
if not re.fullmatch(r'emulator-\d+', a.serial) or a.emulator_pid < 2:
    raise SystemExit('Dedicated emulator required')
out = Path(a.output)
out.mkdir(parents=True, exist_ok=False)
adb = [str(Path(a.sdk) / 'platform-tools/adb'), '-s', a.serial]
report = {'scope': 'SOFTWARE_ARM64_SYSTEM_BOOT_ONLY_NOT_WECHAT_OR_PHYSICAL_VIVO',
          'requested_api': int(a.api),
          'state': 'PENDING', 'boot_completed': False, 'passed': False,
          'system_image_modified': False, 'target_apps_tested': False}

def run(args, timeout=10):
    return subprocess.run(adb + args, capture_output=True, timeout=timeout)

def shell(args):
    r = run(['shell'] + args)
    if r.returncode:
        raise RuntimeError(r.stderr.decode(errors='replace')[-1000:])
    return r.stdout.decode(errors='replace').strip()

start = time.monotonic()
last_health = last_notice = last_console = 0
try:
    while time.monotonic() - start < 1200:
        try:
            os.kill(a.emulator_pid, 0)
        except ProcessLookupError:
            report['state'] = 'EMULATOR_EXITED_BEFORE_BOOT'
            break
        if a.console_log and time.monotonic() - last_console >= 30:
            console = Path(a.console_log)
            if console.is_file():
                with console.open('rb') as stream:
                    stream.seek(0, os.SEEK_END)
                    stream.seek(max(0, stream.tell() - 2 * 1024 * 1024))
                    data = stream.read()
                pids = critical_crash_pids(data)
                report['console_critical_crash_pids'] = pids
                if any(len(values) >= 2 for values in pids.values()):
                    (out / 'console-native-crashes.txt').write_bytes(data)
                    report['state'] = 'CRITICAL_SYSTEM_SERVICE_NATIVE_CRASH_LOOP'
                    break
            last_console = time.monotonic()
        try:
            if shell(['getprop', 'sys.boot_completed']) == '1':
                report['boot_completed'] = True
                break
            if time.monotonic() - last_health >= 30:
                crash = run(['logcat', '-d', '-b', 'crash']).stdout
                (out / 'platform-crash-log.txt').write_bytes(crash)
                pids = sorted({int(x) for x in re.findall(
                    rb'pid:\s*(\d+),[^\n]*>>>\s*system_server\s*<<<', crash)})
                report['system_server_crash_pids'] = pids
                if len(pids) >= 2:
                    report['state'] = 'SYSTEM_SERVER_NATIVE_CRASH_LOOP'
                    break
                last_health = time.monotonic()
        except (RuntimeError, subprocess.TimeoutExpired):
            pass
        if time.monotonic() - last_notice >= 30:
            print('Waiting for real ARM64 Android boot:', round(time.monotonic() - start), flush=True)
            last_notice = time.monotonic()
        time.sleep(5)
    if report['boot_completed']:
        run(['unroot'])
        deadline = time.monotonic() + 45
        while time.monotonic() < deadline:
            try:
                if shell(['id', '-u']) == '2000': break
            except (RuntimeError, subprocess.TimeoutExpired): pass
            time.sleep(1)
        props = {key: shell(['getprop', key]) for key in ['ro.build.version.sdk',
            'ro.build.fingerprint', 'ro.product.cpu.abi', 'ro.product.cpu.abilist',
            'ro.dalvik.vm.native.bridge', 'ro.kernel.qemu']}
        report.update(properties=props, shell_uid=shell(['id', '-u']),
                      selinux=shell(['getenforce']), kernel_arch=shell(['uname', '-m']))
        passed = props['ro.build.version.sdk'] == a.api and props['ro.kernel.qemu'] == '1' \
            and props['ro.product.cpu.abi'] == 'arm64-v8a' \
            and 'arm64-v8a' in props['ro.product.cpu.abilist'].split(',') \
            and not any('x86' in abi for abi in props['ro.product.cpu.abilist'].split(',')) \
            and props['ro.dalvik.vm.native.bridge'] in ['', '0'] \
            and report['shell_uid'] == '2000' and report['selinux'] == 'Enforcing' \
            and report['kernel_arch'] == 'aarch64'
        report.update(passed=passed, state='QUALIFIED_BOOT' if passed else 'BOOT_IDENTITY_REJECTED')
        (out / 'platform-crash-log.txt').write_bytes(run(['logcat', '-d', '-b', 'crash']).stdout)
    elif report['state'] == 'PENDING': report['state'] = 'BOOT_TIMEOUT'
except Exception as error:
    report.update(state='PROBE_ERROR', error=type(error).__name__ + ': ' + str(error)[:1500])
finally:
    report['observed_seconds'] = round(time.monotonic() - start, 3)
    (out / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2), flush=True)
raise SystemExit(0 if report['passed'] else 1)
