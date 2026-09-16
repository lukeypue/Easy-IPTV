from pathlib import Path
import re

main = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text(encoding='utf-8')
gradle = Path('app/build.gradle.kts').read_text(encoding='utf-8')
checks = {
    'versionCode 70': bool(re.search(r'versionCode\s*=\s*70', gradle)),
    'versionName 4.45': 'versionName = "4.45"' in gradle,
    'full fluid LEFT navigation restored': 'onBackToRoot()' in main and 'externalFocus.requestFocus()' in main,
    'three-row mini guide restored': '3 channels + 3 shows' in main and 'height(102.dp)' in main and '.height(33.dp)' in main,
    'mini guide shows current program': 'ZAKO_V445_MINI_EPG' in main and 'EpgStore.guide(ch.epgId, ch.name)' in main,
    'mini guide rows tune on OK': '.clickable { touch(); onTune(ch) }' in main,
    'download resume retained': 'Range", "bytes=$offset-"' in Path('app/src/main/java/com/easyiptv/player/Downloads.kt').read_text(encoding='utf-8'),
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('v4.45 verification failed: ' + ', '.join(failed))
print('Zako 4.45 regression verification passed')
