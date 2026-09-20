#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
base=root/'app/src/main/java/com/easyiptv/player'
main=(base/'MainActivity.kt').read_text()
overlay=(base/'LiveOverlay.kt').read_text()
runtime=(base/'MediaRuntimePolicy.kt').read_text()
storage=(base/'LiveStorageManager.kt').read_text()
checks={
 'mini guide stays three rows': 'visibleRowCount = 3' in overlay,
 'mini guide shows program title': 'rowProgram.title' in main,
 'mini guide retains record': 'RECORD' in overlay,
 'mini guide retains captions': 'CAPTIONS' in overlay,
 'previous channel retained': 'PREVIOUS_CHANNEL' in overlay,
 'startup input gate retained': 'StartupPolicy.state(' in main,
 'one stream warning retained': '1-stream IPTV plan' in runtime,
 'USB fallback retained': 'internal storage' in runtime,
 'USB ring remains bounded': 'USB_MAX_RING_BYTES' in storage,
 'obsolete append-only DVR copy removed': 'append-only temporary buffer' not in main,
 'DVR wording is local not cloud': 'cloud DVR' not in main.lower(),
}
for k,v in checks.items(): print(('PASS: ' if v else 'FAIL: ')+k)
raise SystemExit(0 if all(checks.values()) else 1)
