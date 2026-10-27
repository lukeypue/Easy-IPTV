"""Verify signed 4.81 assets and monotonically promote both updater manifests.

All remote reads use gh's authenticated API. No mutation occurs on import.
--check is a read-only preflight before release upload; --verify-release also
checks uploaded assets without promoting. API/read failures fail closed.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import subprocess

REPO = 'lukeypue/Easy-IPTV'
VERSION = '4.81'
VERSION_CODE = 105
MANIFESTS = ('latest.json', 'latest-v2.json')


def api(endpoint, payload=None, method=None):
    command = ['gh', 'api', f'repos/{REPO}/{endpoint}']
    if payload is not None:
        command += ['--method', method or 'POST', '--input', '-']
    return json.loads(subprocess.check_output(
        command, input=json.dumps(payload).encode() if payload is not None else None))


def build_manifest(directory):
    apk = Path(directory) / 'RYZOD-v4.81.apk'
    tv_apk = Path(directory) / 'RYZOD-v4.81-FireTV.apk'
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
        raise ValueError('Expected a public, stable v4.81 release')
    for name, digest, url in (('RYZOD-v4.81.apk', 'sha256', 'downloadUrl'),
                              ('RYZOD-v4.81-FireTV.apk', 'sha256Tv', 'downloadUrlTv')):
        matches = [asset for asset in release['assets'] if asset['name'] == name]
        if (len(matches) != 1
                or matches[0].get('size') != (Path(directory) / name).stat().st_size
                or matches[0].get('digest') != 'sha256:' + manifest[digest]
                or matches[0].get('browser_download_url') != manifest[url]):
            raise ValueError(f'Uploaded asset identity/size/digest mismatch: {name}')


def publish(directory=Path('/tmp'), *, check_only=False, verify_only=False):
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
        'message': 'Publish verified signed RYZOD 4.81 updater [skip ci]',
        'tree': tree, 'parents': [head]})['sha']
    # A concurrent main advance must fail, never force or blindly retry.
    api('git/refs/heads/main', {'sha': commit, 'force': False}, 'PATCH')
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--check', action='store_true')
    mode.add_argument('--verify-release', action='store_true')
    args = parser.parse_args()
    manifest = publish(check_only=args.check, verify_only=args.verify_release)
    print('Verified' if args.check or args.verify_release else 'Published', manifest)


if __name__ == '__main__':
    main()
