"""Controlled ADB fixtures, never an actual Android or target-app result."""
import hashlib
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
                         'junit_failed', 'cleanup_failed', 'preexisting', 'wrong_abi']:
                with self.subTest(mode=mode):
                    calls = []
                    observed = {'passed': True, 'runtime': 'cuttlefish-arm64', 'package': entry['package'],
                                'sha256': entry['sha256'], 'original_signer': True, 'location_enabled': False}
                    if mode == 'wrong_signer': observed['original_signer'] = False
                    if mode == 'wrong_runtime': observed['runtime'] = 'official-translation'
                    if mode == 'wrong_package': observed['package'] = 'unrelated'

                    def adb(argv, **kwargs):
                        command = argv[3:]
                        calls.append(command)
                        output, code = b'', 0
                        if command[:3] == ['shell', 'pm', 'path']:
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
                            apk, entry, root, root / mode, dict(os.environ))
                    self.assertEqual(mode == 'ready', result['passed'])
                    if mode == 'preexisting':
                        self.assertFalse(any(c[0] in ['install', 'uninstall'] for c in calls))
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
                         'install_timed_out', 'native_crash', 'unrelated_ui', 'cleanup_failed']:
                with self.subTest(mode=mode):
                    calls = []

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
                        elif command == ['shell', 'am', 'get-started-user-state', '0']:
                            output = b'RUNNING_UNLOCKED'
                        elif command[:3] == ['shell', 'pm', 'path']:
                            output = b'package:/preexisting.apk' if mode == 'preexisting' else b''
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
                            if mode in ['native_crash', 'unrelated_ui']:
                                output = b'pid: 12 >>> com.tencent.mm <<< signal 11'
                        elif command[0] == 'uninstall':
                            code = int(mode == 'cleanup_failed')
                            output = b'Failure [FIXTURE]' if code else b'Success'
                        return subprocess.CompletedProcess(argv, code, output, b'')

                    with patch.object(baselines.subprocess, 'run', side_effect=adb), patch('builtins.print'):
                        result = baselines.installed_baseline(Path('/controlled/adb'), '127.0.0.1:6520',
                            apk, entry, root / mode, environment, properties)
                    self.assertEqual(mode == 'welcome', result['passed'])
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
