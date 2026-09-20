#!/usr/bin/env python3
from pathlib import Path
p=Path('app/build.gradle.kts'); s=p.read_text()
s=s.replace('versionCode = 74','versionCode = 75').replace('versionName = "4.49"','versionName = "4.50"')
p.write_text(s)
print('Set Zako 4.50 / versionCode 75')
