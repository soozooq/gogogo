#!/usr/bin/env python3
"""Download pinned artifacts through their ordinary public viewer links; no login/API key."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import urllib.parse
import urllib.request


def download(spec, entry, directory):
    target = directory / entry['name']
    if target.exists():
        if target.stat().st_size != entry['size'] or hashlib.file_digest(
                target.open('rb'), 'sha256').hexdigest() != entry['sha256']:
            raise RuntimeError('Existing artifact differs; preserved: ' + entry['name'])
        return
    # The public HTML embeds the same short-lived URL as its Download button.
    # Do not save or print that URL, scrape API keys, or use an authenticated session.
    with urllib.request.urlopen(spec['public_page'] + entry['name'], timeout=45) as response:
        html = response.read(256 * 1024).decode()
    match = re.search(r'var JSVariables = (\{[^\n]+\});', html)
    if not match:
        raise RuntimeError('Public viewer did not provide the expected artifact metadata')
    metadata = json.loads(match.group(1))
    if any(metadata.get(k) != spec[v] for k, v in [
            ('buildId', 'build_id'), ('branch', 'branch'), ('target', 'target')]) \
            or metadata.get('forceLogin') is not False or metadata.get('idToken') is not None:
        raise RuntimeError('Public build identity/access does not match')
    url = urllib.parse.urlparse(metadata['artifactUrl'])
    if url.scheme != 'https' or url.hostname != 'storage.googleapis.com' \
            or not url.path.endswith('/' + entry['name']):
        raise RuntimeError('Unexpected official download host or artifact name')
    temporary = target.with_name(target.name + '.partial')
    digest = hashlib.sha256()
    total = 0
    try:
        with urllib.request.urlopen(metadata['artifactUrl'], timeout=90) as response, temporary.open('xb') as output:
            if int(response.headers.get('Content-Length', '0')) != entry['size']:
                raise RuntimeError('Unexpected official artifact length')
            while block := response.read(1024 * 1024):
                total += len(block)
                if total > entry['size']:
                    raise RuntimeError('Artifact exceeded its pinned size')
                digest.update(block)
                output.write(block)
        if total != entry['size'] or digest.hexdigest() != entry['sha256']:
            raise RuntimeError('Official artifact size/SHA mismatch')
        temporary.rename(target)
    except Exception:
        temporary.unlink(missing_ok=True)
        raise


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    spec = json.loads((Path(__file__).resolve().parents[1] / 'cuttlefish-inputs.json').read_text())
    directory = Path(args.output).resolve()
    directory.mkdir(parents=True, exist_ok=True)
    for entry in spec['artifacts']:
        download(spec, entry, directory)
        print('Verified official artifact:', entry['name'], entry['size'], entry['sha256'], flush=True)


if __name__ == '__main__':
    main()
