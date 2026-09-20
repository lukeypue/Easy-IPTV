#!/usr/bin/env python3
from pathlib import Path
p=Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
s=p.read_text() if p.exists() else ''
checks={
'screen marker':'ZAKO_V449_MANAGED_DVR_SCREEN' in s,
'Upcoming':'ManagedDvrUi.upcoming' in s,
'Manual Recording':'ManagedDvrUi.manualLabel' in s,
'Cancel':'ManagedDvrUi.cancel' in s,
'Edit':'ManagedDvrUi.edit' in s,
}
for k,v in checks.items(): print(('PASS: ' if v else 'FAIL: ')+k)
raise SystemExit(0 if all(checks.values()) else 1)
