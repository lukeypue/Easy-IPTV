"""Offline publisher regressions: no GitHub calls or release writes."""
import base64
import copy
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import publish_v481 as publisher


class PublisherTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        for name in ('RYZOD-v4.81.apk', 'RYZOD-v4.81-FireTV.apk'):
            (self.directory / name).write_bytes(name.encode())
        self.manifest = publisher.build_manifest(self.directory)
        self.current = {name: dict(self.manifest, versionCode=104, versionName='4.80')
                        for name in publisher.MANIFESTS}
        self.release = {'draft': False, 'prerelease': False, 'tag_name': 'v4.81',
                        'assets': [
                            {'name': name, 'size': (self.directory / name).stat().st_size,
                             'digest': 'sha256:' + self.manifest[digest],
                             'browser_download_url': self.manifest[url]}
                            for name, digest, url in (
                                ('RYZOD-v4.81.apk', 'sha256', 'downloadUrl'),
                                ('RYZOD-v4.81-FireTV.apk', 'sha256Tv', 'downloadUrlTv'))]}
        self.calls = []
        self.api_patch = patch.object(publisher, 'api', side_effect=self.api)
        self.api_patch.start()
        self.addCleanup(self.api_patch.stop)

    def api(self, endpoint, payload=None, method=None):
        self.calls.append((endpoint, payload, method))
        if endpoint == 'git/ref/heads/main':
            return {'object': {'sha': 'head'}}
        if endpoint.startswith('contents/'):
            name = endpoint.split('/')[1].split('?')[0]
            return {'encoding': 'base64', 'content': base64.b64encode(
                json.dumps(self.current[name]).encode()).decode()}
        if endpoint == 'releases/tags/v4.81':
            return self.release
        if endpoint == 'git/commits/head':
            return {'tree': {'sha': 'base'}}
        return {'sha': 'created'}

    def writes(self):
        return [call for call in self.calls if call[1] is not None]

    def test_promotes_both_manifests_in_one_non_force_commit(self):
        publisher.publish(self.directory)
        writes = self.writes()
        self.assertEqual([c[0] for c in writes],
                         ['git/trees', 'git/commits', 'git/refs/heads/main'])
        entries = writes[0][1]['tree']
        self.assertEqual([entry['path'] for entry in entries], list(publisher.MANIFESTS))
        self.assertTrue(all(json.loads(entry['content']) == self.manifest for entry in entries))
        self.assertEqual(writes[1][1]['parents'], ['head'])
        self.assertEqual(writes[-1], ('git/refs/heads/main',
                                      {'sha': 'created', 'force': False}, 'PATCH'))
        self.assertTrue(all('?ref=head' in c[0] for c in self.calls
                            if c[0].startswith('contents/')))

    def test_preflight_never_writes_or_requires_uploaded_release(self):
        publisher.publish(self.directory, check_only=True)
        self.assertEqual(self.writes(), [])
        self.assertNotIn('releases/tags/v4.81', [c[0] for c in self.calls])

    def test_release_verification_only_never_writes(self):
        publisher.publish(self.directory, verify_only=True)
        self.assertEqual(self.writes(), [])
        self.assertIn('releases/tags/v4.81', [c[0] for c in self.calls])

    def test_downgrade_in_either_manifest_is_rejected_before_writes(self):
        for name in publisher.MANIFESTS:
            with self.subTest(name=name):
                self.current[name]['versionCode'] = 106
                with self.assertRaisesRegex(ValueError, 'downgrade'):
                    publisher.publish(self.directory)
                self.assertEqual(self.writes(), [])
                self.current[name]['versionCode'] = 104

    def test_equal_version_conflicts_are_rejected_before_writes(self):
        for name in publisher.MANIFESTS:
            for field in ('sha256', 'sha256Tv', 'downloadUrl', 'downloadUrlTv', 'versionName'):
                with self.subTest(name=name, field=field):
                    self.current[name] = dict(self.manifest, **{field: 'conflict'})
                    with self.assertRaisesRegex(ValueError, 'conflict'):
                        publisher.publish(self.directory)
                    self.assertEqual(self.writes(), [])
                    self.current[name] = dict(self.manifest, versionCode=104)

    def test_identical_release_is_idempotent(self):
        self.current = {name: self.manifest.copy() for name in publisher.MANIFESTS}
        publisher.publish(self.directory)
        self.assertEqual(self.writes(), [])
        self.assertIn('releases/tags/v4.81', [c[0] for c in self.calls])

    def test_partial_previous_promotion_repairs_both_manifests(self):
        self.current['latest.json'] = self.manifest.copy()
        publisher.publish(self.directory)
        self.assertEqual(len(self.writes()[0][1]['tree']), 2)

    def test_malformed_current_versions_fail_closed(self):
        for value in (None, True, '105', -1):
            with self.subTest(value=value):
                self.current['latest-v2.json']['versionCode'] = value
                with self.assertRaises(ValueError):
                    publisher.publish(self.directory)
                self.assertEqual(self.writes(), [])

    def test_unverified_release_is_rejected_before_writes(self):
        original = copy.deepcopy(self.release)
        for field, value in (('draft', True), ('prerelease', True), ('tag_name', 'v4.80')):
            with self.subTest(field=field):
                self.release = dict(original, **{field: value})
                with self.assertRaises(ValueError):
                    publisher.publish(self.directory)
                self.assertEqual(self.writes(), [])

    def test_missing_or_conflicting_assets_fail_closed(self):
        original = copy.deepcopy(self.release)
        for index in (0, 1):
            for field, value in (('digest', 'sha256:bad'), ('size', 0),
                                 ('browser_download_url', 'https://example.com/file.apk')):
                with self.subTest(index=index, field=field):
                    self.release = copy.deepcopy(original)
                    self.release['assets'][index][field] = value
                    with self.assertRaises(ValueError):
                        publisher.publish(self.directory)
                    self.assertEqual(self.writes(), [])
        self.release = dict(original, assets=[])
        with self.assertRaises(ValueError):
            publisher.publish(self.directory)
        self.assertEqual(self.writes(), [])

    def test_manifest_read_failure_never_writes(self):
        with patch.object(publisher, 'api', side_effect=RuntimeError('API failed')):
            with self.assertRaises(RuntimeError):
                publisher.publish(self.directory)
        self.assertEqual(self.writes(), [])

    def test_concurrent_ref_update_failure_is_not_force_retried(self):
        original_api = self.api

        def reject_patch(endpoint, payload=None, method=None):
            result = original_api(endpoint, payload, method)
            if method == 'PATCH':
                raise subprocess.CalledProcessError(1, ['gh', 'api'])
            return result

        with patch.object(publisher, 'api', side_effect=reject_patch):
            with self.assertRaises(subprocess.CalledProcessError):
                publisher.publish(self.directory)
        self.assertEqual(len([c for c in self.calls if c[2] == 'PATCH']), 1)
        self.assertFalse(self.writes()[-1][1]['force'])


class ApiTest(unittest.TestCase):
    def test_api_passes_explicit_patch_and_json_to_gh(self):
        with patch.object(publisher.subprocess, 'check_output', return_value=b'{"sha":"ok"}') as call:
            self.assertEqual(publisher.api('git/refs/heads/main', {'force': False}, 'PATCH'),
                             {'sha': 'ok'})
        args, kwargs = call.call_args
        self.assertIn('PATCH', args[0])
        self.assertEqual(json.loads(kwargs['input']), {'force': False})


if __name__ == '__main__':
    unittest.main()
