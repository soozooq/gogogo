"""Public viewer/transport fixtures: no credentials, network or actual artifact download."""
import importlib.util
import io
import json
from pathlib import Path
import unittest
from unittest.mock import patch
from urllib.error import HTTPError

spec = importlib.util.spec_from_file_location('public_artifacts',
    Path(__file__).resolve().parents[1] / 'tools/fetch-cuttlefish-inputs.py')
fetch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fetch)


class PublicArtifactInputTest(unittest.TestCase):
    def setUp(self):
        self.build = {'public_page': 'https://ci.android.com/public/',
                      'build_id': '123', 'branch': 'fixed', 'target': 'fixed-userdebug'}
        self.entry = {'name': 'image.zip'}
        self.link = 'https://storage.googleapis.com/public/image.zip?Signature=fixture'

    def page(self, url=None, **changes):
        metadata = {'buildId': '123', 'branch': 'fixed', 'target': 'fixed-userdebug',
                    'forceLogin': False, 'idToken': None, 'artifactUrl': url or self.link}
        metadata.update(changes)
        return io.BytesIO(('var JSVariables = ' + json.dumps(metadata) + ';\n').encode())

    def test_transient_link_refresh_uses_same_public_page_and_all_checks(self):
        with patch.object(fetch, 'public_open', side_effect=[self.page('about:blank'), self.page()]) as open_, \
                patch.object(fetch.time, 'sleep') as sleep:
            self.assertEqual(self.link, fetch.artifact_download_url(self.build, self.entry))
            self.assertEqual([self.build['public_page'] + self.entry['name']] * 2,
                             [x.args[0].full_url for x in open_.call_args_list])
            sleep.assert_called_once_with(2)

    def test_access_and_build_rejection_are_final_without_refresh(self):
        for changes in [{'forceLogin': True}, {'idToken': 'not-accepted'}, {'buildId': 'other'}]:
            with self.subTest(changes=changes), patch.object(fetch, 'public_open', return_value=self.page(**changes)) as open_, \
                    patch.object(fetch.time, 'sleep') as sleep:
                with self.assertRaisesRegex(RuntimeError, 'identity/access'):
                    fetch.artifact_download_url(self.build, self.entry)
                self.assertEqual(1, open_.call_count); sleep.assert_not_called()

    def test_untrusted_or_wrong_name_links_are_never_followed(self):
        for link in ['http://storage.googleapis.com/public/image.zip',
                     'https://unrelated.invalid/public/image.zip',
                     'https://user@storage.googleapis.com/public/image.zip',
                     'https://storage.googleapis.com/public/other.zip']:
            with self.subTest(link=link), patch.object(fetch, 'public_open',
                    side_effect=[self.page(link) for _ in range(3)]) as open_, patch.object(fetch.time, 'sleep'):
                with self.assertRaisesRegex(RuntimeError, 'after 3 public reads'):
                    fetch.artifact_download_url(self.build, self.entry)
                self.assertTrue(all(x.args[0].full_url == self.build['public_page'] + self.entry['name']
                                    for x in open_.call_args_list))

    def test_http_rate_limit_respects_bounded_retry_after_and_auth_is_final(self):
        error = HTTPError('https://ci.android.com/public/', 429, 'limited',
                          {'Retry-After': '120'}, io.BytesIO())
        with patch.object(fetch.urllib.request, 'urlopen', side_effect=[error, 'ready']) as open_, \
                patch.object(fetch.time, 'sleep') as sleep, patch('builtins.print'):
            self.assertEqual('ready', fetch.public_open('https://ci.android.com/public/', 5))
            sleep.assert_called_once_with(30); self.assertEqual(2, open_.call_count)
        for code in [401, 403]:
            with self.subTest(code=code), patch.object(fetch.urllib.request, 'urlopen',
                    side_effect=HTTPError('https://ci.android.com/public/', code, 'denied', {}, io.BytesIO())) as open_, \
                    patch.object(fetch.time, 'sleep') as sleep:
                with self.assertRaises(HTTPError): fetch.public_open('https://ci.android.com/public/', 5)
                self.assertEqual(1, open_.call_count); sleep.assert_not_called()
