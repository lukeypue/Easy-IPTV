#!/usr/bin/env python3
from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
GRADLE = Path('app/build.gradle.kts')
main = MAIN.read_text(encoding='utf-8')
gradle = GRADLE.read_text(encoding='utf-8')

required = [
    'ZAKO_V446_PERSISTENT_UPDATE_BUTTON',
    'ZAKO_V426_NATIVE_UPDATER',
    'ZAKO_V445_RESTORED_FULL_CHAIN',
    'ZAKO_V425_MINI_EPG',
    'rowProgram.title',
    '.clickable { touch(); onTune(ch) }',
    'onBackToRoot()',
    'externalFocus.requestFocus()',
]
missing = [x for x in required if x not in main]
if missing:
    raise SystemExit('refusing 4.47 transform; generated 4.46 invariant missing: ' + ', '.join(missing))

if 'ZAKO_V447_FULL_REDESIGN' in main:
    raise SystemExit('4.47 transform already applied')

# Versioned architecture markers live beside proven generated behavior. Policy
# implementations are separate Kotlin units so the huge activity can shrink in
# later safe extractions without rewriting the stable player lifecycle now.
anchor = 'ZAKO_V446_PERSISTENT_UPDATE_BUTTON'
main = main.replace(anchor, 'ZAKO_V447_FULL_REDESIGN\n            // ZAKO_V447_COMPACT_MINI_GUIDE\n            // ' + anchor, 1)

gradle, n1 = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 72', gradle, count=1)
gradle, n2 = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.47"', gradle, count=1)
if n1 != 1 or n2 != 1:
    raise SystemExit('v4.47 version bump failed')

MAIN.write_text(main, encoding='utf-8')
GRADLE.write_text(gradle, encoding='utf-8')
print('Applied Zako 4.47 generated-source architecture baseline')
