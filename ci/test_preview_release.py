"""Publication boundaries: keep failed, stale or changed candidates unpublished."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('preview_release', Path(__file__).with_name('preview_release.py'))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class PublicationBoundaryTest(unittest.TestCase):
    def test_native_qualifier_rejects_other_source_before_reading_raw_runtime(self):
        import subprocess
        import sys
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            config = {'qualification_round': 57, 'qualification_run_id': 57,
                      'qualification_job_ids': {'native': 123}, 'runtime_commit': 'a' * 40}
            (root / 'policy.json').write_text(json.dumps(config))
            (root / 'proof.json').write_text(json.dumps({'integrity_verified': True,
                'commit': 'b' * 40, 'run_id': 57}))
            output = root / 'output.json'
            actual = subprocess.run([sys.executable, str(Path(__file__).with_name('qualify_native_preview.py')),
                '--config', str(root / 'policy.json'), '--proof', str(root / 'proof.json'),
                '--raw', str(root / 'does-not-exist'), '--output', str(output)], capture_output=True, text=True)
            self.assertNotEqual(0, actual.returncode)
            self.assertIn('Native proof source/run differs from frozen policy', actual.stderr)
            self.assertNotIn('FileNotFoundError', actual.stderr)
            self.assertFalse(output.exists())

    def test_container_hold_accepts_auxiliary_pid_changes_but_rejects_main_replacement(self):
        hold = {'passed': True, 'requested_seconds': 300, 'observed_ms': 300635,
                'identity_basis': 'CORE_REGISTERED_MAIN_PROCESS_AND_ACTUAL_ACTIVITY_MANAGER_PID_UID',
                'main_pid': 9841, 'main_uid': 10102,
                'initial_target_pid': '9841 10504', 'last_target_pid': '9841',
                'initial_registered_processes': [{'pid': 9841, 'uid': 10102, 'process_name': 'target'}],
                'last_registered_processes': [{'pid': 9841, 'uid': 10102, 'process_name': 'target'}]}
        restart = {'previous_main_pid': 9841, 'main_uid': 10102}
        release.verify_container_main_hold(hold, restart, 'target')
        for label, change in [
                ('main_changed_even_when_old_pid_still_listed', lambda h: h['last_registered_processes'][0].update(pid=10504)),
                ('uid_changed', lambda h: h['last_registered_processes'][0].update(uid=10103)),
                ('missing_main', lambda h: h.update(last_registered_processes=[])),
                ('ambiguous_main', lambda h: h['last_registered_processes'].append(dict(h['last_registered_processes'][0]))),
                ('short_hold', lambda h: h.update(observed_ms=299999)),
                ('wrong_main_role_basis', lambda h: h.update(identity_basis='PIDOF_SET_ONLY'))]:
            h = copy.deepcopy(hold); change(h)
            with self.subTest(label=label), self.assertRaises(ValueError):
                release.verify_container_main_hold(h, restart, 'target')

    def test_api_failure_preserves_bounded_server_reason_and_original_error_type(self):
        import subprocess
        error = subprocess.CalledProcessError(1, ['gh', 'api'],
            output=b'{"message":"example service refusal"}', stderr=b'gh: example (HTTP 403)')
        with patch.object(release, 'run', side_effect=error), self.assertRaises(subprocess.CalledProcessError) as caught:
            release.api('releases', 'POST', {'draft': True})
        self.assertIn('HTTP 403', '\n'.join(caught.exception.__notes__))
        self.assertIn('example service refusal', '\n'.join(caught.exception.__notes__))

    def test_only_never_started_mac_capacity_failure_after_native_success_can_retry(self):
        config = {'qualification_run_id': 56, 'runtime_commit': 'a' * 40,
                  'qualification_job_ids': {'linux': 1, 'native': 2},
                  'mac_job_name': 'Mac', 'mac_initial_job_id': 3}
        workflow = {'id': 56, 'head_sha': 'a' * 40, 'status': 'completed', 'run_attempt': 1}
        jobs = {'Linux': {'id': 1, 'conclusion': 'success'},
                'Native': {'id': 2, 'conclusion': 'success'},
                'Mac': {'id': 3, 'status': 'completed', 'conclusion': 'cancelled', 'runner_id': 0, 'steps': []}}
        annotations = [{'message': 'The job was not acquired by Runner of type hosted even after multiple attempts'}]
        self.assertTrue(release.mac_capacity_retry_allowed(workflow, jobs, config, annotations))
        for name, change in [('source', lambda w, j: w.update(head_sha='b' * 40)),
                             ('still_running', lambda w, j: w.update(status='in_progress')),
                             ('already_retried', lambda w, j: w.update(run_attempt=2)),
                             ('native_failed_like_ci53', lambda w, j: j['Native'].update(conclusion='failure')),
                             ('native_skipped_like_ci55', lambda w, j: j['Native'].update(conclusion='skipped')),
                             ('linux_failed', lambda w, j: j['Linux'].update(conclusion='failure')),
                             ('runner_started', lambda w, j: j['Mac'].update(runner_id=4)),
                             ('compilation_started', lambda w, j: j['Mac'].update(steps=[{'name': 'Build'}])),
                             ('different_mac_job', lambda w, j: j['Mac'].update(id=5))]:
            w, j = copy.deepcopy(workflow), copy.deepcopy(jobs)
            change(w, j)
            with self.subTest(name=name):
                self.assertFalse(release.mac_capacity_retry_allowed(w, j, config, annotations))
        self.assertFalse(release.mac_capacity_retry_allowed(workflow, jobs, config, []))

    def test_existing_assets_and_published_releases_are_preserved_on_mismatch(self):
        config = {'tag': 'preview-test', 'runtime_commit': 'a' * 40}
        manifest = {'apks': {'main': {'file': 'main.apk', 'bytes': 10, 'sha256': 'b' * 64},
                             'host': {'file': 'host.apk', 'bytes': 20, 'sha256': 'c' * 64}}}
        value = {'tag_name': config['tag'], 'target_commitish': config['runtime_commit'],
                 'draft': True, 'prerelease': True, 'assets': [
                     {'name': 'main.apk', 'size': 10, 'digest': 'sha256:' + 'b' * 64, 'state': 'uploaded'},
                     {'name': 'host.apk', 'size': 20, 'digest': 'sha256:' + 'c' * 64, 'state': 'uploaded'}]}
        release.validate_release(value, config, manifest, True, True)
        for name, change in [('published', lambda v: v.update(draft=False)),
                             ('other_source', lambda v: v.update(target_commitish='d' * 40)),
                             ('old_draft', lambda v: v.update(tag_name='preview-old')),
                             ('changed_bytes', lambda v: v['assets'][0].update(digest='sha256:' + '0' * 64)),
                             ('unexpected_asset', lambda v: v['assets'].append(dict(v['assets'][0], name='other.apk'))),
                             ('missing_asset', lambda v: v['assets'].pop())]:
            changed = copy.deepcopy(value)
            change(changed)
            with self.subTest(name=name), self.assertRaises(ValueError):
                release.validate_release(changed, config, manifest, True, True)
        subset = dict(value, assets=value['assets'][:1])
        release.validate_release(subset, config, manifest, True, False)

    def test_live_policy_change_stops_before_a_release_write(self):
        import base64
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'policy.json'
            path.write_text('{"publish_enabled":true}\n')
            config = {'live_config_path': 'ci/policy.json', 'branch': 'research/test'}
            with patch.object(release, 'api', return_value={
                    'encoding': 'base64', 'content': base64.b64encode(path.read_bytes()).decode()}):
                release.live_policy_matches(path, config)
            with patch.object(release, 'api', return_value={
                    'encoding': 'base64', 'content': base64.b64encode(b'{"publish_enabled":false}\n').decode()}), \
                    self.assertRaises(ValueError):
                release.live_policy_matches(path, config)

    def test_another_phone_source_or_unverified_manifest_is_rejected(self):
        config = {'runtime_commit': 'a' * 40, 'qualification_run_id': 56,
                  'profile_sha256': 'b' * 64,
                  'expected_apks': {'main': {'file': 'main.apk'},
                                    'host': {'file': 'host.apk', 'sha256': 'c' * 64, 'bytes': 20}}}
        manifest = {'source_commit': 'a' * 40, 'delivery_run_id': 7,
                    'main_checks': {'junit': dict(release.COUNTS), 'lint_error_fatal_count': 0},
                    'host_original_ci_run': 56, 'host_ci_non_signature_entries_equal': 415,
                    'private_key_used_or_included': False, 'original_target_apks_included': False,
                    'metadata_profile_sha256': 'b' * 64, 'apks': {
                        'main': {'file': 'main.apk', 'sha256': 'd' * 64, 'bytes': 10,
                                 'signature_and_alignment_verified': True},
                        'host': {'file': 'host.apk', 'sha256': 'c' * 64, 'bytes': 20,
                                 'signature_and_alignment_verified': True}}}
        release.validate_manifest(manifest, config, 7)
        for name, change in [('old_source', lambda v: v.update(source_commit='e' * 40)),
                             ('other_delivery', lambda v: v.update(delivery_run_id=6)),
                             ('failed_checks', lambda v: v['main_checks']['junit'].update(failures=1)),
                             ('unverified_apk', lambda v: v['apks']['main'].update(signature_and_alignment_verified=False)),
                             ('private_key', lambda v: v.update(private_key_used_or_included=True)),
                             ('original_targets', lambda v: v.update(original_target_apks_included=True)),
                             ('wrong_host_bytes', lambda v: v['apks']['host'].update(bytes=21))]:
            changed = copy.deepcopy(manifest)
            change(changed)
            with self.subTest(name=name), self.assertRaises(ValueError):
                release.validate_manifest(changed, config, 7)

    def test_unsafe_or_duplicate_archive_is_rejected_before_extraction(self):
        import warnings
        import zipfile
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for index, names in enumerate([['../outside'], ['/absolute'], ['a\\b'], ['same', 'same']]):
                path = root / (str(index) + '.zip')
                with warnings.catch_warnings():
                    warnings.simplefilter('ignore', UserWarning)
                    with zipfile.ZipFile(path, 'w') as archive:
                        for name in names:
                            archive.writestr(name, b'{}')
                with self.subTest(names=names), self.assertRaises(ValueError):
                    release.extract(path, root / ('output' + str(index)))
                self.assertFalse((root / ('output' + str(index))).exists())
            self.assertFalse((root / 'outside').exists())

    def test_latest_attempt_does_not_hide_successful_unrerun_native_job(self):
        jobs = {'jobs': [{'name': 'Mac', 'id': 3, 'run_attempt': 1},
                         {'name': 'Native', 'id': 2, 'run_attempt': 1},
                         {'name': 'Mac', 'id': 4, 'run_attempt': 2}]}
        self.assertEqual(4, release.latest_jobs(jobs)['Mac']['id'])
        self.assertEqual(2, release.latest_jobs(jobs)['Native']['id'])


if __name__ == '__main__':
    unittest.main()
