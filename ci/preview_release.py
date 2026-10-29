#!/usr/bin/env python3
"""Qualify pinned phone packages, then publish or deliver the verified artifact."""
import argparse
import base64
import hashlib
import importlib.util
import json
import os
import re
import shutil
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


def preview_metadata(config):
    """Return exact current pins while retaining legacy qualification receipts."""
    version = config.get('expected_apks', {}).get('main', {}).get('version_name', '2.4.35-container-preview')
    require(re.fullmatch(r'2\.4\.[0-9]+-container-preview', version), 'Unexpected main preview version')
    profile = config.get('metadata_profile_path', 'docs/build-profiles/main-preview-2.4.35.init.gradle')
    require(profile == 'docs/build-profiles/main-preview-' + version.removesuffix('-container-preview') + '.init.gradle',
            'Frozen preview profile differs from version')
    count = config.get('host_ci_non_signature_entries', 415)
    require(type(count) is int and 1 <= count <= 10000, 'Frozen host entry count missing or invalid')
    return {'version': version.removesuffix('-container-preview'), 'profile': profile,
            'profile_file': Path(profile).name, 'host_entries': count}


def verify_visual_layout(folder, expected_pages):
    """New UI candidates require the complete same-source raw layout result."""
    require(expected_pages == 20, 'Unexpected visual qualification profile')
    value = json.loads((folder / 'result.json').read_text())
    runner = json.loads((folder / 'runner-result.json').read_text())
    profiles = ['light-360', 'dark-360', 'light-large-320', 'dark-large-320']
    stages = ['map', 'search', 'preview', 'keyboard', 'keyboard-longitude']
    names = {p + '-' + s for p in profiles for s in stages}
    pages = value['pages']
    require(value['passed'] is True and value['issues'] == [] and value['expected_page_count'] == expected_pages
            and len(pages) == expected_pages and {p['name'] for p in pages} == names,
            'Every visual profile and both coordinate keyboard stages must pass')
    require(all(p['input_method_window_visible'] is True for p in pages if '-keyboard' in p['name'])
            and all(value[k] is False for k in ['guest_imported', 'guest_launched', 'location_permission_requested',
                                              'target_sdk_adoption_verified']), 'Visual keyboard or read-only scope differs')
    require(runner['passed'] is True and runner['observed'] == value and runner['display_settings_restored'] is True
            and runner['system_server_before'] == runner['system_server_after']
            and re.fullmatch('[1-9][0-9]*', runner['system_server_before'])
            and set(runner['owned_package_cleanup']) == {'com.soozooq.gogogo.test',
                'com.soozooq.tianwaitian.containerresearch', 'com.soozooq.tianwaitian.containerresearch.test'}
            and all(v is True for v in runner['owned_package_cleanup'].values()),
            'Visual system stability, cleanup or settings restore failed')
    require({p.stem for p in folder.glob('*.png')} == names
            and {p.stem for p in folder.glob('*.xml')} == names
            and all((folder / (name + ext)).stat().st_size > 0 for name in names for ext in ['.png', '.xml']),
            'Complete actual visual image/hierarchy set missing')
    return {'passed': True, 'page_count': expected_pages, 'profiles': profiles,
            'both_coordinates_edited_with_real_keyboard': True,
            'scope': 'SELF_OWNED_UI_ONLY_NOT_TARGET_LOCATION'}


def run(command, **kwargs):
    return subprocess.check_output(command, stderr=subprocess.PIPE, **kwargs)


def api(path, method='GET', data=None):
    command = ['gh', 'api', '--method', method, 'repos/' + REPO + '/' + path]
    if data is not None:
        command += ['--input', '-']
    try:
        result = run(command, input=json.dumps(data).encode() if data is not None else None)
    except subprocess.CalledProcessError as error:
        detail = (error.output or b'').decode(errors='replace') + '\n' + (error.stderr or b'').decode(errors='replace')
        error.add_note('GitHub API ' + method + ' ' + path + ': ' + detail[:2500])
        raise
    return json.loads(result) if result.strip() else {}


def save(path, value):
    with path.open('x') as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write('\n')


def download(path, destination):
    command = ['gh', 'api']
    if re.fullmatch(r'releases/assets/[1-9][0-9]*', path):
        command += ['-H', 'Accept: application/octet-stream']
    else:
        require(re.fullmatch(r'actions/artifacts/[1-9][0-9]*/zip', path), 'Unsupported binary download endpoint')
        # Actions ZIPs use the normal GitHub API media type before redirecting.
        # An octet-stream Accept header on this endpoint is rejected with HTTP415.
    command += ['repos/' + REPO + '/' + path]
    with destination.open('xb') as stream:
        subprocess.run(command, stdout=stream, check=True)


def delivery_reference(config, current_run, current_commit):
    pin = config.get('reuse_delivery')
    if pin is None:
        require(type(current_run) is int and current_run > 0
                and re.fullmatch('[0-9a-f]{40}', current_commit), 'Current delivery identity missing')
        return {'run_id': current_run, 'commit': current_commit, 'reused': False}
    require(pin['runtime_commit'] == config['runtime_commit']
            and re.fullmatch('[0-9a-f]{40}', pin['commit'])
            and re.fullmatch('[0-9a-f]{64}', pin['artifact_sha256'])
            and all(type(pin[k]) is int and pin[k] > 0
                    for k in ['run_id', 'stage_job_id', 'artifact_id', 'artifact_bytes']),
            'Frozen reusable delivery identity differs')
    return {'run_id': pin['run_id'], 'commit': pin['commit'], 'reused': True}


def validate_reused_delivery(config, run_id, commit, stage, artifact):
    if 'reuse_delivery' not in config:
        return
    pin = config['reuse_delivery']
    ref = delivery_reference(config, run_id, commit)
    require(run_id == ref['run_id'] and commit == ref['commit']
            and stage['id'] == pin['stage_job_id'] and stage['conclusion'] == 'success'
            and artifact['id'] == pin['artifact_id']
            and artifact['size_in_bytes'] == pin['artifact_bytes']
            and artifact['digest'] == 'sha256:' + pin['artifact_sha256'],
            'Reused package, staging job or frozen archive differs')


def save_qualified_release_refusal(error, work, qualified_dir, qualified):
    """A known creation refusal may end in artifact delivery, never publication."""
    if not isinstance(error, subprocess.CalledProcessError):
        return False
    command = ['gh', 'api', '--method', 'POST', 'repos/' + REPO + '/releases', '--input', '-']
    try:
        response = json.loads(error.output or b'{}')
    except (ValueError, TypeError):
        return False
    if error.cmd != command or not isinstance(response, dict) \
            or response.get('message') != 'Resource not accessible by integration' \
            or str(response.get('status')) != '403' or b'HTTP 403' not in (error.stderr or b''):
        return False
    contents_verified = qualified.get('profile_main447lint_versions_signatures_and_host_contents_verified',
        qualified.get('profile_main447lint_versions_signatures_and_415_entries_verified'))
    require(qualified_dir.is_dir() and qualified['published'] is False and contents_verified is True
            and all(qualified[k] is True for k in [
                'all_four_archive_sources_signatures_materials_verified',
                'native_seven_methods_and_real_guest_nine_paths_verified']),
            'Artifact fallback requires full existing qualification')
    receipt = {**qualified, 'schema': 'GOGOGO_QUALIFIED_ARTIFACT_RELEASE_UNAVAILABLE_V1',
               'delivery_state': 'QUALIFIED_ARTIFACT_READY_RELEASE_CREATION_REFUSED',
               'qualified_download_ready': True, 'published': False,
               'release_server_bytes_and_tag': 'NOT_VERIFIED_RELEASE_CREATION_REFUSED_HTTP403',
               'release_error': response, 'api_details': getattr(error, '__notes__', [])}
    save(work / 'release-unavailable.json', receipt)
    save(qualified_dir / 'DELIVERY-STATUS.json', receipt)
    print(json.dumps({'qualified_download_ready': True, 'published': False,
                      'release_creation': 'REFUSED_HTTP403_RESOURCE_NOT_ACCESSIBLE_BY_INTEGRATION'}))
    return True


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
    require(value.get('delivery_generation', 1) == config.get('delivery_generation', 1),
            'Phone packaging generation differs')
    require(value['main_checks'] == {'junit': COUNTS, 'lint_error_fatal_count': 0},
            'Phone main checks did not pass')
    require(value['host_original_ci_run'] == config['qualification_run_id']
            and value['host_ci_non_signature_entries_equal'] == preview_metadata(config)['host_entries']
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


def attach_draft(config, manifest, package, work):
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
    return after


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
    after = attach_draft(config, manifest, package, work)
    print(json.dumps({'draft_staged': True, 'release_id': after['id'], 'published': False}))


def latest_jobs(value):
    latest = {}
    for job in value['jobs']:
        old = latest.get(job['name'])
        if old is None or (job.get('run_attempt', 1), job['id']) > (old.get('run_attempt', 1), old['id']):
            latest[job['name']] = job
    return latest


def verify_container_main_hold(hold, restart, package):
    require(hold['passed'] is True and hold['requested_seconds'] == 300
            and hold['observed_ms'] >= 300000
            and hold['identity_basis'] == 'CORE_REGISTERED_MAIN_PROCESS_AND_ACTUAL_ACTIVITY_MANAGER_PID_UID',
            'Container main-role observation did not pass')
    main, uid = hold['main_pid'], hold['main_uid']
    require(type(main) is int and main > 0 and type(uid) is int and uid >= 10000
            and main == restart['previous_main_pid'] and uid == restart['main_uid'],
            'Held main differs from the closed-mode process used for restart')
    for field in ['initial_registered_processes', 'last_registered_processes']:
        records = hold[field]
        require(isinstance(records, list) and 0 < len(records) <= 32,
                'Missing or unbounded main-role snapshot')
        roles = [v for v in records if v['process_name'] == package]
        require(len(roles) == 1 and roles[0]['pid'] == main and roles[0]['uid'] == uid,
                'Actual registered main changed, is ambiguous, or has another UID')
    # pidof sets may gain/lose same-name auxiliary processes during the hold.
    # The actual AMS/Core main role and UID, not complete set equality, are required.


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
    metadata = preview_metadata(config)
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
    visual = None
    if config.get('required_visual_page_count') is not None:
        visual = verify_visual_layout(work / 'linux/visual-layout', config['required_visual_page_count'])
        save(work / 'visual-qualified.json', visual)
    subprocess.run([sys.executable, 'ci/qualify_native_preview.py', '--config', str(config_path),
                    '--proof', str(work / 'verified.json'),
                    '--raw', str(work / 'native'), '--output', str(work / 'native-qualified.json')], check=True)
    for name, count in [('x86_64-location-contract', 7), ('x86_64-radio-privacy', 19)]:
        text = (work / 'linux' / name / 'cloud-instrumentation.txt').read_text()
        require(re.findall(r'^OK \((\d+) tests?\)$', text, re.M) == [str(count)]
                and 'FAILURES!!!' not in text, 'Required Linux methods differ')

    # A frozen replay reuses exact already-built bytes, while repeating every gate.
    delivery = api('actions/runs/' + str(args.delivery_run))
    require(delivery['head_sha'] == args.delivery_commit, 'Delivery workflow source differs')
    jobs = api('actions/runs/' + str(args.delivery_run) + '/jobs?filter=all&per_page=100')
    stage_job = latest_jobs(jobs)['stage-draft']
    require(stage_job['conclusion'] == 'success', 'Draft staging did not succeed')
    values = api('actions/runs/' + str(args.delivery_run) + '/artifacts?per_page=100')['artifacts']
    matches = [v for v in values if v['name'] == config['delivery_artifact_name']]
    require(len(matches) == 1, 'Ambiguous/missing current phone artifact')
    item = matches[0]
    validate_reused_delivery(config, args.delivery_run, args.delivery_commit, stage_job, item)
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
        'candidate-manifest.json', 'SHA256SUMS', 'PHONE-README.txt', metadata['profile_file']}
    require({p.name for p in (work / 'phone').iterdir()} == expected_files
            and all(p.is_file() for p in (work / 'phone').iterdir()), 'Phone download is not the flat six-file set')
    profile = (work / 'phone' / metadata['profile_file']).read_bytes()
    require(sha(profile) == config['profile_sha256']
            and profile == run(['git', 'show', config['runtime_commit'] + ':' + metadata['profile']]),
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
    require(len(contents) == metadata['host_entries'] and contents == inspector.non_signature_entries(work / 'own/probeHost-debug.apk'),
            'Actual host differs from matched CI APK')
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
                    metadata['profile']]).strip(), 'Main source advanced')
    qualified = {'schema': 'GOGOGO_MATCHED_QUALIFIED_PHONE_ARTIFACT_V1',
                 'runtime_commit': config['runtime_commit'], 'qualification_run_id': config['qualification_run_id'],
                 'delivery_commit': args.delivery_commit, 'delivery_run_id': args.delivery_run,
                 'qualification_execution_run_id': int(os.environ['GITHUB_RUN_ID']) if os.environ.get('GITHUB_RUN_ID') else None,
                 'qualification_execution_commit': os.environ.get('GITHUB_SHA'),
                 'all_four_archive_sources_signatures_materials_verified': True,
                 'native_seven_methods_and_real_guest_nine_paths_verified': True,
                 'profile_main447lint_versions_signatures_and_host_contents_verified': True,
                 'host_ci_non_signature_entry_count': metadata['host_entries'],
                 'apks': manifest['apks'], 'published': False,
                 'release_server_bytes_and_tag': 'NOT_YET_VERIFIED',
                 'target_sdk_or_vivo_business_verified': False}
    if metadata['host_entries'] == 415:
        qualified['profile_main447lint_versions_signatures_and_415_entries_verified'] = True
    if visual is not None:
        qualified['visual_layout'] = visual
    save(work / 'qualified-phone.json', qualified)
    qualified_dir = work / 'qualified-phone'
    qualified_dir.mkdir()
    for name in expected_files:
        shutil.copyfile(work / 'phone' / name, qualified_dir / name)
    save(qualified_dir / 'QUALIFICATION-RECEIPT.json', qualified)
    # Release attachment is separate from this already-verified phone artifact.
    live_policy_matches(config_path, config)
    draft_work = work / 'release-draft'
    draft_work.mkdir()
    try:
        release = attach_draft(config, manifest, work / 'phone', draft_work)
    except subprocess.CalledProcessError as error:
        if save_qualified_release_refusal(error, work, qualified_dir, qualified):
            return
        raise
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

    receipt = {'schema': 'GOGOGO_MATCHED_PREVIEW_PUBLICATION_V1', 'runtime_commit': config['runtime_commit'],
               'qualification_run_id': config['qualification_run_id'], 'delivery_commit': args.delivery_commit,
               'delivery_run_id': args.delivery_run, 'release_id': release['id'], 'tag': config['tag'],
               'all_four_archive_sources_signatures_materials_verified': True,
               'host_non_signature_entries_equal': metadata['host_entries'],
               'native_seven_methods_and_real_guest_nine_paths_verified': True, 'apks': manifest['apks'],
               'previous_failed_results_preserved': True, 'private_key_used': False,
               'target_sdk_or_vivo_business_verified': False, 'published': False}
    save(work / 'qualified-before-publication.json', receipt)
    notes = Path(config['notes_path']).read_text() + '\n发版前实际核对：同源CI' + str(config['qualification_round']) + '的Mac构建、Linux7定位／19无线／447主检查及ARM64原包对照／7方法／独立Guest九路径通过；原包300秒主身份、系统稳定、清理、两包签名／' + str(metadata['host_entries']) + '份非签名内容与服务器字节均已核对。云端通过不等于微信／抖音定位业务或vivo通过。\n'
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
    metadata = preview_metadata(config)
    require(config['schema'] == 'GOGOGO_MATCHED_PREVIEW_DELIVERY_POLICY_V1'
            and config['publish_enabled'] is True and config['repository'] == REPO
            and re.fullmatch('[0-9a-f]{40}', config['runtime_commit'])
            and config['tag'] == 'preview-' + metadata['version'] + '-container-' + config['runtime_commit'][:8], 'Policy disabled or identity differs')
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
                'runtime_commit': config['runtime_commit'], 'qualification_run_id': config['qualification_run_id'],
                'api_details': getattr(error, '__notes__', [])}, indent=2) + '\n')
        raise


if __name__ == '__main__':
    main()
