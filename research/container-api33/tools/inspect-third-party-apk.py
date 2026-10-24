#!/usr/bin/env python3
"""Verify the exact official GPSTest input; never alter/re-sign the third-party APK."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile

if not __debug__:
    raise SystemExit('Run this verifier without optimization')
parser = argparse.ArgumentParser()
parser.add_argument('--sdk', required=True)
parser.add_argument('--apk', required=True)
parser.add_argument('--output', required=True)
args = parser.parse_args()
spec = Path(__file__).resolve().parents[1]
entry = json.loads((spec / 'sources.json').read_text())['third_party_location']
apk = Path(args.apk)
assert apk.stat().st_size == entry['size']
assert hashlib.sha256(apk.read_bytes()).hexdigest() == entry['sha256']
sdk = Path(args.sdk)
manifest = ET.fromstring(subprocess.check_output([
    str(sdk / 'cmdline-tools/latest/bin/apkanalyzer'), 'manifest', 'print', str(apk)]))
ns = '{http://schemas.android.com/apk/res/android}'
assert manifest.get('package') == entry['package']
assert int(manifest.get(ns + 'versionCode')) == entry['version_code']
assert manifest.get(ns + 'versionName') == entry['version_name']
with zipfile.ZipFile(apk) as archive:
    assert archive.testzip() is None
    native = sorted(name for name in archive.namelist() if name.endswith('.so'))
signature = subprocess.check_output([str(sdk / 'build-tools/33.0.2/apksigner'),
    'verify', '--print-certs', str(apk)], text=True)
certificate = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)', signature)
assert certificate and certificate.group(1) == entry['certificate_sha256']
result = {
    'scope': 'OFFICIAL_THIRD_PARTY_INPUT_IDENTITY_NOT_RUNTIME_SUCCESS',
    'package': entry['package'], 'version_code': entry['version_code'],
    'sha256': entry['sha256'], 'size': entry['size'], 'source': entry['source'],
    'certificate_sha256': certificate.group(1), 'signature_verified': True,
    'min_sdk': manifest.find('uses-sdk').get(ns + 'minSdkVersion'),
    'target_sdk': manifest.find('uses-sdk').get(ns + 'targetSdkVersion'),
    'declared_permissions': sorted(element.get(ns + 'name') for element in manifest.findall('uses-permission')),
    'native_libraries': native, 'unmodified_release_digest_match': True,
}
Path(args.output).write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2))
