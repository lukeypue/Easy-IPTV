#!/usr/bin/env python3
from pathlib import Path
p=Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
s=p.read_text()
if 'ZAKO_V450_UNIVERSAL_GUIDE' in s:
    print('already applied'); raise SystemExit()
old='''                            if (schedule.isNotEmpty()) {
                                IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = {
                                    expandedId = if (expandedId == ch.id) null else ch.id
                                }) {
                                    Icon(
                                        Icons.Filled.Today,
                                        contentDescription = "See what's on later",
                                        tint = if (expandedId == ch.id) Accent else Muted
                                    )
                                }
                            }'''
new='''                            // ZAKO_V450_UNIVERSAL_GUIDE: every playlist channel always has
                            // a guide/timer entry, even when the provider supplies zero EPG rows.
                            IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = {
                                expandedId = if (expandedId == ch.id) null else ch.id
                            }) {
                                Icon(
                                    Icons.Filled.Today,
                                    contentDescription = if (schedule.isNotEmpty()) "See what's on later" else "Open time guide",
                                    tint = if (expandedId == ch.id) Accent else Muted
                                )
                            }'''
if old not in s: raise SystemExit('guide icon anchor missing')
s=s.replace(old,new,1)
old2='''                        if (expandedId == ch.id && schedule.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            val dayFmt = remember { SimpleDateFormat("EEE h:mm a", Locale.getDefault()) }
                            schedule.take(30).forEach { e ->'''
new2='''                        if (expandedId == ch.id) {
                            Spacer(Modifier.height(6.dp))
                            val dayFmt = remember { SimpleDateFormat("EEE h:mm a", Locale.getDefault()) }
                            if (schedule.isEmpty()) {
                                // Provider metadata is optional; the timeline is not.
                                // A blank channel still exposes future wall-clock slots so
                                // phone touch and Fire TV D-pad users get the same recorder.
                                Text("No program information from provider", color = Muted, fontSize = 11.sp)
                                val slotStart = ((now + 29L * 60L * 1000L) / (30L * 60L * 1000L)) * (30L * 60L * 1000L)
                                (0 until 12).forEach { slot ->
                                    val startMs = slotStart + slot * 30L * 60L * 1000L
                                    val endMs = startMs + 30L * 60L * 1000L
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(dayFmt.format(Date(startMs)), fontSize = 12.sp, color = Muted, modifier = Modifier.width(96.dp))
                                        Text("No information", fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f))
                                        IconButton(
                                            modifier = Modifier.size(32.dp).tvFocus(RoundedCornerShape(16.dp)),
                                            onClick = {
                                                toast(context, ScheduleStore.add(context, prefs, "Manual Recording", ch.name, ch.url, startMs, endMs))
                                            }
                                        ) {
                                            Icon(Icons.Filled.FiberManualRecord, contentDescription = "Record this time", tint = Live)
                                        }
                                    }
                                }
                            }
                            schedule.take(30).forEach { e ->'''
if old2 not in s: raise SystemExit('expanded guide anchor missing')
s=s.replace(old2,new2,1)
p.write_text(s)
print('Applied universal guide/timer surface to every channel')
