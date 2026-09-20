#!/usr/bin/env python3
from pathlib import Path
r=Path('app/src/main/java/com/easyiptv/player/Recording.kt').read_text()
checks={
'upcoming':'fun upcoming(' in r,
'edit':'fun edit(' in r,
'rearm':'fun rearmAll(' in r,
'manual validation':'fun validateManual(' in r,
'central arm':'private fun arm(' in r,
}
for k,v in checks.items(): print(('PASS: ' if v else 'FAIL: ')+k)
raise SystemExit(0 if all(checks.values()) else 1)
