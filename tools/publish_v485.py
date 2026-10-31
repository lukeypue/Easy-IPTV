"""Verify signed 4.85 assets and monotonically promote both updater manifests.

All remote reads use gh's authenticated API. No mutation occurs on import.
--check is a read-only preflight before release upload; --verify-release also
checks uploaded assets without promoting. API/read failures fail closed.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

REPO = 'lukeypue/Easy-IPTV'
VERSION = '4.85'
VERSION_CODE = 109
MANIFESTS = ('latest.json', 'latest-v2.json')
CERTIFICATE = '8EF5FE2873F7A9D40302E722822C05B37B471AB08DF23785FA0A1AEB19A2C165'
ROOT = Path(__file__).resolve().parents[1]


def android_tool(name):
    found = shutil.which(name)
    if found:
        return found
    home = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    candidates = list((Path(home) / 'build-tools').glob(f'*/{name}')) if home else []
    if not candidates:
        raise ValueError(f'Android build tool missing: {name}')
    return str(max(candidates, key=lambda p: tuple(
        int(n) for n in re.findall(r'\d+', p.parent.name))))


def output(command):
    return subprocess.check_output(command, text=True)


def check_apk_certificates(certs, path):
    # Build Tools 35 uses "Signer #1"; 37 uses scheme names like "V2 Signer:".
    # A single signer may be printed once per scheme. Check every APK cert,
    # independently of the label, and require the verifier's signer count to be 1.
    counts = re.findall(r'^Number of signers: ([0-9]+)[ \t]*$', certs, re.M)
    marker = ' certificate SHA-256 digest: '
    digests = [line.split(marker, 1)[1].strip().upper() for line in certs.splitlines()
               if marker in line and not line.startswith('Source Stamp Signer')]
    if counts != ['1'] or not digests or any(digest != CERTIFICATE for digest in digests):
        raise ValueError(f'APK permanent certificate mismatch: {path}')


def verify_apk(path):
    path = str(path)
    badging = output([android_tool('aapt'), 'dump', 'badging', path])
    package = next((line for line in badging.splitlines() if line.startswith('package:')), '')
    for field in ("name='com.easyiptv.player'", "versionCode='109'", "versionName='4.85'"):
        if field not in package:
            raise ValueError(f'APK identity mismatch: {path}: {field}')
    if "targetSdkVersion:'36'" not in badging.splitlines():
        raise ValueError(f'APK target SDK mismatch: {path}')
    signer = android_tool('apksigner')
    subprocess.run([signer, 'verify', '--verbose', path], check=True)
    certs = output([signer, 'verify', '--verbose', '--print-certs', path])
    check_apk_certificates(certs, path)
    subprocess.run([android_tool('zipalign'), '-c', '-P', '16', '4', path], check=True)
    subprocess.run(['python3', str(ROOT / 'tools/verify_native_alignment.py'), path], check=True)
    if Path(path).name == 'RYZOD-v4.85-Amazon.apk':
        permissions = output([android_tool('aapt'), 'dump', 'permissions', path])
        if 'REQUEST_INSTALL_PACKAGES' in permissions:
            raise ValueError('Amazon candidate must not request APK installation')


def verify_aab(path):
    # Duplicate ZIP names can make different readers verify different payloads.
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Play candidate contains duplicate ZIP entries')
    certs = output(['keytool', '-printcert', '-rfc', '-jarfile', str(path)])
    certificates = re.findall(r'-----BEGIN CERTIFICATE-----\s*(.*?)\s*-----END CERTIFICATE-----',
                              certs, re.S)
    if (len(certificates) != 1 or hashlib.sha256(base64.b64decode(
            ''.join(certificates[0].split()), validate=True)).hexdigest().upper() != CERTIFICATE):
        raise ValueError('Play candidate permanent certificate mismatch')
    pem = '-----BEGIN CERTIFICATE-----\n' + certificates[0] + '\n-----END CERTIFICATE-----'
    # Trust only the pinned public cert. Strict verification then rejects unsigned
    # entries without rejecting our intentionally self-signed release certificate.
    with tempfile.TemporaryDirectory(prefix='ryzod-aab-') as directory:
        truststore = str(Path(directory) / 'public-cert.p12')
        options = ['-keystore', truststore, '-storetype', 'PKCS12', '-storepass', 'public-cert']
        subprocess.run(['keytool', '-importcert', '-noprompt', '-alias', 'release', *options],
                       input=pem, text=True, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        subprocess.run(['jarsigner', '-verify', '-strict', *options, str(path), 'release'], check=True)
    bundletool = os.environ.get('BUNDLETOOL_JAR')
    if not bundletool or not Path(bundletool).is_file():
        raise ValueError('BUNDLETOOL_JAR must point to the pinned bundletool JAR')
    try:
        manifest = ET.fromstring(output(['java', '-jar', bundletool, 'dump', 'manifest',
                                        f'--bundle={path}', '--module=base']))
    except ET.ParseError as error:
        raise ValueError('Play candidate manifest is malformed') from error
    android = '{http://schemas.android.com/apk/res/android}'

    def number(element, name, default=None):
        try:
            # Compiled protobuf manifest integers can be rendered as hexadecimal.
            return int(element.get(android + name, default), 0)
        except (TypeError, ValueError) as error:
            raise ValueError(f'Play candidate identity/SDK missing or invalid: {name}') from error

    if (manifest.tag != 'manifest' or manifest.get('package') != 'com.easyiptv.player'
            or number(manifest, 'versionCode') != VERSION_CODE
            or number(manifest, 'versionCodeMajor', '0') != 0
            or manifest.get(android + 'versionName') != VERSION):
        raise ValueError('Play candidate package/version identity mismatch')
    sdk = manifest.findall('uses-sdk')
    if (len(sdk) != 1 or number(sdk[0], 'minSdkVersion') != 26
            or number(sdk[0], 'targetSdkVersion') != 36):
        raise ValueError('Play candidate SDK identity mismatch')


def verify_inputs(directory):
    directory = Path(directory)
    for suffix in ('', '-FireTV', '-Amazon'):
        verify_apk(directory / f'RYZOD-v{VERSION}{suffix}.apk')
    verify_aab(directory / f'RYZOD-v{VERSION}-Play.aab')
    for suffix in ('', '-FireTV', '-Amazon', '-Play'):
        if not (directory / f'RYZOD-v{VERSION}{suffix}-mapping.txt').read_bytes():
            raise ValueError('Missing minification mapping')
    head = output(['git', '-C', str(ROOT), 'rev-parse', 'HEAD']).strip()
    if os.environ.get('GITHUB_SHA', head) != head:
        raise ValueError('Checkout does not match workflow source HEAD')
    source = subprocess.check_output(['git', '-C', str(ROOT), 'archive', '--format=zip', 'HEAD'])
    if (directory / f'RYZOD-v{VERSION}-source.zip').read_bytes() != source:
        raise ValueError('Source archive does not match exact source HEAD')


def api(endpoint, payload=None, method=None):
    command = ['gh', 'api', f'repos/{REPO}/{endpoint}']
    if payload is not None:
        command += ['--method', method or 'POST', '--input', '-']
    return json.loads(subprocess.check_output(
        command, input=json.dumps(payload).encode() if payload is not None else None))


def build_manifest(directory):
    apk = Path(directory) / 'RYZOD-v4.85.apk'
    tv_apk = Path(directory) / 'RYZOD-v4.85-FireTV.apk'
    root = f'https://github.com/{REPO}/releases/download/v{VERSION}/'
    return {'versionCode': VERSION_CODE, 'versionName': VERSION,
            'downloadUrl': root + apk.name,
            'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
            'downloadUrlTv': root + tv_apk.name,
            'sha256Tv': hashlib.sha256(tv_apk.read_bytes()).hexdigest()}


def check_current(current, candidate, name):
    code = current.get('versionCode')
    if type(code) is not int or code < 0:
        raise ValueError(f'{name}: invalid current versionCode')
    if code > candidate['versionCode']:
        raise ValueError(f'{name}: refusing updater downgrade from {code}')
    if code == candidate['versionCode']:
        for field, value in candidate.items():
            if current.get(field) != value:
                raise ValueError(f'{name}: equal-version conflict in {field}')


def verify_release(directory, manifest):
    release = api(f'releases/tags/v{VERSION}')
    if (release.get('draft') is not False or release.get('prerelease') is not False
            or release.get('tag_name') != f'v{VERSION}'):
        raise ValueError('Expected a public, stable v4.85 release')
    source = f'RYZOD-v{VERSION}-source.zip'
    expected = [('RYZOD-v4.85.apk', manifest['sha256'], manifest['downloadUrl']),
                ('RYZOD-v4.85-FireTV.apk', manifest['sha256Tv'], manifest['downloadUrlTv']),
                (source, hashlib.sha256((Path(directory) / source).read_bytes()).hexdigest(),
                 f'https://github.com/{REPO}/releases/download/v{VERSION}/{source}')]
    if sorted(asset['name'] for asset in release['assets']) != sorted(item[0] for item in expected):
        raise ValueError('Release must contain exactly two APKs and the source ZIP')
    for name, digest, url in expected:
        matches = [asset for asset in release['assets'] if asset['name'] == name]
        if (len(matches) != 1
                or matches[0].get('size') != (Path(directory) / name).stat().st_size
                or matches[0].get('digest') != 'sha256:' + digest
                or matches[0].get('browser_download_url') != url):
            raise ValueError(f'Uploaded asset identity/size/digest mismatch: {name}')


def publish(directory=Path('/tmp'), *, check_only=False, verify_only=False):
    verify_inputs(directory)
    manifest = build_manifest(directory)
    # Pin both contents reads and the parent commit to the same main snapshot.
    head = api('git/ref/heads/main')['object']['sha']
    current = {}
    for name in MANIFESTS:
        blob = api(f'contents/{name}?ref={head}')
        if blob.get('encoding') != 'base64':
            raise ValueError(f'{name}: unsupported manifest encoding')
        current[name] = json.loads(base64.b64decode(blob['content']))
        check_current(current[name], manifest, name)
    if check_only:
        return manifest
    verify_release(directory, manifest)
    if verify_only or all(current[name] == manifest for name in MANIFESTS):
        return manifest
    base = api(f'git/commits/{head}')['tree']['sha']
    tree = api('git/trees', {'base_tree': base, 'tree': [
        {'path': name, 'mode': '100644', 'type': 'blob',
         'content': json.dumps(manifest) + '\n'} for name in MANIFESTS]})['sha']
    commit = api('git/commits', {
        'message': 'Publish verified signed RYZOD 4.85 updater [skip ci]',
        'tree': tree, 'parents': [head]})['sha']
    # A concurrent main advance must fail, never force or blindly retry.
    api('git/refs/heads/main', {'sha': commit, 'force': False}, 'PATCH')
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--check', action='store_true')
    mode.add_argument('--verify-release', action='store_true')
    mode.add_argument('--verify-inputs', action='store_true')
    mode.add_argument('--verify-apk', type=Path)
    mode.add_argument('--verify-aab', type=Path)
    parser.add_argument('--directory', type=Path, default=Path('/tmp'))
    args = parser.parse_args()
    if args.verify_apk:
        verify_apk(args.verify_apk)
        return
    if args.verify_aab:
        verify_aab(args.verify_aab)
        return
    if args.verify_inputs:
        verify_inputs(args.directory)
        return
    manifest = publish(args.directory, check_only=args.check, verify_only=args.verify_release)
    print('Verified' if args.check or args.verify_release else 'Published', manifest)


if __name__ == '__main__':
    main()
