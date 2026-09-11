from pathlib import Path
import re
MAIN=Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
GRADLE=Path('app/build.gradle.kts')
main=MAIN.read_text(); gradle=GRADLE.read_text()
def once(text, old, new, label):
    n=text.count(old)
    if n!=1: raise SystemExit(f'{label}: expected 1, found {n}')
    return text.replace(old,new,1)
gradle,n=re.subn(r'versionCode\s*=\s*\d+','versionCode = 59',gradle,count=1)
if n!=1: raise SystemExit('version code')
gradle,n=re.subn(r'versionName\s*=\s*"[^"]+"','versionName = "4.34"',gradle,count=1)
if n!=1: raise SystemExit('version name')
marker='@Composable\nprivate fun MiniGuide(\n'
dialog='''@Composable
private fun MiniGuideNowInfoDialog(
    channelName: String,
    nowShow: EpgEntry,
    fmt: SimpleDateFormat,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = SurfaceCol,
        title = {
            Text(
                nowShow.title,
                color = Color(0xFFFFE45C),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                Text(channelName, color = ProgramCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(
                    "${fmt.format(Date(nowShow.startMs))}–${fmt.format(Date(nowShow.endMs))}",
                    color = Ink,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    nowShow.desc.ifBlank { "No description was supplied by the guide." },
                    color = Ink,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
            }
        },
        confirmButton = {
            TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)), onClick = onClose) {
                Text("CLOSE", color = ProgramCyan, fontWeight = FontWeight.Bold)
            }
        }
    )
}

'''
if marker not in main: raise SystemExit('MiniGuide marker missing')
main=main.replace(marker, dialog+marker, 1)
main=once(main,
'''    var showRecent by remember { mutableStateOf(false) }
    var playerBufferMs by remember { mutableLongStateOf(0L) }
''',
'''    var showRecent by remember { mutableStateOf(false) }
    var showNowInfo by remember { mutableStateOf(false) }
    if (showNowInfo && nowShow != null) {
        // ZAKO_V434_MINI_INFO: current-show information is readable without leaving Live TV.
        MiniGuideNowInfoDialog(
            channelName = current?.name.orEmpty(),
            nowShow = nowShow,
            fmt = fmt,
            onClose = { showNowInfo = false }
        )
    }
    var playerBufferMs by remember { mutableLongStateOf(0L) }
''','info state')
main=once(main,
'''    BackHandler(enabled = showRecent) {
        showRecent = false
        lastTouch = System.currentTimeMillis()
        runCatching { previousFocus.requestFocus() }
    }
''',
'''    BackHandler(enabled = showRecent) {
        showRecent = false
        lastTouch = System.currentTimeMillis()
        runCatching { previousFocus.requestFocus() }
    }
    BackHandler(enabled = showNowInfo) {
        showNowInfo = false
        lastTouch = System.currentTimeMillis()
    }
''','info back')
main=once(main,
'''    val sizeFocus = remember { FocusRequester() }
    val previousFocus = remember { FocusRequester() }
''',
'''    val sizeFocus = remember { FocusRequester() }
    val infoFocus = remember { FocusRequester() }
    val previousFocus = remember { FocusRequester() }
''','info focus')
old='''        // Compact information header. No focus targets here.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (current != null) {
                ChannelIcon(current.name, current.artwork, 32.dp)
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        current?.name ?: "",
                        color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (recordingThis) {
                        Spacer(Modifier.width(8.dp))
                        Text("● REC", color = Live, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (nowShow != null) {
                    Text(
                        "${nowShow.title}  •  ${fmt.format(Date(nowShow.startMs))}–${fmt.format(Date(nowShow.endMs))}" +
                            (if (nextShow != null) "   •   Next ${fmt.format(Date(nextShow.startMs))}: ${nextShow.title}" else ""),
                        color = ProgramCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
'''
new='''        // ZAKO_V434_MINI_HEADER: keep channel identity on the left and give the
        // current program its own readable space on the right instead of stacking
        // the title underneath the channel name.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (current != null) {
                ChannelIcon(current.name, current.artwork, 32.dp)
                Spacer(Modifier.width(8.dp))
            }
            Row(
                modifier = Modifier.widthIn(max = 210.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    current?.name ?: "",
                    color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (recordingThis) {
                    Spacer(Modifier.width(7.dp))
                    Text("● REC", color = Live, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(14.dp))
            if (nowShow != null) {
                Column(Modifier.weight(1f)) {
                    Text(
                        nowShow.title,
                        color = Color(0xFFFFE45C), // ZAKO_V434_MINI_NOW_YELLOW
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${fmt.format(Date(nowShow.startMs))}–${fmt.format(Date(nowShow.endMs))}" +
                            (if (nextShow != null) "   •   Next ${fmt.format(Date(nextShow.startMs))}: ${nextShow.title}" else ""),
                        color = Muted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
'''
main=once(main,old,new,'header')
main=once(main,
'''            MiniGuideControl(
                "SIZE",
                modifier = Modifier.weight(0.8f).focusRequester(sizeFocus).focusProperties {
                    left = modeFocus; right = previousFocus; down = timelineFocus
                }
            ) { touch(); onResize() }

            MiniGuideControl(
                "PREVIOUS",
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(previousFocus)
                    .focusProperties { left = sizeFocus; right = settingsFocus; down = timelineFocus }
''',
'''            MiniGuideControl(
                "SIZE",
                modifier = Modifier.weight(0.72f).focusRequester(sizeFocus).focusProperties {
                    left = modeFocus; right = infoFocus; down = timelineFocus
                }
            ) { touch(); onResize() }

            MiniGuideControl(
                "ⓘ INFO",
                enabled = nowShow != null,
                activeColor = Color(0xFFFFE45C),
                modifier = Modifier.weight(0.82f).focusRequester(infoFocus).focusProperties {
                    left = sizeFocus; right = previousFocus; down = timelineFocus
                }
            ) {
                touch()
                if (nowShow != null) showNowInfo = true
                else toast(context, "Program information is not available right now.")
            }

            MiniGuideControl(
                "PREVIOUS",
                modifier = Modifier
                    .weight(0.92f)
                    .focusRequester(previousFocus)
                    .focusProperties { left = infoFocus; right = settingsFocus; down = timelineFocus }
''','info control')
MAIN.write_text(main); GRADLE.write_text(gradle)
print('applied v4.34')
