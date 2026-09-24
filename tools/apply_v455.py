#!/usr/bin/env python3
from pathlib import Path
import re
P=Path("app/src/main/java/com/easyiptv/player/MainActivity.kt")
G=Path("app/build.gradle.kts")
S=Path("app/src/main/res/values/strings.xml")
m=P.read_text(); g=G.read_text(); s=S.read_text()
m=m.replace("Zako","RYZOD")
s=s.replace(">Zako<",">RYZOD<")
old='''        val oldRing = ring
        ring = null
        runCatching { oldRing?.close() }
        bytesWritten = 0L'''
new='''        val oldRing = ring
        ring = null
        if (oldRing != null) Thread({ runCatching { oldRing.close() } }, "timeshift-cleanup").apply { isDaemon = true }.start()
        bytesWritten = 0L'''
if old not in m: raise SystemExit("v4.55 rolling-ring cleanup target missing")
m=m.replace(old,new,1)
m="// RYZOD_V455_VERIFIED_FULL\n"+m
g=re.sub(r'versionCode\s*=\s*\d+','versionCode = 79',g,count=1)
g=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "4.55"',g,count=1)
P.write_text(m); G.write_text(g); S.write_text(s)
print("Applied RYZOD 4.55 branding and non-blocking timeshift cleanup")
