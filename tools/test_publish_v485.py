"""Offline publisher regressions: no GitHub calls or release writes."""
import base64
import copy
import json
import hashlib
from pathlib import Path
import subprocess
import shutil
import tempfile
import unittest
import zipfile
from unittest.mock import patch

import publish_v485 as publisher


class PublisherTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        for name in ('RYZOD-v4.85.apk', 'RYZOD-v4.85-FireTV.apk', 'RYZOD-v4.85-source.zip'):
            (self.directory / name).write_bytes(name.encode())
        self.inputs_patch = patch.object(publisher, 'verify_inputs')
        self.verify_inputs = self.inputs_patch.start()
        self.addCleanup(self.inputs_patch.stop)
        self.manifest = publisher.build_manifest(self.directory)
        self.current = {name: dict(self.manifest, versionCode=105, versionName='4.81')
                        for name in publisher.MANIFESTS}
        self.release = {'draft': False, 'prerelease': False, 'tag_name': 'v4.85',
                        'assets': [
                            {'name': name, 'size': (self.directory / name).stat().st_size,
                             'digest': 'sha256:' + self.manifest[digest],
                             'browser_download_url': self.manifest[url]}
                            for name, digest, url in (
                                ('RYZOD-v4.85.apk', 'sha256', 'downloadUrl'),
                                ('RYZOD-v4.85-FireTV.apk', 'sha256Tv', 'downloadUrlTv'))]}
        source = 'RYZOD-v4.85-source.zip'
        self.release['assets'].append({
            'name': source, 'size': (self.directory / source).stat().st_size,
            'digest': 'sha256:' + hashlib.sha256((self.directory / source).read_bytes()).hexdigest(),
            'browser_download_url': f'https://github.com/{publisher.REPO}/releases/download/v4.85/{source}'})
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
        if endpoint == 'releases/tags/v4.85':
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
        self.verify_inputs.assert_called_once_with(self.directory)
        self.assertEqual(self.writes(), [])
        self.assertNotIn('releases/tags/v4.85', [c[0] for c in self.calls])

    def test_release_verification_only_never_writes(self):
        publisher.publish(self.directory, verify_only=True)
        self.assertEqual(self.writes(), [])
        self.assertIn('releases/tags/v4.85', [c[0] for c in self.calls])

    def test_downgrade_in_either_manifest_is_rejected_before_writes(self):
        for name in publisher.MANIFESTS:
            with self.subTest(name=name):
                self.current[name]['versionCode'] = publisher.VERSION_CODE + 1
                with self.assertRaisesRegex(ValueError, 'downgrade'):
                    publisher.publish(self.directory)
                self.assertEqual(self.writes(), [])
                self.current[name]['versionCode'] = 105

    def test_equal_version_conflicts_are_rejected_before_writes(self):
        for name in publisher.MANIFESTS:
            for field in ('sha256', 'sha256Tv', 'downloadUrl', 'downloadUrlTv', 'versionName'):
                with self.subTest(name=name, field=field):
                    self.current[name] = dict(self.manifest, **{field: 'conflict'})
                    with self.assertRaisesRegex(ValueError, 'conflict'):
                        publisher.publish(self.directory)
                    self.assertEqual(self.writes(), [])
                    self.current[name] = dict(self.manifest, versionCode=105)

    def test_identical_release_is_idempotent(self):
        self.current = {name: self.manifest.copy() for name in publisher.MANIFESTS}
        publisher.publish(self.directory)
        self.assertEqual(self.writes(), [])
        self.assertIn('releases/tags/v4.85', [c[0] for c in self.calls])

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
        for index in (0, 1, 2):
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

    def test_extra_store_asset_is_rejected(self):
        self.release['assets'].append(dict(self.release['assets'][0], name='Amazon.apk'))
        with self.assertRaisesRegex(ValueError, 'exactly'):
            publisher.publish(self.directory)
        self.assertEqual(self.writes(), [])

    def test_duplicate_release_asset_is_rejected(self):
        self.release['assets'].append(self.release['assets'][0].copy())
        with self.assertRaises(ValueError):
            publisher.publish(self.directory)
        self.assertEqual(self.writes(), [])

    def test_invalid_local_input_prevents_even_remote_reads(self):
        self.verify_inputs.side_effect = ValueError('invalid input')
        with self.assertRaisesRegex(ValueError, 'invalid input'):
            publisher.publish(self.directory, check_only=True)
        self.assertEqual(self.calls, [])

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


class PackageVerificationTest(unittest.TestCase):
    def setUp(self):
        self.badging = ("package: name='com.easyiptv.player' versionCode='109' versionName='4.85'\n"
                        "targetSdkVersion:'36'\n")
        self.certs = f'Number of signers: 1\nSigner #1 certificate SHA-256 digest: {publisher.CERTIFICATE.lower()}\n'
        self.permissions = ''
        self.output_patch = patch.object(publisher, 'output', side_effect=self.output)
        self.output_patch.start()
        self.addCleanup(self.output_patch.stop)
        self.run_patch = patch.object(publisher.subprocess, 'run')
        self.run = self.run_patch.start()
        self.addCleanup(self.run_patch.stop)
        self.tool_patch = patch.object(publisher, 'android_tool', side_effect=lambda name: name)
        self.tool_patch.start()
        self.addCleanup(self.tool_patch.stop)

    def output(self, command):
        if command[0] == 'keytool':
            return 'SHA256: ' + publisher.CERTIFICATE
        if 'badging' in command:
            return self.badging
        if 'permissions' in command:
            return self.permissions
        return self.certs

    def test_apk_verifies_signature_zip_and_all_native_alignment(self):
        publisher.verify_apk(Path('/tmp/RYZOD-v4.85.apk'))
        calls = [call.args[0] for call in self.run.call_args_list]
        self.assertEqual(calls[0], ['apksigner', 'verify', '--verbose', '/tmp/RYZOD-v4.85.apk'])
        self.assertEqual(calls[1], ['zipalign', '-c', '-P', '16', '4', '/tmp/RYZOD-v4.85.apk'])
        self.assertTrue(calls[2][1].endswith('tools/verify_native_alignment.py'))
        self.assertTrue(all(call.kwargs['check'] for call in self.run.call_args_list))

    def test_wrong_package_version_code_name_or_sdk_is_rejected(self):
        original = self.badging
        for old, new in [('com.easyiptv.player', 'com.example.other'), ("'109'", "'105'"),
                         ("'4.85'", "'4.81'"), ("'36'", "'35'")]:
            with self.subTest(field=old):
                self.badging = original.replace(old, new)
                with self.assertRaises(ValueError):
                    publisher.verify_apk('RYZOD-v4.85.apk')

    def test_wrong_missing_or_multiple_signers_are_rejected(self):
        original = self.certs
        for certs in ('', original.replace(publisher.CERTIFICATE.lower(), 'deadbeef'),
                      original + original.replace('#1', '#2')):
            with self.subTest(certs=certs):
                self.certs = certs
                with self.assertRaisesRegex(ValueError, 'certificate'):
                    publisher.verify_apk('RYZOD-v4.85.apk')

    def test_sdk37_actual_public481_certificate_output_is_accepted(self):
        # Actual Build Tools 37 output for the immutable public 4.81 APK.
        # Keep the fixture independent of the configured pin to catch pin typos.
        self.certs = (
            'Number of signers: 1\n'
            'V2 Signer: certificate DN: CN=Zako Release, OU=Zako, O=Zako, L=Denver, ST=Colorado, C=US\n'
            'V2 Signer: certificate SHA-256 digest: '
            '8ef5fe2873f7a9d40302e722822c05b37b471ab08df23785fa0a1aeb19a2c165\n'
            'V2 Signer: public key SHA-256 digest: '
            '733e248a226f0e24140a52a881bc9770bc5cfc5c86cd3fd123323b68572d00a7\n')
        publisher.verify_apk('RYZOD-v4.85.apk')

    def test_repeated_pinned_cert_across_signature_schemes_is_accepted(self):
        self.certs = ('Number of signers: 1\n'
                      f'V1 Signer: certificate SHA-256 digest: {publisher.CERTIFICATE}\n'
                      f'V2 Signer: certificate SHA-256 digest: {publisher.CERTIFICATE}\n'
                      f'V3.0 Signer (minSdkVersion=28, maxSdkVersion=2147483647): certificate SHA-256 digest: {publisher.CERTIFICATE}\n')
        publisher.verify_apk('RYZOD-v4.85.apk')

    def test_any_different_or_malformed_scheme_certificate_is_rejected(self):
        original = self.certs
        for digest in ('0' * 64, publisher.CERTIFICATE[:-1], 'not-a-digest'):
            with self.subTest(digest=digest):
                self.certs = original + f'V2 Signer: certificate SHA-256 digest: {digest}\n'
                with self.assertRaisesRegex(ValueError, 'certificate'):
                    publisher.verify_apk('RYZOD-v4.85.apk')

    def test_exactly_one_reported_signer_is_required_even_for_same_certificate(self):
        original = self.certs
        for count in ('', 'Number of signers: 0\n', 'Number of signers: 2\n',
                      'Number of signers: 1\nNumber of signers: 1\n'):
            with self.subTest(count=count):
                self.certs = original.replace('Number of signers: 1\n', count)
                with self.assertRaisesRegex(ValueError, 'certificate'):
                    publisher.verify_apk('RYZOD-v4.85.apk')

    def test_source_stamp_cannot_replace_or_change_apk_certificate_identity(self):
        original = self.certs
        stamp = f'Source Stamp Signer: certificate SHA-256 digest: {publisher.CERTIFICATE}\n'
        self.certs = 'Number of signers: 1\n' + stamp
        with self.assertRaisesRegex(ValueError, 'certificate'):
            publisher.verify_apk('RYZOD-v4.85.apk')
        self.certs = original + stamp.replace(publisher.CERTIFICATE, '0' * 64)
        publisher.verify_apk('RYZOD-v4.85.apk')

    def test_signature_zipalign_and_native_tool_failures_propagate(self):
        for index in range(3):
            with self.subTest(tool=index):
                self.run.side_effect = [None] * index + [subprocess.CalledProcessError(1, ['tool'])]
                with self.assertRaises(subprocess.CalledProcessError):
                    publisher.verify_apk('RYZOD-v4.85.apk')

    def test_amazon_cannot_request_apk_installation(self):
        self.permissions = "uses-permission: name='android.permission.REQUEST_INSTALL_PACKAGES'"
        with self.assertRaisesRegex(ValueError, 'Amazon'):
            publisher.verify_apk('RYZOD-v4.85-Amazon.apk')

class AabVerificationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.bundletool = self.directory / 'bundletool.jar'
        self.bundletool.write_bytes(b'test tool')
        self.pem = ('-----BEGIN CERTIFICATE-----\n' + base64.b64encode(b'public certificate').decode()
                    + '\n-----END CERTIFICATE-----')
        self.manifest = ('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                         'package="com.easyiptv.player" android:versionCode="109" android:versionName="4.85">'
                         '<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/></manifest>')
        self.env = patch.dict(publisher.os.environ, {'BUNDLETOOL_JAR': str(self.bundletool)})
        self.env.start()
        self.addCleanup(self.env.stop)
        self.cert = patch.object(publisher, 'CERTIFICATE', hashlib.sha256(b'public certificate').hexdigest().upper())
        self.cert.start()
        self.addCleanup(self.cert.stop)
        self.output_patch = patch.object(publisher, 'output', side_effect=self.output)
        self.output_mock = self.output_patch.start()
        self.addCleanup(self.output_patch.stop)
        self.run_patch = patch.object(publisher.subprocess, 'run')
        self.run = self.run_patch.start()
        self.addCleanup(self.run_patch.stop)
        self.path = self.directory / 'candidate.aab'
        with zipfile.ZipFile(self.path, 'w') as archive:
            archive.writestr('base/manifest/AndroidManifest.xml', b'protobuf manifest')

    def output(self, command):
        return self.manifest if command[0] == 'java' else self.pem

    def test_strict_signature_uses_only_pinned_public_certificate(self):
        publisher.verify_aab(self.path)
        commands = [call.args[0] for call in self.run.call_args_list]
        self.assertEqual(commands[0][:3], ['keytool', '-importcert', '-noprompt'])
        self.assertEqual(self.run.call_args_list[0].kwargs['input'], self.pem)
        verify = commands[1]
        self.assertEqual(verify[:3], ['jarsigner', '-verify', '-strict'])
        self.assertIn('-keystore', verify)
        self.assertEqual(verify[-2:], [str(self.path), 'release'])
        self.assertTrue(all(call.kwargs['check'] for call in self.run.call_args_list))
        self.output_mock.assert_any_call(['java', '-jar', str(self.bundletool), 'dump', 'manifest',
                                         f'--bundle={self.path}', '--module=base'])

    def test_unsigned_entry_strict_exit_prevents_manifest_acceptance(self):
        self.run.side_effect = [None, subprocess.CalledProcessError(16, ['jarsigner', '-verify', '-strict'])]
        with self.assertRaises(subprocess.CalledProcessError):
            publisher.verify_aab(self.path)
        self.assertFalse(any(c.args[0][0] == 'java' for c in self.output_mock.call_args_list))

    def test_duplicate_zip_entry_is_rejected_before_signature_or_manifest_tools(self):
        with self.assertWarns(UserWarning), zipfile.ZipFile(self.path, 'a') as archive:
            archive.writestr('base/manifest/AndroidManifest.xml', b'conflicting manifest')
        with self.assertRaisesRegex(ValueError, 'duplicate ZIP'):
            publisher.verify_aab(self.path)
        self.output_mock.assert_not_called()
        self.run.assert_not_called()

    def test_missing_wrong_or_multiple_certificates_are_rejected_before_trust(self):
        for pem in ('', self.pem.replace(base64.b64encode(b'public certificate').decode(), 'YWJj'),
                    self.pem + '\n' + self.pem):
            with self.subTest(pem=pem), patch.object(publisher, 'output', return_value=pem):
                with self.assertRaisesRegex(ValueError, 'certificate'):
                    publisher.verify_aab(self.path)
        self.run.assert_not_called()

    def test_each_identity_field_is_required_and_must_match(self):
        original = self.manifest
        for field, value, wrong in [('package', 'com.easyiptv.player', 'com.example.other'),
                                    ('android:versionCode', '109', '105'),
                                    ('android:versionName', '4.85', '4.81'),
                                    ('android:minSdkVersion', '26', '25'),
                                    ('android:targetSdkVersion', '36', '35')]:
            for replacement in (f'{field}="{wrong}"', ''):
                with self.subTest(field=field, replacement=replacement):
                    self.manifest = original.replace(f'{field}="{value}"', replacement)
                    with self.assertRaisesRegex(ValueError, 'identity|SDK'):
                        publisher.verify_aab(self.path)

    def test_compiled_numeric_hex_values_are_accepted(self):
        self.manifest = self.manifest.replace('"109"', '"0x0000006d"').replace('"36"', '"0x00000024"')
        publisher.verify_aab(self.path)

    def test_nonzero_version_code_major_is_rejected(self):
        self.manifest = self.manifest.replace(
            'android:versionCode="109"', 'android:versionCode="109" android:versionCodeMajor="1"')
        with self.assertRaisesRegex(ValueError, 'identity'):
            publisher.verify_aab(self.path)

    def test_malformed_manifest_missing_or_duplicate_sdk_is_rejected(self):
        original = self.manifest
        sdk = '<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/>'
        for manifest in ('not XML', original.replace(sdk, ''), original.replace(sdk, sdk + sdk)):
            with self.subTest(manifest=manifest):
                self.manifest = manifest
                with self.assertRaises(ValueError):
                    publisher.verify_aab(self.path)

    def test_bundletool_unavailable_or_failure_is_rejected(self):
        self.bundletool.unlink()
        with self.assertRaisesRegex(ValueError, 'bundletool|BUNDLETOOL'):
            publisher.verify_aab(self.path)
        self.bundletool.write_bytes(b'tool')
        with patch.object(publisher, 'output', side_effect=[self.pem, subprocess.CalledProcessError(1, ['java'])]):
            with self.assertRaises(subprocess.CalledProcessError):
                publisher.verify_aab(self.path)


class AabSignatureIntegrationTest(unittest.TestCase):
    """Exercise actual JDK tools on throwaway signed ZIPs; never release keys."""

    @classmethod
    def setUpClass(cls):
        for tool in ('keytool', 'jarsigner'):
            if not shutil.which(tool):
                raise RuntimeError(f'{tool} is required for offline AAB signature regressions')
        cls.temp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.temp.cleanup)
        cls.directory = Path(cls.temp.name)
        cls.store = cls.directory / 'fixture.p12'
        cls.signed = cls.directory / 'signed.aab'
        subprocess.run(['keytool', '-genkeypair', '-alias', 'fixture', '-keyalg', 'RSA',
                        '-keysize', '2048', '-validity', '3650', '-dname', 'CN=Offline Test',
                        '-keystore', str(cls.store), '-storepass', 'fixture', '-keypass', 'fixture'],
                       check=True, capture_output=True)
        with zipfile.ZipFile(cls.signed, 'w') as archive:
            archive.writestr('base/manifest/AndroidManifest.xml', b'signed manifest fixture')
            archive.writestr('base/dex/classes.dex', b'signed dex fixture')
        subprocess.run(['jarsigner', '-keystore', str(cls.store), '-storepass', 'fixture',
                        str(cls.signed), 'fixture'], check=True, capture_output=True)
        pem = subprocess.check_output(['keytool', '-exportcert', '-rfc', '-alias', 'fixture',
                                       '-keystore', str(cls.store), '-storepass', 'fixture'], text=True)
        der = base64.b64decode(''.join(pem.splitlines()[1:-1]))
        cls.fingerprint = hashlib.sha256(der).hexdigest().upper()

    def setUp(self):
        self.cert_patch = patch.object(publisher, 'CERTIFICATE', self.fingerprint)
        self.cert_patch.start()
        self.addCleanup(self.cert_patch.stop)
        self.env = patch.dict(publisher.os.environ, {'BUNDLETOOL_JAR': str(self.signed)})
        self.env.start()
        self.addCleanup(self.env.stop)
        original = publisher.output

        def output(command):
            if command[0] == 'java':
                return ('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                        'package="com.easyiptv.player" android:versionCode="109" android:versionName="4.85">'
                        '<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/></manifest>')
            return original(command)

        self.output_patch = patch.object(publisher, 'output', side_effect=output)
        self.output_patch.start()
        self.addCleanup(self.output_patch.stop)

    def test_fully_signed_self_signed_certificate_is_accepted(self):
        publisher.verify_aab(self.signed)

    def test_appended_unsigned_entries_plain_verify_accepts_are_rejected(self):
        for name in ('base/assets/injected.txt', 'META-INF/injected.txt'):
            with self.subTest(entry=name):
                path = self.directory / 'appended.aab'
                shutil.copyfile(self.signed, path)
                with zipfile.ZipFile(path, 'a') as archive:
                    archive.writestr(name, b'unsigned injection')
                # Reproduce the actual review finding before exercising our gate.
                plain = subprocess.run(['jarsigner', '-verify', str(path)], capture_output=True)
                self.assertEqual(plain.returncode, 0)
                with self.assertRaises(subprocess.CalledProcessError) as error:
                    publisher.verify_aab(path)
                self.assertTrue(error.exception.returncode & 16)

    def test_modified_signed_entry_is_rejected(self):
        path = self.directory / 'tampered.aab'
        with zipfile.ZipFile(self.signed) as source, zipfile.ZipFile(path, 'w') as target:
            for entry in source.infolist():
                target.writestr(entry, b'tampered' if entry.filename.endswith('.dex') else source.read(entry))
        with self.assertRaises(subprocess.CalledProcessError):
            publisher.verify_aab(path)

    def test_unsigned_bundle_is_rejected(self):
        path = self.directory / 'unsigned.aab'
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('base/manifest/AndroidManifest.xml', b'unsigned')
        with self.assertRaises(ValueError):
            publisher.verify_aab(path)

    def test_valid_signature_from_unpinned_certificate_is_rejected(self):
        with patch.object(publisher, 'CERTIFICATE', '0' * 64):
            with self.assertRaisesRegex(ValueError, 'permanent certificate'):
                publisher.verify_aab(self.signed)


class InputVerificationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        for suffix in ('', '-FireTV', '-Amazon', '-Play'):
            (self.directory / f'RYZOD-v4.85{suffix}-mapping.txt').write_bytes(b'R8 mapping')
        (self.directory / 'RYZOD-v4.85-source.zip').write_bytes(b'exact HEAD archive')
        patches = [patch.object(publisher, 'verify_apk'), patch.object(publisher, 'verify_aab'),
                   patch.object(publisher, 'output', return_value='source-head\n'),
                   patch.object(publisher.subprocess, 'check_output', return_value=b'exact HEAD archive'),
                   patch.dict(publisher.os.environ, {'GITHUB_SHA': 'source-head'})]
        self.mocks = [item.start() for item in patches]
        for item in patches:
            self.addCleanup(item.stop)

    def test_reverifies_all_four_packages_and_exact_head_archive(self):
        publisher.verify_inputs(self.directory)
        self.assertEqual([call.args[0].name for call in self.mocks[0].call_args_list],
                         ['RYZOD-v4.85.apk', 'RYZOD-v4.85-FireTV.apk', 'RYZOD-v4.85-Amazon.apk'])
        self.mocks[1].assert_called_once_with(self.directory / 'RYZOD-v4.85-Play.aab')
        self.assertEqual(self.mocks[3].call_args.args[0][-3:], ['archive', '--format=zip', 'HEAD'])

    def test_source_archive_mismatch_is_rejected(self):
        (self.directory / 'RYZOD-v4.85-source.zip').write_bytes(b'wrong commit')
        with self.assertRaisesRegex(ValueError, 'exact source HEAD'):
            publisher.verify_inputs(self.directory)

    def test_checkout_must_match_workflow_sha(self):
        with patch.dict(publisher.os.environ, {'GITHUB_SHA': 'other-head'}):
            with self.assertRaisesRegex(ValueError, 'workflow source HEAD'):
                publisher.verify_inputs(self.directory)

    def test_empty_mapping_rejected_for_each_distribution(self):
        for suffix in ('', '-FireTV', '-Amazon', '-Play'):
            with self.subTest(distribution=suffix):
                path = self.directory / f'RYZOD-v4.85{suffix}-mapping.txt'
                path.write_bytes(b'')
                with self.assertRaisesRegex(ValueError, 'mapping'):
                    publisher.verify_inputs(self.directory)
                path.write_bytes(b'R8 mapping')

    def test_missing_source_is_rejected(self):
        (self.directory / 'RYZOD-v4.85-source.zip').unlink()
        with self.assertRaises(FileNotFoundError):
            publisher.verify_inputs(self.directory)


if __name__ == '__main__':
    unittest.main()
