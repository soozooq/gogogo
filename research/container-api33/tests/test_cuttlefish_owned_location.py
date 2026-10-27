"""Controlled process/ADB receipts; never a native runtime or location result."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

tools = Path(__file__).resolve().parents[1] / 'tools'
spec = importlib.util.spec_from_file_location('owned_native_fixture', tools / 'cuttlefish_owned_location.py')
owned = importlib.util.module_from_spec(spec)
with patch.object(sys, 'path', [str(tools), *sys.path]):
    spec.loader.exec_module(owned)


class NativeOwnContractControlTest(unittest.TestCase):
    def test_qualification_is_required_before_reading_apks(self):
        with patch.object(owned, 'require_owned_qualified_vm', side_effect=RuntimeError('unqualified')), \
                patch.object(owned, 'verify') as verify:
            with self.assertRaises(RuntimeError):
                owned.run_owned_location_control(Path('/not-a-vm'), Path('/unused'), {}, {}, {},
                                                  Path('/unused'), '9001')
            verify.assert_not_called()

    def test_no_false_pass_from_green_junit_or_stale_environment_and_cleanup_is_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for mode in ['ready', 'wrong_hash', 'same_pid', 'missing_stage', 'old_report_source', 'false_framework',
                         'junit_failed', 'wrong_api', 'changed_system', 'preexisting_package',
                         'preexisting_staging', 'cleanup_failed', 'staging_cleanup_timeout', 'diagnostic_unavailable',
                         'presentation_wrong_count', 'presentation_failed']:
                with self.subTest(mode=mode):
                    out = root / mode
                    out.mkdir()
                    calls, state = [], {'case': 'framework', 'instrumented': False, 'uninstalled': set()}
                    manifest = {'apks': {'consumer': {'sha256': 'a' * 64}}}
                    environment = {'OWNED_TEST_ENV': 'preserved'}

                    def adb(argv, **kwargs):
                        self.assertIs(environment, kwargs['env'])
                        command = argv[3:]
                        calls.append(command)
                        data, code = b'', 0
                        if mode == 'diagnostic_unavailable':
                            if command[:4] == ['logcat', '-d', '-b', 'main']:
                                state['optional_crash_next'] = True
                                raise subprocess.TimeoutExpired(command, kwargs['timeout'])
                            if command == ['logcat', '-d', '-b', 'crash'] and state.pop('optional_crash_next', False):
                                raise subprocess.TimeoutExpired(command, kwargs['timeout'])
                        values = {('id', '-u'): '2000', ('getenforce',): 'Enforcing',
                                  ('getprop', 'sys.boot_completed'): '1',
                                  ('getprop', 'ro.build.version.sdk'): '32' if mode == 'wrong_api' else '33',
                                  ('getprop', 'ro.product.cpu.abi'): 'arm64-v8a',
                                  ('getprop', 'ro.dalvik.vm.native.bridge'): '0',
                                  ('pidof', 'system_server'): '9002' if mode == 'changed_system'
                                    and state['instrumented'] else '9001'}
                        if command and command[0] == 'shell' and tuple(command[1:]) in values:
                            data = values[tuple(command[1:])].encode()
                        elif command[:3] == ['shell', 'pm', 'path']:
                            if mode == 'preexisting_package' or mode == 'cleanup_failed' \
                                    and command[3] in state['uninstalled']:
                                data = b'package:/preserved-or-cleanup-failed.apk'
                        elif command[:3] == ['shell', 'test', '-e']:
                            code = 0 if mode == 'preexisting_staging' else 1
                        elif command[:3] == ['shell', 'dumpsys', 'package']:
                            data = b'primaryCpuAbi=arm64-v8a'
                        elif command[:3] == ['shell', 'am', 'instrument']:
                            state['instrumented'] = True
                            state['case'] = 'presentation-contract' if 'top.niunaijun.bcore.fake.service.LocationContractInstrumentedTest' in command else \
                                'location' if 'GuestLocationInstrumentedTest#' in ' '.join(command) else 'framework'
                            count = 5 if mode == 'presentation_wrong_count' else 6
                            data = b'FAILURES!!!' if mode == 'junit_failed' or mode == 'presentation_failed' and state['case'] == 'presentation-contract' \
                                else f'OK ({count} tests)'.encode() if state['case'] == 'presentation-contract' else b'OK (1 test)'
                        elif command[:2] == ['exec-out', 'run-as']:
                            if command[-1].endswith('result.json'):
                                if state['case'] == 'framework':
                                    value = {'passed': True, 'scope': 'OWN_GUEST_FRAMEWORK_STARTUP_ONLY_NOT_TARGET_APP_OR_LOCATION',
                                             'provider_call_nonce_matches': True, 'provider_process_separate': mode != 'false_framework'}
                                else:
                                    value = {'passed': True, 'scope': 'OWNED_GUEST_REAL_PROCESS_NOT_THIRD_PARTY_OR_WECHAT',
                                             'report_source': 'OLD_UI_SOURCE' if mode == 'old_report_source' else 'OWN_GUEST_ATOMIC_PRIVATE_FILE',
                                             'host_pid': 31, 'guest_pid': 31 if mode == 'same_pid' else 32,
                                             'consumer_sha256': ('b' if mode == 'wrong_hash' else 'a') * 64,
                                             'stages': [{'name': n} for n in ['TARGET_A', 'TARGET_B', 'UNREGISTERED_STABLE']
                                                        + ([] if mode == 'missing_stage' else ['CURRENT_CANCELLED'])]}
                                data = json.dumps(value).encode()
                            else: data = b'controlled-fixture'
                        elif command and command[0] == 'uninstall':
                            state['uninstalled'].add(command[1])
                        elif command[:3] == ['shell', 'rm', '-f'] and mode == 'staging_cleanup_timeout':
                            raise subprocess.TimeoutExpired(command, kwargs['timeout'])
                        return subprocess.CompletedProcess(argv, code, data, b'')

                    with patch.object(owned.subprocess, 'run', side_effect=adb):
                        result = owned.owned_contracts(Path('/fixture/adb'), '127.0.0.1:6520', root,
                                                      manifest, out, environment, '9001')
                    self.assertEqual(mode in ['ready', 'diagnostic_unavailable'], result['passed'])
                    if mode in ['wrong_api', 'preexisting_package', 'preexisting_staging']:
                        self.assertFalse(any(c[0] in ['install', 'uninstall', 'push'] for c in calls))
                        self.assertFalse(any(c[:3] == ['shell', 'rm', '-f'] for c in calls))
                    else:
                        self.assertEqual(2, len(result['owned_package_cleanup']))
                    if mode == 'staging_cleanup_timeout':
                        self.assertFalse(result['own_staging_removed'])
                        self.assertTrue(all(result['owned_package_cleanup'].values()))
                    if mode == 'diagnostic_unavailable':
                        self.assertTrue(all(not c['crash_capture']['available'] for c in result['cases'].values()))
