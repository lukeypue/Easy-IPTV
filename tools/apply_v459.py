#!/usr/bin/env python3
from pathlib import Path
import re
P=Path("app/src/main/java/com/easyiptv/player/MainActivity.kt"); G=Path("app/build.gradle.kts")
m=P.read_text(); g=G.read_text()

# RYZOD 4.59: requested guide + keyboard corrections.
# Make the shared keyboard a true modal overlay by moving its existing compact panel
# out of the page layout and into a full-screen Dialog.
old='''        if(editing) {
            LaunchedEffect(Unit){kotlinx.coroutines.delay(80);runCatching{keyFocus.requestFocus()}}
            Spacer(Modifier.height(2.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment=Alignment.Center) {
            Column(Modifier.widthIn(max=640.dp).fillMaxWidth(0.72f).background(Color(0xF20A2038),RoundedCornerShape(18.dp))'''
new='''        if(editing) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest={editing=false},
                properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)
            ) {
            LaunchedEffect(Unit){kotlinx.coroutines.delay(80);runCatching{keyFocus.requestFocus()}}
            Box(Modifier.fillMaxSize().background(Color(0xB3000000)).padding(18.dp), contentAlignment=Alignment.Center) {
            Column(Modifier.widthIn(max=620.dp).fillMaxWidth(0.82f).background(Color(0xFF0A2038),RoundedCornerShape(14.dp))'''
if old not in m: raise SystemExit("keyboard overlay start missing")
m=m.replace(old,new,1)
old='''            }
            }
        }
    }
}

@Composable
private fun ChannelIcon'''
new='''            }
            }
            }
        }
    }
}

@Composable
private fun ChannelIcon'''
if old not in m: raise SystemExit("keyboard overlay close missing")
m=m.replace(old,new,1)
m=m.replace('.height(40.dp)\\n                            .background(if(selected)', '.height(34.dp)\\n                            .background(if(selected)',1)

# Favorite state/action into grid guide.
old='''LiveGridGuide(
                prefs = prefs, channels = filtered,
                onPlayLive = onPlayLive,
                onClose = { }
            )'''
new='''LiveGridGuide(
                prefs = prefs, channels = filtered,
                favs = favs, onToggleFavorite = { toggleFav(it) },
                onPlayLive = onPlayLive,
                onClose = { }
            )'''
if old not in m: raise SystemExit("grid call missing")
m=m.replace(old,new,1)
old='''private fun LiveGridGuide(
    prefs: SharedPreferences,
    channels: List<LiveChannel>,
    onPlayLive: (List<Playable>, Int) -> Unit,
    onClose: () -> Unit
) {'''
new='''private fun LiveGridGuide(
    prefs: SharedPreferences,
    channels: List<LiveChannel>,
    favs: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onPlayLive: (List<Playable>, Int) -> Unit,
    onClose: () -> Unit
) {'''
if old not in m: raise SystemExit("grid signature missing")
m=m.replace(old,new,1)
m=m.replace('''    var selected by remember { mutableStateOf<Pair<LiveChannel, EpgEntry>?>(null) }
    BackHandler { onClose() }''','''    var selected by remember { mutableStateOf<Pair<LiveChannel, EpgEntry>?>(null) }
    var manualChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var manualDay by remember { mutableIntStateOf(0) }
    BackHandler { onClose() }''',1)

# White header separator and channel/program divider.
needle='''            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),'''
if needle not in m: raise SystemExit("guide separator anchor missing")
m=m.replace(needle,'''            }
        }
        // RYZOD_V459_GUIDE_SEPARATORS
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha=.55f)))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),''',1)
old='''Modifier.width(142.dp).fillMaxHeight().background(SurfaceCol, RoundedCornerShape(8.dp)).padding(5.dp),'''
new='''Modifier.width(142.dp).fillMaxHeight()
                            .border(1.dp,Color.White.copy(alpha=.45f),RoundedCornerShape(8.dp))
                            .background(SurfaceCol, RoundedCornerShape(8.dp))
                            .tvFocus(RoundedCornerShape(8.dp)).clickable { manualChannel = ch }.padding(5.dp),'''
if old not in m: raise SystemExit("channel bubble anchor missing")
m=m.replace(old,new,1)

# Every blank grid bubble is selectable and becomes a manual half-hour slot.
old='''.clickable(enabled = entry != null) {
                                    if (entry != null) selected = ch to entry
                                }'''
new='''.clickable {
                                    selected = ch to (entry ?: EpgEntry(
                                        title="Manual Recording", desc="No program information from provider.",
                                        startMs=slotStart, endMs=slotEnd
                                    ))
                                }'''
if old not in m: raise SystemExit("blank bubble anchor missing")
m=m.replace(old,new,1)

# Move record out of dialog body; put Record/Favorite/Close/Watch together.
body_start=m.find('''                    if (recordable) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(''')
body_end=m.find('''                    }
                }
            },
            confirmButton = {''',body_start)
if body_start<0 or body_end<0: raise SystemExit("record body bounds missing")
m=m[:body_start]+m[body_end+len('''                    }
'''):]
cb=m.find('''            confirmButton = {''',body_start)
db=m.find('''            dismissButton = {''',cb)
ae=m.find('''        )
    }''',db)
if cb<0 or db<0 or ae<0: raise SystemExit("dialog action bounds missing")
actions=r'''            confirmButton = {
                // RYZOD_V459_ONE_ACTION_ROW
                Row(horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(recordable) TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={
                        if(airing) {
                            if(prefs.getBoolean("simple_mode",true)) toast(context,"Recording needs DVR Live. Switch off Smooth Live first.")
                            else { Recorder.start(context,ch.url,entry.title+" ("+ch.name+")",entry.endMs+2*60*1000);toast(context,"Recording "+entry.title+".");selected=null }
                        } else { toast(context,ScheduleStore.add(context,prefs,entry.title,ch.name,ch.url,entry.startMs,entry.endMs));selected=null }
                    }) { Text(if(airing)"● RECORD" else "● SCHEDULE",color=Live,fontWeight=FontWeight.Bold,fontSize=10.sp) }
                    TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={onToggleFavorite(ch.id)}) {
                        Text(if(favs.contains(ch.id))"★ FAVORITE" else "☆ FAVORITE",color=Accent,fontWeight=FontWeight.Bold,fontSize=10.sp)
                    }
                    TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={selected=null}) { Text("CLOSE",color=Ink,fontSize=10.sp) }
                    if(airing) TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={
                        val queue=channels.map{livePlayable(prefs,it)};selected=null;onPlayLive(queue,chIndexOf(channels,ch))
                    }) { Text("▶ WATCH",color=ProgramCyan,fontWeight=FontWeight.Bold,fontSize=10.sp) }
                }
            },
            dismissButton = {}
'''
m=m[:cb]+actions+m[ae:]

# Channel-name bubble opens a 7-day manual scheduler.
insert=m.find('''    selected?.let { pair ->''')
manual=r'''    manualChannel?.let { ch ->
        val chosen=java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR,manualDay);set(java.util.Calendar.MINUTE,0);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)
        }
        AlertDialog(
            onDismissRequest={manualChannel=null},containerColor=SurfaceCol,
            title={Text("Schedule "+ch.name,color=Ink,fontWeight=FontWeight.ExtraBold)},
            text={Column {
                Text("Choose a day and hour • up to 7 days",color=Muted,fontSize=11.sp)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    items(7){day->
                        val cal=java.util.Calendar.getInstance().apply{add(java.util.Calendar.DAY_OF_YEAR,day)}
                        val label=SimpleDateFormat(if(day==0)"'Today'" else "EEE M/d",Locale.getDefault()).format(cal.time)
                        Chip(label,manualDay==day){manualDay=day}
                    }
                }
                LazyRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    items(24){hour->
                        val st=(chosen.clone() as java.util.Calendar).apply{set(java.util.Calendar.HOUR_OF_DAY,hour)}.timeInMillis
                        val en=st+60L*60L*1000L
                        if(en>System.currentTimeMillis()) Chip(SimpleDateFormat("h a",Locale.getDefault()).format(Date(st)),false){
                            toast(context,ScheduleStore.add(context,prefs,"Manual Recording",ch.name,ch.url,st,en));manualChannel=null
                        }
                    }
                }
            }},
            confirmButton={TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={manualChannel=null}){Text("CLOSE",color=Ink)}},
            dismissButton={}
        )
    }

'''
if insert<0: raise SystemExit("manual scheduler insertion missing")
m=m[:insert]+manual+m[insert:]

# Visible branding only; keep historical internal markers intact.
m=m.replace('Text("ZAKO"', 'Text("RYZOD"').replace('"ZAKO GUIDE"','"RYZOD GUIDE"')
m="// RYZOD_V459_USER_REQUESTED_UI_FIXES\\n"+m
g=re.sub(r'versionCode\\s*=\\s*\\d+','versionCode = 83',g,count=1)
g=re.sub(r'versionName\\s*=\\s*"[^"]+"','versionName = "4.59"',g,count=1)
P.write_text(m);G.write_text(g)
print("Applied RYZOD 4.59 requested UI corrections")
