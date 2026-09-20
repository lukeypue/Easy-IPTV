#!/usr/bin/env python3
from pathlib import Path
m=Path('app/src/main/AndroidManifest.xml').read_text()
p=Path('app/src/main/java/com/easyiptv/player/ScheduleRecoveryReceiver.kt')
s=p.read_text() if p.exists() else ''
checks={
'boot permission':'android.permission.RECEIVE_BOOT_COMPLETED' in m,
'receiver declared':'ScheduleRecoveryReceiver' in m,
'boot action':'android.intent.action.BOOT_COMPLETED' in m,
'package replaced':'android.intent.action.MY_PACKAGE_REPLACED' in m,
'time set':'android.intent.action.TIME_SET' in m,
'timezone':'android.intent.action.TIMEZONE_CHANGED' in m,
'rearms':'ScheduleStore.rearmAll' in s,
'no UI launch':'startActivity' not in s and 'MainActivity' not in s,
}
for k,v in checks.items(): print(('PASS: ' if v else 'FAIL: ')+k)
raise SystemExit(0 if all(checks.values()) else 1)
