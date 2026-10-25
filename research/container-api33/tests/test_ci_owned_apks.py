"""Transport guards with dummy bytes; not a compiled APK or app result."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from unittest.mock import patch


spec = importlib.util.spec_from_file_location('transport',
    Path(__file__).resolve().parents[1] / 'tools/ci_owned_apks.py')
transport = importlib.util.module_from_spec(spec)
spec.loader.exec_module(transport)


class OwnBuildTransportTest(unittest.TestCase):
    def test_optional_consumer_requires_complete_scope_source_and_flat_inventory(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            files = {**transport.FILES, 'consumer': transport.CONSUMER_FILE}
            for name in files.values(): (directory / name).write_bytes(b'owned-only-fixture')
            manifest = {'scope': transport.CONSUMER_SCOPE, 'commit': 'a' * 40,
                        'sources_sha256': {'own-consumer-source': 'hash'},
                        'apks': {role: {'name': name, 'size': (directory / name).stat().st_size,
                                       'sha256': transport.digest(directory / name)} for role, name in files.items()}}
            path = directory / 'manifest.json'
            path.write_text(json.dumps(manifest))
            with patch.object(transport, 'sources', return_value=manifest['sources_sha256']) as sources, \
                    patch.object(transport, 'verify_notices'):
                self.assertEqual(manifest, transport.verify(directory, directory, 'a' * 40))
                sources.assert_called_with(directory, True)
                manifest['scope'] = transport.SCOPE
                path.write_text(json.dumps(manifest))
                with self.assertRaises(RuntimeError): transport.verify(directory, directory, 'a' * 40)
                manifest['scope'] = transport.CONSUMER_SCOPE
                path.write_text(json.dumps(manifest))
                (directory / transport.CONSUMER_FILE).unlink()
                with self.assertRaises(RuntimeError): transport.verify(directory, directory, 'a' * 40)

    def test_source_license_materials_and_no_embedded_original(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'licenses').mkdir()
            for path in [root / 'licenses/GPL-3.0.txt', root / 'THIRD_PARTY_NOTICES.md',
                         root / 'CI_SOURCE_AND_RELINK.md']:
                path.write_text('controlled text for ' + path.name)
            apk = root / 'own.apk'
            materials = [root / 'licenses/GPL-3.0.txt', root / 'THIRD_PARTY_NOTICES.md',
                         root / 'CI_SOURCE_AND_RELINK.md']
            for mode in ['good', 'different_text', 'embedded_apk']:
                with self.subTest(mode=mode):
                    with zipfile.ZipFile(apk, 'w') as archive:
                        for path in materials:
                            data = b'different' if mode == 'different_text' else path.read_bytes()
                            archive.writestr('assets/research-notices/' + path.name, data)
                        if mode == 'embedded_apk': archive.writestr('assets/original.apk', b'not-admitted')
                    if mode == 'good':
                        transport.verify_notices(apk, root)
                    else:
                        with self.assertRaises(RuntimeError): transport.verify_notices(apk, root)

    def test_commit_source_bytes_and_flat_allowlist(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            for name in transport.FILES.values():
                (directory / name).write_bytes(b'controlled-own-build-fixture')
            manifest = {'scope': transport.SCOPE, 'commit': 'a' * 40, 'sources_sha256': {'fixture': 'hash'},
                        'apks': {role: {'name': name, 'size': (directory / name).stat().st_size,
                                        'sha256': transport.digest(directory / name)}
                                 for role, name in transport.FILES.items()}}
            (directory / 'manifest.json').write_text(json.dumps(manifest))
            with patch.object(transport, 'sources', return_value={'fixture': 'hash'}), \
                    patch.object(transport, 'verify_notices'):
                self.assertEqual(manifest, transport.verify(directory, directory, 'a' * 40))
                with self.assertRaises(RuntimeError):
                    transport.verify(directory, directory, 'b' * 40)
                for extra in ['original-target.apk', 'nested']:
                    (directory / extra).write_bytes(b'not-admitted')
                    with self.assertRaises(RuntimeError):
                        transport.verify(directory, directory, 'a' * 40)
                    (directory / extra).unlink()
                host = directory / transport.FILES['host']
                host.write_bytes(b'different')
                with self.assertRaises(RuntimeError):
                    transport.verify(directory, directory, 'a' * 40)
            with patch.object(transport, 'sources', return_value={'different': 'source'}):
                with self.assertRaises(RuntimeError):
                    transport.verify(directory, directory, 'a' * 40)


if __name__ == '__main__':
    unittest.main()
