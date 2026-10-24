"""Installed original startup on the boot driver's owned, qualified ARM VM only."""
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def require_owned_qualified_vm(work, report, record):
    temporary = Path(os.environ['RUNNER_TEMP']).resolve()
    if os.environ.get('GITHUB_ACTIONS') != 'true' or platform.system() != 'Linux' \
            or platform.machine() not in ['aarch64', 'arm64'] \
            or os.getuid() == 0 or not work.resolve().is_relative_to(temporary):
        raise RuntimeError('Use the ordinary native ARM ephemeral runner and its dedicated VM')
    if report.get('passed') is not True or report.get('state') != 'QUALIFIED_BOOT' \
            or report.get('boot_completed') is not True \
            or report.get('serial') != '127.0.0.1:6520' \
            or report.get('host_report', {}).get('uid') != os.getuid():
        raise RuntimeError('Current system qualification is required before any app action')
    process = Path('/proc') / str(record['pid'])
    binary = Path('/usr/bin/qemu-system-aarch64').resolve()
    if (process / 'exe').resolve(strict=True) != binary \
            or (process / 'cmdline').read_bytes().split(b'\0')[:-1] != \
                [record['binary'].encode()] + [x.encode() for x in record['effective_arguments']] \
            or (process / 'stat').read_text().rsplit(')', 1)[1].split()[0] == 'Z' \
            or digest(binary) != report['host_report']['qemu']['sha256']:
        raise RuntimeError('The qualified owned QEMU is no longer the same live process')


def anonymous_stage(target, texts):
    text = '\n'.join(texts).lower()
    privacy = ('隐私' in text or 'privacy' in text) and ('同意' in text or 'agree' in text)
    if target == 'wechat':
        if ('登录' in text or 'log in' in text) and ('注册' in text or 'sign up' in text):
            return 'WELCOME_WITHOUT_LOGIN'
        if privacy and ('微信' in text or 'wechat' in text):
            return 'PRIVACY_WITHOUT_ACCEPTANCE'
    elif target == 'douyin' and privacy:
        return 'PRIVACY_WITHOUT_ACCEPTANCE'
    return None


def original_input(work, entry, url, environment):
    names = {'wechat': 'weixin-8.0.79-arm64.apk', 'douyin': 'douyin-40.8.0.apk'}
    directory = work / 'targets'
    directory.mkdir(exist_ok=True)
    target = directory / names[entry['source_key']]

    def approved(path):
        return path.is_file() and path.stat().st_size == entry['size'] and digest(path) == entry['sha256']

    if target.exists():
        if not approved(target):
            raise RuntimeError('Existing original differs; preserved without overwriting')
        return target
    with tempfile.NamedTemporaryFile(dir=directory, prefix='original-', delete=False) as stream:
        partial = Path(stream.name)
    try:
        subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error', '--retry', '2',
                        '--max-time', '180', '--max-filesize', str(entry['size']), '--output', str(partial), url],
                       check=True, capture_output=True, timeout=550, env=environment)
        if not approved(partial):
            raise RuntimeError('Official original size/SHA-256 changed; no APK installed')
        # Do not overwrite another input if it appeared during the download.
        os.link(partial, target)
    finally:
        partial.unlink(missing_ok=True)
    return target


def installed_baseline(adb, serial, apk, entry, out, environment, expected_properties):
    """A fresh system package control; never import a container or click app UI."""
    out.mkdir(parents=True, exist_ok=False)
    target, package = entry['source_key'], entry['package']
    result = {'scope': 'ORIGINAL_INSTALLED_ANONYMOUS_UI_ONLY_NOT_CONTAINER_LOCATION_OR_VIVO',
              'target': target, 'package': package, 'apk_sha256': digest(apk),
              'passed': False, 'state': 'NOT_RUN', 'launch_requested': False,
              'terms_accepted': False, 'login_attempted': False, 'messages_sent': False,
              'location_business_tested': False, 'container_tested': False}
    install_requested = False

    def run(args, timeout=30, check=True):
        response = subprocess.run([str(adb), '-s', serial, *args], capture_output=True, timeout=timeout,
                                  env=environment)
        if check and response.returncode:
            raise RuntimeError('ADB failed: ' + response.stderr.decode(errors='replace')[-1000:])
        return response

    def shell(args, **kwargs):
        return run(['shell', *args], **kwargs).stdout.decode(errors='replace').strip()

    def capture():
        path = '/data/local/tmp/gogogo-original-window.xml'
        run(['shell', 'rm', '-f', path])
        try:
            dumped = run(['shell', 'uiautomator', 'dump', path], timeout=20, check=False)
            (out / 'ui-dump.txt').write_bytes(dumped.stdout + dumped.stderr)
            xml = run(['exec-out', 'cat', path], check=False).stdout
        except subprocess.TimeoutExpired:
            (out / 'ui-dump.txt').write_text('UI dump timed out; no UI confirmation\n')
            xml = b''
        if xml.lstrip().startswith(b'<?xml'):
            (out / 'window.xml').write_bytes(xml)
        screen = run(['exec-out', 'screencap', '-p'], check=False).stdout
        if screen.startswith(b'\x89PNG\r\n\x1a\n'):
            (out / 'screen.png').write_bytes(screen)
        try:
            return [node.get('text') for node in ET.fromstring(xml).iter('node')
                    if node.get('package') == package and node.get('text')]
        except ET.ParseError:
            return []

    try:
        if apk.stat().st_size != entry['size'] or result['apk_sha256'] != entry['sha256']:
            raise RuntimeError('Pinned original input mismatch')
        if shell(['id', '-u']) != '2000' or shell(['getenforce']) != 'Enforcing':
            raise RuntimeError('Ordinary enforcing shell required')
        if shell(['getprop', 'sys.boot_completed']) != '1':
            raise RuntimeError('System is no longer fully booted')
        for name, expected in expected_properties.items():
            if shell(['getprop', name]) != expected:
                raise RuntimeError('Current ADB system identity differs from qualification: ' + name)
        if shell(['uname', '-m']) != 'aarch64':
            raise RuntimeError('Current ADB kernel is not ARM64')
        if shell(['pm', 'path', package], check=False):
            result['state'] = 'PREEXISTING_SYSTEM_PACKAGE_PRESERVED'
            raise RuntimeError('Fresh package absence required; existing app preserved')
        shell(['input', 'keyevent', 'KEYCODE_WAKEUP'])
        shell(['wm', 'dismiss-keyguard'], check=False)
        run(['logcat', '-c'])
        # Include timed-out install attempts in cleanup: the package may appear later.
        install_requested = True
        result['state'] = 'INSTALL_REQUESTED'
        installed = run(['install', str(apk)], timeout=180, check=False)
        (out / 'install.txt').write_bytes(installed.stdout + installed.stderr)
        if installed.returncode or b'Success' not in installed.stdout:
            result['state'] = 'INSTALL_FAILED_OR_UNCONFIRMED'
            raise RuntimeError('No original startup conclusion after an unconfirmed install')
        component = shell(['cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN',
                           '-c', 'android.intent.category.LAUNCHER', '-p', package]).splitlines()[-1]
        if not re.fullmatch(re.escape(package) + r'/[A-Za-z0-9_.$]+', component):
            raise RuntimeError('No ordinary launcher component')
        started = run(['shell', 'am', 'start', '-n', component])
        (out / 'start.txt').write_bytes(started.stdout + started.stderr)
        result['launch_requested'] = True
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            texts = capture()
            (out / 'visible-text.json').write_text(json.dumps(texts, ensure_ascii=False, indent=2) + '\n')
            stage = anonymous_stage(target, texts)
            crash = run(['logcat', '-d', '-b', 'crash'], check=False).stdout
            (out / 'crash-log.txt').write_bytes(crash)
            if stage:
                result.update(state=stage, passed=True)
                break
            if re.search(rb'>>> ' + re.escape(package.encode()) + rb'(?::[^\s]+)? <<<', crash) and b'signal ' in crash:
                result['state'] = 'ORIGINAL_SYSTEM_CONTROL_NATIVE_CRASH'
                break
            time.sleep(2)
        else:
            result['state'] = 'STARTUP_UI_NOT_REACHED_WITHIN_BOUND_UNKNOWN_CAUSE'
    except Exception as error:
        result['error'] = type(error).__name__ + ': ' + str(error)[:1500]
    finally:
        if install_requested:
            try:
                removed = run(['uninstall', package], timeout=120, check=False)
                (out / 'cleanup.txt').write_bytes(removed.stdout + removed.stderr)
                remaining = shell(['pm', 'path', package], check=False)
                result['owned_package_removed'] = removed.returncode == 0 and b'Success' in removed.stdout and not remaining
                if not result['owned_package_removed']:
                    result['passed'] = False
            except Exception as error:
                result.update(passed=False, cleanup_error=type(error).__name__ + ': ' + str(error)[:1000])
        (out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    return result


def run_original_baselines(work, spec_dir, report, record, environment):
    require_owned_qualified_vm(work, report, record)
    pins = json.loads((spec_dir / 'evidence/official-target-apk-readiness.json').read_text())
    out = work / 'evidence/original-target-baselines'
    out.mkdir(exist_ok=False)
    results = {}
    for key in ['wechat', 'douyin']:
        try:
            require_owned_qualified_vm(work, report, record)
            entry = next(x for x in pins['targets'] if x['source_key'] == key)
            apk = original_input(work, entry, pins['source'][key]['url'], environment)
            result = installed_baseline(work / 'host/bin/adb', report['serial'], apk, entry, out / key,
                                        environment, report['properties'])
        except Exception as error:
            result = {'target': key, 'passed': False, 'launch_requested': False,
                      'state': 'PREPARATION_FAILED_NO_STARTUP_CONCLUSION',
                      'error': type(error).__name__ + ': ' + str(error)[:1500]}
        results[key] = result
        print('Original installed startup:', key, result['state'], flush=True)
    summary = {'scope': 'ORIGINAL_INSTALLED_STARTUP_ONLY_NOT_CONTAINER_OR_LOCATION',
               'results': results, 'passed': all(x.get('passed') is True for x in results.values()),
               'input_manifest_sha256': digest(spec_dir / 'evidence/official-target-apk-readiness.json'),
               'apk_uploaded_or_modified': False, 'accounts_used': False,
               'network_scope': 'No DHCP/NAT or Internet/Wi-Fi/mobile-data success was established'}
    (out / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    return summary
