#!/usr/bin/env python3
"""Verify downloaded CI receipts. Never installs an APK or declares location success."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--inputs', required=True, help='Trusted run/commit/archive SHA manifest')
parser.add_argument('--repo', default=str(Path(__file__).resolve().parents[3]))
parser.add_argument('--sdk', default=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME'))
parser.add_argument('--output', required=True)
args = parser.parse_args()
output_path = Path(args.output)
if output_path.exists(): raise SystemExit('Use a new receipt path; existing evidence is preserved')
config = json.loads(Path(args.inputs).read_text())
if not config.get('artifacts') or set(config['artifacts']) - {'linux', 'mac', 'own', 'native'}:
    raise SystemExit('Unsupported receipt roles; use linux, mac, own or native')
commit = config['commit']
if not re.fullmatch(r'[0-9a-f]{40}', commit): raise SystemExit('Full expected commit SHA required')
repo = Path(args.repo)
cache = {}


def original(path):
    if path not in cache:
        name = PurePosixPath(path)
        if name.is_absolute() or '..' in name.parts or '\\' in path:
            raise ValueError('Invalid source path')
        cache[path] = subprocess.run(['git', 'show', commit + ':' + path], cwd=repo,
                                     check=True, capture_output=True).stdout
    return cache[path]


def sha(data): return hashlib.sha256(data).hexdigest()


def verify_source_map(values):
    if not isinstance(values, dict) or not values: raise ValueError('Missing source map')
    for name, expected in values.items():
        if sha(original('research/container-api33/' + name)) != expected:
            raise ValueError('Source does not match expected commit: ' + name)
    return len(values)


def extract(path, expected, destination):
    if not re.fullmatch(r'[0-9a-f]{64}', expected) or sha(path.read_bytes()) != expected:
        raise ValueError('Archive SHA mismatch: ' + path.name)
    with zipfile.ZipFile(path) as archive:
        if sum(x.file_size for x in archive.infolist()) > 256_000_000:
            raise ValueError('Archive exceeds receipt size bound')
        for entry in archive.infolist():
            p = PurePosixPath(entry.filename)
            if p.is_absolute() or '..' in p.parts or '\\' in entry.filename \
                    or stat.S_IFMT(entry.external_attr >> 16) == stat.S_IFLNK:
                raise ValueError('Indirect or unsafe receipt entry')
        archive.extractall(destination)


receipt = {'scope': 'EVIDENCE_INTEGRITY_AND_REPORTED_RUNTIME_NOT_TARGET_LOCATION_OR_PHONE',
           'run_id': config['run_id'], 'commit': commit, 'integrity_verified': False,
           'inputs_manifest_sha256': sha(Path(args.inputs).read_bytes()),
           'verifier_sha256': sha(Path(__file__).read_bytes()), 'artifacts': {}}
with tempfile.TemporaryDirectory(prefix='ci-receipts-') as temporary:
    directories = {}
    for role, entry in config['artifacts'].items():
        destination = Path(temporary) / role
        extract(Path(entry['path']), entry['sha256'], destination)
        directories[role] = destination
        receipt['artifacts'][role] = {'id': entry['id'], 'sha256': entry['sha256'], 'verified': True}
    if 'mac' in directories:
        mac = directories['mac']
        if (mac / 'research-commit.txt').read_text().strip() != commit: raise ValueError('Mac commit differs')
        receipt['mac_scope'] = 'BUILD_ONLY_NOT_ANDROID_RUNTIME'
    if 'own' in directories:
        own = directories['own']
        files = {'host': 'probeHost-debug.apk', 'driver': 'probeHost-debug-androidTest.apk'}
        manifest = json.loads((own / 'manifest.json').read_text())
        include_consumer = manifest.get('scope') == 'CI_SELF_OWNED_HOST_DRIVER_CONSUMER_NOT_ORIGINAL_TARGET_APKS_OR_PHONE_RELEASE'
        if include_consumer: files['consumer'] = 'locationConsumer-debug.apk'
        if {p.name for p in own.iterdir()} != {*files.values(), 'manifest.json'}:
            raise ValueError('Unexpected own transport entry')
        if manifest.get('commit') != commit or manifest.get('scope') not in [
                'CI_SELF_OWNED_HOST_AND_DRIVER_NOT_ORIGINAL_TARGET_APKS_OR_PHONE_RELEASE',
                'CI_SELF_OWNED_HOST_DRIVER_CONSUMER_NOT_ORIGINAL_TARGET_APKS_OR_PHONE_RELEASE'] \
                or set(manifest.get('apks', {})) != set(files):
            raise ValueError('Own transport identity differs')
        count = verify_source_map(manifest['sources_sha256'])
        tracked = subprocess.run(['git', 'ls-tree', '-r', '--name-only', commit, '--',
            'research/container-api33/'], cwd=repo, check=True, capture_output=True, text=True).stdout.splitlines()
        fixed = {'sources.json', 'cuttlefish-inputs.json', 'evidence/official-target-apk-readiness.json',
                 'THIRD_PARTY_NOTICES.md', 'CI_SOURCE_AND_RELINK.md'}
        expected_sources = set()
        for path in tracked:
            name = path.removeprefix('research/container-api33/')
            parts = PurePosixPath(name).parts
            suffix = PurePosixPath(name).suffix
            if name in fixed or parts[0] == 'licenses' and suffix in ['.txt', '.json'] \
                    or parts[0] in ['tools', 'probe-host', 'patches'] + (['location-consumer'] if include_consumer else []) \
                    and suffix in ['.py', '.sh', '.java', '.xml', '.gradle', '.patch']:
                expected_sources.add(name)
        if set(manifest['sources_sha256']) != expected_sources:
            raise ValueError('Own source inventory is incomplete or has extra inputs')
        listed = subprocess.run(['git', 'ls-tree', '-r', '--name-only', commit, '--',
            'research/container-api33/licenses/'], cwd=repo, check=True, capture_output=True, text=True).stdout.splitlines()
        materials = [name for name in listed if name.endswith('.txt') or name.endswith('provenance.json')]
        materials += ['research/container-api33/THIRD_PARTY_NOTICES.md', 'research/container-api33/CI_SOURCE_AND_RELINK.md']
        certs = []
        for role, name in files.items():
            path, pin = own / name, manifest['apks'][role]
            if pin.get('name') != name or path.stat().st_size != pin['size'] or sha(path.read_bytes()) != pin['sha256']:
                raise ValueError('Own APK size/hash differs')
            with zipfile.ZipFile(path) as apk:
                if any(name.endswith('.apk') for name in apk.namelist()): raise ValueError('Embedded APK rejected')
                for material in materials:
                    if apk.read('assets/research-notices/' + Path(material).name) != original(material):
                        raise ValueError('Own APK source/license material differs')
            if not args.sdk: raise ValueError('SDK required for own APK signature verification')
            signed = subprocess.run([str(Path(args.sdk) / 'build-tools/33.0.2/apksigner'), 'verify',
                '--print-certs', str(path)], check=True, capture_output=True, text=True).stdout
            certs.append(re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)', signed)[1])
        if len(set(certs)) != 1: raise ValueError('Own APK signing certificates differ')
        receipt['own_transport'] = {'source_count_verified': count, 'apks': manifest['apks'],
                                    'notice_materials_per_apk': len(materials), 'signatures_verified_and_same': True}
    if 'linux' in directories:
        linux = directories['linux']
        if (linux / 'research-commit.txt').read_text().strip() != commit: raise ValueError('Linux commit differs')
        cases = {}
        for path in linux.glob('*/case-inputs.json'):
            inputs = json.loads(path.read_text())
            result_path = path.with_name('case-result.json')
            runtime = json.loads(result_path.read_text()) if result_path.exists() else {}
            cases[path.parent.name] = {'source_count_verified': verify_source_map(inputs['research_source_sha256']),
                                      'passed': runtime.get('passed') is True}
        ui_path = linux / 'read-only-ui-capture/result.json'
        if ui_path.exists():
            ui = json.loads(ui_path.read_text())
            cases['read-only-ui-capture'] = {'source_count_verified': verify_source_map(ui['research_source_sha256']),
                'passed': ui.get('passed') is True, 'scope': ui.get('scope'), 'old_cli': ui.get('old_cli'),
                'observed': ui.get('observed'), 'cleanup': ui.get('owned_package_cleanup'),
                'host_engine_process_absent': ui.get('host_engine_process_absent_after_capture')}
        receipt['default_cases'] = cases
        feedback = linux / 'feedback-main'
        if feedback.exists():
            if (feedback / 'commit.txt').read_text().strip() != commit: raise ValueError('Feedback commit differs')
            for line in (feedback / 'source-sha256.txt').read_text().splitlines():
                expected, name = line.split(maxsplit=1)
                if sha(original(name)) != expected: raise ValueError('Feedback source differs')
            counts = {key: 0 for key in ['tests', 'failures', 'errors', 'skipped']}
            for path in (feedback / 'unit').glob('TEST-*.xml'):
                suite = ET.parse(path).getroot()
                for key in counts: counts[key] += int(suite.get(key, '0'))
            severe = sum(x.get('severity') in ['Error', 'Fatal'] for x in ET.parse(feedback / 'lint-results-debug.xml').iter('issue'))
            receipt['feedback_main'] = {'junit': counts, 'lint_error_fatal_count': severe,
                'passed': (feedback / 'returncode.txt').read_text().strip() == '0' and counts['tests'] > 0
                          and all(counts[k] == 0 for k in ['failures', 'errors', 'skipped']) and severe == 0}
    receipt['native'] = {'state': 'NOT_SUPPLIED_NOT_PASS', 'passed': False}
    if 'native' in directories:
        candidates = [(p, json.loads(p.read_text())) for p in directories['native'].rglob('result.json')]
        def early_preparation_failure(value):
            return value.get('state') == 'PREPARATION_OR_PROBE_ERROR' \
                and value.get('passed') is False and value.get('boot_completed') is False \
                and (value.get('qemu_launch_requested') is None or value.get('qemu_launch_requested') is False) \
                and (value.get('system_boot_passed') is None or value.get('system_boot_passed') is False) \
                and isinstance(value.get('error'), str) and bool(value['error'])
        boots = [(p, x) for p, x in candidates if x.get('research_commit') == commit
                 and str(x.get('scope', '')).startswith('CUTTLEFISH_')
                 and ('qemu_process_stopped' in x or early_preparation_failure(x))]
        if len(boots) != 1: raise ValueError('Exactly one native boot receipt for expected commit required')
        boot_path, boot = boots[0]
        preparation_only = 'qemu_process_stopped' not in boot
        receipt['native'] = {'source_count_verified': verify_source_map(boot['source_sha256']),
            'state': boot['state'], 'passed': not preparation_only and boot.get('passed') is True,
            'phase': 'PREPARATION_FAILED_BEFORE_REPORTED_QEMU_LAUNCH' if preparation_only
                else 'BOOT_OR_RUNTIME_REPORTED',
            'system_boot_passed': boot.get('system_boot_passed') is True,
            'qemu_process_stopped': None if preparation_only else boot.get('qemu_process_stopped') is True}
        if 'error' in boot: receipt['native']['error'] = boot['error']
        for name, folder in [('ordinary_originals', 'original-target-baselines'),
                             ('container_targets', 'container-target-startups')]:
            path = boot_path.parent / folder / 'summary.json'
            if path.exists():
                receipt['native'][name] = json.loads(path.read_text()).get('results', {})
        owned = boot_path.parent / 'owned-location-native/result.json'
        if owned.exists(): receipt['native']['owned_location'] = json.loads(owned.read_text())
    receipt['integrity_verified'] = True
with output_path.open('x', encoding='utf-8') as stream:
    stream.write(json.dumps(receipt, indent=2) + '\n')
print(json.dumps({'integrity_verified': True, 'run_id': config['run_id'],
                  'default_cases': receipt.get('default_cases', {}), 'native': receipt['native']}))
