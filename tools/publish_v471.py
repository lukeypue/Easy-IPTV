"""Publish matching updater manifests after the signed release upload succeeds."""
import hashlib
import json
import subprocess
from pathlib import Path

REPO = 'lukeypue/Easy-IPTV'
def api(endpoint, payload=None):
    command = ['gh', 'api', f'repos/{REPO}/{endpoint}']
    if payload is not None:
        command += ['--method', 'POST', '--input', '-']
    return json.loads(subprocess.check_output(command, input=json.dumps(payload).encode() if payload else None))

apk = Path('/tmp/RYZOD-v4.71.apk')
manifest = {'versionCode': 95, 'versionName': '4.71',
            'downloadUrl': f'https://github.com/{REPO}/releases/download/v4.71/RYZOD-v4.71.apk',
            'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()}
release = api('releases/tags/v4.71')
assert not release['draft'] and any(a['name'] == apk.name and a['size'] == apk.stat().st_size for a in release['assets'])
head = api('git/ref/heads/main')['object']['sha']
base = api(f'git/commits/{head}')['tree']['sha']
tree = api('git/trees', {'base_tree': base, 'tree': [
    {'path': name, 'mode': '100644', 'type': 'blob', 'content': json.dumps(manifest) + '\n'}
    for name in ('latest.json', 'latest-v2.json')]})['sha']
commit = api('git/commits', {'message': 'Publish verified signed RYZOD 4.71 updater [skip ci]', 'tree': tree, 'parents': [head]})['sha']
subprocess.run(['gh', 'api', '--method', 'PATCH', f'repos/{REPO}/git/refs/heads/main', '--input', '-'],
               input=json.dumps({'sha': commit, 'force': False}).encode(), check=True)
print('Published both updater manifests:', manifest)
