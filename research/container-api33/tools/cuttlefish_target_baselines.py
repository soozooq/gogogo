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


def platform_failure(crash):
    """A system-process crash invalidates this environment, not the target APK."""
    if b'FATAL EXCEPTION IN SYSTEM PROCESS' in crash:
        return 'SYSTEM_PROCESS_JAVA_FATAL'
    if re.search(rb'>>>\s*system_server\s*<<<', crash):
        return 'SYSTEM_PROCESS_NATIVE_FATAL'
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


def staged_original_install(run, apk, entry, out, result):
    """Measure transfer separately and retain ownership of one installer session."""
    install = result['installation'] = {'phases': {}, 'commit_requested': False, 'commit_confirmed': False}
    directory = session = None
    deadline = time.monotonic() + 1500

    def phase(name, arguments, timeout):
        install['phase'] = name
        started = time.monotonic()
        print('Original install phase:', entry['source_key'], name, flush=True)
        try:
            remaining = deadline - started
            if remaining <= 0:
                raise subprocess.TimeoutExpired(arguments, 0)
            response = run(arguments, timeout=min(timeout, remaining), check=False)
            (out / ('install-' + name.lower() + '.txt')).write_bytes(response.stdout + response.stderr)
            install['phases'][name] = {'seconds': round(time.monotonic() - started, 3),
                                       'returncode': response.returncode}
            if response.returncode:
                raise RuntimeError('Original install phase failed: ' + name)
            return response
        except subprocess.TimeoutExpired as error:
            install['failed_phase'] = name
            install['phases'][name] = {'seconds': round(time.monotonic() - started, 3), 'timed_out': True}
            (out / ('install-' + name.lower() + '.txt')).write_bytes(
                (error.stdout or b'') + (error.stderr or b'') + b'\nCOMMAND_TIMEOUT\n')
            raise
        except Exception:
            install['failed_phase'] = name
            raise

    def success(response, name):
        if b'Success' not in response.stdout:
            install['failed_phase'] = name
            raise RuntimeError('Original installer did not confirm ' + name)

    try:
        prefix = '/data/local/tmp/gogogo-original-' + entry['source_key'] + '-'
        made = phase('STAGING_DIRECTORY', ['shell', 'mktemp', '-d', prefix + 'XXXXXX'], 30)
        candidate = made.stdout.decode().strip()
        if not re.fullmatch(re.escape(prefix) + r'[A-Za-z0-9]{6,20}', candidate):
            raise RuntimeError('No valid owned staging directory; no path removed')
        directory = candidate
        remote = directory + '/base.apk'
        phase('TRANSFER', ['push', '-Z', str(apk), remote], 600)
        checksum = phase('REMOTE_SHA256', ['shell', 'sha256sum', remote], 180)
        checksum_fields = checksum.stdout.decode().split()
        if not checksum_fields or checksum_fields[0] != entry['sha256']:
            install['failed_phase'] = 'REMOTE_SHA256'
            raise RuntimeError('Transferred original differs; no installer session committed')
        created = phase('SESSION_CREATE', ['shell', 'pm', 'install-create', '--user', '0',
                        '--abi', 'arm64-v8a', '-S', str(entry['size'])], 120)
        parsed = re.search(rb'Success: created install session \[(\d+)\]', created.stdout)
        if parsed is None:
            raise RuntimeError('Installer did not return an owned session ID')
        session_id = int(parsed[1])
        if not 0 < session_id < 2**31:
            raise RuntimeError('Installer session ID is outside its platform range')
        session = str(session_id)
        install['owned_session_id'] = session
        written = phase('SESSION_WRITE', ['shell', 'pm', 'install-write', '-S', str(entry['size']),
                                         session, 'base.apk', remote], 600)
        success(written, 'SESSION_WRITE')
        install['commit_requested'] = True
        committed = phase('SESSION_COMMIT', ['shell', 'pm', 'install-commit', session], 900)
        success(committed, 'SESSION_COMMIT')
        install['commit_confirmed'] = True
    except Exception:
        install.setdefault('failed_phase', install.get('phase', 'UNKNOWN'))
        raise
    finally:
        if session is not None and not install['commit_confirmed']:
            try:
                abandoned = run(['shell', 'pm', 'install-abandon', session], timeout=120, check=False)
                (out / 'install-session-abandon.txt').write_bytes(abandoned.stdout + abandoned.stderr)
                install['owned_session_abandon_confirmed'] = abandoned.returncode == 0 and b'Success' in abandoned.stdout
            except Exception as error:
                install['session_cleanup_error'] = type(error).__name__ + ': ' + str(error)[:500]
        if directory is not None:
            try:
                removed = run(['shell', 'rm', '-rf', directory], timeout=30, check=False)
                install['owned_staging_remove_returned'] = removed.returncode == 0
            except Exception as error:
                install['staging_cleanup_error'] = type(error).__name__ + ': ' + str(error)[:500]


def installed_baseline(adb, serial, apk, entry, out, environment, expected_properties):
    """A fresh system package control; never import a container or click app UI."""
    out.mkdir(parents=True, exist_ok=False)
    target, package = entry['source_key'], entry['package']
    result = {'scope': 'ORIGINAL_INSTALLED_ANONYMOUS_UI_ONLY_NOT_CONTAINER_LOCATION_OR_VIVO',
              'target': target, 'package': package, 'apk_sha256': digest(apk),
              'passed': False, 'state': 'NOT_RUN', 'launch_requested': False,
              'terms_accepted': False, 'login_attempted': False, 'messages_sent': False,
              'location_business_tested': False, 'container_tested': False}
    result['startup_ui_wait_limit_seconds'] = 300

    def run(args, timeout=30, check=True):
        response = subprocess.run([str(adb), '-s', serial, *args], capture_output=True, timeout=timeout,
                                  env=environment)
        if check and response.returncode:
            raise RuntimeError('ADB failed: ' + response.stderr.decode(errors='replace')[-1000:])
        return response

    def shell(args, **kwargs):
        return run(['shell', *args], **kwargs).stdout.decode(errors='replace').strip()

    def check_system_process(stage):
        current = shell(['pidof', 'system_server'], check=False)
        result.setdefault('system_process_checkpoints', {})[stage] = current
        expected = result.get('system_server_pid_at_start')
        if not re.fullmatch(r'[1-9][0-9]*', current) or expected and current != expected:
            result['passed'] = False
            result['platform_failure'] = 'SYSTEM_PROCESS_MISSING_OR_CHANGED'
            result['state'] = 'ENVIRONMENT_SYSTEM_PROCESS_MISSING_OR_CHANGED'
            raise RuntimeError('System process changed; no target compatibility conclusion')
        return current

    def read_crash():
        crash = run(['logcat', '-d', '-b', 'crash'], check=False).stdout
        (out / 'crash-log.txt').write_bytes(crash)
        failure = platform_failure(crash)
        if failure:
            result.update(passed=False, platform_failure=failure, state='ENVIRONMENT_' + failure)
        return crash

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
        result['system_server_pid_at_start'] = check_system_process('BEFORE_INSTALL')
        if shell(['pm', 'path', package], check=False):
            result['state'] = 'PREEXISTING_SYSTEM_PACKAGE_PRESERVED'
            raise RuntimeError('Fresh package absence required; existing app preserved')
        shell(['input', 'keyevent', 'KEYCODE_WAKEUP'])
        shell(['wm', 'dismiss-keyguard'], check=False)
        unlock_deadline = time.monotonic() + 300
        while time.monotonic() < unlock_deadline:
            user_state = shell(['am', 'get-started-user-state', '0'])
            result['user_storage_state_before_install'] = user_state
            if user_state == 'RUNNING_UNLOCKED':
                break
            time.sleep(3)
        else:
            result['state'] = 'USER_STORAGE_NOT_UNLOCKED_WITHIN_BOUND'
            raise RuntimeError('Wait for ordinary user storage before installing an original')
        run(['logcat', '-c'])
        result['state'] = 'INSTALL_STAGING_REQUESTED'
        staged_original_install(run, apk, entry, out, result)
        result['state'] = 'INSTALL_CONFIRMED'
        read_crash()
        if result.get('platform_failure'):
            raise RuntimeError('System process failed during installation')
        check_system_process('AFTER_INSTALL')
        component = shell(['cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN',
                           '-c', 'android.intent.category.LAUNCHER', '-p', package]).splitlines()[-1]
        if not re.fullmatch(re.escape(package) + r'/[A-Za-z0-9_.$]+', component):
            raise RuntimeError('No ordinary launcher component')
        started = run(['shell', 'am', 'start', '-n', component])
        (out / 'start.txt').write_bytes(started.stdout + started.stderr)
        result['launch_requested'] = True
        deadline = time.monotonic() + result['startup_ui_wait_limit_seconds']
        while time.monotonic() < deadline:
            crash = read_crash()
            if result.get('platform_failure'):
                break
            check_system_process('DURING_UI_WAIT')
            texts = capture()
            (out / 'visible-text.json').write_text(json.dumps(texts, ensure_ascii=False, indent=2) + '\n')
            stage = anonymous_stage(target, texts)
            if stage:
                result.update(state=stage, passed=True)
                break
            if re.search(rb'>>> ' + re.escape(package.encode()) + rb'(?::[^\s]+)? <<<', crash) and b'signal ' in crash:
                result['state'] = 'ORIGINAL_SYSTEM_CONTROL_NATIVE_CRASH'
                break
            time.sleep(2)
        else:
            result['state'] = 'STARTUP_UI_NOT_REACHED_WITHIN_BOUND_UNKNOWN_CAUSE'
        if result['passed']:
            check_system_process('AFTER_UI')
    except Exception as error:
        result['passed'] = False
        result['error'] = type(error).__name__ + ': ' + str(error)[:1500]
        installation = result.get('installation', {})
        if installation.get('failed_phase') and not result.get('platform_failure'):
            result['state'] = 'INSTALL_' + installation['failed_phase'] \
                + ('_TIMEOUT' if isinstance(error, subprocess.TimeoutExpired) else '_FAILED')
    finally:
        if result.get('installation'):
            try:
                read_crash()
            except Exception as error:
                result['crash_read_error'] = type(error).__name__
        if result.get('installation', {}).get('commit_requested'):
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


def container_startup(adb, serial, apk, entry, own_apks, out, environment, expected_system_pid=None):
    """Use the existing own instrumentation; no account/UI actions are added."""
    out.mkdir(parents=True, exist_ok=False)
    host_package = 'com.soozooq.tianwaitian.containerresearch'
    driver_package = host_package + '.test'
    target, package = entry['source_key'], entry['package']
    result = {'scope': 'CVD_ARM64_ANONYMOUS_CONTAINER_STARTUP_ONLY_NOT_LOCATION_OR_VIVO',
              'target': target, 'passed': False, 'state': 'NOT_RUN',
              'terms_accepted': False, 'login_attempted': False,
              'messages_sent': False, 'location_enabled': False}
    requested_packages = []

    def run(args, timeout=30, check=True):
        response = subprocess.run([str(adb), '-s', serial, *args], capture_output=True,
                                  timeout=timeout, env=environment)
        if check and response.returncode:
            raise RuntimeError('Container driver ADB failed: ' + response.stderr.decode(errors='replace')[-1000:])
        return response

    try:
        identity = {name: run(['shell', *command], check=False).stdout.decode().strip()
                    for name, command in [('uid', ['id', '-u']), ('selinux', ['getenforce']),
                                          ('boot', ['getprop', 'sys.boot_completed']),
                                          ('system_pid', ['pidof', 'system_server'])]}
        result['environment_before_container'] = identity
        crash = run(['logcat', '-d', '-b', 'crash'], check=False).stdout
        failure = platform_failure(crash)
        if failure or identity['uid'] != '2000' or identity['selinux'] != 'Enforcing' \
                or identity['boot'] != '1' or not re.fullmatch(r'[1-9][0-9]*', identity['system_pid']) \
                or (expected_system_pid is not None and identity['system_pid'] != expected_system_pid):
            result['state'] = 'ENVIRONMENT_' + (failure or 'CURRENT_SYSTEM_NOT_QUALIFIED')
            raise RuntimeError('Current system must still match the successful original control')
        for candidate in [package, host_package, driver_package]:
            if run(['shell', 'pm', 'path', candidate], check=False).stdout.strip():
                raise RuntimeError('Existing system package preserved: ' + candidate)
        requested_packages.append(host_package)
        run(['install', '--abi', 'arm64-v8a', str(own_apks / 'probeHost-debug.apk')], timeout=240)
        package_state = run(['shell', 'dumpsys', 'package', host_package]).stdout
        (out / 'host-package.txt').write_bytes(package_state)
        if b'primaryCpuAbi=arm64-v8a' not in package_state:
            raise RuntimeError('Platform did not select the actual ARM64 host ABI')
        requested_packages.append(driver_package)
        run(['install', str(own_apks / 'probeHost-debug-androidTest.apk')], timeout=240)
        run(['push', '-Z', str(apk), '/data/local/tmp/gogogo-official-target.apk'], timeout=600)
        run(['logcat', '-c'])
        result['state'] = 'INSTRUMENTATION_REQUESTED'
        instrument = run(['shell', 'am', 'instrument', '-w', '-r', '-e', 'target', target,
            '-e', 'api', '33', '-e', 'runtime', 'cuttlefish-arm64', '-e', 'class',
            'com.soozooq.tianwaitian.containerresearch.OfficialTargetStartupInstrumentedTest#pinnedOfficialTargetColdStartup',
            driver_package + '/androidx.test.runner.AndroidJUnitRunner'], timeout=900, check=False)
        transcript = instrument.stdout + instrument.stderr
        (out / 'junit.txt').write_bytes(transcript)
        observed = None
        for name in ['result.json', 'window.xml', 'screen.png']:
            path = 'files/target-startup-' + target + '/' + name
            exists = run(['shell', 'run-as', host_package, 'test', '-f', path], check=False)
            if exists.returncode == 0:
                data = run(['exec-out', 'run-as', host_package, 'cat', path]).stdout
                (out / name).write_bytes(data)
                if name == 'result.json':
                    observed = json.loads(data)
        crash = run(['logcat', '-d', '-b', 'crash'], check=False).stdout
        (out / 'crash-log.txt').write_bytes(crash)
        after = {'system_pid': run(['shell', 'pidof', 'system_server'], check=False).stdout.decode().strip(),
                 'boot': run(['shell', 'getprop', 'sys.boot_completed'], check=False).stdout.decode().strip()}
        result['environment_after_container'] = after
        failure = platform_failure(crash)
        stable = failure is None and after['system_pid'] == identity['system_pid'] and after['boot'] == '1'
        passed = stable and instrument.returncode == 0 and b'OK (1 test)' in transcript \
            and b'FAILURES!!!' not in transcript and b'INSTRUMENTATION_FAILED' not in transcript \
            and isinstance(observed, dict) and observed.get('passed') is True \
            and observed.get('runtime') == 'cuttlefish-arm64' \
            and observed.get('package') == package and observed.get('sha256') == entry['sha256'] \
            and observed.get('original_signer') is True \
            and observed.get('location_enabled') is False
        state = 'ANONYMOUS_CONTAINER_UI_CONFIRMED' if passed else 'CONTAINER_STARTUP_NOT_CONFIRMED_SEE_EVIDENCE'
        if not stable:
            state = 'ENVIRONMENT_' + (failure or 'SYSTEM_CHANGED_AFTER_INSTRUMENTATION')
        result.update(passed=passed, state=state, observed=observed)
    except Exception as error:
        result['error'] = type(error).__name__ + ': ' + str(error)[:1500]
    finally:
        cleanup = {}
        for candidate in reversed(requested_packages):
            try:
                removed = run(['uninstall', candidate], timeout=120, check=False)
                remaining = run(['shell', 'pm', 'path', candidate], check=False).stdout.strip()
                cleanup[candidate] = removed.returncode == 0 and b'Success' in removed.stdout and not remaining
            except Exception:
                cleanup[candidate] = False
        result['owned_package_cleanup'] = cleanup
        if not all(cleanup.values()):
            result['passed'] = False
        (out / 'control-result.json').write_text(json.dumps(result, indent=2) + '\n')
    return result


def run_container_control(work, spec_dir, report, record, environment, own_apks, key, baseline):
    """Immediately follow one successful ordinary control, before another target."""
    if key not in ['wechat', 'douyin']:
        raise ValueError('Only pinned target controls are admitted')
    if baseline.get('passed') is not True:
        return {'passed': False, 'state': 'SKIPPED_ORIGINAL_SYSTEM_CONTROL_NOT_CONFIRMED'}
    if not re.fullmatch(r'[1-9][0-9]*', str(baseline.get('system_server_pid_at_start', ''))):
        return {'passed': False, 'state': 'SKIPPED_ORIGINAL_SYSTEM_IDENTITY_NOT_RECORDED'}
    try:
        from ci_owned_apks import verify
        require_owned_qualified_vm(work, report, record)
        verify(own_apks, spec_dir, report['research_commit'])
        pins = json.loads((spec_dir / 'evidence/official-target-apk-readiness.json').read_text())
        out = work / 'evidence/container-target-startups'
        out.mkdir(exist_ok=True)
        entry = next(x for x in pins['targets'] if x['source_key'] == key)
        apk = original_input(work, entry, pins['source'][key]['url'], environment)
        result = container_startup(work / 'host/bin/adb', report['serial'], apk, entry, own_apks,
                                   out / key, environment, baseline.get('system_server_pid_at_start'))
    except Exception as error:
        result = {'passed': False, 'state': 'PREPARATION_FAILED_NO_CONTAINER_CONCLUSION',
                  'error': type(error).__name__ + ': ' + str(error)[:1500]}
    print('Anonymous container startup:', key, result['state'], flush=True)
    return result


def run_original_baselines(work, spec_dir, report, record, environment, after_target=None):
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
        if after_target is not None:
            after_target(key, result)
    summary = {'scope': 'ORIGINAL_INSTALLED_STARTUP_ONLY_NOT_CONTAINER_OR_LOCATION',
               'results': results, 'passed': all(x.get('passed') is True for x in results.values()),
               'input_manifest_sha256': digest(spec_dir / 'evidence/official-target-apk-readiness.json'),
               'apk_uploaded_or_modified': False, 'accounts_used': False,
               'network_scope': 'No DHCP/NAT or Internet/Wi-Fi/mobile-data success was established'}
    (out / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    return summary
