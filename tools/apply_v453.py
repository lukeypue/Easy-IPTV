#!/usr/bin/env python3
from pathlib import Path
import re
P=Path("app/src/main/java/com/easyiptv/player/MainActivity.kt")
G=Path("app/build.gradle.kts")
m=P.read_text(); g=G.read_text()
if "ZAKO_V453_REMOTE_POLISH" in m: raise SystemExit("already applied")

# Last-channel autoplay is opt-in, not opt-out.
m=m.replace('prefs.getBoolean("autoplay_last", true)','prefs.getBoolean("autoplay_last", false)')
m=m.replace('mutableStateOf(prefs.getBoolean("autoplay_last", true))','mutableStateOf(prefs.getBoolean("autoplay_last", false))')

# Keyboard: compact enough for 720p Fire TV setup screens; selected field stays visible.
m=m.replace('.height(40.dp)\n                            .background(if(selected)', '.height(30.dp)\n                            .background(if(selected)')
m=m.replace('.padding(7.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {', '.padding(4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {')
m=m.replace('fontSize=if(k.length>3)9.sp else 14.sp,', 'fontSize=if(k.length>3)8.sp else 12.sp,')
m=m.replace('Spacer(Modifier.height(6.dp))\n            Column(Modifier.fillMaxWidth()', 'Spacer(Modifier.height(2.dp))\n            Column(Modifier.fillMaxWidth()')

# Live guide program times: cyan, not faint secondary text.
m=m.replace('color = if (isNow) Accent else Muted,\n                                        modifier = Modifier.width(96.dp)',
            'color = if (isNow) Accent else ElectricCyan,\n                                        modifier = Modifier.width(96.dp)')
m=m.replace('color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis',
            'color = ElectricCyan, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis',1)
m=m.replace('color = Muted, fontSize = 8.sp\n                    )\n                    Spacer(Modifier.weight(1f))\n                    Text(if (hasProgramWindow) fmt.format(Date(showEnd)) else "LIVE", color = Muted, fontSize = 8.sp)',
            'color = ElectricCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold\n                    )\n                    Spacer(Modifier.weight(1f))\n                    Text(if (hasProgramWindow) fmt.format(Date(showEnd)) else "LIVE", color = ElectricCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)')

# Confirmation state for manual schedule.
m=m.replace('var manualDayOffset by remember { mutableIntStateOf(0) }',
'''var manualDayOffset by remember { mutableIntStateOf(0) }
    var pendingManual by remember { mutableStateOf<Triple<LiveChannel, Long, Long>?>(null) }''',1)
m=m.replace('toast(context,ScheduleStore.add(context,prefs,"Manual Recording",ch.name,ch.url,startMs,endMs))',
            'pendingManual = Triple(ch, startMs, endMs)')

# Add confirmation dialog once in LivePane before root Column.
anchor='''    Column(Modifier.fillMaxSize()) {
        if (guideLoading) {'''
dialog='''    pendingManual?.let { pending ->
        val (pendingChannel, pendingStart, pendingEnd) = pending
        AlertDialog(
            onDismissRequest = { pendingManual = null },
            containerColor = SurfaceCol,
            title = { Text("Confirm recording", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Record ${pendingChannel.name} on ${SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault()).format(Date(pendingStart))}?",
                    color = ElectricCyan, fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    toast(context, ScheduleStore.add(context, prefs, "Manual Recording", pendingChannel.name, pendingChannel.url, pendingStart, pendingEnd))
                    pendingManual = null
                }) { Text("Record", color = NeonGreen, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingManual = null }) { Text("Cancel", color = Ink) }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (guideLoading) {'''
if anchor not in m: raise SystemExit("LivePane anchor missing")
m=m.replace(anchor,dialog,1)

# Expanded record/guide panel is a modal navigation island: edge-left must not escape to app rail.
m=m.replace('''if (expandedId == ch.id && schedule.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))''',
'''if (expandedId == ch.id && schedule.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            // ZAKO_V453_RECORD_PANEL_TRAP: guide/timer navigation stays in this channel panel.
                            Box(Modifier.fillMaxWidth().onPreviewKeyEvent { ev ->
                                ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft
                            }) {''',1)
# close Box after schedule loop at the exact known sequence
m=m.replace('''                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* The timeshift DVR''',
'''                                }
                            }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* The timeshift DVR''',1)

# Recording stop/delete confirmations.
m=m.replace('''    val activeRecording = Recorder.activeName.value
    LaunchedEffect(activeRecording) {''',
'''    val activeRecording = Recorder.activeName.value
    var confirmStopRecording by remember { mutableStateOf(false) }
    var confirmDeleteRecording by remember { mutableStateOf<File?>(null) }
    if (confirmStopRecording) {
        AlertDialog(
            onDismissRequest = { confirmStopRecording = false },
            containerColor = SurfaceCol,
            title = { Text("Stop recording?", color = Ink) },
            text = { Text("Are you sure you want to stop the current recording?", color = ElectricCyan) },
            confirmButton = { TextButton(onClick = {
                Recorder.stop(context); confirmStopRecording = false
                files = Recorder.recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
            }) { Text("Stop recording", color = Live) } },
            dismissButton = { TextButton(onClick = { confirmStopRecording = false }) { Text("Keep recording", color = NeonGreen) } }
        )
    }
    confirmDeleteRecording?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDeleteRecording = null },
            containerColor = SurfaceCol,
            title = { Text("Delete recording?", color = Ink) },
            text = { Text("Delete \"${target.nameWithoutExtension.removePrefix("REC_").replace('_',' ')}\"?", color = ElectricCyan) },
            confirmButton = { TextButton(onClick = {
                target.delete(); confirmDeleteRecording = null
                files = Recorder.recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
            }) { Text("Delete", color = Live) } },
            dismissButton = { TextButton(onClick = { confirmDeleteRecording = null }) { Text("Keep", color = NeonGreen) } }
        )
    }
    LaunchedEffect(activeRecording) {''',1)
m=m.replace('''Button(onClick = {
                    Recorder.stop(context)
                    files = Recorder.recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                }) {''','''Button(onClick = { confirmStopRecording = true }) {''',1)
m=m.replace('''onClick = {
                                f.delete()
                                files = Recorder.recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                            }''','''onClick = { confirmDeleteRecording = f }''',1)

# Failed/interrupted download gets an explicit Resume action; ready items retain Delete.
needle='''                        IconButton(
                            modifier = Modifier.focusRequester(btnFocus).tvFocus(RoundedCornerShape(24.dp)),
                            onClick = {
                                DownloadStore.stopAndRemove(context, prefs, d)
                                items = DownloadStore.load(prefs)
                            }
                        ) {
                            Icon(
                                if (ready) Icons.Filled.Delete else Icons.Filled.Stop,
                                contentDescription = if (ready) "Delete" else "Stop download",
                                tint = Muted
                            )
                        }'''
replacement='''                        val dlState = DownloadStore.state(context, d.id)
                        if (!ready && dlState == DownloadStore.STATE_FAILED) {
                            Button(
                                modifier = Modifier.focusRequester(btnFocus).tvFocus(RoundedCornerShape(20.dp)),
                                onClick = {
                                    toast(context, DownloadStore.resume(context, prefs, d.id))
                                    items = DownloadStore.load(prefs)
                                }
                            ) { Text("Resume") }
                        } else {
                            IconButton(
                                modifier = Modifier.focusRequester(btnFocus).tvFocus(RoundedCornerShape(24.dp)),
                                onClick = {
                                    DownloadStore.stopAndRemove(context, prefs, d)
                                    items = DownloadStore.load(prefs)
                                }
                            ) {
                                Icon(if (ready) Icons.Filled.Delete else Icons.Filled.Stop,
                                    contentDescription = if (ready) "Delete" else "Stop download", tint = Muted)
                            }
                        }'''
if needle not in m: raise SystemExit("download action missing")
m=m.replace(needle,replacement,1)

# Marker + version.
m=m.replace('ZAKO_V452_CONSOLIDATED','ZAKO_V453_REMOTE_POLISH\n            // ZAKO_V452_CONSOLIDATED',1)
g=re.sub(r'versionCode\s*=\s*\d+','versionCode = 77',g,count=1)
g=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "4.53"',g,count=1)
P.write_text(m); G.write_text(g)
print("Applied 4.53 remote/navigation polish")
