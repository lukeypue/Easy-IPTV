#!/usr/bin/env python3
from pathlib import Path
g=Path('app/build.gradle.kts').read_text()
m=Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text()
checks={
'versionCode 74':'versionCode = 74' in g,
'versionName 4.49':'versionName = "4.49"' in g,
'startup recovery':'ScheduleStore.rearmAll' in m,
}
for k,v in checks.items(): print(('PASS: ' if v else 'FAIL: ')+k)
raise SystemExit(0 if all(checks.values()) else 1)
