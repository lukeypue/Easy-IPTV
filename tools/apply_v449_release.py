#!/usr/bin/env python3
from pathlib import Path
g=Path('app/build.gradle.kts'); s=g.read_text()
s=s.replace('versionCode = 73','versionCode = 74').replace('versionName = "4.48"','versionName = "4.49"')
g.write_text(s)
p=Path('app/src/main/java/com/easyiptv/player/MainActivity.kt'); m=p.read_text()
if 'ZAKO_V449_STARTUP_REARM' not in m:
    anchor='setContent {'
    if anchor not in m: raise SystemExit('setContent anchor missing')
    m=m.replace(anchor,'// ZAKO_V449_STARTUP_REARM\n        ScheduleStore.rearmAll(this, getSharedPreferences("easyiptv", MODE_PRIVATE))\n        '+anchor,1)
    p.write_text(m)
print('Applied Zako 4.49 release metadata and startup recovery')
