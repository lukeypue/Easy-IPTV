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

# Movie click opens one-row Play / Download / Close actions.
movie_start=m.find("fun MoviesPane(")
movie_end=m.find("/* ----------------------------- series pane ----------------------------- */",movie_start)
if movie_start<0 or movie_end<0: raise SystemExit("MoviesPane section missing")
movie=m[movie_start:movie_end]
movie=movie.replace('''    val context = LocalContext.current
''','''    val context = LocalContext.current
    var movieActions by remember { mutableStateOf<MovieItem?>(null) }
''',1)
play_call='''                        onPlay(Playable(m.name, m.url, isLive = false, artwork = m.icon))
'''
if play_call not in movie: raise SystemExit("MoviesPane play call missing")
movie=movie.replace(play_call,'''                        movieActions = m
''',1)
close='''        }
    }
}
'''
pos=movie.rfind(close)
if pos<0: raise SystemExit("MoviesPane closing block missing")
dialog='''        }
        movieActions?.let { item ->
            AlertDialog(
                onDismissRequest={movieActions=null}, containerColor=SurfaceCol,
                title={Text(item.name,color=Ink,fontWeight=FontWeight.ExtraBold)},
                text={Text("Choose an action",color=Muted,fontSize=12.sp)},
                confirmButton={
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={
                            movieActions=null; onPlay(Playable(item.name,item.url,isLive=false,artwork=item.icon))
                        }) { Text("▶ PLAY",color=ProgramCyan,fontWeight=FontWeight.Bold) }
                        TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={
                            toast(context,DownloadStore.start(context,prefs,item.name,item.url));movieActions=null
                        }) { Text("⬇ DOWNLOAD",color=Accent,fontWeight=FontWeight.Bold) }
                        TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={movieActions=null}) { Text("CLOSE",color=Ink) }
                    }
                }, dismissButton={}
            )
        }
    }
}
'''
movie=movie[:pos]+dialog+movie[pos+len(close):]
m=m[:movie_start]+movie+m[movie_end:]

# Episode click gets the same one-row Play / Download / Close dialog.
m=m.replace('''    var watchTick by remember { mutableIntStateOf(0) }
    BackHandler { onBack() }''','''    var watchTick by remember { mutableIntStateOf(0) }
    var episodeActions by remember { mutableStateOf<Pair<Episode, Pair<String, Int>>?>(null) }
    BackHandler { if(episodeActions!=null) episodeActions=null else onBack() }''',1)
old='''                            onClick = {
                                val idx = queue.indexOfFirst { it.url == ep.url }.coerceAtLeast(0)
                                onPlayQueue(queue, idx)
                            },'''
new='''                            onClick = {
                                val idx = queue.indexOfFirst { it.url == ep.url }.coerceAtLeast(0)
                                episodeActions = ep to (epName to idx)
                            },'''
if old not in m: raise SystemExit("episode click missing")
m=m.replace(old,new,1)
anchor='''        }
    }
}

@Composable
fun PlayerScreen('''
dialog='''        }
        episodeActions?.let { picked ->
            val ep=picked.first; val epName=picked.second.first; val idx=picked.second.second
            AlertDialog(
                onDismissRequest={episodeActions=null},containerColor=SurfaceCol,
                title={Text(epName,color=Ink,fontWeight=FontWeight.ExtraBold)},
                text={Text("Choose an action",color=Muted,fontSize=12.sp)},
                confirmButton={
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={episodeActions=null;onPlayQueue(queue,idx)}) { Text("▶ PLAY",color=ProgramCyan,fontWeight=FontWeight.Bold) }
                        TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={toast(context,DownloadStore.start(context,prefs,epName,ep.url));episodeActions=null}) { Text("⬇ DOWNLOAD",color=Accent,fontWeight=FontWeight.Bold) }
                        TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={episodeActions=null}) { Text("CLOSE",color=Ink) }
                    }
                },dismissButton={}
            )
        }
    }
}

@Composable
fun PlayerScreen('''
# choose last occurrence before PlayerScreen (SeriesDetail end)
if anchor not in m: raise SystemExit("series dialog anchor missing")
m=m.replace(anchor,dialog,1)

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
