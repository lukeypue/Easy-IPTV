from pathlib import Path
import re

main=Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text(encoding='utf-8')
gradle=Path('app/build.gradle.kts').read_text(encoding='utf-8')
downloads=Path('app/src/main/java/com/easyiptv/player/Downloads.kt').read_text(encoding='utf-8')
checks={
 'versionCode 70': bool(re.search(r'versionCode\s*=\s*70\b',gradle)),
 'versionName 4.45': 'versionName = "4.45"' in gradle,
 'full-chain marker': 'ZAKO_V445_RESTORED_FULL_CHAIN' in main,
 'fluid LEFT navigation': 'onBackToRoot()' in main and 'externalFocus.requestFocus()' in main,
 'three-row mini guide': 'ZAKO_V425_MINI_EPG' in main and '3 rows stay on screen' in main and 'height(98.dp)' in main and '.height(31.dp)' in main,
 'mini guide program title': 'EpgStore.guide(ch.guideKey, ch.name)' in main and 'rowProgram.title' in main,
 'mini guide OK tune': '.clickable { touch(); onTune(ch) }' in main,
 'download resume retained': 'header("Range", "bytes=$resumeFrom-")' in downloads and 'resumeFrom > 0L && r.code == 206' in downloads,
}
failed=[k for k,v in checks.items() if not v]
if failed: raise SystemExit('v4.45 verification failed: '+', '.join(failed))
print('Zako 4.45 regression verification passed: '+', '.join(checks))
