"""Reject a rendering APK mislabeled as a phone main; preserve frozen updates."""
import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('phone_stage', Path(__file__).with_name('stage_rootless_phone_preview.py'))
stage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(stage)


class PhoneStagingBoundaryTest(unittest.TestCase):
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
