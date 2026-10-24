"""Transport guards with dummy bytes; not a compiled APK or app result."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location('transport',
    Path(__file__).resolve().parents[1] / 'tools/ci_owned_apks.py')
transport = importlib.util.module_from_spec(spec)
spec.loader.exec_module(transport)


class OwnBuildTransportTest(unittest.TestCase):
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
            with patch.object(transport, 'sources', return_value={'fixture': 'hash'}):
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
