from pathlib import Path
import re
main=Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text(encoding='utf-8')
gradle=Path('app/build.gradle.kts').read_text(encoding='utf-8')
required=['ZAKO_V446_PERSISTENT_UPDATE_BUTTON','Chip("Check for updates", false)','checkZakoUpdate','ZAKO_V445_RESTORED_FULL_CHAIN','ZAKO_V426_NATIVE_UPDATER']
missing=[x for x in required if x not in main]
if missing: raise SystemExit('v4.46 missing: '+', '.join(missing))
if not re.search(r'versionCode\s*=\s*71',gradle): raise SystemExit('versionCode 71 missing')
if 'versionName = "4.46"' not in gradle: raise SystemExit('versionName 4.46 missing')
print('Zako 4.46 persistent updater verification passed')
