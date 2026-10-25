"""Synthetic native receipts test the actual CLI; none is an Android runtime result."""
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile


class PreparationReceiptTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.repo = Path(__file__).resolve().parents[3]
        cls.commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=cls.repo).decode().strip()
        names = ['tools/run-cuttlefish-arm-boot.py', 'tools/cuttlefish-tcg-qemu.py',
                 'tools/fetch-cuttlefish-inputs.py', 'cuttlefish-inputs.json',
                 'tools/cuttlefish_target_baselines.py', 'tools/ci_owned_apks.py',
                 'tools/inspect-cuttlefish-arm-host.py', 'evidence/official-target-apk-readiness.json']
        cls.report = {'scope': 'CUTTLEFISH_SYNTHETIC_PREPARATION_FIXTURE_NOT_RUNTIME',
                      'research_commit': cls.commit, 'state': 'PREPARATION_OR_PROBE_ERROR',
                      'passed': False, 'boot_completed': False, 'error': 'Controlled preparation failure',
                      'source_sha256': {name: hashlib.sha256(subprocess.check_output(
                          ['git', 'show', cls.commit + ':research/container-api33/' + name],
                          cwd=cls.repo)).hexdigest() for name in names}}

    def verify(self, report):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); archive = root / 'native.zip'
            with zipfile.ZipFile(archive, 'w') as z:
                z.writestr('evidence/result.json', json.dumps(report))
            manifest = {'run_id': 1, 'commit': self.commit, 'artifacts': {'native': {
                'id': 1, 'path': str(archive), 'sha256': hashlib.sha256(archive.read_bytes()).hexdigest()}}}
            config = root / 'input.json'; config.write_text(json.dumps(manifest)); output = root / 'out.json'
            result = subprocess.run([sys.executable, str(self.repo / 'research/container-api33/tools/verify-ci-receipts.py'),
                                     '--inputs', str(config), '--repo', str(self.repo), '--output', str(output)],
                                    capture_output=True, timeout=15)
            return result.returncode, json.loads(output.read_text()) if output.exists() else None

    def test_early_failure_keeps_unknown_cleanup_and_never_becomes_runtime_pass(self):
        code, observed = self.verify(self.report)
        self.assertEqual(0, code); self.assertTrue(observed['integrity_verified'])
        self.assertEqual(8, observed['native']['source_count_verified'])
        self.assertFalse(observed['native']['passed']); self.assertFalse(observed['native']['system_boot_passed'])
        self.assertIsNone(observed['native']['qemu_process_stopped'])
        self.assertEqual('PREPARATION_FAILED_BEFORE_REPORTED_QEMU_LAUNCH', observed['native']['phase'])

    def test_missing_lifecycle_cannot_be_disguised_as_success_or_launched_vm(self):
        for changes in [{'passed': True}, {'boot_completed': True}, {'qemu_launch_requested': True},
                        {'qemu_launch_requested': 'true'}, {'error': None},
                        {'state': 'QUALIFIED_BOOT_AND_CONTAINER_UI'}, {'scope': 'UNRELATED'}]:
            with self.subTest(changes=changes):
                report = copy.deepcopy(self.report); report.update(changes)
                code, observed = self.verify(report)
                self.assertNotEqual(0, code); self.assertIsNone(observed)
