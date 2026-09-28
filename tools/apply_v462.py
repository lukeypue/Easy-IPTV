#!/usr/bin/env python3
from pathlib import Path
import re
P=Path("app/src/main/java/com/easyiptv/player/MainActivity.kt")
G=Path("app/build.gradle.kts")
m=P.read_text(); g=G.read_text()

# RYZOD 4.62 — stability pass on top of the verified 4.61 feature chain.
# Temporary DVR is channel-clock based: EPG boundaries never reset or reshape it.
m=m.replace("private const val DVR_HISTORY_MS = 45L * 60L * 1000L","private const val DVR_HISTORY_MS = 55L * 60L * 1000L",1)

old='''        val showStart = nowShow?.startMs ?: 0L
        val showEnd = nowShow?.endMs ?: 0L
        val showDuration = (showEnd - showStart).coerceAtLeast(0L)
        val hasProgramWindow = showDuration > 1_000L
        val trueDvrStartWall = Timeshift.startedAtWallMs
        val visibleDvrStartWall = if (trueDvrStartWall > 0L)
            maxOf(trueDvrStartWall, nowMs - DVR_HISTORY_MS)
        else 0L
        val playWall = if (trueDvrStartWall > 0L) trueDvrStartWall + playerPosMs else nowMs
        val programLiveFraction = if (hasProgramWindow)
            ((nowMs - showStart).toFloat() / showDuration.toFloat()).coerceIn(0f, 1f)
        else dvrProgress
        val programPlayFraction = if (hasProgramWindow)
            ((playWall - showStart).toFloat() / showDuration.toFloat()).coerceIn(0f, 1f)
        else dvrProgress
        val dvrStartFraction = if (hasProgramWindow && visibleDvrStartWall > 0L)
            ((maxOf(visibleDvrStartWall, showStart) - showStart).toFloat() / showDuration.toFloat()).coerceIn(0f, 1f)
        else 0f
        val dvrEndFraction = if (hasProgramWindow && dvrActive)
            ((minOf(nowMs, showEnd) - showStart).toFloat() / showDuration.toFloat()).coerceIn(0f, 1f)
        else if (dvrActive) 1f else 0f'''
new='''        // RYZOD_V462_CHANNEL_CLOCK_DVR
        // The temporary DVR belongs to the CHANNEL, never to an EPG program.
        // A show boundary therefore cannot jump/reset/reload the timeline.
        val trueDvrStartWall = Timeshift.startedAtWallMs
        val availableMs = minOf(dvrWindowMs, DVR_HISTORY_MS).coerceAtLeast(0L)
        val visibleDvrStartWall = if (trueDvrStartWall > 0L)
            (nowMs - availableMs).coerceAtLeast(trueDvrStartWall)
        else 0L
        val playInVisibleMs = (playerPosMs - (dvrWindowMs - availableMs).coerceAtLeast(0L)).coerceIn(0L, availableMs.coerceAtLeast(1L))
        val programLiveFraction = 1f
        val programPlayFraction = if (availableMs > 0L) (playInVisibleMs.toFloat()/availableMs.toFloat()).coerceIn(0f,1f) else 1f
        val dvrStartFraction = 0f
        val dvrEndFraction = if (dvrActive) 1f else 0f
        val hasProgramWindow = false
        val showEnd = nowMs'''
if old not in m: raise SystemExit("v4.62 timeline target missing")
m=m.replace(old,new,1)

# Avoid the old fixed-byte cliff landing around the end of a normal 55-minute
# viewing session. USB keeps the FAT-safe ceiling; internal gets enough headroom
# for a high-bitrate 55-minute session while the storage-floor guard remains active.
m=m.replace('capBytes = if (prefs != null && Storage.usingDrive(context, prefs)) 3_500_000_000L else 1_000_000_000L',
            'capBytes = if (prefs != null && Storage.usingDrive(context, prefs)) 3_500_000_000L else 2_500_000_000L',1)

# Reconnect calmly: don't hammer a provider after a transient socket failure.
m=m.replace('try { Thread.sleep(1_000) } catch (e: InterruptedException) { break }',
            'try { Thread.sleep(1_750) } catch (e: InterruptedException) { break }',1)

# Full shared remote keyboard. Keep URL/login punctuation visible in alpha mode,
# and restore Fire-TV-sized keys instead of the over-shrunk 4.59 layout.
m=m.replace('listOf("123","SPACE",".","-","_","@","/","DONE")',
            'listOf("123","SPACE",".","/","-","_","@","DONE")')
m=m.replace('listOf("ABC","SPACE",":","/","@","_","-","DONE")',
            'listOf("ABC","SPACE",":","/","@","_","-","DONE")')
m=m.replace('.height(34.dp)\\n                            .background(if(selected)', '.height(40.dp)\\n                            .background(if(selected)')
m=m.replace('.height(30.dp)\\n                            .background(if(selected)', '.height(40.dp)\\n                            .background(if(selected)')
m=m.replace('fontSize=if(k.length>3)8.sp else 12.sp,','fontSize=if(k.length>3)9.sp else 14.sp,')

m="// RYZOD_V462_STREAM_DVR_KEYBOARD_STABILITY\n"+m
g=re.sub(r'versionCode\\s*=\\s*\\d+','versionCode = 86',g,count=1)
g=re.sub(r'versionName\\s*=\\s*"[^"]+"','versionName = "4.62"',g,count=1)
P.write_text(m); G.write_text(g)
print("Applied RYZOD 4.62 stream/DVR/keyboard stability")
