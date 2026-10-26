"""Controlled ADB fixtures, never an actual Android or target-app result."""
import hashlib
import itertools
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


module_spec = importlib.util.spec_from_file_location('baselines',
    Path(__file__).resolve().parents[1] / 'tools/cuttlefish_target_baselines.py')
baselines = importlib.util.module_from_spec(module_spec)
module_spec.loader.exec_module(baselines)


class OriginalBaselineControlTest(unittest.TestCase):
    def test_startup_diagnostics_are_bounded_redacted_and_do_not_change_ui_result(self):
        calls = []
        def read(command, timeout, check):
            calls.append(command)
            self.assertEqual(30, timeout); self.assertFalse(check)
            if command[:4] == ['shell', 'dumpsys', 'activity', 'activities']:
                raise subprocess.TimeoutExpired(command, timeout)
            data = b'framework fixture'
            if command[0] == 'logcat':
                data = b'NativeCore: https://example.invalid/path?secret=fixture\n' + b'x' * (2 * 1024 * 1024 + 10)
            return subprocess.CompletedProcess(command, 0, data, b'')
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder); result = {'passed': True}
            baselines.capture_container_startup_diagnostics(read, out, result)
            self.assertTrue(result['passed'])
            self.assertEqual('TimeoutExpired', result['startup_diagnostics']['activities.txt']['error_class'])
            self.assertTrue(result['startup_diagnostics']['startup-framework-log.txt']['truncated'])
            data = (out / 'startup-framework-log.txt').read_bytes()
            self.assertEqual(2 * 1024 * 1024, len(data))
            self.assertIn(b'[URL_REDACTED]', data); self.assertNotIn(b'secret=fixture', data)
            self.assertTrue(all(c[0] == 'logcat' or c[:2] in [['shell', 'dumpsys'], ['shell', 'ps']]
                                for c in calls))
            log = next(c for c in calls if c[0] == 'logcat')
            self.assertIn('System.err:W', log)  # Caught startup exceptions use printStackTrace.
            self.assertIn('DatabaseUtils:E', log)  # Provider parcel errors include the actual UID rejection.
            self.assertIn('-s', log)  # Keep filtered capture instead of all app logs.

    def test_install_phases_hash_gate_and_owned_session_abandon(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            apk = root / 'original.apk'
            apk.write_bytes(b'controlled-original')
            entry = {'source_key': 'wechat', 'size': apk.stat().st_size,
                     'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()}
            for mode in ['ready', 'transfer_timeout', 'hash_mismatch', 'write_failed', 'commit_timeout']:
                with self.subTest(mode=mode):
                    calls, result = [], {}
                    out = root / mode
                    out.mkdir()

                    def run(command, **kwargs):
                        calls.append(command)
                        output, code = b'', 0
                        if command[:3] == ['shell', 'mktemp', '-d']:
                            output = b'/data/local/tmp/gogogo-original-wechat-fixture123'
                        elif command[0] == 'push' and mode == 'transfer_timeout':
                            raise subprocess.TimeoutExpired(command, kwargs['timeout'], output=b'partial transfer')
                        elif command[:2] == ['shell', 'sha256sum']:
                            output = (('0' * 64 if mode == 'hash_mismatch' else entry['sha256']) + ' base.apk').encode()
                        elif command[:3] == ['shell', 'pm', 'install-create']:
                            output = b'Success: created install session [501]'
                        elif command[:3] == ['shell', 'pm', 'install-write']:
                            output, code = (b'Failure [FIXTURE]', 1) if mode == 'write_failed' else (b'Success', 0)
                        elif command[:3] == ['shell', 'pm', 'install-commit']:
                            if mode == 'commit_timeout':
                                raise subprocess.TimeoutExpired(command, kwargs['timeout'], output=b'partial commit')
                            output = b'Success'
                        elif command[:3] == ['shell', 'pm', 'install-abandon']:
                            self.assertEqual('501', command[-1])
                            output = b'Success'
                        return subprocess.CompletedProcess(command, code, output, b'')

                    with patch('builtins.print'):
                        if mode == 'ready':
                            baselines.staged_original_install(run, apk, entry, out, result)
                        else:
                            with self.assertRaises((RuntimeError, subprocess.TimeoutExpired)):
                                baselines.staged_original_install(run, apk, entry, out, result)
                    self.assertEqual(mode == 'ready', result['installation']['commit_confirmed'])
                    abandoned = any(c[:3] == ['shell', 'pm', 'install-abandon'] for c in calls)
                    self.assertEqual(mode in ['write_failed', 'commit_timeout'], abandoned)
                    if mode in ['transfer_timeout', 'hash_mismatch']:
                        self.assertFalse(any(c[:3] == ['shell', 'pm', 'install-create'] for c in calls))
                    self.assertTrue(any(c[:3] == ['shell', 'rm', '-rf'] for c in calls))
                    if mode == 'transfer_timeout':
                        self.assertIn(b'partial transfer', (out / 'install-transfer.txt').read_bytes())

    def test_container_requires_real_result_signer_runtime_and_cleanup(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            apk = root / 'original.apk'
            apk.write_bytes(b'controlled-original')
            entry = {'source_key': 'wechat', 'package': 'com.tencent.mm',
                     'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()}
            for mode in ['ready', 'wrong_signer', 'wrong_runtime', 'wrong_package',
                         'junit_failed', 'cleanup_failed', 'preexisting', 'wrong_abi',
                         'system_changed', 'system_fatal', 'post_changed', 'post_fatal',
                         'old_closed_only', 'virtual_failed', 'virtual_mode_wrong',
                         'old_process_still_alive', 'same_main_pid', 'missing_virtual_identity', 'missing_old_identity']:
                with self.subTest(mode=mode):
                    calls = []
                    observed = {'passed': True, 'runtime': 'cuttlefish-arm64', 'package': entry['package'],
                                'sha256': entry['sha256'], 'original_signer': True, 'location_enabled': True,
                                'closed_startup_confirmed': True, 'virtual_startup_observation': {
                                    'passed': True, 'input_pattern': 2, 'previous_main_absent': True,
                                    'previous_main_pid': 8001, 'main_pid': 8002, 'main_uid': 10102}}
                    if mode == 'wrong_signer': observed['original_signer'] = False
                    if mode == 'wrong_runtime': observed['runtime'] = 'official-translation'
                    if mode == 'wrong_package': observed['package'] = 'unrelated'
                    if mode == 'old_closed_only':
                        observed['location_enabled'] = False
                        observed.pop('virtual_startup_observation')
                    if mode == 'virtual_failed': observed['virtual_startup_observation']['passed'] = False
                    if mode == 'virtual_mode_wrong': observed['virtual_startup_observation']['input_pattern'] = 0
                    if mode == 'old_process_still_alive': observed['virtual_startup_observation']['previous_main_absent'] = False
                    if mode == 'same_main_pid': observed['virtual_startup_observation']['main_pid'] = 8001
                    if mode == 'missing_virtual_identity': observed['virtual_startup_observation'].pop('main_pid')
                    if mode == 'missing_old_identity': observed['virtual_startup_observation'].pop('previous_main_pid')

                    def adb(argv, **kwargs):
                        command = argv[3:]
                        calls.append(command)
                        output, code = b'', 0
                        if command == ['shell', 'id', '-u']:
                            output = b'2000'
                        elif command == ['shell', 'getenforce']:
                            output = b'Enforcing'
                        elif command == ['shell', 'getprop', 'sys.boot_completed']:
                            output = b'1'
                        elif command == ['shell', 'pidof', 'system_server']:
                            instrumented = any(c[:3] == ['shell', 'am', 'instrument'] for c in calls)
                            output = b'9002' if mode == 'system_changed' or mode == 'post_changed' and instrumented else b'9001'
                        elif command == ['logcat', '-d', '-b', 'crash'] and (mode == 'system_fatal'
                                or mode == 'post_fatal' and any(c[:3] == ['shell', 'am', 'instrument'] for c in calls)):
                            output = b'E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: FinalizerWatchdogDaemon'
                        elif command[:3] == ['shell', 'pm', 'path']:
                            if mode == 'preexisting': output = b'package:/preserve.apk'
                        elif command[:3] == ['shell', 'dumpsys', 'package']:
                            output = b'primaryCpuAbi=x86_64' if mode == 'wrong_abi' else b'primaryCpuAbi=arm64-v8a'
                        elif command[:3] == ['shell', 'am', 'instrument']:
                            output = b'FAILURES!!!' if mode == 'junit_failed' else b'OK (1 test)'
                        elif command[:2] == ['exec-out', 'run-as']:
                            output = json.dumps(observed).encode() if command[-1].endswith('result.json') else b'fixture'
                        elif command[0] == 'uninstall':
                            code = int(mode == 'cleanup_failed')
                            output = b'Failure [FIXTURE]' if code else b'Success'
                        return subprocess.CompletedProcess(argv, code, output, b'')

                    with patch.object(baselines.subprocess, 'run', side_effect=adb):
                        result = baselines.container_startup(Path('/controlled/adb'), '127.0.0.1:6520',
                            apk, entry, root, root / mode, dict(os.environ), expected_system_pid='9001')
                    self.assertEqual(mode == 'ready', result['passed'])
                    if mode in ['preexisting', 'system_changed', 'system_fatal']:
                        self.assertFalse(any(c[0] in ['install', 'uninstall'] for c in calls))
                    if mode in ['system_changed', 'system_fatal', 'post_changed', 'post_fatal']:
                        self.assertTrue(result['state'].startswith('ENVIRONMENT_'))
                    if mode == 'wrong_abi':
                        self.assertFalse(any(c[:3] == ['shell', 'am', 'instrument'] for c in calls))
                    self.assertFalse(any(c[:3] in [['shell', 'input', 'tap'], ['shell', 'input', 'text']]
                                         for c in calls))

    def test_stale_or_different_qualification_never_downloads_apps(self):
        with tempfile.TemporaryDirectory() as folder:
            work = Path(folder) / 'dedicated-instance'
            report = {'passed': True, 'state': 'QUALIFIED_BOOT', 'boot_completed': True,
                      'serial': '127.0.0.1:6520', 'host_report': {'uid': os.getuid()}}
            with patch.dict(os.environ, {'GITHUB_ACTIONS': 'true', 'RUNNER_TEMP': folder}), \
                    patch.object(baselines.platform, 'machine', return_value='aarch64'), \
                    patch.object(baselines, 'original_input') as download:
                for change in [{'passed': False}, {'state': 'BOOT_TIMEOUT'}, {'boot_completed': False},
                               {'serial': 'physical-device'}, {'host_report': {'uid': os.getuid() + 1}}]:
                    with self.subTest(change=change), self.assertRaises(RuntimeError):
                        baselines.run_original_baselines(work, Path(folder), {**report, **change}, {}, {})
                impossible_pid = int(Path('/proc/sys/kernel/pid_max').read_text()) + 1
                with self.assertRaises(OSError):
                    baselines.run_original_baselines(work, Path(folder), report, {'pid': impossible_pid}, {})
                download.assert_not_called()
            self.assertFalse(work.exists())

    def test_ui_classification(self):
        self.assertEqual('WELCOME_WITHOUT_LOGIN', baselines.anonymous_stage('wechat', ['Log in', 'Sign up']))
        self.assertIsNone(baselines.anonymous_stage('wechat', ['Privacy', 'Agree']))
        self.assertEqual('PRIVACY_WITHOUT_ACCEPTANCE', baselines.anonymous_stage('douyin', ['隐私', '同意']))
        self.assertIsNone(baselines.anonymous_stage('unlisted', ['Privacy', 'Agree']))

    def test_target_fatal_scope_excludes_other_apps_and_package_prefixes(self):
        package = 'com.ss.android.ugc.aweme'
        java = b'E AndroidRuntime: FATAL EXCEPTION: main\nE AndroidRuntime: Process: com.ss.android.ugc.aweme, PID: 123\n'
        self.assertEqual('TARGET_JAVA_FATAL', baselines.target_failure(java, package))
        self.assertEqual('TARGET_JAVA_FATAL', baselines.target_failure(java.replace(b'aweme,', b'aweme:push,'), package))
        self.assertIsNone(baselines.target_failure(java.replace(b'aweme,', b'aweme.other,'), package))
        self.assertIsNone(baselines.target_failure(java, 'com.tencent.mm'))
        native = b'pid: 123 >>> com.ss.android.ugc.aweme <<<\nsignal 11 (SIGSEGV)'
        self.assertEqual('TARGET_NATIVE_FATAL', baselines.target_failure(native, package))
        self.assertIsNone(baselines.target_failure(native, 'com.tencent.mm'))

    def test_preservation_ui_scope_cleanup_and_environment(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            apk = root / 'original.apk'
            apk.write_bytes(b'controlled-original-fixture')
            entry = {'source_key': 'wechat', 'package': 'com.tencent.mm',
                     'size': apk.stat().st_size, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()}
            environment = {**os.environ, 'ANDROID_DATA': 'fixture-data',
                           'ANDROID_ROOT': 'fixture-root', 'ANDROID_TZDATA_ROOT': 'fixture-tz'}
            properties = {'ro.build.version.sdk': '33', 'ro.hardware': 'cutf_cvm'}
            xml = ('<?xml version="1.0"?><hierarchy>'
                   '<node package="com.tencent.mm" text="Log in"/>'
                   '<node package="com.tencent.mm" text="Sign up"/></hierarchy>').encode()
            for mode in ['welcome', 'preexisting', 'identity_changed', 'install_failed',
                         'install_timed_out', 'native_crash', 'unrelated_ui', 'cleanup_failed',
                         'system_changed', 'system_fatal', 'post_ui_changed',
                         'nonidle_ready', 'nonidle_cleanup_failed', 'nonidle_preexisting',
                         'nonidle_hold_ready', 'nonidle_hold_target_fatal',
                         'nonidle_hold_system_fatal', 'nonidle_hold_process_changed']:
                with self.subTest(mode=mode):
                    calls = []
                    markers = {'ui': False, 'crash_reads': 0, 'pid_reads': 0}
                    ticks = itertools.count()

                    def read_ui(*args):
                        markers['ui'] = True
                        return ['Log in', 'Sign up'], 'WELCOME_WITHOUT_LOGIN', {'controlled_fixture': True}

                    def adb(argv, **kwargs):
                        self.assertIs(environment, kwargs['env'])
                        command = argv[3:]
                        calls.append(command)
                        code, output = 0, b''
                        if command == ['shell', 'id', '-u']:
                            output = b'2000'
                        elif command == ['shell', 'getenforce']:
                            output = b'Enforcing'
                        elif command[:2] == ['shell', 'getprop']:
                            value = '1' if command[2] == 'sys.boot_completed' else properties[command[2]]
                            output = ('different' if mode == 'identity_changed' else value).encode()
                        elif command == ['shell', 'uname', '-m']:
                            output = b'aarch64'
                        elif command == ['shell', 'pidof', 'system_server']:
                            committed = any(c[:3] == ['shell', 'pm', 'install-commit'] for c in calls)
                            captured = any(c[:2] == ['exec-out', 'cat'] for c in calls)
                            changed = mode == 'system_changed' and committed or mode == 'post_ui_changed' and captured
                            output = b'9002' if changed else b'9001'
                        elif command == ['shell', 'am', 'get-started-user-state', '0']:
                            output = b'RUNNING_UNLOCKED'
                        elif command[:3] == ['shell', 'pm', 'path']:
                            existing = mode == 'preexisting' or (mode == 'nonidle_preexisting'
                                and command[-1] == 'com.soozooq.tianwaitian.containerresearch')
                            output = b'package:/preexisting.apk' if existing else b''
                        elif command == ['shell', 'pidof', entry['package']]:
                            markers['pid_reads'] += 1
                            output = b'9501' if mode == 'nonidle_hold_process_changed' and markers['pid_reads'] > 1 else b'9500'
                        elif command[:3] == ['shell', 'mktemp', '-d']:
                            output = b'/data/local/tmp/gogogo-original-wechat-fixture123'
                        elif command[:2] == ['shell', 'sha256sum']:
                            output = (entry['sha256'] + '  ' + command[-1]).encode()
                        elif command[:3] == ['shell', 'pm', 'install-create']:
                            output = b'Success: created install session [501]'
                        elif command[:3] in [['shell', 'pm', 'install-write'], ['shell', 'pm', 'install-abandon']]:
                            output = b'Success'
                        elif command[:3] == ['shell', 'pm', 'install-commit']:
                            if mode == 'install_timed_out':
                                raise subprocess.TimeoutExpired(argv, 900)
                            code = int(mode == 'install_failed')
                            output = b'Failure [FIXTURE]' if code else b'Success'
                        elif command[:4] == ['shell', 'cmd', 'package', 'resolve-activity']:
                            output = b'com.tencent.mm/.ui.LauncherUI'
                        elif command[:2] == ['exec-out', 'cat']:
                            output = xml if mode not in ['native_crash', 'unrelated_ui'] \
                                else xml.replace(b'com.tencent.mm', b'unrelated')
                        elif command[:3] == ['exec-out', 'screencap', '-p']:
                            output = b'\x89PNG\r\n\x1a\ncontrolled-fixture'
                        elif command == ['logcat', '-d', '-b', 'crash']:
                            if markers['ui']: markers['crash_reads'] += 1
                            if mode in ['native_crash', 'unrelated_ui']:
                                output = b'pid: 12 >>> com.tencent.mm <<< signal 11'
                            elif mode == 'system_fatal':
                                output = b'E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: FinalizerWatchdogDaemon'
                            elif markers['crash_reads'] > 1 and mode == 'nonidle_hold_system_fatal':
                                output = b'E AndroidRuntime: FATAL EXCEPTION IN SYSTEM PROCESS: FinalizerWatchdogDaemon'
                            elif markers['crash_reads'] > 1 and mode == 'nonidle_hold_target_fatal':
                                output = b'E AndroidRuntime: FATAL EXCEPTION: worker\nE AndroidRuntime: Process: com.tencent.mm, PID: 9500\n'
                        elif command[0] == 'uninstall':
                            code = int(mode == 'cleanup_failed' or mode == 'nonidle_cleanup_failed'
                                       and command[-1].endswith('.test'))
                            output = b'Failure [FIXTURE]' if code else b'Success'
                        return subprocess.CompletedProcess(argv, code, output, b'')

                    with patch.object(baselines.subprocess, 'run', side_effect=adb), patch('builtins.print'), \
                            patch.object(baselines, 'read_only_original_ui', side_effect=read_ui), \
                            patch.object(baselines.time, 'monotonic', side_effect=lambda: next(ticks)), \
                            patch.object(baselines.time, 'sleep'):
                        result = baselines.installed_baseline(Path('/controlled/adb'), '127.0.0.1:6520',
                            apk, entry, root / mode, environment, properties,
                            ui_apks=root if mode.startswith('nonidle_') else None,
                            post_ui_hold_seconds=4 if mode.startswith('nonidle_hold_') else 0)
                    self.assertEqual(mode in ['welcome', 'nonidle_ready', 'nonidle_hold_ready'], result['passed'])
                    if mode.startswith('nonidle_hold_'):
                        self.assertEqual(mode == 'nonidle_hold_ready', result['post_ui_observation']['passed'])
                        if mode != 'nonidle_hold_ready':
                            self.assertTrue(result['state'].startswith('ORIGINAL_POST_UI_') or result['state'].startswith('ENVIRONMENT_'))
                    if mode.startswith('nonidle_'):
                        if mode == 'nonidle_preexisting':
                            self.assertFalse(any(c[0] == 'uninstall' and c[-1] != entry['package'] for c in calls))
                        else:
                            self.assertEqual(2, len(result['owned_ui_package_cleanup']))
                            self.assertEqual(mode != 'nonidle_cleanup_failed', all(result['owned_ui_package_cleanup'].values()))
                        self.assertFalse(any(c[:3] == ['shell', 'uiautomator', 'dump'] for c in calls))
                    if mode in ['preexisting', 'identity_changed']:
                        self.assertFalse(any(c[0] in ['install', 'uninstall'] for c in calls))
                    else:
                        self.assertTrue(any(c[0] == 'uninstall' for c in calls))
                    self.assertFalse(any(c[:3] in [['shell', 'input', 'tap'], ['shell', 'input', 'text']]
                                         for c in calls))
                    if mode == 'preexisting':
                        self.assertEqual('PREEXISTING_SYSTEM_PACKAGE_PRESERVED', result['state'])
                    if mode in ['native_crash', 'unrelated_ui']:
                        self.assertEqual('ORIGINAL_SYSTEM_CONTROL_NATIVE_CRASH', result['state'])
                    if mode in ['system_changed', 'system_fatal', 'post_ui_changed']:
                        self.assertTrue(result['state'].startswith('ENVIRONMENT_'))
                        self.assertEqual(mode == 'post_ui_changed', result['launch_requested'])

    def test_each_original_finishes_its_callback_before_the_next_target(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'evidence').mkdir()
            pins = {'targets': [{'source_key': key} for key in ['wechat', 'douyin']],
                    'source': {key: {'url': 'https://unused.invalid'} for key in ['wechat', 'douyin']}}
            (root / 'evidence/official-target-apk-readiness.json').write_text(json.dumps(pins))
            events = []

            def original(adb, serial, apk, entry, *args, **kwargs):
                key = entry['source_key']
                events.append(('original', key))
                return {'passed': key == 'wechat', 'state': 'CONTROLLED_FIXTURE'}

            def after_target(key, result):
                events.append(('after', key, result['passed']))

            with patch.object(baselines, 'require_owned_qualified_vm'), \
                    patch.object(baselines, 'original_input', return_value=root / 'fixture.apk'), \
                    patch.object(baselines, 'installed_baseline', side_effect=original), patch('builtins.print'):
                result = baselines.run_original_baselines(root, root, {'serial': 'fixture', 'properties': {}},
                                                         {}, {}, after_target)
            self.assertFalse(result['passed'])
            self.assertEqual([('original', 'wechat'), ('after', 'wechat', True),
                              ('original', 'douyin'), ('after', 'douyin', False)], events)

    def test_nonidle_read_only_capture_checks_report_identity_and_completion(self):
        with tempfile.TemporaryDirectory() as folder:
            entry = {'source_key': 'wechat', 'package': 'com.tencent.mm'}
            accepted = {'scope': 'ORIGINAL_INSTALLED_READ_ONLY_UI_NOT_CONTAINER_OR_LOCATION',
                        'capture_method': 'UI_AUTOMATION_WITHOUT_IDLE_WAIT',
                        'target': 'wechat', 'package': entry['package'], 'root_package': entry['package'],
                        'api': 33, 'own_driver_targeting': True, 'plain_application': True,
                        'passed': True, 'terms_accepted': False,
                        'login_attempted': False, 'messages_sent': False, 'input_events_sent': False,
                        'visible_text': ['Log in', 'Sign up'], 'stage': 'WELCOME_WITHOUT_LOGIN'}
            variants = [{}, {'root_package': 'unrelated'}, {'passed': False}, {'api': 32},
                        {'scope': 'SELF_OWNED_UI_CAPTURE_ONLY_NOT_TARGET_STARTUP'},
                        {'own_driver_targeting': False}, {'plain_application': False}, {'input_events_sent': True},
                        {'visible_text': ['privacy']}, {'visible_text': 'Log in / Sign up'},
                        {'stage': 'NOT_REACHED'}, {'unfinished_instrumentation': True}]
            for index, changes in enumerate(variants):
                with self.subTest(changes=changes):
                    out = Path(folder) / str(index)
                    out.mkdir()
                    record = {**accepted, **changes}
                    calls = []

                    def run(command, **kwargs):
                        calls.append(command)
                        if command[:3] == ['shell', 'am', 'instrument']:
                            self.assertEqual(360, kwargs['timeout'])
                            output = b'INSTRUMENTATION_CODE: 0\n' if changes.get('unfinished_instrumentation') \
                                else b'INSTRUMENTATION_CODE: -1\n'
                        elif command[0] == 'exec-out' and command[-1].endswith('result.json'):
                            output = json.dumps(record).encode()
                        else:
                            output = b''
                        return subprocess.CompletedProcess(command, 0, output, b'')

                    text, stage, report = baselines.read_only_original_ui(run, entry, out)
                    self.assertEqual('WELCOME_WITHOUT_LOGIN' if index == 0 else None, stage)
                    self.assertEqual(record, report)
                    instrument = calls[0][-1]
                    self.assertTrue(instrument.startswith('com.soozooq.tianwaitian.containerresearch/'))
                    self.assertFalse(any(c[0] in ['install', 'uninstall'] or c[:2] == ['shell', 'input'] for c in calls))

    def test_failed_original_never_requests_container_inputs_or_guest_actions(self):
        with patch.object(baselines, 'require_owned_qualified_vm') as gate, \
                patch.object(baselines, 'original_input') as download, \
                patch.object(baselines, 'container_startup') as start:
            for original in [{'passed': False}, {'passed': True}]:
                result = baselines.run_container_control(Path('/unused'), Path('/unused'), {}, {}, {},
                    Path('/unused'), 'wechat', original)
                self.assertTrue(result['state'].startswith('SKIPPED_ORIGINAL_SYSTEM_'))
        gate.assert_not_called()
        download.assert_not_called()
        start.assert_not_called()

    def test_different_existing_input_is_preserved(self):
        with tempfile.TemporaryDirectory() as folder:
            work = Path(folder)
            (work / 'targets').mkdir()
            target = work / 'targets/weixin-8.0.79-arm64.apk'
            target.write_bytes(b'preserve-existing')
            entry = {'source_key': 'wechat', 'size': 1, 'sha256': '0' * 64}
            with patch.object(baselines.subprocess, 'run') as network:
                with self.assertRaisesRegex(RuntimeError, 'preserved'):
                    baselines.original_input(work, entry, 'https://unused.invalid', dict(os.environ))
                network.assert_not_called()
            self.assertEqual(b'preserve-existing', target.read_bytes())


if __name__ == '__main__':
    unittest.main()
