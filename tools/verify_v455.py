#!/usr/bin/env python3
from pathlib import Path
m=Path("app/src/main/java/com/easyiptv/player/MainActivity.kt").read_text()
g=Path("app/build.gradle.kts").read_text()
s=Path("app/src/main/res/values/strings.xml").read_text()
checks={
"RYZOD marker":"RYZOD_V455_VERIFIED_FULL" in m,
"app name":">RYZOD<" in s,
"mini guide":"rowProgram.title" in m,
"updater":'Chip("Check for updates", false)' in m,
"bubble UI":"LimeBubble" in m,
"pink focus":"FocusPink" in m,
"green channels":"ZAKO_V452_CHANNEL_GREEN" in m,
"keyboard":"ZAKO_V452_WRAP_KEYBOARD" in m,
"manual DVR":"items(7)" in m and "items(24)" in m,
"async timeshift cleanup":"timeshift-cleanup" in m and "oldRing.close()" in m,
"version":"versionCode = 79" in g and 'versionName = "4.55"' in g,
"release signing":"signingConfigs" in g and "ZAKO_KEYSTORE_PATH" in g,
}
bad=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(("PASS: " if v else "FAIL: ")+k)
raise SystemExit(1 if bad else 0)
