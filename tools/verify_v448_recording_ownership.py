#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
base=root/'app/src/main/java/com/easyiptv/player'
rec=(base/'Recording.kt').read_text()
main=(base/'MainActivity.kt').read_text()
checks={
 'same-channel eligibility uses active ring': 'Timeshift.snapshot() != null' in main,
 'recording source can read rolling ring': 'Timeshift.openReader(' in rec,
 'recording does not depend on obsolete Timeshift.file': 'Timeshift.file' not in rec,
 'provider budget logic remains present': ('ProviderStreams' in rec or 'provider' in rec.lower()),
}
for k,v in checks.items(): print(('PASS: ' if v else 'FAIL: ')+k)
raise SystemExit(0 if all(checks.values()) else 1)
