#!/usr/bin/env python3
from pathlib import Path
s=Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text()
checks={
'universal guide marker':'ZAKO_V450_UNIVERSAL_GUIDE' in s,
'guide no longer hidden by EPG': 'if (schedule.isNotEmpty()) {\n                                IconButton' not in s,
'blank provider message':'No program information from provider' in s,
'blank time slots':'(0 until 12).forEach' in s,
'blank slots can record':'"Manual Recording", ch.name, ch.url, startMs, endMs' in s,
'same screen uses TV focus':'.tvFocus(RoundedCornerShape(16.dp))' in s,
}
for k,v in checks.items(): print(('PASS: ' if v else 'FAIL: ')+k)
raise SystemExit(0 if all(checks.values()) else 1)
