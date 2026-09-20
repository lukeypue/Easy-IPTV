#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
base=root/'app/src/main/java/com/easyiptv/player'
recording=(base/'Recording.kt').read_text()
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
scheduler=(base/'RecordingScheduler.kt')
checks={
 'scheduler boundary exists': scheduler.exists(),
 'schedule store does not call setAlarmClock directly': 'am.setAlarmClock(' not in recording,
 'exact alarm capability checked': scheduler.exists() and 'canScheduleExactAlarms' in scheduler.read_text(),
 'exact alarm permission declared': 'android.permission.SCHEDULE_EXACT_ALARM' in manifest,
 'security exception handled': scheduler.exists() and 'SecurityException' in scheduler.read_text(),
}
bad=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS: ' if v else 'FAIL: ')+k)
raise SystemExit(1 if bad else 0)
