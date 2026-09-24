#!/usr/bin/env python3
from pathlib import Path
m=Path("app/src/main/java/com/easyiptv/player/MainActivity.kt").read_text()
g=Path("app/build.gradle.kts").read_text()
checks={
"4.56 marker":"RYZOD_V456_VISUAL_UNIFICATION" in m,
"4.55 recovered chain":"ZAKO_V452_CONSOLIDATED" in m and "ZAKO_V445_RESTORED_FULL_CHAIN" in m,
"mini guide":"ZAKO_V425_MINI_EPG" in m and "rowProgram.title" in m,
"persistent updater":"ZAKO_V446_PERSISTENT_UPDATE_BUTTON" in m and "ZAKO_V426_NATIVE_UPDATER" in m,
"bubble UI":"ZAKO_V432_LIME_BUBBLES" in m and "LimeBubble" in m,
"pink focus":"FocusPink" in m,
"green channel palette":"ZAKO_V452_CHANNEL_GREEN" in m,
"manual DVR":"ZAKO_V448_MANUAL" in m or "Manual" in m and "ScheduleStore" in m,
"upcoming recordings":"Upcoming" in m and "ScheduleStore" in m,
"background recording":"Recorder" in m and "ScheduleStore" in m,
"resumable downloads":"Range" in Path("app/src/main/java/com/easyiptv/player/Downloads.kt").read_text(),
"startup gate":"ZAKO_V449_STARTUP_REARM" in m,
"timeshift async cleanup":"timeshift-cleanup" in m and "oldRing.close()" in m,
"low-memory image cache":"maxSizePercent(0.06)" in m,
"RYZOD brand mark":"RyzodBrandMark" in m,
"single main guide":'Text("RYZOD GUIDE"' in m and 'Chip(if (showGridGuide) "CHANNEL LIST" else "GRID GUIDE"' not in m,
"compact overlay keyboard":"widthIn(max=720.dp).fillMaxWidth(0.78f)" in m,
"keyboard wrap":"ZAKO_V452_WRAP_KEYBOARD" in m,
"versionCode 80":"versionCode = 80" in g,
"versionName 4.56":'versionName = "4.56"' in g,
}
bad=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(("PASS" if v else "FAIL")+": "+k)
if bad: raise SystemExit("4.56 regression guard failed: "+", ".join(bad))
print("RYZOD 4.56 no-regression guard passed")
