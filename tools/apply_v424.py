from pathlib import Path

PATH = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
text = PATH.read_text(encoding='utf-8')
changes = []

def replace_once(old: str, new: str, name: str):
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{name}: expected exactly 1 match, found {count}')
    text = text.replace(old, new, 1)
    changes.append(name)

def edit_section(start_marker: str, end_marker: str, editor, name: str):
    global text
    start = text.index(start_marker)
    end = text.index(end_marker, start)
    section = text[start:end]
    updated = editor(section)
    if updated == section:
        raise SystemExit(f'{name}: section did not change')
    text = text[:start] + updated + text[end:]
    changes.append(name)

replace_once(
    'import androidx.compose.ui.input.key.onPreviewKeyEvent\n',
    'import androidx.compose.ui.input.key.onPreviewKeyEvent\nimport androidx.compose.ui.input.key.onKeyEvent\n',
    'import onKeyEvent'
)

accelerator = r'''

private class DvrSeekAccelerator {
    data class Step(val deltaMs: Long, val gear: Int, val applyNow: Boolean)

    private var direction = 0
    private var gear = 0
    private var lastPressAt = 0L
    private var lastAppliedAt = 0L

    fun next(dir: Int, repeatCount: Int): Step {
        val now = android.os.SystemClock.uptimeMillis()
        val freshBurst = direction != dir || now - lastPressAt > 1_400L
        if (freshBurst) {
            gear = 0
        } else if (repeatCount == 0) {
            gear = (gear + 1) % JUMPS.size
        } else if (repeatCount > 0 && repeatCount % 6 == 0) {
            gear = (gear + 1).coerceAtMost(JUMPS.lastIndex)
        }
        direction = dir
        lastPressAt = now

        val apply = repeatCount == 0 || now - lastAppliedAt >= 380L
        if (apply) lastAppliedAt = now
        return Step(JUMPS[gear] * dir, gear, apply)
    }

    fun reset() {
        direction = 0
        gear = 0
        lastPressAt = 0L
        lastAppliedAt = 0L
    }

    companion object {
        private val JUMPS = longArrayOf(10_000L, 30_000L, 60_000L, 180_000L, 300_000L)

        fun label(gear: Int, dir: Int): String {
            val side = if (dir > 0) "FF" else "REW"
            return when (gear) {
                0 -> "$side 1×"
                1 -> "$side 2×"
                2 -> "$side 3×"
                3 -> "$side 4×"
                else -> "⚡ $side"
            }
        }
    }
}
'''
replace_once(
    'private const val DVR_PRIME_BYTES = 512L * 1024L\n',
    'private const val DVR_PRIME_BYTES = 512L * 1024L\n' + accelerator,
    'add DVR seek accelerator'
)

replace_once(
    '    var handler: ((Int) -> Boolean)? = null\n',
    '    var handler: ((Int, android.view.KeyEvent?) -> Boolean)? = null\n',
    'PlayerKeys event-aware handler'
)
replace_once(
    '        if (PlayerKeys.handler?.invoke(keyCode) == true) return true\n',
    '        if (PlayerKeys.handler?.invoke(keyCode, event) == true) return true\n',
    'pass KeyEvent to PlayerKeys'
)

replace_once(
    '            } catch (e: Exception) {\n                if (data == null) loadError = e.message ?: "error"\n            }\n',
    '            } catch (e: Exception) {\n                loadError = e.message ?: "Provider refresh unavailable"\n            }\n',
    'retain refresh failure with cache'
)

def patch_home(section: str) -> str:
    marker = '    val railFocus = remember { FocusRequester() }\n'
    safe = '''    val railFocus = remember { FocusRequester() }\n    val safeData = data ?: AppData(\n        liveCats = emptyList(), live = emptyList(),\n        vodCats = emptyList(), movies = emptyList(),\n        seriesCats = emptyList(), series = emptyList()\n    )\n'''
    if marker not in section:
        raise SystemExit('home: railFocus marker missing')
    section = section.replace(marker, safe, 1)

    old_outer = '''            data == null && loadError == null -> Box(Modifier.weight(1f)) { LoadingBox("Loading your playlist…") }\n            loadError != null -> Box(Modifier.weight(1f)) { ErrorBox(loadError, onRetry) }\n            else -> Row(Modifier.weight(1f)) {\n'''
    new_outer = '''            false && data == null && loadError == null -> Box(Modifier.weight(1f)) { LoadingBox("Loading your playlist…") }\n            false && loadError != null -> Box(Modifier.weight(1f)) { ErrorBox(loadError, onRetry) }\n            else -> Row(Modifier.weight(1f)) {\n'''
    if old_outer not in section:
        raise SystemExit('home: outer loading block missing')
    section = section.replace(old_outer, new_outer, 1)
    section = section.replace('data!!', 'safeData')

    old_box = '                Box(Modifier.weight(1f)) {\n                    if (catalogLoading'
    new_box = '''                Box(\n                    Modifier\n                        .weight(1f)\n                        .onKeyEvent { ev ->\n                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft) {\n                                runCatching { railFocus.requestFocus() }\n                                true\n                            } else false\n                        }\n                ) {\n                    if (catalogLoading'''
    if old_box not in section:
        raise SystemExit('home: content Box marker missing')
    section = section.replace(old_box, new_box, 1)

    old_when = '''                    when {\n                        !catalogLoading && catalogError != null &&'''
    new_when = '''                    when {\n                        data == null && loadError == null &&\n                            (section == "live" || section == "movies" || section == "series" || section == "search") ->\n                            LoadingBox("Loading your playlist…")\n                        data == null && loadError != null &&\n                            (section == "live" || section == "movies" || section == "series" || section == "search") ->\n                            ErrorBox(\n                                err = (loadError ?: "No internet connection") +\n                                    "\\n\\nDownloads and saved recordings still work. Choose them from the left menu.",\n                                onRetry = onRetry,\n                                title = "Live service unavailable"\n                            )\n                        !catalogLoading && catalogError != null &&'''
    if old_when not in section:
        raise SystemExit('home: inner when marker missing')
    section = section.replace(old_when, new_when, 1)

    old_clock = '            Text(clock, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)\n'
    new_clock = '''            if (loadError != null) {\n                Text(\n                    "OFFLINE / SAVED",\n                    fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Accent,\n                    modifier = Modifier.padding(end = 10.dp)\n                )\n            }\n            Text(clock, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)\n'''
    if old_clock not in section:
        raise SystemExit('home: clock marker missing')
    section = section.replace(old_clock, new_clock, 1)
    return section

edit_section('@Composable\nfun HomeScreen(', '\nprivate val RootItems = listOf(', patch_home, 'offline-capable HomeScreen')

def patch_rail(section: str) -> str:
    old = '''    val firstRootFocus = remember { FocusRequester() }\n    LaunchedEffect(depth) {\n        if (depth == 0) {\n            kotlinx.coroutines.delay(100)\n            runCatching { firstRootFocus.requestFocus() }\n        }\n    }\n    LazyColumn(\n        modifier = Modifier.width(126.dp).fillMaxHeight().background(SurfaceCol),'''
    new = '''    LaunchedEffect(depth, section) {\n        if (depth == 0) {\n            kotlinx.coroutines.delay(100)\n            runCatching { externalFocus.requestFocus() }\n        }\n    }\n    LazyColumn(\n        modifier = Modifier\n            .width(126.dp)\n            .fillMaxHeight()\n            .background(SurfaceCol)\n            .onKeyEvent { ev ->\n                if (depth == 1 && ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft) {\n                    onBackToRoot()\n                    true\n                } else false\n            },'''
    if old not in section:
        raise SystemExit('rail: root focus block missing')
    section = section.replace(old, new, 1)
    old_item = '''                    p.second, section == p.first,\n                    modifier = if (pos == 0) Modifier.focusRequester(firstRootFocus) else Modifier\n                ) { onRoot(p.first) }'''
    new_item = '''                    p.second, section == p.first,\n                    modifier = if (p.first == section) Modifier.focusRequester(externalFocus) else Modifier\n                ) { onRoot(p.first) }'''
    if old_item not in section:
        raise SystemExit('rail: root item focus block missing')
    section = section.replace(old_item, new_item, 1)
    return section

edit_section('@Composable\nprivate fun HomeRail(', '\n@Composable\nprivate fun RailItem(', patch_rail, 'fluid LEFT navigation')

search_keyboard = r'''

@Composable
private fun SearchKey(
    label: String,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .weight(weight)
            .height(34.dp)
            .tvFocus(RoundedCornerShape(7.dp))
            .background(Color(0x55202634), RoundedCornerShape(7.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SearchTvKeyboardField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val firstKey = remember { FocusRequester() }
    BackHandler(enabled = open) { open = false }
    LaunchedEffect(open) {
        if (open) {
            kotlinx.coroutines.delay(80)
            runCatching { firstKey.requestFocus() }
        }
    }

    Column(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .tvFocus(RoundedCornerShape(9.dp))
                .background(Color(0x33202634), RoundedCornerShape(9.dp))
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text("Search", color = Muted, fontSize = 10.sp)
            Text(
                value.ifEmpty { "Press OK to type with the Zako TV keyboard" },
                color = if (value.isEmpty()) Muted else Ink,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (open) {
            Spacer(Modifier.height(5.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xEE171922), RoundedCornerShape(10.dp))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val rows = listOf("1234567890", "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
                rows.forEachIndexed { rowIndex, chars ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        chars.forEachIndexed { colIndex, c ->
                            SearchKey(
                                label = c.toString(),
                                modifier = if (rowIndex == 0 && colIndex == 0) Modifier.focusRequester(firstKey) else Modifier
                            ) { onValueChange(value + c.lowercaseChar()) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SearchKey("SPACE", weight = 2f) { onValueChange(value + " ") }
                    SearchKey("⌫") { if (value.isNotEmpty()) onValueChange(value.dropLast(1)) }
                    SearchKey("CLEAR") { onValueChange("") }
                    SearchKey("DONE", weight = 1.4f) { open = false }
                }
                Text("Back closes keyboard • D-pad moves • OK types", color = Muted, fontSize = 8.sp)
            }
        }
    }
}
'''
replace_once(
    '@Composable\nfun SearchTab(\n',
    search_keyboard + '\n@Composable\nfun SearchTab(\n',
    'insert custom search TV keyboard'
)
replace_once(
    '''            TvTextField(\n                value = query, onValueChange = onQuery,\n                label = "Search",\n                placeholder = "Search live, movies & series…",\n                modifier = Modifier.weight(1f)\n            )\n''',
    '''            SearchTvKeyboardField(\n                value = query, onValueChange = onQuery,\n                modifier = Modifier.weight(1f)\n            )\n''',
    'use custom keyboard in Search'
)

def patch_settings(section: str) -> str:
    old = '''    val ctx = LocalContext.current\n    var usbPermissionRefresh by remember { mutableIntStateOf(0) }'''
    new = '''    val ctx = LocalContext.current\n    var updateStatus by remember { mutableStateOf("") }\n    var updateUrl by remember { mutableStateOf<String?>(null) }\n    val updateScope = rememberCoroutineScope()\n    var usbPermissionRefresh by remember { mutableIntStateOf(0) }'''
    if old not in section:
        raise SystemExit('settings: ctx marker missing')
    section = section.replace(old, new, 1)

    old_version = '        Text("Zako 4.23 — plays the playlists you provide. This app includes no channels or content of its own.", fontSize = 11.sp, color = Muted)\n'
    update_ui = r'''        Text("Updates", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "Zako checks only when you press the button — no background updater taking memory or waking the Fire Stick.",
            fontSize = 12.sp, color = Muted
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Check for updates", false) {
                updateStatus = "Checking…"
                updateUrl = null
                updateScope.launch {
                    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            val raw = java.net.URL(
                                "https://raw.githubusercontent.com/lukeypue/Easy-IPTV/main/latest.json"
                            ).readText()
                            val obj = org.json.JSONObject(raw)
                            Triple(
                                obj.optInt("versionCode", 0),
                                obj.optString("versionName", "new version"),
                                obj.optString("downloadUrl", "")
                            )
                        }
                    }
                    result.onSuccess { (code, name, url) ->
                        if (code > BuildConfig.VERSION_CODE) {
                            updateStatus = "Zako $name is available."
                            updateUrl = url.takeIf { it.startsWith("http") }
                        } else {
                            updateStatus = "You're up to date — Zako ${BuildConfig.VERSION_NAME}."
                        }
                    }.onFailure {
                        updateStatus = "Couldn't check right now. Your saved downloads still work offline."
                    }
                }
            }
            if (updateUrl != null) {
                Chip("Get update", false) {
                    runCatching {
                        ctx.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(updateUrl)
                            )
                        )
                    }.onFailure { toast(ctx, "Couldn't open the update page on this device.") }
                }
            }
        }
        if (updateStatus.isNotBlank()) {
            Text(updateStatus, color = Accent, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
        }

        Spacer(Modifier.height(20.dp))
        Text("Zako ${BuildConfig.VERSION_NAME} — plays the playlists you provide. This app includes no channels or content of its own.", fontSize = 11.sp, color = Muted)
'''
    if old_version not in section:
        raise SystemExit('settings: version footer missing')
    section = section.replace(old_version, update_ui, 1)
    return section

edit_section('@Composable\nfun SettingsPane(', '\n/* ----------------------------- search (bottom tab, with recents) ----------------------------- */', patch_settings, 'manual update checker')

for needle, name in [
    ('TextButton(onClick = {\n                    askDeleteRecording = false', 'recording delete focus'),
    ('TextButton(onClick = {\n                    askDeleteDownload = false', 'download delete focus'),
]:
    replace_once(
        needle,
        'TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)), onClick = {\n                    ' + needle.split('                    ',1)[1],
        name
    )
replace_once(
    '''            dismissButton = {\n                TextButton(onClick = {\n                    askDeleteRecording = false''',
    '''            dismissButton = {\n                TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)), onClick = {\n                    askDeleteRecording = false''',
    'recording keep focus'
)
replace_once(
    '''            dismissButton = {\n                TextButton(onClick = {\n                    askDeleteDownload = false''',
    '''            dismissButton = {\n                TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)), onClick = {\n                    askDeleteDownload = false''',
    'download keep focus'
)

def patch_player(section: str) -> str:
    old_state = '    var pvRef by remember { mutableStateOf<PlayerView?>(null) }\n'
    new_state = '''    var pvRef by remember { mutableStateOf<PlayerView?>(null) }\n    val remoteDvrSeek = remember { DvrSeekAccelerator() }\n    var dvrSeekHint by remember { mutableStateOf("") }\n\n    fun remoteDvrSeek(dir: Int, event: android.view.KeyEvent?) {\n        val step = remoteDvrSeek.next(dir, event?.repeatCount ?: 0)\n        dvrSeekHint = DvrSeekAccelerator.label(step.gear, dir)\n        if (step.applyNow && !Playback.seekDvrBy(step.deltaMs) && (event?.repeatCount ?: 0) == 0) {\n            toast(context, "DVR is still building — pause a moment, then try again.")\n        }\n    }\n\n    LaunchedEffect(dvrSeekHint) {\n        if (dvrSeekHint.isNotEmpty()) {\n            val seen = dvrSeekHint\n            kotlinx.coroutines.delay(950)\n            if (dvrSeekHint == seen) dvrSeekHint = ""\n        }\n    }\n'''
    if old_state not in section:
        raise SystemExit('player: pvRef marker missing')
    section = section.replace(old_state, new_state, 1)

    if '        PlayerKeys.handler = { key ->\n' not in section:
        raise SystemExit('player: handler signature missing')
    section = section.replace('        PlayerKeys.handler = { key ->\n', '        PlayerKeys.handler = { key, event ->\n', 1)

    old_ff = '''                android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {\n                    if (current.isLive) {\n                        if (!Playback.simpleRaw && !Playback.directLive) Playback.seekDvrBy(DVR_REMOTE_SKIP_MS)\n                    } else exo.seekForward()\n                    true\n                }\n                android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {\n                    if (current.isLive) {\n                        if (!Playback.simpleRaw && !Playback.directLive) Playback.seekDvrBy(-DVR_REMOTE_SKIP_MS)\n                    } else exo.seekBack()\n                    true\n                }'''
    new_ff = '''                android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {\n                    val liveNow = queue.getOrNull(Playback.currentIdxC.intValue)?.isLive == true\n                    if (liveNow) {\n                        if (!Playback.simpleRaw && !Playback.directLive) remoteDvrSeek(+1, event)\n                    } else exo.seekForward()\n                    true\n                }\n                android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {\n                    val liveNow = queue.getOrNull(Playback.currentIdxC.intValue)?.isLive == true\n                    if (liveNow) {\n                        if (!Playback.simpleRaw && !Playback.directLive) remoteDvrSeek(-1, event)\n                    } else exo.seekBack()\n                    true\n                }'''
    if old_ff not in section:
        raise SystemExit('player: FF/RW block missing')
    section = section.replace(old_ff, new_ff, 1)

    section = section.replace(
        '                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> { exo.playWhenReady = true; true }',
        '                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> { remoteDvrSeek.reset(); exo.playWhenReady = true; true }',
        1
    )

    hint_marker = '''        if (showClock && clockText.isNotEmpty()) {'''
    hint_ui = '''        if (dvrSeekHint.isNotEmpty()) {\n            Text(\n                dvrSeekHint,\n                color = Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,\n                modifier = Modifier\n                    .align(Alignment.CenterEnd)\n                    .padding(end = 26.dp)\n                    .background(Color(0xCC171922), RoundedCornerShape(10.dp))\n                    .border(2.dp, FocusPink, RoundedCornerShape(10.dp))\n                    .padding(horizontal = 14.dp, vertical = 8.dp)\n            )\n        }\n        if (showClock && clockText.isNotEmpty()) {'''
    if hint_marker not in section:
        raise SystemExit('player: hint render marker missing')
    section = section.replace(hint_marker, hint_ui, 1)
    return section

edit_section('@OptIn(UnstableApi::class)\n@Composable\nfun PlayerScreen(', '\n/* ---------------------------------------------------------------------------\n * X1-STYLE MINI GUIDE', patch_player, 'accelerated remote FF/REW')

def patch_miniguide(section: str) -> str:
    state_marker = '''    var isPlaying by remember { mutableStateOf(player?.isPlaying == true) }\n'''
    state_new = '''    var isPlaying by remember { mutableStateOf(player?.isPlaying == true) }\n    val timelineSeek = remember { DvrSeekAccelerator() }\n    val lineupFocus = remember { FocusRequester() }\n    val lineupState = androidx.compose.foundation.lazy.rememberLazyListState(\n        initialFirstVisibleItemIndex = (currentIdx - 1).coerceAtLeast(0)\n    )\n\n    LaunchedEffect(currentIdx, queue.size) {\n        if (queue.isNotEmpty()) {\n            lineupState.scrollToItem((currentIdx - 1).coerceIn(0, queue.lastIndex))\n        }\n    }\n'''
    if state_marker not in section:
        raise SystemExit('mini: state marker missing')
    section = section.replace(state_marker, state_new, 1)

    old_seek = '''    fun seekDvr(deltaMs: Long) {\n        touch()\n        when {\n            Playback.simpleRaw -> toast(context, "Rewind needs DVR Live.")\n            Playback.directLive -> toast(context, "DVR is in Direct Rescue. Select DVR RETRY first.")\n            current?.isLive != true -> toast(context, "DVR controls are only for Live TV.")\n            !Playback.seekDvrBy(deltaMs) ->\n                toast(context, "DVR is still building — pause a moment, then try again.")\n        }\n    }\n'''
    new_seek = '''    fun seekDvr(deltaMs: Long, quiet: Boolean = false) {\n        touch()\n        when {\n            Playback.simpleRaw -> if (!quiet) toast(context, "Rewind needs DVR Live.")\n            Playback.directLive -> if (!quiet) toast(context, "DVR is in Direct Rescue. Select DVR RETRY first.")\n            current?.isLive != true -> if (!quiet) toast(context, "DVR controls are only for Live TV.")\n            !Playback.seekDvrBy(deltaMs) -> if (!quiet)\n                toast(context, "DVR is still building — pause a moment, then try again.")\n        }\n    }\n\n    fun acceleratedTimelineSeek(dir: Int, repeatCount: Int) {\n        val step = timelineSeek.next(dir, repeatCount)\n        if (step.applyNow) seekDvr(step.deltaMs, quiet = repeatCount > 0)\n    }\n'''
    if old_seek not in section:
        raise SystemExit('mini: seek function missing')
    section = section.replace(old_seek, new_seek, 1)

    old_focus = '''                .focusProperties {\n                    up = playFocus\n                    if (showRecent && entries.isNotEmpty()) down = recentFocus\n                }'''
    new_focus = '''                .focusProperties {\n                    up = playFocus\n                    if (queue.size > 1) down = lineupFocus\n                    else if (showRecent && entries.isNotEmpty()) down = recentFocus\n                }'''
    if old_focus not in section:
        raise SystemExit('mini: timeline focus block missing')
    section = section.replace(old_focus, new_focus, 1)

    old_keys = '''                    when (ev.key) {\n                        Key.DirectionLeft -> { seekDvr(-DVR_REMOTE_SKIP_MS); true }\n                        Key.DirectionRight -> { seekDvr(DVR_REMOTE_SKIP_MS); true }\n                        Key.DirectionDown -> {\n                            if (entries.isNotEmpty()) { showRecent = true; touch(); true } else false\n                        }\n                        else -> false\n                    }'''
    new_keys = '''                    when (ev.key) {\n                        Key.DirectionLeft -> {\n                            acceleratedTimelineSeek(-1, ev.nativeKeyEvent.repeatCount)\n                            true\n                        }\n                        Key.DirectionRight -> {\n                            acceleratedTimelineSeek(+1, ev.nativeKeyEvent.repeatCount)\n                            true\n                        }\n                        Key.DirectionDown -> false\n                        else -> false\n                    }'''
    if old_keys not in section:
        raise SystemExit('mini: timeline key block missing')
    section = section.replace(old_keys, new_keys, 1)

    section = section.replace(
        '"DVR ${shortTime(available)} available • ${shortTime(behindMs)} behind LIVE • FF/RW remote = 10 sec"',
        '"DVR ${shortTime(available)} available • ${shortTime(behindMs)} behind LIVE • FF/RW repeat or hold = faster"',
        1
    )

    recent_marker = '''        if (showRecent) {\n'''
    lineup_ui = r'''        if (queue.size > 1) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Channels", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("↑/↓ browse • OK tunes • only 3 rows stay on screen", color = Muted, fontSize = 8.sp)
            }
            Spacer(Modifier.height(2.dp))
            LazyColumn(
                state = lineupState,
                modifier = Modifier.fillMaxWidth().height(66.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                itemsIndexed(queue, key = { _, ch -> ch.url }) { itemIndex, ch ->
                    val selectedNow = itemIndex == currentIdx
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(21.dp)
                            .then(if (selectedNow) Modifier.focusRequester(lineupFocus) else Modifier)
                            .onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft) {
                                    runCatching { timelineFocus.requestFocus() }
                                    true
                                } else false
                            }
                            .tvFocus(RoundedCornerShape(6.dp))
                            .background(
                                if (selectedNow) Accent.copy(alpha = 0.15f) else Color(0x33171922),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { touch(); onTune(ch) }
                            .padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (selectedNow) "NOW" else "${itemIndex + 1}",
                            color = if (selectedNow) Accent else Muted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(38.dp)
                        )
                        Text(
                            ch.name,
                            color = Ink,
                            fontSize = 9.sp,
                            fontWeight = if (selectedNow) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

'''
    if recent_marker not in section:
        raise SystemExit('mini: recent panel marker missing')
    section = section.replace(recent_marker, lineup_ui + recent_marker, 1)
    return section

edit_section('@Composable\nprivate fun MiniGuide(', '\n@Composable\nprivate fun MiniGuideControl(', patch_miniguide, 'X1 lineup + accelerated yellow bar')

PATH.write_text(text, encoding='utf-8')
print('Applied Zako v4.24 patches:')
for c in changes:
    print(' -', c)
