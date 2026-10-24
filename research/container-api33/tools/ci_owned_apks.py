"""Transport only this project's own host/test APK pair between jobs of one CI run."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess


FILES = {'host': 'probeHost-debug.apk', 'driver': 'probeHost-debug-androidTest.apk'}
SCOPE = 'CI_SELF_OWNED_HOST_AND_DRIVER_NOT_ORIGINAL_TARGET_APKS_OR_PHONE_RELEASE'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def sources(spec):
    paths = [spec / 'sources.json', spec / 'cuttlefish-inputs.json',
             spec / 'evidence/official-target-apk-readiness.json']
    for folder in ['tools', 'probe-host', 'patches']:
        paths.extend(p for p in (spec / folder).rglob('*') if p.is_file()
                     and p.suffix in ['.py', '.sh', '.java', '.xml', '.gradle', '.patch'])
    return {str(p.relative_to(spec)): digest(p) for p in sorted(set(paths))}


def verify(directory, spec, commit):
    if directory.is_symlink() or not directory.is_dir():
        raise RuntimeError('Own-build transport directory missing or indirect')
    if {p.name for p in directory.iterdir()} != {*FILES.values(), 'manifest.json'}:
        raise RuntimeError('Only the own host, own test driver and manifest may be transported')
    if any(p.is_symlink() or not p.is_file() for p in directory.iterdir()):
        raise RuntimeError('Transport entries must be ordinary files')
    manifest = json.loads((directory / 'manifest.json').read_text())
    if manifest.get('scope') != SCOPE or manifest.get('commit') != commit \
            or manifest.get('sources_sha256') != sources(spec) \
            or set(manifest.get('apks', {})) != set(FILES):
        raise RuntimeError('Transport is not bound to this exact reviewed CI source')
    for role, name in FILES.items():
        path, entry = directory / name, manifest['apks'][role]
        if entry.get('name') != name or path.stat().st_size != entry.get('size') \
                or digest(path) != entry.get('sha256'):
            raise RuntimeError('Own APK transport size/hash mismatch: ' + role)
    return manifest


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--workdir', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS') != 'true':
        raise SystemExit('Only the CI build job prepares this non-release pair')
    spec = Path(__file__).resolve().parents[1]
    commit = subprocess.check_output(['git', '-C', str(spec), 'rev-parse', 'HEAD'], text=True).strip()
    if commit != os.environ.get('GITHUB_SHA'):
        raise SystemExit('CI source commit differs')
    output = Path(args.output).resolve()
    temporary = Path(os.environ['RUNNER_TEMP']).resolve()
    if output == temporary or not output.is_relative_to(temporary):
        raise SystemExit('Use a dedicated runner temporary directory')
    build = Path(args.workdir).resolve() / 'probe-host-build/outputs/apk'
    inputs = {'host': build / 'debug' / FILES['host'],
              'driver': build / 'androidTest/debug' / FILES['driver']}
    if not all(p.is_file() for p in inputs.values()):
        raise SystemExit('Build both own APKs first')
    output.mkdir(exist_ok=False)
    for role, path in inputs.items():
        shutil.copyfile(path, output / FILES[role])
    manifest = {'scope': SCOPE, 'commit': commit, 'sources_sha256': sources(spec),
                'apks': {role: {'name': FILES[role], 'size': path.stat().st_size,
                                 'sha256': digest(path)} for role, path in inputs.items()},
                'original_target_apks_included': False, 'phone_candidate': False}
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    verify(output, spec, commit)
    print('Prepared the exact own-build pair for this CI run; no original target APK or phone release')


if __name__ == '__main__':
    main()
