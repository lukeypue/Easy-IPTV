#!/usr/bin/env python3
from pathlib import Path
import re
MAIN=Path("app/src/main/java/com/easyiptv/player/MainActivity.kt")
REC=Path("app/src/main/java/com/easyiptv/player/Recording.kt")
GRADLE=Path("app/build.gradle.kts")
main=MAIN.read_text(); rec=REC.read_text(); gradle=GRADLE.read_text()
if "ZAKO_V452_CONSOLIDATED" in main: raise SystemExit("4.52 already applied")
main=re.sub(r'private val Muted = Color\\(0x[0-9A-Fa-f]+\\)','private val Muted = Color(0xFFBFEFFF)',main,count=1)
if 'private val NeonGreen = Color(0xFF39FF88)' in main:
    main=main.replace('private val NeonGreen = Color(0xFF39FF88)','private val NeonGreen = Color(0xFF39FF88) // ZAKO_V452_CHANNEL_GREEN',1)
a=main.index('@Composable\nprivate fun TvTextField('); b=main.index('@Composable\nprivate fun ChannelIcon(',a)
keyboard=r'''@Composable
private fun TvTextField(
    value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, placeholder: String = "", password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    // ZAKO_V452_WRAP_KEYBOARD
    var editing by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }
    var upper by remember { mutableStateOf(false) }
    var row by remember { mutableIntStateOf(0) }
    var col by remember { mutableIntStateOf(0) }
    val keyFocus = remember { FocusRequester() }
    val alphaRows = if (upper) listOf(
        listOf("Q","W","E","R","T","Y","U","I","O","P"), listOf("A","S","D","F","G","H","J","K","L"),
        listOf("SHIFT","Z","X","C","V","B","N","M","DEL"), listOf("123","SPACE",".","-","_","@","/","DONE")
    ) else listOf(
        listOf("q","w","e","r","t","y","u","i","o","p"), listOf("a","s","d","f","g","h","j","k","l"),
        listOf("SHIFT","z","x","c","v","b","n","m","DEL"), listOf("123","SPACE",".","-","_","@","/","DONE")
    )
    val symbolRows = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"), listOf("!","#","$","%","&","*","(",")","+","="),
        listOf(":",";","'",",",".","?","-","_","@","DEL"), listOf("ABC","SPACE",":","/","@","_","-","DONE")
    )
    val rows = if(symbols) symbolRows else alphaRows
    fun press(k:String) { when(k) {
        "DEL" -> if(value.isNotEmpty()) onValueChange(value.dropLast(1)); "SPACE" -> onValueChange(value+" ");
        "SHIFT" -> upper=!upper; "123" -> {symbols=true;row=0;col=0}; "ABC" -> {symbols=false;row=0;col=0};
        "DONE" -> editing=false; else -> onValueChange(value+k)
    }}
    BackHandler(enabled=editing){editing=false}
    Column(modifier) {
        Column(Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(18.dp))
            .background(PanelGlow.copy(alpha=.72f),RoundedCornerShape(18.dp))
            .border(2.dp,ElectricCyan.copy(alpha=.72f),RoundedCornerShape(18.dp))
            .clickable{editing=true}.padding(horizontal=14.dp,vertical=11.dp)) {
            Text(label,color=ElectricCyan,fontSize=11.sp,fontWeight=FontWeight.Bold)
            Text(when{value.isEmpty()->placeholder.ifEmpty{"Press OK to type"};password->"•".repeat(value.length.coerceAtMost(16));else->value},
                color=if(value.isEmpty())Muted else Ink,fontSize=15.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        if(editing) {
            LaunchedEffect(Unit){kotlinx.coroutines.delay(80);runCatching{keyFocus.requestFocus()}}
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth().background(Color(0xF20A2038),RoundedCornerShape(18.dp))
                .border(2.dp,ElectricCyan.copy(alpha=.60f),RoundedCornerShape(18.dp))
                .focusRequester(keyFocus).focusable().onPreviewKeyEvent { ev ->
                    if(ev.type!=KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when(ev.key) {
                        Key.DirectionRight->{col=(col+1)%rows[row].size;true}
                        Key.DirectionLeft->{col=(col-1+rows[row].size)%rows[row].size;true}
                        Key.DirectionDown->{row=(row+1)%rows.size;col=col.coerceAtMost(rows[row].lastIndex);true}
                        Key.DirectionUp->{row=(row-1+rows.size)%rows.size;col=col.coerceAtMost(rows[row].lastIndex);true}
                        Key.DirectionCenter,Key.Enter->{press(rows[row][col]);true}
                        else->false
                    }}.padding(7.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                rows.forEachIndexed { ri,keys -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    keys.forEachIndexed { ci,k -> val selected=ri==row&&ci==col
                        Box(Modifier.weight(if(k=="SPACE")2.1f else if(k=="DONE")1.45f else 1f).height(40.dp)
                            .background(if(selected)FocusPink.copy(alpha=.28f) else PanelGlow,RoundedCornerShape(10.dp))
                            .border(if(selected)3.dp else 1.dp,if(selected)FocusPink else ElectricCyan.copy(alpha=.30f),RoundedCornerShape(10.dp)),
                            contentAlignment=Alignment.Center) {
                            Text(k,color=if(k=="DONE")NeonGreen else Ink,fontSize=if(k.length>3)9.sp else 14.sp,
                                fontWeight=if(selected)FontWeight.ExtraBold else FontWeight.SemiBold)
                        }
                    }
                }}
                Text("D-pad moves • OK types • Left/Right wraps • Back closes",color=ElectricCyan,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
            }
        }
    }
}

'''
main=main[:a]+keyboard+main[b:]
main=main.replace('var expandedId by remember { mutableStateOf<String?>(null) }',
    'var expandedId by remember { mutableStateOf<String?>(null) }\n    var manualDayOffset by remember { mutableIntStateOf(0) }',1)
old_start='''                            if (schedule.isEmpty()) {
                                // Provider metadata is optional; the timeline is not.'''
si=main.find(old_start); ei=main.find('                            schedule.take(30).forEach { e ->',si)
if si<0 or ei<0: raise SystemExit("4.50 universal guide block missing")
manual=r'''                            // ZAKO_V452_SEVEN_DAY_TIMER
                            Text(if(schedule.isEmpty()) "No program information from provider • Manual timer available"
                                else "Manual timer • choose any day/time up to 7 days",
                                color=ElectricCyan,fontSize=11.sp,fontWeight=FontWeight.Bold)
                            LazyRow(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                                items(7) { day ->
                                    val cal=java.util.Calendar.getInstance().apply{add(java.util.Calendar.DAY_OF_YEAR,day)}
                                    val label=SimpleDateFormat(if(day==0)"'Today'" else "EEE M/d",Locale.getDefault()).format(cal.time)
                                    Chip(label,manualDayOffset==day){manualDayOffset=day}
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            val chosen=java.util.Calendar.getInstance().apply{
                                add(java.util.Calendar.DAY_OF_YEAR,manualDayOffset);set(java.util.Calendar.MINUTE,0);
                                set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)
                            }
                            LazyRow(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                                items(24) { hour ->
                                    val startMs=(chosen.clone() as java.util.Calendar).apply{set(java.util.Calendar.HOUR_OF_DAY,hour)}.timeInMillis
                                    val endMs=startMs+60L*60L*1000L
                                    if(endMs>now) Chip(SimpleDateFormat("h a",Locale.getDefault()).format(Date(startMs)),false) {
                                        toast(context,ScheduleStore.add(context,prefs,"Manual Recording",ch.name,ch.url,startMs,endMs))
                                    }
                                }
                            }
'''
main=main[:si]+manual+main[ei:]
root_old='''RailItem(
                    p.second, section == p.first,
                    modifier = if (pos == 0) Modifier.focusRequester(firstRootFocus) else Modifier
                ) { onRoot(p.first) }'''
root_new='''RailItem(
                    p.second, section == p.first,
                    modifier = Modifier.then(if (pos == 0) Modifier.focusRequester(firstRootFocus) else Modifier)
                        .then(if (section == p.first) Modifier.focusRequester(externalFocus) else Modifier)
                ) { onRoot(p.first) }'''
if root_old in main: main=main.replace(root_old,root_new,1)
for old,new in [
('section == "downloads" -> DownloadsPane(prefs, onPlay)','section == "downloads" -> Box(Modifier.fillMaxSize().onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) { runCatching { railFocus.requestFocus() }; true } else false }) { DownloadsPane(prefs, onPlay) }'),
('section == "recordings" -> RecordingsPane(prefs, onPlay)','section == "recordings" -> Box(Modifier.fillMaxSize().onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) { runCatching { railFocus.requestFocus() }; true } else false }) { RecordingsPane(prefs, onPlay) }'),
('section == "playlists" -> PlaylistsPane(playlists, activeIdx, onSelectPlaylist, onDeletePlaylist, onAddPlaylist)','section == "playlists" -> Box(Modifier.fillMaxSize().onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) { runCatching { railFocus.requestFocus() }; true } else false }) { PlaylistsPane(playlists, activeIdx, onSelectPlaylist, onDeletePlaylist, onAddPlaylist) }'),
('section == "settings" -> SettingsPane(prefs, onModeChanged = onRetry)','section == "settings" -> Box(Modifier.fillMaxSize().onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) { runCatching { railFocus.requestFocus() }; true } else false }) { SettingsPane(prefs, onModeChanged = onRetry) }')
]:
    if old in main: main=main.replace(old,new,1)
needle='''                        val req = Request.Builder().url(url).header("User-Agent", Net.UA).build()
                        Net.streamClient.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
                            val body = resp.body
                            if (body != null) {
                                body.byteStream().use { inp ->
                                    val buf = ByteArray(64 * 1024)
                                    var sinceCheck = 0L
                                    while (isActive && (stopAt == null || System.currentTimeMillis() < stopAt)) {
                                        val n = inp.read(buf)
                                        if (n < 0) break
                                        out.write(buf, 0, n)
                                        sinceCheck += n
                                        if (sinceCheck > 32_000_000) {
                                            sinceCheck = 0
                                            val free = runCatching { android.os.StatFs(dir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
                                            if (free < 2_000_000_000L) break
                                        }
                                    }
                                }
                            }
                        }'''
if needle not in rec:
    # tolerate comments inside the legacy block by matching from request to closing section
    start=rec.find('                        val req = Request.Builder().url(url).header("User-Agent", Net.UA).build()')
    stop=rec.find('                    }\n                }\n            } catch',start)
    if start<0 or stop<0: raise SystemExit("recording network block missing")
    old=rec[start:stop]
else: old=needle
retry=r'''                        // ZAKO_V452_RECORD_RETRY
                        var lastNetworkError: Exception? = null
                        var connected = false
                        for (attempt in 0 until 5) {
                            if (!isActive || (stopAt != null && System.currentTimeMillis() >= stopAt)) break
                            try {
                                val req=Request.Builder().url(url).header("User-Agent",Net.UA).build()
                                Net.streamClient.newCall(req).execute().use { resp ->
                                    if(!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
                                    val body=resp.body ?: throw java.io.IOException("Empty stream")
                                    connected=true
                                    body.byteStream().use { inp ->
                                        val buf=ByteArray(64*1024); var sinceCheck=0L
                                        while(isActive && (stopAt==null || System.currentTimeMillis()<stopAt)) {
                                            val n=inp.read(buf); if(n<0) break; out.write(buf,0,n); sinceCheck+=n
                                            if(sinceCheck>32_000_000){sinceCheck=0
                                                val free=runCatching{android.os.StatFs(dir.absolutePath).availableBytes}.getOrDefault(Long.MAX_VALUE)
                                                if(free<2_000_000_000L) break
                                            }
                                        }
                                    }
                                }
                                break
                            } catch(e:Exception) {
                                lastNetworkError=e
                                if(attempt<4) Thread.sleep(1500L*(attempt+1))
                            }
                        }
                        if(!connected) throw(lastNetworkError ?: java.io.IOException("Provider stream did not start"))
'''
rec=rec.replace(old,retry,1)
main=main.replace('ZAKO_V447_FULL_REDESIGN','ZAKO_V452_CONSOLIDATED\n            // ZAKO_V447_FULL_REDESIGN',1)
gradle=re.sub(r'versionCode\\s*=\\s*\\d+','versionCode = 76',gradle,count=1)
gradle=re.sub(r'versionName\\s*=\\s*"[^"]+"','versionName = "4.52"',gradle,count=1)
MAIN.write_text(main);REC.write_text(rec);GRADLE.write_text(gradle)
print("Applied Zako 4.52 consolidated regression repair")
