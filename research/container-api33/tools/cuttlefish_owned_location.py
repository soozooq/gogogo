"""Real self-owned Guest contracts in the already qualified, owned native ARM VM."""
import json
from pathlib import Path
import re
import subprocess

from ci_owned_apks import verify, CONSUMER_FILE
from cuttlefish_target_baselines import require_owned_qualified_vm, platform_failure, capture_container_startup_diagnostics


def run_owned_location_control(work, spec_dir, report, record, environment, own_apks, expected_system_pid):
    require_owned_qualified_vm(work, report, record)
    manifest = verify(own_apks, spec_dir, report['research_commit'])
    if 'consumer' not in manifest['apks']:
        raise RuntimeError('The independent own consumer must be in the verified same-run transport')
    out = work / 'evidence/owned-location-native'
    out.mkdir(exist_ok=False)
    return owned_contracts(work / 'host/bin/adb', report['serial'], own_apks, manifest,
                           out, environment, expected_system_pid)


def owned_contracts(adb, serial, own_apks, manifest, out, environment, expected_system_pid):
    host = 'com.soozooq.tianwaitian.containerresearch'
    driver = host + '.test'
    consumer = 'com.soozooq.tianwaitian.locationconsumer'
    staging = '/data/local/tmp/gogogo-location-consumer.apk'
    result = {'scope': 'CVD_NATIVE_ARM64_OWNED_GUEST_FRAMEWORK_AND_LOCATION_NOT_TARGET_SDK_OR_VIVO',
              'passed': False, 'cases': {}, 'third_party_accounts_used': False,
              'target_location_business_tested': False, 'consumer_system_installed': None,
              'consumer_sha256': manifest['apks']['consumer']['sha256']}
    requested, staging_requested = [], False

    def run(command, timeout=30, check=True):
        response = subprocess.run([str(adb), '-s', serial, *command], capture_output=True,
                                  timeout=timeout, env=environment)
        if check and response.returncode:
            raise RuntimeError('Owned contract ADB failed')
        return response

    def identity():
        values = {name: run(['shell', *command]).stdout.decode().strip() for name, command in [
            ('uid', ['id', '-u']), ('selinux', ['getenforce']),
            ('boot', ['getprop', 'sys.boot_completed']), ('api', ['getprop', 'ro.build.version.sdk']),
            ('abi', ['getprop', 'ro.product.cpu.abi']),
            ('bridge', ['getprop', 'ro.dalvik.vm.native.bridge']),
            ('system_pid', ['pidof', 'system_server'])]}
        if values['uid'] != '2000' or values['selinux'] != 'Enforcing' or values['boot'] != '1' \
                or values['api'] != '33' or values['abi'] != 'arm64-v8a' or values['bridge'] not in ['', '0'] \
                or not re.fullmatch(r'[1-9][0-9]*', str(expected_system_pid)) \
                or values['system_pid'] != expected_system_pid:
            raise RuntimeError('Owned location requires the same qualified native system')
        if platform_failure(run(['logcat', '-d', '-b', 'crash'], check=False).stdout):
            raise RuntimeError('System process failure invalidates own contract evidence')
        return values

    try:
        result['environment_before'] = identity()
        for package in [host, driver, consumer]:
            if run(['shell', 'pm', 'path', package], check=False).stdout.strip():
                if package == consumer: result['consumer_system_installed'] = True
                raise RuntimeError('Existing system package preserved: ' + package)
        result['consumer_system_installed'] = False
        if run(['shell', 'test', '-e', staging], check=False).returncode == 0:
            raise RuntimeError('Existing staging file preserved')
        for package, name in [(host, 'probeHost-debug.apk'), (driver, 'probeHost-debug-androidTest.apk')]:
            requested.append(package)
            arguments = ['install', '--abi', 'arm64-v8a'] if package == host else ['install']
            run([*arguments, str(own_apks / name)], timeout=240)
        package_state = run(['shell', 'dumpsys', 'package', host]).stdout
        (out / 'host-package.txt').write_bytes(package_state)
        if b'primaryCpuAbi=arm64-v8a' not in package_state:
            raise RuntimeError('Actual host ARM64 ABI was not selected')
        staging_requested = True
        run(['push', '-Z', str(own_apks / CONSUMER_FILE), staging], timeout=120)
        cases = [('framework', 'StartupFrameworkInstrumentedTest#ownGuestResolvesLegacyHttpAndCreatesPendingIntents',
                  'guest_sha256', 'startup-framework-evidence'),
                 ('location', 'GuestLocationInstrumentedTest#publicLocationApisInUninstalledGuestFollowTargetAndStop',
                  'consumer_sha256', 'guest-location-evidence')]
        for name, method, argument, folder in cases:
            identity()
            case_out = out / name
            case_out.mkdir()
            command = ['shell', 'am', 'instrument', '-w', '-r', '-e', argument,
                manifest['apks']['consumer']['sha256'], '-e', 'class',
                'com.soozooq.tianwaitian.containerresearch.' + method,
                driver + '/androidx.test.runner.AndroidJUnitRunner']
            try:
                completed = run(command, timeout=1200, check=False)
            except subprocess.TimeoutExpired as error:
                (case_out / 'junit.txt').write_bytes((error.stdout or b'') + (error.stderr or b'')
                                                    + b'\nINSTRUMENTATION_TIMEOUT\n')
                raise
            transcript = completed.stdout + completed.stderr
            (case_out / 'junit.txt').write_bytes(transcript)
            observed = None
            for filename in ['result.json', 'window.xml', 'screen.png', 'last-guest-ui.txt']:
                path = 'files/' + folder + '/' + filename
                if run(['shell', 'run-as', host, 'test', '-f', path], check=False).returncode == 0:
                    data = run(['exec-out', 'run-as', host, 'cat', path]).stdout
                    (case_out / filename).write_bytes(data)
                    if filename == 'result.json': observed = json.loads(data)
            case_passed = completed.returncode == 0 and b'OK (1 test)' in transcript \
                and b'FAILURES!!!' not in transcript and b'INSTRUMENTATION_FAILED' not in transcript \
                and isinstance(observed, dict) and observed.get('passed') is True
            if case_passed and name == 'location':
                case_passed = observed.get('scope') == 'OWNED_GUEST_REAL_PROCESS_NOT_THIRD_PARTY_OR_WECHAT' \
                    and observed.get('consumer_sha256') == manifest['apks']['consumer']['sha256'] \
                    and observed.get('host_pid') != observed.get('guest_pid') \
                    and {s.get('name') for s in observed.get('stages', [])} == {
                        'TARGET_A', 'TARGET_B', 'UNREGISTERED_STABLE', 'CURRENT_CANCELLED'}
            if case_passed and name == 'framework':
                case_passed = observed.get('scope') == 'OWN_GUEST_FRAMEWORK_STARTUP_ONLY_NOT_TARGET_APP_OR_LOCATION' \
                    and observed.get('provider_call_nonce_matches') is True \
                    and observed.get('provider_process_separate') is True
            case = {'passed': case_passed, 'observed': observed, 'environment_after': identity()}
            capture_container_startup_diagnostics(run, case_out, case)
            try:
                response = run(['logcat', '-d', '-b', 'crash'], check=False)
                crash = re.sub(rb'https?://[^\s]+', b'[URL_REDACTED]', response.stdout)
                (case_out / 'crash-log.txt').write_bytes(crash[:2097152])
                case['crash_capture'] = {'available': response.returncode == 0,
                    'bytes': min(len(crash), 2097152), 'truncated': len(crash) > 2097152}
            except Exception as error:
                case['crash_capture'] = {'available': False, 'error_class': type(error).__name__}
            result['cases'][name] = case
        result['consumer_system_installed'] = bool(run(['shell', 'pm', 'path', consumer], check=False).stdout.strip())
        result['passed'] = all(c['passed'] for c in result['cases'].values()) \
            and len(result['cases']) == 2 and not result['consumer_system_installed']
    except Exception as error:
        result['error'] = type(error).__name__ + ': ' + str(error)[:1000]
    finally:
        if staging_requested:
            try:
                result['own_staging_removed'] = run(['shell', 'rm', '-f', staging], check=False).returncode == 0
            except Exception:
                result['own_staging_removed'] = False
            if not result['own_staging_removed']: result['passed'] = False
        cleanup = result['owned_package_cleanup'] = {}
        for package in reversed(requested):
            try:
                run(['uninstall', package], timeout=90, check=False)
                cleanup[package] = not run(['shell', 'pm', 'path', package], check=False).stdout.strip()
            except Exception:
                cleanup[package] = False
            if not cleanup[package]: result['passed'] = False
        (out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    return result
