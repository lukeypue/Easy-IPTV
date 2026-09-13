from pathlib import Path
import re

GRADLE = Path('app/build.gradle.kts')
RING = Path('app/src/main/java/com/easyiptv/player/TimeshiftRing.kt')
STORAGE = Path('app/src/main/java/com/easyiptv/player/LiveStorageManager.kt')
RING_TEMPLATE = Path('tools/v440/TimeshiftRing.kt.template')
STORAGE_TEMPLATE = Path('tools/v440/LiveStorageManager.kt.template')

gradle = GRADLE.read_text(encoding='utf-8')
gradle, n = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 65', gradle, count=1)
if n != 1:
    raise SystemExit('versionCode bump failed')
gradle, n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.40"', gradle, count=1)
if n != 1:
    raise SystemExit('versionName bump failed')
GRADLE.write_text(gradle, encoding='utf-8')

RING.write_text(RING_TEMPLATE.read_text(encoding='utf-8'), encoding='utf-8')
STORAGE.write_text(STORAGE_TEMPLATE.read_text(encoding='utf-8'), encoding='utf-8')
print('Applied Zako 4.40 rolling-ring foundation')
