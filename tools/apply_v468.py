#!/usr/bin/env python3
"""RYZOD 4.68: duration-based guide geometry and conservative 30-minute DVR retention."""
from pathlib import Path
import re, shutil

root=Path("app/src/main/java/com/easyiptv/player")
p=root/"MainActivity.kt"
s=p.read_text()

# Normal TV-guide geometry: each EPG entry is rendered once and spans its real
# intersection with the visible two-hour window. The ruler remains 30-minute marks.
start=s.index("@Composable\nprivate fun LiveGridGuide(")
end=s.index("\nprivate fun chIndexOf(", start)
block=s[start:end]
old='''                    repeat(4) { slot ->
                        val slotStart = windowStart + slot * halfHour
                        val slotEnd = slotStart + halfHour
                        val entry = schedule.firstOrNull { slotStart in it.startMs until it.endMs }
                            ?: schedule.firstOrNull { it.startMs in slotStart until slotEnd }
                        val airing = entry != null && now in entry.startMs until entry.endMs
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(start = 3.dp)
                                .tvFocus(RoundedCornerShape(8.dp))
                                .background(
                                    if (airing) ProgramCyan.copy(alpha = 0.20f) else Surface2,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = entry != null) {
                                    if (entry != null) selected = ch to entry
                                }
                                .padding(horizontal = 6.dp, vertical = 5.dp)
                        ) {
                            if (entry == null) {
                                Text("—", color = Muted, fontSize = 10.sp)
                            } else {
                                Column {
                                    Text(
                                        entry.title,
                                        color = if (airing) ProgramCyan else Ink,
                                        fontSize = 10.sp,
                                        fontWeight = if (airing) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${fmt.format(Date(entry.startMs))}–${fmt.format(Date(entry.endMs))}",
                                        color = Ink, fontSize = 8.sp, maxLines = 1
                                    )
                                }
                            }
                        }
                    }'''
new='''                    val windowEnd = windowStart + 4L * halfHour
                    val visiblePrograms = schedule
                        .filter { it.endMs > windowStart && it.startMs < windowEnd }
                        .sortedBy { it.startMs }
                    Row(Modifier.weight(4f).fillMaxHeight()) {
                        var cursor = windowStart
                        visiblePrograms.forEach { entry ->
                            val visibleStart = maxOf(cursor, maxOf(entry.startMs, windowStart))
                            if (visibleStart > cursor) {
                                Spacer(Modifier.weight((visibleStart - cursor).toFloat(), fill = true))
                            }
                            val visibleEnd = minOf(entry.endMs, windowEnd)
                            if (visibleEnd > visibleStart) {
                                val airing = now in entry.startMs until entry.endMs
                                Box(
                                    Modifier
                                        .weight((visibleEnd - visibleStart).toFloat(), fill = true)
                                        .fillMaxHeight()
                                        .padding(start = 3.dp)
                                        .tvFocus(RoundedCornerShape(8.dp))
                                        .background(
                                            if (airing) ProgramCyan.copy(alpha = 0.20f) else Surface2,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selected = ch to entry }
                                        .padding(horizontal = 6.dp, vertical = 5.dp)
                                ) {
                                    Column {
                                        Text(
                                            entry.title,
                                            color = if (airing) ProgramCyan else Ink,
                                            fontSize = 10.sp,
                                            fontWeight = if (airing) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${fmt.format(Date(entry.startMs))}–${fmt.format(Date(entry.endMs))}",
                                            color = Ink, fontSize = 8.sp, maxLines = 1
                                        )
                                    }
                                }
                                cursor = visibleEnd
                            }
                        }
                        if (cursor < windowEnd) {
                            Spacer(Modifier.weight((windowEnd - cursor).toFloat(), fill = true))
                        }
                    }'''
if block.count(old)!=1: raise SystemExit("4.68 guide slot anchor changed")
block=block.replace(old,new,1)
s=s[:start]+block+s[end:]

# Keep the fallback ring bounded to 30 minutes. 4.68's first priority is
# eliminating the long-lived temporary DVR that regressed Fire Stick playback.
frag=Path("tools/v467/Timeshift.kt.fragment")
t=frag.read_text()
oldhist='val historyMs = if (target.kind == StorageKind.USB) 8L * 60L * 60L * 1000L else 90L * 60L * 1000L'
if t.count(oldhist)!=1: raise SystemExit("4.68 history anchor changed")
t=t.replace(oldhist,'val historyMs = 30L * 60L * 1000L',1)
frag.write_text(t)

# Version is generated after the 4.67 patch, so bump last.
p.write_text("// RYZOD_V468_STABILITY_GUIDE\n"+s)
g=Path("app/build.gradle.kts")
gt=g.read_text()
if 'versionCode = 91' not in gt or 'versionName = "4.67"' not in gt:
    raise SystemExit("4.68 version baseline changed")
g.write_text(gt.replace('versionCode = 91','versionCode = 92',1).replace('versionName = "4.67"','versionName = "4.68"',1))
print("Applied RYZOD 4.68 duration guide and 30-minute DVR cap")
