#!/usr/bin/env python3
"""Inspect the built research host, independently of the runtime UI test."""
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
    raise SystemExit('Run this verifier without Python optimization')

parser = argparse.ArgumentParser()
parser.add_argument('--sdk', required=True)
parser.add_argument('--host', required=True)
parser.add_argument('--output', required=True)
args = parser.parse_args()
spec = Path(__file__).resolve().parents[1]
repository = Path(subprocess.check_output(['git', '-C', str(spec), 'rev-parse', '--show-toplevel'], text=True).strip())
sys.dont_write_bytecode = True
sys.path.insert(0, str(repository / 'ci'))
from verify_container_v31_artifacts import apk_defined_classes

sdk = Path(args.sdk)
host = Path(args.host)
manifest = subprocess.check_output([str(sdk / 'cmdline-tools/latest/bin/apkanalyzer'), 'manifest', 'print', str(host)])
tree = ET.fromstring(manifest)
ns = '{http://schemas.android.com/apk/res/android}'
package = tree.attrib['package']
assert package == 'com.soozooq.tianwaitian.containerresearch'
permissions = sorted(e.get(ns + 'name') for e in tree.findall('uses-permission'))
assert permissions == sorted([package + '.INTERNAL', 'android.permission.REORDER_TASKS',
    'android.permission.ACCESS_COARSE_LOCATION', 'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE',
    'android.permission.FOREGROUND_SERVICE'])
with zipfile.ZipFile(host) as apk:
    assert apk.testzip() is None
    definitions = apk_defined_classes(apk)
    embedded_apks = [n for n in apk.namelist() if n.endswith('.apk')]
    guest_definitions = [n for n in definitions if n.startswith(('Lorg/fossify/',
        'Lcom/android/gpstest/', 'Lcom/soozooq/tianwaitian/locationconsumer/'))]
    abis = sorted({n.split('/')[1] for n in apk.namelist() if n.startswith('lib/') and n.endswith('.so')})
    mirror = 'Lblack/com/android/internal/InternalResources$styleable;' in definitions
    catalog = json.loads(apk.read('assets/approved-guests.json'))
    for path in [*sorted((spec / 'licenses').glob('*.txt')),
                 *sorted((spec / 'licenses').glob('*provenance.json')),
                 spec / 'THIRD_PARTY_NOTICES.md', spec / 'CI_SOURCE_AND_RELINK.md']:
        assert apk.read('assets/research-notices/' + path.name) == path.read_bytes(), path.name
sources = json.loads((spec / 'sources.json').read_text())
assert len(catalog) == 2
for actual, key, location in zip(catalog, ['guest', 'third_party_location'], [False, True]):
    expected = sources[key]
    assert actual['location'] is location
    for field in ['package', 'sha256', 'size', 'version_code', 'version_name', 'certificate_sha256', 'url']:
        assert actual[field] == expected[field], field
assert not embedded_apks and not guest_definitions and mirror
assert abis == ['arm64-v8a', 'x86_64']
signature = subprocess.check_output([str(sdk / 'build-tools/33.0.2/apksigner'), 'verify', '--print-certs', str(host)], text=True)
certificate = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)', signature)
assert certificate is not None
report = {
    'scope': 'COMPILED_RESEARCH_HOST_NOT_WECHAT_COMPATIBILITY',
    'package': package,
    'sha256': hashlib.sha256(host.read_bytes()).hexdigest(),
    'size': host.stat().st_size,
    'native_abis': abis,
    'uses_permissions': permissions,
    'default_component_permission': tree.find('application').get(ns + 'permission'),
    'resource_mirror_defined': mirror,
    'embedded_guest_apks': embedded_apks,
    'embedded_guest_class_definitions': guest_definitions,
    'signature_verified': True,
    'pinned_guest_catalog': catalog,
    'certificate_sha256': certificate.group(1),
}
Path(args.output).write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
