#!/usr/bin/env python3
from pathlib import Path
import re
P=Path("app/src/main/java/com/easyiptv/player/MainActivity.kt")
D=Path("app/src/main/java/com/easyiptv/player/Downloads.kt")
G=Path("app/build.gradle.kts")
m=P.read_text(); d=D.read_text(); g=G.read_text()

# Manual record picker: day + hour + 5-minute increments, then explicit confirmation.
m=m.replace("var manualDay by remember { mutableIntStateOf(0) }","var manualDay by remember { mutableIntStateOf(0) }\n    var manualHour by remember { mutableIntStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) }\n    var manualMinute by remember { mutableIntStateOf((java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)/5)*5) }\n    var confirmManual by remember { mutableStateOf<Triple<LiveChannel,Long,Long>?>(null) }",1)
start=m.find("    manualChannel?.let { ch ->")
end=m.find("    selected?.let { pair ->",start)
if start<0 or end<0: raise SystemExit("manual scheduler bounds missing")
manual=r'''    manualChannel?.let { ch ->
        val chosen=java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR,manualDay)
            set(java.util.Calendar.HOUR_OF_DAY,manualHour);set(java.util.Calendar.MINUTE,manualMinute)
            set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)
        }
        AlertDialog(onDismissRequest={manualChannel=null},containerColor=SurfaceCol,
            title={Text("Schedule "+ch.name,color=Ink,fontWeight=FontWeight.ExtraBold)},
            text={Column {
                Text("Day • next 7 days",color=Muted,fontSize=11.sp)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){items(7){day->
                    val cal=java.util.Calendar.getInstance().apply{add(java.util.Calendar.DAY_OF_YEAR,day)}
                    val label=SimpleDateFormat(if(day==0)"'Today'" else "EEE M/d",Locale.getDefault()).format(cal.time)
                    Chip(label,manualDay==day){manualDay=day}
                }}
                Text("Hour • 24 hours",color=Muted,fontSize=11.sp)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){items(24){hour->
                    val cal=(chosen.clone() as java.util.Calendar).apply{set(java.util.Calendar.HOUR_OF_DAY,hour)}
                    Chip(SimpleDateFormat("h a",Locale.getDefault()).format(cal.time),manualHour==hour){manualHour=hour}
                }}
                Text("Minutes • every 5 minutes",color=Muted,fontSize=11.sp)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){items(12){ix->
                    val minute=ix*5; Chip(String.format(Locale.US,"%02d",minute),manualMinute==minute){manualMinute=minute}
                }}
                Spacer(Modifier.height(6.dp))
                Text("Start: "+SimpleDateFormat("EEE h:mm a",Locale.getDefault()).format(chosen.time),color=Accent,fontWeight=FontWeight.Bold)
            }},
            confirmButton={TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={
                val st=chosen.timeInMillis
                if(st<=System.currentTimeMillis()) toast(context,"Choose a future time.")
                else { confirmManual=Triple(ch,st,st+60L*60L*1000L);manualChannel=null }
            }){Text("RECORD",color=Live,fontWeight=FontWeight.Bold)}},
            dismissButton={TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={manualChannel=null}){Text("CLOSE",color=Ink)}}
        )
    }
    confirmManual?.let { req ->
        val ch=req.first;val st=req.second;val en=req.third
        val whenText=SimpleDateFormat("EEEE 'at' h:mm a",Locale.getDefault()).format(Date(st))
        AlertDialog(onDismissRequest={confirmManual=null},containerColor=SurfaceCol,
            title={Text("Are you sure?",color=Ink,fontWeight=FontWeight.ExtraBold)},
            text={Text("Are you sure you want to record "+whenText+" on "+ch.name+"?",color=Ink)},
            confirmButton={TextButton(onClick={toast(context,ScheduleStore.add(context,prefs,"Manual Recording",ch.name,ch.url,st,en));confirmManual=null}){Text("YES, RECORD",color=Live,fontWeight=FontWeight.Bold)}},
            dismissButton={TextButton(onClick={confirmManual=null}){Text("CLOSE",color=Ink)}})
    }

'''
m=m[:start]+manual+m[end:]

# Recordings: clicking a saved recording opens Play/Delete/Close instead of immediate play.
needle="    var confirmRecordingFile by remember { mutableStateOf<File?>(null) }"
m=m.replace(needle,needle+"\n    var selectedRecordingFile by remember { mutableStateOf<File?>(null) }",1)
m=m.replace("onClick = { onPlay(Playable(f.nameWithoutExtension, Uri.fromFile(f).toString(), false)) }",
            "onClick = { selectedRecordingFile=f }",1)
anchor="        confirmStopRecording.takeIf{it}?.let {"
idx=m.find(anchor)
if idx<0: raise SystemExit("recording dialog anchor missing")
recorddlg=r'''        selectedRecordingFile?.let { f ->
            AlertDialog(onDismissRequest={selectedRecordingFile=null},containerColor=SurfaceCol,
                title={Text(f.nameWithoutExtension,color=Ink,fontWeight=FontWeight.ExtraBold)},
                confirmButton={Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    TextButton(onClick={selectedRecordingFile=null;onPlay(Playable(f.nameWithoutExtension,Uri.fromFile(f).toString(),false))}){Text("PLAY",color=Accent,fontWeight=FontWeight.Bold)}
                    TextButton(onClick={selectedRecordingFile=null;confirmRecordingFile=f}){Text("DELETE",color=Live,fontWeight=FontWeight.Bold)}
                    TextButton(onClick={selectedRecordingFile=null}){Text("CLOSE",color=Ink)}
                }},dismissButton={})
        }
'''
m=m[:idx]+recorddlg+m[idx:]

# Downloads: clicking any item opens a Play/Resume/Delete/Close action dialog.
dstart=m.find("fun DownloadsPane("); dend=m.find("/* ----------------------------- recordings ----------------------------- */",dstart)
if dstart<0 or dend<0: raise SystemExit("downloads pane bounds missing")
ds=m[dstart:dend]
state_anchor="    var confirmDownload by remember { mutableStateOf<DownloadStore.Item?>(null) }"
ds=ds.replace(state_anchor,state_anchor+"\n    var selectedDownload by remember { mutableStateOf<DownloadStore.Item?>(null) }",1)
# Make the title/row itself selectable where the existing ready play click is present.
ds=ds.replace("onClick = { if (ready) onPlay(Playable(d.title, Uri.fromFile(File(d.path)).toString(), false)) }",
              "onClick = { selectedDownload=d }")
close=ds.rfind("\n}")
dialog=r'''
        selectedDownload?.let { d ->
            val ready=DownloadStore.isReady(context,d)
            val resumable=!ready && DownloadStore.state(context,d.id)!=DownloadStore.STATE_RUNNING
            AlertDialog(onDismissRequest={selectedDownload=null},containerColor=SurfaceCol,
                title={Text(d.title,color=Ink,fontWeight=FontWeight.ExtraBold)},
                confirmButton={Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
                    if(ready) TextButton(onClick={selectedDownload=null;onPlay(Playable(d.title,Uri.fromFile(File(d.path)).toString(),false))}){Text("PLAY",color=Accent,fontWeight=FontWeight.Bold)}
                    if(DownloadStore.state(context,d.id)==DownloadStore.STATE_RUNNING) TextButton(onClick={toast(context,DownloadStore.pause(context,prefs,d));items=DownloadStore.load(prefs);selectedDownload=null}){Text("PAUSE",color=Accent,fontWeight=FontWeight.Bold)}\n                    if(resumable) TextButton(onClick={toast(context,DownloadStore.resume(context,prefs,d));items=DownloadStore.load(prefs);selectedDownload=null}){Text("RESUME",color=Accent,fontWeight=FontWeight.Bold)}
                    TextButton(onClick={selectedDownload=null;confirmDownload=d}){Text("DELETE",color=Live,fontWeight=FontWeight.Bold)}
                    TextButton(onClick={selectedDownload=null}){Text("CLOSE",color=Ink)}
                }},dismissButton={})
        }
'''
ds=ds[:close]+dialog+ds[close:]
m=m[:dstart]+ds+m[dend:]

# Version
m="// RYZOD_V463_RECORD_DOWNLOAD_REVIEW_PASS\n"+m
g=re.sub(r'versionCode\s*=\s*\d+','versionCode = 87',g,count=1)
g=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "4.63"',g,count=1)
P.write_text(m);G.write_text(g)
print("Applied RYZOD 4.63 recording/download UX")
