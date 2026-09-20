#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
base=root/'app/src/main/java/com/easyiptv/player'
controller=(base/'LiveDvrController.kt').read_text()
main=(base/'MainActivity.kt').read_text()
checks={
 'transport has stepped rates': all(x in controller for x in ['2','4','8','16']),
 'transport has fast forward state': 'fastForward' in controller or 'FastForward' in controller,
 'transport has rewind state': 'rewind' in controller.lower(),
 'transport can pause': 'pause' in controller.lower(),
 'transport clamps retained history': 'clampToRetainedHistory' in controller,
 'transport can jump live': 'jumpLive' in controller,
}
for k,v in checks.items(): print(('PASS: ' if v else 'FAIL: ')+k)
raise SystemExit(0 if all(checks.values()) else 1)
