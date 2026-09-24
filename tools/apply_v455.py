#!/usr/bin/env python3
from pathlib import Path
import re
P=Path("app/src/main/java/com/easyiptv/player/MainActivity.kt")
G=Path("app/build.gradle.kts")
S=Path("app/src/main/res/values/strings.xml")
m=P.read_text(); g=G.read_text(); s=S.read_text()
m=m.replace("Zako","RYZOD")
s=s.replace(">Zako<",">RYZOD<")
old='''        stopInternal()
        val dir = if (prefs != null) Storage.timeshiftDir(context, prefs) else context.cacheDir
        val f = File(dir, "timeshift.ts")
        runCatching { f.delete() }
        file = f'''
new='''        val stale = file
        stopInternal()
        val dir = if (prefs != null) Storage.timeshiftDir(context, prefs) else context.cacheDir
        val f = File(dir, "timeshift_${System.nanoTime()}.ts")
        file = f
        if (stale != null) Thread({ runCatching { stale.delete() } }, "timeshift-cleanup").apply { isDaemon = true }.start()'''
if old not in m: raise SystemExit("v4.55 start patch target missing")
m=m.replace(old,new,1)
old2='''    fun stop() {
        stopInternal()
        runCatching { file?.delete() }
        file = null
    }'''
new2='''    fun stop() {
        val stale = file
        stopInternal()
        file = null
        if (stale != null) Thread({ runCatching { stale.delete() } }, "timeshift-cleanup").apply { isDaemon = true }.start()
    }'''
if old2 not in m: raise SystemExit("v4.55 stop patch target missing")
m=m.replace(old2,new2,1)
m="// RYZOD_V455_VERIFIED_FULL\n"+m
g=re.sub(r'versionCode\s*=\s*\d+','versionCode = 79',g,count=1)
g=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "4.55"',g,count=1)
P.write_text(m); G.write_text(g); S.write_text(s)
print("Applied RYZOD 4.55 branding and non-blocking timeshift cleanup")
