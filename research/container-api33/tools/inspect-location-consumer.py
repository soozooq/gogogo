#!/usr/bin/env python3
"""Inspect the independently built owned consumer; not third-party compatibility."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

if not __debug__:
    raise SystemExit('Run the verifier without optimization')
parser = argparse.ArgumentParser()
parser.add_argument('--sdk', required=True)
parser.add_argument('--apk', required=True)
parser.add_argument('--output', required=True)
args = parser.parse_args()
spec = Path(__file__).resolve().parents[1]
repository = Path(subprocess.check_output(['git', '-C', str(spec), 'rev-parse', '--show-toplevel'], text=True).strip())
sys.dont_write_bytecode = True
sys.path.insert(0, str(repository / 'ci'))
from verify_container_v31_artifacts import apk_defined_classes

sdk = Path(args.sdk)
apk_path = Path(args.apk)
manifest = ET.fromstring(subprocess.check_output([
    str(sdk / 'cmdline-tools/latest/bin/apkanalyzer'), 'manifest', 'print', str(apk_path)]))
ns = '{http://schemas.android.com/apk/res/android}'
package = 'com.soozooq.tianwaitian.locationconsumer'
assert manifest.get('package') == package
permissions = sorted(element.get(ns + 'name') for element in manifest.findall('uses-permission'))
assert permissions == ['android.permission.ACCESS_COARSE_LOCATION', 'android.permission.ACCESS_FINE_LOCATION']
assert manifest.find('uses-sdk').get(ns + 'targetSdkVersion') == '33'
with zipfile.ZipFile(apk_path) as apk:
    assert apk.testzip() is None
    definitions = apk_defined_classes(apk)
    assert 'Lcom/soozooq/tianwaitian/locationconsumer/LocationActivity;' in definitions
    assert all(name.startswith('Lcom/soozooq/tianwaitian/locationconsumer/') for name in definitions)
    assert not [name for name in apk.namelist() if name.endswith(('.apk', '.so'))]
    dex = b''.join(apk.read(name) for name in apk.namelist() if re.fullmatch(r'classes\d*\.dex', name))
    forbidden = [b'top/niunaijun/bcore', b'containerresearch', b'java/lang/reflect', b'ILocationManagerProxy']
    assert not [name for name in forbidden if name in dex]
    for method in [b'getSystemService', b'getLastKnownLocation', b'getCurrentLocation',
                   b'requestLocationUpdates', b'removeUpdates']:
        assert method in dex
signature = subprocess.check_output([str(sdk / 'build-tools/33.0.2/apksigner'),
    'verify', '--print-certs', str(apk_path)], text=True)
certificate = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)', signature)
assert certificate is not None
report = {
    'scope': 'OWNED_CONSUMER_STATIC_INPUT_NOT_LOCATION_SUCCESS',
    'package': package, 'sha256': hashlib.sha256(apk_path.read_bytes()).hexdigest(),
    'size': apk_path.stat().st_size, 'uses_permissions': permissions,
    'only_own_class_definitions': True, 'engine_host_or_reflection_references': False,
    'expected_target_coordinates_embedded_by_design': False,
    'native_libraries_or_embedded_apks': False, 'public_android_location_methods_present': True,
    'certificate_sha256': certificate.group(1),
}
Path(args.output).write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
