#!/usr/bin/env python3
from pathlib import Path
m=Path("app/src/main/java/com/easyiptv/player/MainActivity.kt").read_text()
r=Path("app/src/main/java/com/easyiptv/player/Recording.kt").read_text()
g=Path("app/build.gradle.kts").read_text()
checks={
"consolidated full chain":"ZAKO_V452_CONSOLIDATED" in m and "ZAKO_V445_RESTORED_FULL_CHAIN" in m,
"mini guide restored":"ZAKO_V425_MINI_EPG" in m and "rowProgram.title" in m,
"updater restored":"ZAKO_V446_PERSISTENT_UPDATE_BUTTON" in m and "ZAKO_V426_NATIVE_UPDATER" in m and 'Chip("Check for updates", false)' in m,
"bubble design":"ZAKO_V432_LIME_BUBBLES" in m and "LimeBubble" in m,
"hot pink focus":"FocusPink" in m,
"green channel palette":"ZAKO_V452_CHANNEL_GREEN" in m,
"readable secondary text":"0xFFBFEFFF" in m,
"single keyboard":"ZAKO_V452_WRAP_KEYBOARD" in m,
"keyboard right wrap":"col=(col+1)%rows[row].size" in m,
"keyboard left wrap":"col=(col-1+rows[row].size)%rows[row].size" in m,
"seven day timer":"ZAKO_V452_SEVEN_DAY_TIMER" in m and "items(7)" in m and "items(24)" in m,
"blank EPG timer":"No program information from provider" in m,
"upcoming recordings":"ManagedDvrUi.upcoming" in m and "Upcoming" in m,
"manual recording":"ManagedDvrUi.manualLabel" in m,
"schedule recovery":"ScheduleStore.rearmAll" in r,
"background recording":"class RecordingService : Service()" in r and "startForeground" in r,
"record retries":"ZAKO_V452_RECORD_RETRY" in r and "0 until 5" in r,
"resumable downloads":'header("Range", "bytes=$resumeFrom-")' in Path("app/src/main/java/com/easyiptv/player/Downloads.kt").read_text(),
"startup gate":"Please wait while we load your content fresh for a better experience" in m,
"version":"versionCode = 76" in g and 'versionName = "4.52"' in g,
}
bad=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(("PASS: " if v else "FAIL: ")+k)
raise SystemExit(1 if bad else 0)
