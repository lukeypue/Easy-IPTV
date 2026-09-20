#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
base=root/'app/src/main/java/com/easyiptv/player'
main=(base/'MainActivity.kt').read_text()
ring=(base/'TimeshiftRing.kt').read_text()
controller=base/'LiveDvrController.kt'
checks={
 'live DVR controller exists': controller.exists(),
 'controller reads rolling ring snapshot': controller.exists() and 'Timeshift.snapshot()' in controller.read_text(),
 'timeline uses oldest and newest virtual bytes': controller.exists() and 'oldestVirtualByte' in controller.read_text() and 'newestVirtualByte' in controller.read_text(),
 'ring retention is time and byte bounded': 'historyMs' in ring and 'maxBytes' in ring and 'reclaimExpired' in ring,
 'ring lifetime is channel session not EPG title': 'currentProgram' not in ring and 'epg' not in ring.lower(),
}
bad=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS: ' if v else 'FAIL: ')+k)
raise SystemExit(1 if bad else 0)
