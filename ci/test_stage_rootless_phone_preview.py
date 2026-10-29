"""Reject a rendering APK mislabeled as a phone main; preserve frozen updates."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('phone_stage', Path(__file__).with_name('stage_rootless_phone_preview.py'))
stage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(stage)


class PhoneStagingBoundaryTest(unittest.TestCase):
    def test_staged_receipt_matches_the_actual_copied_new_profile(self):
        # Structural packaging fixture only; SDK signing inspection is covered
        # separately and mocked here. No device or real 447-test run is claimed.
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); source = root / 'source'; source.mkdir()
            config = copy.deepcopy(stage.staging_spec())
            config.update(runtime_commit='a' * 40, qualification_run_id=67, delivery_generation=2,
                          metadata_profile_path='docs/build-profiles/main-preview-2.4.36.init.gradle',
                          host_ci_non_signature_entries=423)
            config['expected_apks']['main'].update(file='main.apk', version_name='2.4.36-container-preview', version_code=2400104)
            config['expected_apks']['host'].update(file='host.apk', version_name='0.5-preview', version_code=5)
            profile = source / config['metadata_profile_path']; profile.parent.mkdir(parents=True)
            profile.write_text('// deliberately distinct structural profile fixture\n')
            config['profile_sha256'] = hashlib.sha256(profile.read_bytes()).hexdigest()
            main = source / 'app/build/outputs/apk/debug/Go_2.4.36-container-preview_arm64-v8a_debug.apk'
            main.parent.mkdir(parents=True); main.write_bytes(b'structural main fixture only')
            host = root / 'signed-host.apk'
            with zipfile.ZipFile(host, 'w') as archive:
                for i in range(423):
                    archive.writestr('fixture/' + str(i), b'structural payload')
            config['expected_apks']['host']['sha256'] = hashlib.sha256(host.read_bytes()).hexdigest()
            base = root / 'base.apk'; base.write_bytes(host.read_bytes())
            unit = source / 'app/build/test-results/testDebugUnitTest/TEST-fixture.xml'
            unit.parent.mkdir(parents=True); unit.write_text('<testsuite tests="447" failures="0" errors="0" skipped="0"/>')
            lint = source / 'app/build/reports/lint-results-debug.xml'
            lint.parent.mkdir(parents=True); lint.write_text('<issues/>')
            policy = root / 'policy.json'; policy.write_text(json.dumps(config))
            def git(command, **kwargs):
                if command[-2:] == ['rev-parse', 'HEAD']: return 'a' * 40 + '\n'
                if command[-2:] == ['status', '--porcelain']: return ''
                self.fail('Unexpected external command in structural fixture: ' + str(command))
            def inspected(path, sdk, package, version, code, cert):
                return {'package': package, 'version_name': version, 'version_code': code,
                        'certificate_sha256': cert, 'bytes': path.stat().st_size,
                        'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                        'signature_and_alignment_verified': True, 'debuggable': True}
            output = root / 'staged'
            argv = ['stage', '--source', str(source), '--host', str(host), '--base-host', str(base),
                    '--sdk', str(root), '--output', str(output), '--delivery-run-id', '42', '--config', str(policy)]
            with patch('sys.argv', argv), patch.object(stage.subprocess, 'check_output', side_effect=git), \
                    patch.object(stage, 'inspect', side_effect=inspected), patch('builtins.print'):
                stage.main()
            manifest = json.loads((output / 'candidate-manifest.json').read_text())
            actual = hashlib.sha256((output / profile.name).read_bytes()).hexdigest()
            self.assertEqual(actual, manifest['metadata_profile_sha256'])
            self.assertNotEqual(stage.PROFILE_SHA, manifest['metadata_profile_sha256'])
            self.assertEqual(2, manifest['delivery_generation'])
            spec = importlib.util.spec_from_file_location('release_contract', Path(__file__).with_name('preview_release.py'))
            release = importlib.util.module_from_spec(spec); spec.loader.exec_module(release)
            release.validate_manifest(manifest, config, 42)
            with self.assertRaisesRegex(ValueError, 'generation'):
                release.validate_manifest(dict(manifest, delivery_generation=1), config, 42)

    def test_new_profile_and_original_update_certificates_are_required(self):
        config = copy.deepcopy(stage.staging_spec())
        config.update(runtime_commit='a' * 40, metadata_profile_path='docs/build-profiles/main-preview-2.4.36.init.gradle',
                      host_ci_non_signature_entries=423)
        config['expected_apks']['main'].update(version_name='2.4.36-container-preview', version_code=2400104)
        stage.staging_spec(config)
        for label, change in [
                ('old_profile', lambda c: c.update(metadata_profile_path='docs/build-profiles/main-preview-2.4.35.init.gradle')),
                ('new_key', lambda c: c['expected_apks']['host'].update(certificate_sha256='b' * 64)),
                ('unknown_source', lambda c: c.update(runtime_commit='not-a-source')),
                ('boolean_count', lambda c: c.update(host_ci_non_signature_entries=True))]:
            c = copy.deepcopy(config); change(c)
            with self.subTest(label=label), self.assertRaises(ValueError):
                stage.staging_spec(c)

    def test_main_with_x86_or_mixed_libraries_is_rejected_even_with_arm64_filename(self):
        manifest = b'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.soozooq.gogogo.test" android:versionName="2.4.36-container-preview" android:versionCode="2400104"><application android:debuggable="true"/></manifest>'
        signer = 'Signer #1 certificate SHA-256 digest: ' + stage.MAIN_CERT
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            for abis in [{'x86_64'}, {'arm64-v8a', 'x86_64'}, {'arm64-v8a'}]:
                apk = folder / 'main-arm64.apk'
                with zipfile.ZipFile(apk, 'w') as archive:
                    for abi in abis:
                        archive.writestr('lib/' + abi + '/fixture.so', b'structural fixture, not executable')
                with patch.object(stage.subprocess, 'check_output', side_effect=[manifest, signer]), \
                        patch.object(stage.subprocess, 'run'):
                    args = (apk, folder, 'com.soozooq.gogogo.test', '2.4.36-container-preview', 2400104, stage.MAIN_CERT)
                    if abis == {'arm64-v8a'}:
                        self.assertTrue(stage.inspect(*args)['signature_and_alignment_verified'])
                    else:
                        with self.assertRaisesRegex(ValueError, 'rendering APKs cannot be shipped'):
                            stage.inspect(*args)


if __name__ == '__main__':
    unittest.main()
