#!/usr/bin/env python3
"""Stage a draft, then independently qualify the same source before publication."""
import argparse
import base64
import hashlib
import importlib.util
import json
import re
import stat
import subprocess
import sys
import time
from pathlib import Path, PurePosixPath
from urllib.parse import quote
import zipfile

REPO = 'soozooq/gogogo'
COUNTS = {'tests': 447, 'failures': 0, 'errors': 0, 'skipped': 0}


def require(value, reason):
    if not value:
        raise ValueError(reason)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def run(command, **kwargs):
    return subprocess.check_output(command, stderr=subprocess.PIPE, **kwargs)


def api(path, method='GET', data=None):
    command = ['gh', 'api', '--method', method, 'repos/' + REPO + '/' + path]
    if data is not None:
        command += ['--input', '-']
    result = run(command, input=json.dumps(data).encode() if data is not None else None)
    return json.loads(result) if result.strip() else {}


def save(path, value):
    with path.open('x') as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write('\n')


def download(path, destination):
    with destination.open('xb') as stream:
        subprocess.run(['gh', 'api', '-H', 'Accept: application/octet-stream',
                        'repos/' + REPO + '/' + path], stdout=stream, check=True)


def extract(path, destination, maximum=128 * 1024 * 1024):
    require(not destination.exists(), 'Existing extracted evidence is preserved')
    with zipfile.ZipFile(path) as archive:
        infos = archive.infolist()
        require(len(infos) == len({i.filename for i in infos}), 'Duplicate ZIP member')
        require(sum(i.file_size for i in infos) <= maximum, 'Expanded ZIP too large')
        for info in infos:
            name = PurePosixPath(info.filename)
            require(not name.is_absolute() and '..' not in name.parts
                    and '\\' not in info.filename
                    and not stat.S_ISLNK(info.external_attr >> 16), 'Unsafe ZIP member')
        archive.extractall(destination)


def release_for_tag(config):
    matches = []
    for page in range(1, 11):
        values = api('releases?per_page=100&page=' + str(page))
        matches += [v for v in values if v['tag_name'] == config['tag']]
        if len(values) < 100:
            break
    else:
        raise ValueError('Release collection exceeds bounded lookup')
    require(len(matches) <= 1, 'Ambiguous release tag')
    return matches[0] if matches else None


def validate_manifest(value, config, delivery_run):
    require(value['source_commit'] == config['runtime_commit']
            and value['delivery_run_id'] == delivery_run, 'Phone source/run differs')
    require(value['main_checks'] == {'junit': COUNTS, 'lint_error_fatal_count': 0},
            'Phone main checks did not pass')
    require(value['host_original_ci_run'] == config['qualification_run_id']
            and value['host_ci_non_signature_entries_equal'] == 415
            and value['private_key_used_or_included'] is False
            and value['original_target_apks_included'] is False, 'Phone provenance differs')
    require(value['metadata_profile_sha256'] == config['profile_sha256'], 'Phone profile differs')
    require(set(value['apks']) == set(config['expected_apks']) == {'main', 'host'}, 'Phone APK set differs')
    for role, expected in config['expected_apks'].items():
        actual = value['apks'][role]
        require(all(actual.get(k) == v for k, v in expected.items()), 'Phone APK pin differs: ' + role)
        require(actual['signature_and_alignment_verified'] is True
                and re.fullmatch('[0-9a-f]{64}', actual['sha256'])
                and type(actual['bytes']) is int and 0 < actual['bytes'] < 64 * 1024 * 1024,
                'Phone APK verification missing: ' + role)


def validate_release(value, config, manifest, draft, complete):
    require(value['tag_name'] == config['tag'] and value['target_commitish'] == config['runtime_commit']
            and value['draft'] is draft and value['prerelease'] is True, 'Release identity/state differs')
    expected = {v['file']: v for v in manifest['apks'].values()}
    names = [a['name'] for a in value['assets']]
    require(len(names) == len(set(names)) and set(names) <= set(expected), 'Unexpected existing assets')
    require(not complete or set(names) == set(expected), 'Expected assets missing')
    for asset in value['assets']:
        pin = expected[asset['name']]
        require(asset['state'] == 'uploaded' and asset['size'] == pin['bytes']
                and asset['digest'] == 'sha256:' + pin['sha256'], 'Existing asset changed')


def live_policy_matches(config_path, config):
    value = api('contents/' + config['live_config_path'] + '?ref=' + quote(config['branch'], safe=''))
    require(value['encoding'] == 'base64'
            and base64.b64decode(value['content']) == config_path.read_bytes(),
            'Delivery policy changed or was stopped; keep the draft')


def stage(args, config, config_path):
    package = Path(args.package_dir)
    work = Path(args.work)
    work.mkdir(parents=True, exist_ok=False)
    manifest = json.loads((package / 'candidate-manifest.json').read_text())
    validate_manifest(manifest, config, args.delivery_run)
    for value in manifest['apks'].values():
        path = package / value['file']
        require(path.stat().st_size == value['bytes'] and sha(path.read_bytes()) == value['sha256'],
                'Staged bytes differ from manifest')
    live_policy_matches(config_path, config)
    release = release_for_tag(config)
    if release is None:
        release = api('releases', 'POST', {'tag_name': config['tag'],
            'target_commitish': config['runtime_commit'], 'name': config['title'] + '（草稿）',
            'draft': True, 'prerelease': True,
            'body': '候选草稿：等待同源完整资格核对，尚未公开。\n\n' + Path(config['notes_path']).read_text()})
    validate_release(release, config, manifest, draft=True, complete=False)
    save(work / 'draft-before.json', release)
    present = {a['name'] for a in release['assets']}
    for value in manifest['apks'].values():
        if value['file'] not in present:
            subprocess.run(['gh', 'release', 'upload', config['tag'], '--repo', REPO,
                            str(package / value['file'])], check=True)
    after = api('releases/' + str(release['id']))
    validate_release(after, config, manifest, draft=True, complete=True)
    save(work / 'draft-after.json', after)
    print(json.dumps({'draft_staged': True, 'release_id': after['id'], 'published': False}))


def latest_jobs(value):
    latest = {}
    for job in value['jobs']:
        old = latest.get(job['name'])
        if old is None or (job.get('run_attempt', 1), job['id']) > (old.get('run_attempt', 1), old['id']):
            latest[job['name']] = job
    return latest


def mac_capacity_retry_allowed(workflow, jobs, config, annotations):
    mac = jobs.get(config['mac_job_name'])
    required = [next((v for v in jobs.values() if v['id'] == pin), None)
                for pin in config['qualification_job_ids'].values()]
    return (workflow['id'] == config['qualification_run_id']
            and workflow['head_sha'] == config['runtime_commit']
            and workflow['status'] == 'completed' and workflow.get('run_attempt', 1) == 1
            and all(j is not None and j['conclusion'] == 'success' for j in required)
            and mac is not None and mac['id'] == config['mac_initial_job_id']
            and mac['status'] == 'completed'
            and mac['conclusion'] == 'cancelled' and mac.get('runner_id') == 0
            and mac.get('steps') == []
            and any('not acquired' in a.get('message', '') and 'hosted' in a.get('message', '')
                    for a in annotations))


def wait_qualification(config, work, config_path):
    deadline = time.monotonic() + config['wait_seconds']
    retried = False
    last_progress = None
    while time.monotonic() < deadline:
        live_policy_matches(config_path, config)
        workflow = api('actions/runs/' + str(config['qualification_run_id']))
        require(workflow['id'] == config['qualification_run_id']
                and workflow['head_sha'] == config['runtime_commit'], 'Qualification source differs')
        raw_jobs = api('actions/runs/' + str(config['qualification_run_id']) + '/jobs?filter=all&per_page=100')
        require(raw_jobs.get('total_count', len(raw_jobs['jobs'])) <= 100, 'Unbounded job attempts')
        jobs = latest_jobs(raw_jobs)
        for pin in config['qualification_job_ids'].values():
            job = next((j for j in raw_jobs['jobs'] if j['id'] == pin), None)
            require(job is not None and job.get('conclusion') not in ['failure', 'cancelled', 'skipped', 'timed_out'],
                    'Required Linux/native qualification did not pass')
        progress = {
            'run_id': workflow['id'], 'status': workflow['status'], 'conclusion': workflow['conclusion'],
            'jobs': {k: {'id': v['id'], 'status': v['status'], 'conclusion': v['conclusion']} for k, v in jobs.items()},
            'mac_capacity_retry_posted': retried}
        (work / 'qualification-progress.json').write_text(json.dumps(progress, indent=2) + '\n')
        if progress != last_progress:
            print(json.dumps(progress), flush=True)
            last_progress = progress
        if workflow['status'] == 'completed':
            if workflow['conclusion'] == 'success':
                require(all(next(j for j in raw_jobs['jobs'] if j['id'] == pin)['conclusion'] == 'success'
                            for pin in config['qualification_job_ids'].values()), 'Required job not successful')
                require(jobs[config['mac_job_name']]['conclusion'] == 'success', 'Mac build not successful')
                save(work / 'qualification-run.json', workflow)
                save(work / 'qualification-jobs.json', raw_jobs)
                return
            mac = jobs.get(config['mac_job_name'])
            annotations = []
            if mac and mac.get('runner_id') == 0 and mac.get('steps') == []:
                url = mac.get('check_run_url', '')
                match = re.fullmatch(r'https://api\.github\.com/repos/soozooq/gogogo/check-runs/([0-9]+)', url)
                if match:
                    annotations = api('check-runs/' + match[1] + '/annotations?per_page=100')
            require(not retried and config['mac_no_runner_retries'] == 1
                    and mac_capacity_retry_allowed(workflow, jobs, config, annotations),
                    'Qualification did not pass; draft retained without a runtime retry')
            save(work / 'mac-capacity-retry.json', {'job_id': mac['id'], 'annotations': annotations,
                 'reason': 'Completed run; Linux/native successful, Mac never acquired a runner', 'maximum_retries': 1})
            api('actions/jobs/' + str(mac['id']) + '/rerun', 'POST')
            retried = True
        time.sleep(config['poll_seconds'])
    raise TimeoutError('Bounded qualification wait ended; keep the draft')


def receive_artifact(pin, config, work, role):
    value = api('actions/artifacts/' + str(pin['id']))
    require(not value['expired'] and value['workflow_run']['id'] == config['qualification_run_id']
            and value['workflow_run']['head_sha'] == config['runtime_commit']
            and value['name'] == pin['name'], 'Qualification artifact source/name differs: ' + role)
    digest = value['digest'].removeprefix('sha256:')
    require(re.fullmatch('[0-9a-f]{64}', digest), 'Missing API archive digest')
    for key, actual in [('bytes', value['size_in_bytes']), ('sha256', digest)]:
        require(key not in pin or pin[key] == actual, 'Frozen archive differs: ' + role)
    path = work / (role + '.zip')
    download('actions/artifacts/' + str(value['id']) + '/zip', path)
    require(path.stat().st_size == value['size_in_bytes'] and sha(path.read_bytes()) == digest,
            'Downloaded archive differs: ' + role)
    extract(path, work / role)
    save(work / (role + '-artifact-api.json'), value)
    return {'id': value['id'], 'path': str(path.resolve()), 'sha256': digest}


def publish(args, config, config_path):
    work = Path(args.work)
    work.mkdir(parents=True, exist_ok=False)
    wait_qualification(config, work, config_path)
    collection = api('actions/runs/' + str(config['qualification_run_id']) + '/artifacts?per_page=100')
    require(collection.get('total_count', len(collection['artifacts'])) <= 100, 'Unbounded artifact collection')
    save(work / 'qualification-artifacts-api.json', collection)
    archives = {}
    for role, pin in config['qualification_artifacts'].items():
        matches = [v for v in collection['artifacts'] if v['name'] == pin['name']]
        require(len(matches) == 1, 'Ambiguous/missing qualification artifact: ' + role)
        require('id' not in pin or matches[0]['id'] == pin['id'], 'Frozen artifact ID differs')
        archives[role] = receive_artifact({**pin, 'id': matches[0]['id']}, config, work, role)
    require(set(archives) == {'own', 'linux', 'mac', 'native'}, 'All four qualification archives required')
    save(work / 'verification-inputs.json', {'run_id': config['qualification_run_id'],
         'commit': config['runtime_commit'], 'artifacts': archives})
    subprocess.run([sys.executable, 'research/container-api33/tools/verify-ci-receipts.py',
                    '--inputs', str(work / 'verification-inputs.json'), '--sdk', args.sdk,
                    '--output', str(work / 'verified.json')], check=True)
    proof = json.loads((work / 'verified.json').read_text())
    require(proof['integrity_verified'] and proof['mac_scope'] == 'BUILD_ONLY_NOT_ANDROID_RUNTIME'
            and proof['feedback_main'] == {'junit': COUNTS, 'lint_error_fatal_count': 0, 'passed': True}
            and len(proof['default_cases']) == 8 and all(v['passed'] for v in proof['default_cases'].values()),
            'Default qualification did not pass')
    subprocess.run([sys.executable, 'ci/qualify_native_preview.py', '--round', '56',
                    '--job', str(config['qualification_job_ids']['native']), '--proof', str(work / 'verified.json'),
                    '--raw', str(work / 'native'), '--output', str(work / 'native-qualified.json')], check=True)
    for name, count in [('x86_64-location-contract', 7), ('x86_64-radio-privacy', 19)]:
        text = (work / 'linux' / name / 'cloud-instrumentation.txt').read_text()
        require(re.findall(r'^OK \((\d+) tests?\)$', text, re.M) == [str(count)]
                and 'FAILURES!!!' not in text, 'Required Linux methods differ')

    # The publication job consumes its own completed staging job, not another run.
    delivery = api('actions/runs/' + str(args.delivery_run))
    require(delivery['head_sha'] == args.delivery_commit, 'Delivery workflow source differs')
    jobs = api('actions/runs/' + str(args.delivery_run) + '/jobs?filter=all&per_page=100')
    require(latest_jobs(jobs)['stage-draft']['conclusion'] == 'success', 'Draft staging did not succeed')
    values = api('actions/runs/' + str(args.delivery_run) + '/artifacts?per_page=100')['artifacts']
    matches = [v for v in values if v['name'] == config['delivery_artifact_name']]
    require(len(matches) == 1, 'Ambiguous/missing current phone artifact')
    item = matches[0]
    require(not item['expired'] and item['workflow_run']['id'] == args.delivery_run
            and item['workflow_run']['head_sha'] == args.delivery_commit, 'Phone artifact belongs to another source/run')
    phone_zip = work / 'phone.zip'
    download('actions/artifacts/' + str(item['id']) + '/zip', phone_zip)
    require(item['digest'] == 'sha256:' + sha(phone_zip.read_bytes())
            and item['size_in_bytes'] == phone_zip.stat().st_size, 'Phone archive digest differs')
    extract(phone_zip, work / 'phone', 96 * 1024 * 1024)
    save(work / 'phone-artifact-api.json', item)
    manifest = json.loads((work / 'phone/candidate-manifest.json').read_text())
    validate_manifest(manifest, config, args.delivery_run)
    require((work / 'phone/SHA256SUMS').read_text() == ''.join(
        manifest['apks'][role]['sha256'] + '  ' + manifest['apks'][role]['file'] + '\n'
        for role in ['main', 'host']), 'Phone checksum list differs')
    expected_files = {v['file'] for v in manifest['apks'].values()} | {
        'candidate-manifest.json', 'SHA256SUMS', 'PHONE-README.txt', 'main-preview-2.4.35.init.gradle'}
    require({p.name for p in (work / 'phone').iterdir()} == expected_files
            and all(p.is_file() for p in (work / 'phone').iterdir()), 'Phone download is not the flat six-file set')
    profile = (work / 'phone/main-preview-2.4.35.init.gradle').read_bytes()
    require(sha(profile) == config['profile_sha256']
            and profile == run(['git', 'show', config['runtime_commit'] + ':docs/build-profiles/main-preview-2.4.35.init.gradle']),
            'Actual phone profile differs')
    spec = importlib.util.spec_from_file_location('preview_inspector', 'ci/stage_rootless_phone_preview.py')
    inspector = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(inspector)
    for role, value in manifest['apks'].items():
        actual = inspector.inspect(work / 'phone' / value['file'], Path(args.sdk), value['package'],
                                   value['version_name'], value['version_code'], value['certificate_sha256'])
        require(actual == {k: v for k, v in value.items() if k != 'file'}, 'Actual phone APK differs: ' + role)
    host = work / 'phone' / manifest['apks']['host']['file']
    contents = inspector.non_signature_entries(host)
    require(len(contents) == 415 and contents == inspector.non_signature_entries(work / 'own/probeHost-debug.apk'),
            'Actual host differs from matched CI APK')
    release = release_for_tag(config)
    require(release is not None, 'Expected draft missing')
    validate_release(release, config, manifest, draft=True, complete=True)
    save(work / 'release-before.json', release)
    for asset in release['assets']:
        path = work / ('server-' + str(asset['id']) + '.apk')
        download('releases/assets/' + str(asset['id']), path)
        require(path.read_bytes() == (work / 'phone' / asset['name']).read_bytes(), 'Server asset bytes differ')
    tag_path = 'git/ref/tags/' + config['tag']
    try:
        tag = api(tag_path)
    except subprocess.CalledProcessError as error:
        require(b'HTTP 404' in error.stderr, 'Could not verify draft tag')
    else:
        require(tag['object']['type'] == 'commit' and tag['object']['sha'] == config['runtime_commit'], 'Tag source differs')

    # A later code change or policy stop invalidates this pending delivery.
    live_policy_matches(config_path, config)
    run(['git', 'fetch', '--no-tags', 'origin', config['branch']])
    own = json.loads((work / 'own/manifest.json').read_text())
    for name, digest in own['sources_sha256'].items():
        require(sha(run(['git', 'show', 'FETCH_HEAD:research/container-api33/' + name])) == digest,
                'Research inputs advanced; preserve the draft')
    require(not run(['git', 'diff', '--name-only', config['runtime_commit'], 'FETCH_HEAD', '--',
                    'app', 'externalDemo', 'build.gradle', 'settings.gradle', 'gradle', 'gradle.properties',
                    'gradlew', 'gradlew.bat',
                    'docs/build-profiles/main-preview-2.4.35.init.gradle']).strip(), 'Main source advanced')
    receipt = {'schema': 'GOGOGO_MATCHED_PREVIEW_PUBLICATION_V1', 'runtime_commit': config['runtime_commit'],
               'qualification_run_id': config['qualification_run_id'], 'delivery_commit': args.delivery_commit,
               'delivery_run_id': args.delivery_run, 'release_id': release['id'], 'tag': config['tag'],
               'all_four_archive_sources_signatures_materials_verified': True, 'host_415_entries_equal': True,
               'native_seven_methods_and_real_guest_nine_paths_verified': True, 'apks': manifest['apks'],
               'previous_failed_results_preserved': True, 'private_key_used': False,
               'target_sdk_or_vivo_business_verified': False, 'published': False}
    save(work / 'qualified-before-publication.json', receipt)
    notes = Path(config['notes_path']).read_text() + '\n发版前实际核对：同源CI56的Mac构建、Linux7定位／19无线／447主检查及ARM64原包对照／7方法／独立Guest九路径通过；原包300秒主身份、系统稳定、清理、两包签名／415内容与服务器字节均已核对。云端通过不等于微信／抖音定位业务或vivo通过。\n'
    (work / 'published-notes.txt').write_text(notes)
    live_policy_matches(config_path, config)
    subprocess.run(['gh', 'release', 'edit', config['tag'], '--repo', REPO, '--title', config['title'],
                    '--notes-file', str(work / 'published-notes.txt'), '--draft=false', '--prerelease'], check=True)
    after = api('releases/' + str(release['id']))
    validate_release(after, config, manifest, draft=False, complete=True)
    tag = api(tag_path)
    require(tag['object']['type'] == 'commit' and tag['object']['sha'] == config['runtime_commit'], 'Published tag differs')
    save(work / 'release-after.json', after)
    receipt.update(published=True, published_at=after['published_at'], url=after['html_url'], actual_tag=tag['object']['sha'])
    save(work / 'published-release-receipt.json', receipt)
    print(json.dumps({'published': True, 'release_id': after['id'], 'tag': config['tag']}))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('operation', choices=['stage', 'publish'])
    parser.add_argument('--config', required=True)
    parser.add_argument('--work', required=True)
    parser.add_argument('--package-dir')
    parser.add_argument('--sdk')
    parser.add_argument('--delivery-run', type=int, required=True)
    parser.add_argument('--delivery-commit')
    args = parser.parse_args()
    config_path = Path(args.config)
    config = json.loads(config_path.read_text())
    require(config['schema'] == 'GOGOGO_MATCHED_PREVIEW_DELIVERY_POLICY_V1'
            and config['publish_enabled'] is True and config['repository'] == REPO
            and re.fullmatch('[0-9a-f]{40}', config['runtime_commit'])
            and config['tag'] == 'preview-2.4.35-container-' + config['runtime_commit'][:8], 'Policy disabled or identity differs')
    require(0 < config['wait_seconds'] <= 9000 and 1 <= config['poll_seconds'] <= 60, 'Wait is not bounded')
    require(args.delivery_run > 0 and (args.package_dir if args.operation == 'stage'
            else args.sdk and args.delivery_commit and re.fullmatch('[0-9a-f]{40}', args.delivery_commit)),
            'Required staging/publication inputs missing')
    try:
        (stage if args.operation == 'stage' else publish)(args, config, config_path)
    except Exception as error:
        work = Path(args.work)
        if work.is_dir():
            (work / 'failure.json').write_text(json.dumps({'operation': args.operation,
                'error': type(error).__name__ + ': ' + str(error)[:700], 'published_not_assumed': True,
                'runtime_commit': config['runtime_commit'], 'qualification_run_id': config['qualification_run_id']}, indent=2) + '\n')
        raise


if __name__ == '__main__':
    main()
