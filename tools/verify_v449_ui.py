#!/usr/bin/env python3
from pathlib import Path
p=Path('app/src/main/java/com/easyiptv/player/ManagedDvrUi.kt')
s=p.read_text() if p.exists() else ''
checks={
'Upcoming':'Upcoming' in s,
'Manual Recording':'Manual Recording' in s,
'Edit':'Edit' in s,
'Cancel':'Cancel' in s,
'channel':'channel' in s.lower(),
'date':'date' in s.lower(),
'start':'start' in s.lower(),
'end':'end' in s.lower(),
'upcoming call':'ScheduleStore.upcoming' in s,
'edit call':'ScheduleStore.edit' in s,
'cancel call':'ScheduleStore.cancel' in s,
'manual validation':'ScheduleStore.validateManual' in s,
}
for k,v in checks.items(): print(('PASS: ' if v else 'FAIL: ')+k)
raise SystemExit(0 if all(checks.values()) else 1)
