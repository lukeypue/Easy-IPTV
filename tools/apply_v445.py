from pathlib import Path
import re

MAIN=Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
GRADLE=Path('app/build.gradle.kts')
main=MAIN.read_text(encoding='utf-8'); gradle=GRADLE.read_text(encoding='utf-8')

# The complete generated chain already contains the intended three-row EPG mini guide
# and fluid rail navigation. 4.45's repair is to make sure those proven features are
# actually packaged together with 4.44 download resume instead of rebuilding stale 4.23.
required = ['ZAKO_V425_MINI_EPG', '3 rows stay on screen', 'onBackToRoot()', 'externalFocus.requestFocus()']
missing=[x for x in required if x not in main]
if missing: raise SystemExit('restored feature chain missing: '+', '.join(missing))
main=main.replace('@Composable\nprivate fun MiniGuide(', '// ZAKO_V445_RESTORED_FULL_CHAIN\n@Composable\nprivate fun MiniGuide(',1)
gradle,n1=re.subn(r'versionCode\s*=\s*\d+','versionCode = 70',gradle,count=1)
gradle,n2=re.subn(r'versionName\s*=\s*"[^"]+"','versionName = "4.45"',gradle,count=1)
if n1!=1 or n2!=1: raise SystemExit('v4.45 version bump failed')
MAIN.write_text(main,encoding='utf-8'); GRADLE.write_text(gradle,encoding='utf-8')
print('Applied Zako 4.45 full-chain packaging repair')
