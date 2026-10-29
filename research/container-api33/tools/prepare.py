#!/usr/bin/env python3
"""Fetch pinned public research inputs into a separate workspace; never execute them."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

spec = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--workdir', required=True)
parser.add_argument('--candidate', choices=['blackbox', 'virtualapp', 'both'], default='both')
parser.add_argument('--skip-guest', action='store_true')
parser.add_argument('--verify-only', action='store_true')
args = parser.parse_args()
work = Path(args.workdir).resolve()
repository = subprocess.run(['git', '-C', str(spec), 'rev-parse', '--show-toplevel'],
    capture_output=True, text=True, check=True).stdout.strip()
if work == Path(repository) or Path(repository) in work.parents:
    raise SystemExit('Research workdir must be outside the GoGoGo checkout')
if args.verify_only and not work.is_dir():
    raise SystemExit('Prepared research directory missing')
work.mkdir(parents=True, exist_ok=True)
sources = json.loads((spec / 'sources.json').read_text())

def git(path, *arguments, capture=False):
    return subprocess.run(['git', '-C', str(path), *arguments], check=True,
        stdout=subprocess.PIPE if capture else None,
        env=dict(os.environ, GIT_TERMINAL_PROMPT='0')).stdout

prepared = {}
for candidate in (['blackbox', 'virtualapp'] if args.candidate == 'both' else [args.candidate]):
    entry = sources[candidate]
    path = work / entry['directory']
    if not path.exists():
        if args.verify_only:
            raise SystemExit(f'{candidate}: checkout missing')
        subprocess.run(['git', 'clone', '--no-checkout', '--depth', '1', entry['url'], str(path)],
            check=True, env=dict(os.environ, GIT_TERMINAL_PROMPT='0'))
        git(path, 'fetch', '--depth', '1', 'origin', entry['commit'])
        git(path, 'checkout', '--detach', entry['commit'])
    if git(path, 'rev-parse', 'HEAD', capture=True).decode().strip() != entry['commit']:
        raise SystemExit(f'{candidate}: different source head; existing checkout preserved')
    patch = (spec / entry['patch']).read_bytes() if entry.get('patch') else b''
    difference = git(path, 'diff', '--binary', '--no-renames', '--no-ext-diff', 'HEAD', capture=True)
    if difference != patch:
        if args.verify_only:
            raise SystemExit(f'{candidate}: source differs from the pinned source and reviewed patch')
        if difference or git(path, 'ls-files', '--others', '--exclude-standard', capture=True).strip():
            raise SystemExit(f'{candidate}: unexpected existing edits/files; checkout preserved')
        if patch:
            git(path, 'apply', '--check', str(spec / entry['patch']))
            git(path, 'apply', str(spec / entry['patch']))
            for added in entry.get('added_files', []):
                git(path, 'add', '--intent-to-add', '--', added)
        if git(path, 'diff', '--binary', '--no-renames', '--no-ext-diff', 'HEAD', capture=True) != patch:
            raise SystemExit(f'{candidate}: patch result mismatch')
    if git(path, 'ls-files', '--others', '--exclude-standard', capture=True).strip():
        raise SystemExit(f'{candidate}: unexpected untracked files; checkout preserved')
    prepared[candidate] = {'commit': entry['commit'], 'patch_sha256': hashlib.sha256(patch).hexdigest()}

if not args.skip_guest and not args.verify_only:
    for source_key in ['guest', 'third_party_location']:
        entry = sources[source_key]
        directory = work / 'guest'
        directory.mkdir(exist_ok=True)
        target = directory / entry['filename']
        def approved(file):
            return file.stat().st_size == entry['size'] and hashlib.sha256(file.read_bytes()).hexdigest() == entry['sha256']
        if target.exists():
            if not approved(target):
                raise SystemExit('Existing guest differs; file preserved')
        else:
            with tempfile.NamedTemporaryFile(dir=directory, prefix='download-', delete=False) as temp:
                temporary = Path(temp.name)
            try:
                subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error', '--retry', '2',
                    '--max-filesize', str(10 * 1024 * 1024), '--output', str(temporary), entry['url']], check=True)
                if not approved(temporary):
                    raise SystemExit('Guest size/SHA-256 mismatch')
                temporary.rename(target)
            finally:
                temporary.unlink(missing_ok=True)
        prepared[source_key] = {key: entry[key] for key in ['package', 'size', 'sha256', 'source']}
if not args.verify_only:
    (work / 'prepared-sources.json').write_text(json.dumps(prepared, indent=2) + '\n')
print(json.dumps(prepared, indent=2))
