#!/usr/bin/env python3
from pathlib import Path
import re
P=Path("app/src/main/java/com/easyiptv/player/MainActivity.kt"); G=Path("app/build.gradle.kts")
m=P.read_text(); g=G.read_text()

# RYZOD 4.60: startup + action-menu safety/polish.
# Startup must NEVER tune the previous channel. Keep last-channel identity only for
# guide/recent-channel UX; it is no longer a launch behavior or setting.
m=m.replace('''    // Only auto-tune to the last channel once per app start.
    var autoTuned by remember { mutableStateOf(false) }

''','',1)
a=m.find('''    // Cable-box behavior: the app opens straight onto the channel you were''')
b=m.find('''    fun addPlaylist(p: Playlist) {''',a)
if a<0 or b<0: raise SystemExit("autoplay block missing")
m=m[:a]+'''    // RYZOD_V460_FAST_START: never open a provider stream during app startup.
    // The viewer chooses Live TV after the lightweight playlist/guide shell is ready.

'''+m[b:]
m=m.replace('''    var autoLast by remember { mutableStateOf(prefs.getBoolean("autoplay_last", true)) }
''','',1)
a=m.find('''        Text("Start on last channel"''')
b=m.find('''        Text("Stream buffer"''',a)
if a<0 or b<0: raise SystemExit("autoplay setting block missing")
# include preceding spacer but preserve the Stream buffer section's own spacing
pre=m.rfind('''        Spacer(Modifier.height(20.dp))''',0,a)
if pre>=0: a=pre
m=m[:a]+m[b:]
# Permanently migrate old installs off the removed option.
needle='''    val prefs = remember { context.getSharedPreferences("easyiptv", Context.MODE_PRIVATE) }
'''
m=m.replace(needle,needle+'''    LaunchedEffect(Unit) { prefs.edit().putBoolean("autoplay_last", false).apply() }
''',1)

# Live guide: WATCH first, CLOSE last. 4.59 generates the compact row as
# RECORD/FAVORITE/CLOSE/WATCH, so rotate the same actions without changing behavior.
old='''                    if(recordable) TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={
'''
new='''                    if(airing) TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={
                        val queue=channels.map{livePlayable(prefs,it)};selected=null;onPlayLive(queue,chIndexOf(channels,ch))
                    }) { Text("▶ WATCH",color=ProgramCyan,fontWeight=FontWeight.Bold,fontSize=10.sp) }
                    if(recordable) TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={
'''
if old not in m: raise SystemExit("live compact row missing")
m=m.replace(old,new,1)
old='''                    TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={selected=null}) { Text("CLOSE",color=Ink,fontSize=10.sp) }
                    if(airing) TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={
                        val queue=channels.map{livePlayable(prefs,it)};selected=null;onPlayLive(queue,chIndexOf(channels,ch))
                    }) { Text("▶ WATCH",color=ProgramCyan,fontWeight=FontWeight.Bold,fontSize=10.sp) }
'''
new='''                    TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={selected=null}) { Text("CLOSE",color=Ink,fontSize=10.sp) }
'''
if old not in m: raise SystemExit("live compact row tail missing")
m=m.replace(old,new,1)
m=m.replace('''Text("Choose a day and hour • up to 7 days",color=Muted,fontSize=11.sp)''','''Text("Record by time • up to 7 days ahead",color=Muted,fontSize=11.sp)''',1)

# Movies already open the X1-style VodInfoDialog. Normalize that dialog itself to
# the requested single action row: PLAY, DOWNLOAD, CLOSE.
vod_start=m.find("private fun VodInfoDialog(")
vod_end=m.find("@Composable\nfun MoviesPane(",vod_start)
if vod_start<0 or vod_end<0: raise SystemExit("VodInfoDialog section missing")
vod=m[vod_start:vod_end]
old='''                Spacer(Modifier.height(12.dp))
                TextButton(
                    modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)),
                    onClick = {
                        toast(context, DownloadStore.start(context, prefs, movie.name, movie.url))
                    }
                ) {
                    Text("⬇ DOWNLOAD", color = DownloadGreen, fontWeight = FontWeight.Bold)
                }
'''
if old not in vod: raise SystemExit("VodInfoDialog embedded download missing")
vod=vod.replace(old,'''                Spacer(Modifier.height(12.dp))
''',1)
old='''        confirmButton = {
            TextButton(
                modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)),
                onClick = {
                    onClose()
                    onPlay(Playable(movie.name, movie.url, isLive = false, artwork = movie.icon))
                }
            ) { Text("▶ PLAY", color = ProgramCyan, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)), onClick = onClose) {
                Text("CLOSE", color = Ink)
            }
        }
'''
new='''        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(18.dp)),onClick={
                    onClose();onPlay(Playable(movie.name,movie.url,isLive=false,artwork=movie.icon))
                }) { Text("▶ PLAY",color=ProgramCyan,fontWeight=FontWeight.Bold) }
                TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(18.dp)),onClick={
                    toast(context,DownloadStore.start(context,prefs,movie.name,movie.url))
                }) { Text("⬇ DOWNLOAD",color=DownloadGreen,fontWeight=FontWeight.Bold) }
                TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(18.dp)),onClick=onClose) {
                    Text("CLOSE",color=Ink)
                }
            }
        },
        dismissButton = {}
'''
if old not in vod: raise SystemExit("VodInfoDialog buttons missing")
vod=vod.replace(old,new,1)
m=m[:vod_start]+vod+m[vod_end:]

# Series episodes already open EpisodeInfoDialog. Put PLAY / DOWNLOAD / CLOSE
# together in one row, matching Movies.
ep_start=m.find("private fun EpisodeInfoDialog(")
ep_end=m.find("@Composable\nfun SeriesDetailScreen(",ep_start)
if ep_start<0 or ep_end<0: raise SystemExit("EpisodeInfoDialog section missing")
epd=m[ep_start:ep_end]
old='''                Spacer(Modifier.height(14.dp))
                TextButton(
                    modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)),
                    onClick = { toast(context, DownloadStore.start(context, prefs, epName, episode.url)) }
                ) {
                    Text("⬇ DOWNLOAD", color = DownloadGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
'''
if old not in epd: raise SystemExit("EpisodeInfoDialog embedded download missing")
epd=epd.replace(old,'''                Spacer(Modifier.height(14.dp))
''',1)
old='''        confirmButton = {
            TextButton(
                modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)),
                onClick = { onClose(); onPlay() }
            ) { Text("▶ PLAY", color = ProgramCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        },
        dismissButton = {
            TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)), onClick = onClose) {
                Text("CLOSE", color = Ink, fontWeight = FontWeight.Bold)
            }
        }
'''
new='''        confirmButton = {
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(18.dp)),onClick={onClose();onPlay()}) {
                    Text("▶ PLAY",color=ProgramCyan,fontWeight=FontWeight.Bold,fontSize=14.sp)
                }
                TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(18.dp)),onClick={
                    toast(context,DownloadStore.start(context,prefs,epName,episode.url))
                }) { Text("⬇ DOWNLOAD",color=DownloadGreen,fontWeight=FontWeight.Bold,fontSize=14.sp) }
                TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(18.dp)),onClick=onClose) {
                    Text("CLOSE",color=Ink,fontWeight=FontWeight.Bold)
                }
            }
        },
        dismissButton = {}
'''
if old not in epd: raise SystemExit("EpisodeInfoDialog buttons missing")
epd=epd.replace(old,new,1)
m=m[:ep_start]+epd+m[ep_end:]

# Downloads: failed items expose RESUME; destructive stop/delete always confirms.
m=m.replace('''    val lastRate = remember { HashMap<Long, Double>() }
''','''    val lastRate = remember { HashMap<Long, Double>() }
    var confirmDownload by remember { mutableStateOf<DownloadStore.Item?>(null) }
''',1)
old='''                        IconButton(
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
new='''                        if (DownloadStore.state(context,d.id)==DownloadStore.STATE_FAILED && !ready) {
                            TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={
                                DownloadStore.resume(context,prefs,d);items=DownloadStore.load(prefs)
                            }) { Text("RESUME",color=Accent,fontWeight=FontWeight.Bold) }
                        }
                        IconButton(
                            modifier = Modifier.focusRequester(btnFocus).tvFocus(RoundedCornerShape(24.dp)),
                            onClick = { confirmDownload=d }
                        ) {
                            Icon(if (ready) Icons.Filled.Delete else Icons.Filled.Stop,
                                contentDescription = if (ready) "Delete" else "Stop download", tint = Muted)
                        }'''
if old not in m: raise SystemExit("download delete control missing")
m=m.replace(old,new,1)
# Confirmation after list.
needle='''        }
    }
}

fun RecordingsPane'''
rep='''        }
        confirmDownload?.let { d ->
            AlertDialog(
                onDismissRequest={confirmDownload=null},containerColor=SurfaceCol,
                title={Text("Are you sure?",color=Ink,fontWeight=FontWeight.ExtraBold)},
                text={Text(if(DownloadStore.isReady(context,d)) "Delete this download?" else "Stop and remove this download?",color=Muted)},
                confirmButton={TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={
                    DownloadStore.stopAndRemove(context,prefs,d);items=DownloadStore.load(prefs);confirmDownload=null
                }){Text("YES",color=Live,fontWeight=FontWeight.Bold)}},
                dismissButton={TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={confirmDownload=null}){Text("CLOSE",color=Ink)}}
            )
        }
    }
}

fun RecordingsPane'''
if needle not in m: raise SystemExit("download confirm anchor missing")
m=m.replace(needle,rep,1)

# Recordings: confirmation for scheduled cancellation and saved-file deletion.
m=m.replace('''    val activeRecording = Recorder.activeName.value
''','''    val activeRecording = Recorder.activeName.value
    var confirmRecordingFile by remember { mutableStateOf<File?>(null) }
    var confirmSchedule by remember { mutableStateOf<ScheduleStore.Item?>(null) }
    var confirmStopRecording by remember { mutableStateOf(false) }
''',1)
m=m.replace('''                    IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = {
                        ScheduleStore.cancel(context, prefs, s.id)
                        scheds = ScheduleStore.load(prefs)
                    }) {''','''                    IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = { confirmSchedule=s }) {''',1)
m=m.replace('''                Button(onClick = {
                    Recorder.stop(context)
                    files = Recorder.recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                }) {''','''                Button(onClick = { confirmStopRecording=true }) {''',1)
m=m.replace('''                            onClick = {
                                f.delete()
                                files = Recorder.recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                            }
''','''                            onClick = { confirmRecordingFile=f }
''',1)
needle='''        }
    }
}

/* ----------------------------- playlists ----------------------------- */'''
rep='''        }
        confirmStopRecording.takeIf{it}?.let {
            AlertDialog(onDismissRequest={confirmStopRecording=false},containerColor=SurfaceCol,
                title={Text("Are you sure?",color=Ink,fontWeight=FontWeight.ExtraBold)},
                text={Text("Stop the recording now?",color=Muted)},
                confirmButton={TextButton(onClick={Recorder.stop(context);confirmStopRecording=false}){Text("STOP RECORDING",color=Live,fontWeight=FontWeight.Bold)}},
                dismissButton={TextButton(onClick={confirmStopRecording=false}){Text("CLOSE",color=Ink)}})
        }
        confirmSchedule?.let { s ->
            AlertDialog(onDismissRequest={confirmSchedule=null},containerColor=SurfaceCol,
                title={Text("Are you sure?",color=Ink,fontWeight=FontWeight.ExtraBold)},
                text={Text("Delete this scheduled recording?",color=Muted)},
                confirmButton={TextButton(onClick={ScheduleStore.cancel(context,prefs,s.id);scheds=ScheduleStore.load(prefs);confirmSchedule=null}){Text("DELETE",color=Live,fontWeight=FontWeight.Bold)}},
                dismissButton={TextButton(onClick={confirmSchedule=null}){Text("CLOSE",color=Ink)}})
        }
        confirmRecordingFile?.let { f ->
            AlertDialog(onDismissRequest={confirmRecordingFile=null},containerColor=SurfaceCol,
                title={Text("Are you sure?",color=Ink,fontWeight=FontWeight.ExtraBold)},
                text={Text("Delete this recording?",color=Muted)},
                confirmButton={TextButton(onClick={f.delete();files=Recorder.recordingsDir(context).listFiles()?.sortedByDescending{it.lastModified()}?:emptyList();confirmRecordingFile=null}){Text("DELETE",color=Live,fontWeight=FontWeight.Bold)}},
                dismissButton={TextButton(onClick={confirmRecordingFile=null}){Text("CLOSE",color=Ink)}})
        }
    }
}

/* ----------------------------- playlists ----------------------------- */'''
if needle not in m: raise SystemExit("recording confirm anchor missing")
m=m.replace(needle,rep,1)

m="// RYZOD_V460_ACTION_AND_FAST_START_POLISH\n"+m
g=re.sub(r'versionCode\s*=\s*\d+','versionCode = 84',g,count=1)
g=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "4.60"',g,count=1)
P.write_text(m);G.write_text(g)
print("Applied RYZOD 4.60 action/startup polish")
