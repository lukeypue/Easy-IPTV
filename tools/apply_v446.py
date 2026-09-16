from pathlib import Path
import re
MAIN=Path('app/src/main/java/com/easyiptv/player/MainActivity.kt'); GRADLE=Path('app/build.gradle.kts')
main=MAIN.read_text(encoding='utf-8'); gradle=GRADLE.read_text(encoding='utf-8')
required=['Chip("Check for updates", false)','https://raw.githubusercontent.com/lukeypue/Easy-IPTV/main/latest.json','ZAKO_V445_RESTORED_FULL_CHAIN','ZAKO_V426_NATIVE_UPDATER','Chip("Get update", false)']
missing=[x for x in required if x not in main]
if missing: raise SystemExit('full updater chain missing: '+', '.join(missing))
needle='            Chip("Check for updates", false) {'
if main.count(needle)!=1: raise SystemExit('expected exactly one Settings update button')
main=main.replace(needle,'            // ZAKO_V446_PERSISTENT_UPDATE_BUTTON\n'+needle,1)
gradle,n1=re.subn(r'versionCode\s*=\s*\d+','versionCode = 71',gradle,count=1)
gradle,n2=re.subn(r'versionName\s*=\s*"[^"]+"','versionName = "4.46"',gradle,count=1)
if n1!=1 or n2!=1: raise SystemExit('v4.46 version bump failed')
MAIN.write_text(main,encoding='utf-8'); GRADLE.write_text(gradle,encoding='utf-8')
print('Applied Zako 4.46 persistent Settings updater')
