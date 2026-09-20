#!/usr/bin/env python3
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
g=root/'app/build.gradle.kts'
s=g.read_text()
s,n1=re.subn(r'versionCode\s*=\s*\d+','versionCode = 73',s,count=1)
s,n2=re.subn(r'versionName\s*=\s*"[^"]+"','versionName = "4.48"',s,count=1)
if n1 != 1 or n2 != 1: raise SystemExit('4.48 version bump target missing')
g.write_text(s)
print('Applied Zako 4.48 release version')
