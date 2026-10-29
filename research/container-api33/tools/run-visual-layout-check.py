#!/usr/bin/env python3
"""Capture own app screens on a dedicated API33 emulator; never a phone/Guest test."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--sdk', required=True)
parser.add_argument('--root', required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--main-apk', required=True)
args = parser.parse_args()
if not re.fullmatch(r'emulator-\d+', args.serial):
    raise SystemExit('A dedicated emulator is required')
root = Path(args.root).resolve()
out = root / 'evidence/visual-layout'
out.mkdir(exist_ok=False)
adb = [str(Path(args.sdk) / 'platform-tools/adb'), '-s', args.serial]
host = 'com.soozooq.tianwaitian.containerresearch'
main = 'com.soozooq.gogogo.test'
paths = {main: Path(args.main_apk),
         host: root / 'probe-host-build/outputs/apk/debug/probeHost-debug.apk',
         host + '.test': root / 'probe-host-build/outputs/apk/androidTest/debug/probeHost-debug-androidTest.apk'}
result = {'scope': 'SELF_OWNED_VISUAL_UI_ONLY_NOT_TARGET_LOCATION', 'passed': False}
installed = []
settings = {}
profiles = ('light-360', 'dark-360', 'light-large-320', 'dark-large-320')
stages = ('map', 'search', 'preview', 'keyboard', 'keyboard-longitude')
expected_pages = {profile + '-' + stage for profile in profiles for stage in stages}


def run(parts, timeout=30, check=True):
    return subprocess.run(adb + parts, capture_output=True, timeout=timeout, check=check)


def shell(*parts):
    return run(['shell', *parts]).stdout.decode().strip()


def package_present(package):
    probe = run(['shell', 'pm', 'path', package], check=False)
    if probe.returncode == 1 and not (probe.stdout + probe.stderr).strip():
        return False  # API33's normal response for an absent package.
    if probe.returncode != 0 or not probe.stdout.startswith(b'package:'):
        raise RuntimeError('Package presence probe failed for ' + package)
    return True


try:
    assert shell('getprop', 'ro.kernel.qemu') == '1'
    assert shell('getprop', 'sys.boot_completed') == '1'
    assert shell('getprop', 'ro.build.version.sdk') == '33'
    assert shell('id', '-u') == '2000' and shell('getenforce') == 'Enforcing'
    result['system_server_before'] = shell('pidof', 'system_server')
    for key, command in {'size': ['wm', 'size'], 'density': ['wm', 'density'],
                         'font': ['settings', 'get', 'system', 'font_scale'],
                         'night': ['settings', 'get', 'secure', 'ui_night_mode'],
                         'hardware_ime': ['settings', 'get', 'secure', 'show_ime_with_hard_keyboard']}.items():
        settings[key] = shell(*command)
    shell('settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1')
    for package, path in paths.items():
        assert path.is_file() and not package_present(package), 'Existing own install preserved'
    result['apks'] = {package: {'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                              'bytes': path.stat().st_size} for package, path in paths.items()}
    for package, path in paths.items():
        installed.append(package)
        run(['install', str(path)], timeout=180)
    shell('input', 'keyevent', 'KEYCODE_WAKEUP')
    shell('wm', 'dismiss-keyguard')
    execution = run(['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                     host + '.VisualLayoutInstrumentedTest', host + '.test/androidx.test.runner.AndroidJUnitRunner'],
                    timeout=480, check=False)
    (out / 'instrumentation.txt').write_bytes(execution.stdout + execution.stderr)
    names = shell('run-as', host, 'ls', 'files/visual-layout-evidence').splitlines()
    assert len(names) <= 2 * (len(expected_pages) + len(profiles)) + 1, 'Bounded own screenshot set'
    for name in names:
        assert re.fullmatch(r'[a-zA-Z0-9_-]+\.(json|png|xml)', name)
        (out / name).write_bytes(run(['exec-out', 'run-as', host, 'cat',
                                     'files/visual-layout-evidence/' + name]).stdout)
    observed = json.loads((out / 'result.json').read_text())
    result['observed'] = observed
    result['system_server_after'] = shell('pidof', 'system_server')
    assert execution.returncode == 0 and b'OK (1 test)' in execution.stdout
    assert observed['passed'] is True, 'Own visual instrumentation must pass'
    assert observed['expected_page_count'] == len(expected_pages) \
        and len(observed['pages']) == len(expected_pages) \
        and {page['name'] for page in observed['pages']} == expected_pages, \
        'All four profiles and five stages (including both coordinate keyboards) are required'
    assert result['system_server_before'] == result['system_server_after']
    for page in observed['pages']:
        ET.parse(out / (page['name'] + '.xml'))
        assert (out / (page['name'] + '.png')).read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
    result['passed'] = True
except Exception as error:
    result.update(error_class=type(error).__name__, error=str(error)[:1000])
finally:
    cleanup = {}
    for package in reversed(installed):
        try:
            removed = run(['uninstall', package], timeout=120, check=False)
            cleanup[package] = removed.returncode == 0 and b'Success' in removed.stdout and not package_present(package)
        except Exception:
            cleanup[package] = False
    result['owned_package_cleanup'] = cleanup
    restored = True
    try:
        for key in ['size', 'density']:
            override = re.search(r'Override (?:size|density): (\S+)', settings.get(key, ''))
            shell('wm', key, override.group(1) if override else 'reset')
        font = settings.get('font', 'null')
        shell('settings', 'delete' if font == 'null' else 'put', 'system', 'font_scale',
              *([] if font == 'null' else [font]))
        shell('cmd', 'uimode', 'night', {'1': 'no', '2': 'yes', '0': 'auto'}.get(settings.get('night'), 'auto'))
        hardware_ime = settings.get('hardware_ime', 'null')
        shell('settings', 'delete' if hardware_ime == 'null' else 'put', 'secure',
              'show_ime_with_hard_keyboard', *([] if hardware_ime == 'null' else [hardware_ime]))
    except Exception:
        restored = False
    result['display_settings_restored'] = restored
    result['passed'] = result['passed'] and all(cleanup.values()) and restored
    (out / 'runner-result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'scope': result['scope'], 'passed': result['passed'], 'error': result.get('error')}))
raise SystemExit(0 if result['passed'] else 1)
