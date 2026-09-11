from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
GRADLE = Path('app/build.gradle.kts')
main = MAIN.read_text(encoding='utf-8')
gradle = GRADLE.read_text(encoding='utf-8')
changes=[]

def once(text, old, new, label):
    n=text.count(old)
    if n != 1: raise SystemExit(f'{label}: expected 1 match, found {n}')
    changes.append(label)
    return text.replace(old,new,1)

def section(text,start,end):
    a=text.index(start); b=text.index(end,a)
    return a,b,text[a:b]

# Version identity.
gradle,n=re.subn(r'versionCode\s*=\s*\d+','versionCode = 56',gradle,count=1)
if n!=1: raise SystemExit('versionCode missing')
gradle,n=re.subn(r'versionName\s*=\s*"[^"]+"','versionName = "4.31"',gradle,count=1)
if n!=1: raise SystemExit('versionName missing')
changes += ['versionCode 56','versionName 4.31']

main = once(main,
'private val DownloadGreen = Color(0xFF35F06F)\n',
'private val DownloadGreen = Color(0xFF35F06F)\nprivate val NeonGreen = Color(0xFF39FF88)\n',
'neon green palette')
main = once(main,
'''            Text(\n                if (showGridGuide) "2-hour window • arrows move like a normal TV guide" else "Guide data stays lightweight on Fire TV",\n                color = Ink, fontSize = 10.sp\n            )''',
'''            Text(\n                "Hold OK for Info • Record • Favorite",\n                color = NeonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold\n            )''',
'guide instruction')

a,b,live = section(main,'@Composable\nfun LivePane(','\n/* The timeshift DVR records the classic (.ts) live stream')
live = once(live,
'    var expandedId by remember { mutableStateOf<String?>(null) }\n',
'    var expandedId by remember { mutableStateOf<String?>(null) }\n    // ZAKO_V431_LIVE_INFO\n    var liveInfo by remember { mutableStateOf<Pair<LiveChannel, EpgEntry?>?>(null) }\n',
'live info state')

# Keep normal row navigation unchanged. A dedicated Info button is safer than
# relying on repeat-key semantics that vary between Fire TV remotes.
needle='''                            if (schedule.isNotEmpty()) {\n                                IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = {\n                                    expandedId = if (expandedId == ch.id) null else ch.id\n                                }) {'''
replacement='''                            IconButton(\n                                modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)),\n                                onClick = { liveInfo = ch to current }\n                            ) {\n                                Text("ⓘ", color = NeonGreen, fontSize = 20.sp, fontWeight = FontWeight.Black)\n                            }\n                            if (schedule.isNotEmpty()) {\n                                IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = {\n                                    expandedId = if (expandedId == ch.id) null else ch.id\n                                }) {'''
live = once(live,needle,replacement,'visible live info button')

# Insert dialog INSIDE LivePane, immediately before its outer Column. This keeps
# context, prefs, favorites, toggleFav and fmt all in lexical scope.
dialog='''    // ZAKO_V431_LIVE_INFO action sheet uses only the already-loaded EPG row.\n    liveInfo?.let { pair ->\n        val infoChannel = pair.first\n        val entry = pair.second\n        val recordable = infoChannel.url.endsWith(".ts")\n        AlertDialog(\n            onDismissRequest = { liveInfo = null },\n            containerColor = SurfaceCol,\n            title = { Text(entry?.title ?: infoChannel.name, color = Ink, fontWeight = FontWeight.ExtraBold) },\n            text = {\n                Column {\n                    Text(infoChannel.name, color = ProgramCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)\n                    if (entry != null) {\n                        Text("${fmt.format(Date(entry.startMs))}–${fmt.format(Date(entry.endMs))}", color = Ink, fontSize = 11.sp)\n                        Spacer(Modifier.height(7.dp))\n                        Text(entry.desc.ifBlank { "No description was supplied by the guide." }, color = Ink, fontSize = 12.sp)\n                    } else {\n                        Text("Program information is not available for this channel right now.", color = Muted, fontSize = 12.sp)\n                    }\n                    Spacer(Modifier.height(10.dp))\n                    TextButton(\n                        modifier = Modifier.tvFocus(RoundedCornerShape(16.dp)),\n                        onClick = { toggleFav(infoChannel.id) }\n                    ) {\n                        Text(if (favs.contains(infoChannel.id)) "★ REMOVE FAVORITE" else "☆ ADD FAVORITE", color = Accent, fontWeight = FontWeight.Bold)\n                    }\n                    if (recordable && entry != null) {\n                        TextButton(\n                            modifier = Modifier.tvFocus(RoundedCornerShape(16.dp)),\n                            onClick = {\n                                val airing = System.currentTimeMillis() in entry.startMs until entry.endMs\n                                if (airing) {\n                                    if (prefs.getBoolean("simple_mode", true)) {\n                                        toast(context, "Recording needs DVR Live. Switch off Smooth Live first.")\n                                    } else {\n                                        Recorder.start(context, infoChannel.url, "${entry.title} (${infoChannel.name})", entry.endMs + 2 * 60 * 1000)\n                                        toast(context, "Recording ${entry.title}.")\n                                        liveInfo = null\n                                    }\n                                } else {\n                                    toast(context, ScheduleStore.add(context, prefs, entry.title, infoChannel.name, infoChannel.url, entry.startMs, entry.endMs))\n                                    liveInfo = null\n                                }\n                            }\n                        ) { Text("● RECORD", color = Live, fontWeight = FontWeight.Bold) }\n                    }\n                }\n            },\n            confirmButton = {\n                TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(16.dp)), onClick = { liveInfo = null }) {\n                    Text("CLOSE", color = Ink)\n                }\n            }\n        )\n    }\n\n'''
anchor='    Column(Modifier.fillMaxSize()) {\n'
if anchor not in live: raise SystemExit('LivePane Column anchor missing')
live=live.replace(anchor,dialog+anchor,1)
changes.append('live info action sheet scoped inside LivePane')
main=main[:a]+live+main[b:]

a,b,mini=section(main,'@Composable\nprivate fun MiniGuide(','\n@Composable\nprivate fun MiniGuideControl(')
old='''                    val rowProgram = remember(ch.guideKey, ch.name, EpgStore.loaded.value) {\n                        val t = System.currentTimeMillis()\n                        EpgStore.guide(ch.guideKey, ch.name).firstOrNull { t in it.startMs until it.endMs }\n                    }'''
new='''                    // ZAKO_V431_MINI_TITLE: resolve from current guide data every composition.\n                    // Some providers key mini-player rows differently; fall back to epgId.\n                    val rowProgram = run {\n                        val t = System.currentTimeMillis()\n                        EpgStore.guide(ch.guideKey, ch.name).firstOrNull { t in it.startMs until it.endMs }\n                            ?: EpgStore.guide(ch.epgId, ch.name).firstOrNull { t in it.startMs until it.endMs }\n                    }'''
mini=once(mini,old,new,'mini title lookup fallback')
main=main[:a]+mini+main[b:]

old='''                            val raw = java.net.URL(\n                                "https://raw.githubusercontent.com/lukeypue/Easy-IPTV/main/latest.json"\n                            ).readText()'''
new='''                            // ZAKO_V431_FRESH_UPDATE: never reuse stale release metadata.\n                            val freshUrl = java.net.URL(\n                                "https://raw.githubusercontent.com/lukeypue/Easy-IPTV/main/latest.json?t=${System.currentTimeMillis()}"\n                            )\n                            val conn = (freshUrl.openConnection() as java.net.HttpURLConnection).apply {\n                                useCaches = false\n                                connectTimeout = 10_000\n                                readTimeout = 10_000\n                                setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")\n                                setRequestProperty("Pragma", "no-cache")\n                            }\n                            val raw = try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }'''
main=once(main,old,new,'fresh updater metadata')

main=main.replace('Zako 4.30','Zako 4.31')
MAIN.write_text(main,encoding='utf-8')
GRADLE.write_text(gradle,encoding='utf-8')
print('Applied Zako 4.31:')
for c in changes: print(' -',c)
