from pathlib import Path
import re
main=Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text(encoding='utf-8')
gradle=Path('app/build.gradle.kts').read_text(encoding='utf-8')
required=['ZAKO_V446_PERSISTENT_UPDATE_BUTTON','Chip("Check for updates", false)','https://raw.githubusercontent.com/lukeypue/Easy-IPTV/main/latest.json','ZAKO_V445_RESTORED_FULL_CHAIN','ZAKO_V426_NATIVE_UPDATER','Chip("Get update", false)']
missing=[x for x in required if x not in main]
if missing: raise SystemExit('v4.46 missing: '+', '.join(missing))
# The check button itself must not be conditional on updateUrl; only Get update may be conditional.
check_pos=main.index('Chip("Check for updates", false)')
conditional_pos=main.rfind('if (updateUrl != null)',0,check_pos)
settings_pos=main.rfind('fun SettingsPane(',0,check_pos)
if conditional_pos > settings_pos: raise SystemExit('Check for updates is incorrectly conditional')
if not re.search(r'versionCode\s*=\s*71',gradle): raise SystemExit('versionCode 71 missing')
if 'versionName = "4.46"' not in gradle: raise SystemExit('versionName 4.46 missing')
print('Zako 4.46 persistent updater verification passed')
