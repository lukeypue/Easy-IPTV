#!/usr/bin/env python3
"""Validate and build reproducible install and companion archives."""
from pathlib import Path
import hashlib
import re
import struct
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'out'

def channel_files():
    return [ROOT/'manifest']+sorted(p for folder in ['source','components','images'] for p in (ROOT/folder).rglob('*') if p.is_file())

def validate():
    files=channel_files()
    names={p.relative_to(ROOT).as_posix() for p in files}
    manifest=dict(line.split('=',1) for line in (ROOT/'manifest').read_text().splitlines() if '=' in line)
    assert manifest['title']=='RYZOD Media Player'
    assert 'source/main.brs' in names
    for p in (ROOT/'components').glob('*.xml'):
        tree=ET.parse(p)
        for script in tree.findall('script'):
            assert script.attrib['uri'].removeprefix('pkg:/') in names, script.attrib
        for uri in re.findall(r'pkg:/([^"\s]+)',p.read_text()):
            assert uri in names,uri
    assert sum(len(re.findall(r'<Video\b',p.read_text())) for p in (ROOT/'components').glob('*.xml'))==1
    for name,dimensions in [('channel.png',(540,405)),('splash.png',(1280,720))]:
        data=(ROOT/'images'/name).read_bytes()
        assert data[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',data[16:24])==dimensions
    assert not any('/node_modules/' in str(p) or p.suffix in {'.sqlite3','.log'} for p in files)
    return files

def archive(path,files,base):
    with zipfile.ZipFile(path,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for p in sorted(files):
            info=zipfile.ZipInfo(p.relative_to(base).as_posix(),date_time=(2026,10,3,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED
            info.external_attr=0o100644<<16
            z.writestr(info,p.read_bytes())
    with zipfile.ZipFile(path) as z:assert z.testzip() is None
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    files=validate();OUT.mkdir(exist_ok=True)
    install=OUT/'RYZOD-Roku-0.1.0.zip'
    companion=OUT/'RYZOD-Companion-0.1.0.zip'
    sums={install.name:archive(install,files,ROOT),companion.name:archive(companion,[ROOT/'companion'/name for name in ['server.py','test_server.py','README.md']],ROOT/'companion')}
    (OUT/'SHA256SUMS.txt').write_text(''.join(f'{sha}  {name}\n' for name,sha in sums.items()))
    for name,sha in sums.items():print(f'{name}: {sha}')

if __name__=='__main__':main()
