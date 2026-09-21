from pathlib import Path

MAIN = Path("app/src/main/java/com/easyiptv/player/MainActivity.kt")
DATA = Path("app/src/main/java/com/easyiptv/player/Data.kt")
main = MAIN.read_text(encoding="utf-8")
data = DATA.read_text(encoding="utf-8")
changes = []


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    changes.append(label)
    return text.replace(old, new, 1)


def edit_section(text: str, start_marker: str, end_marker: str, editor, label: str) -> str:
    start = text.index(start_marker)
    end = text.index(end_marker, start)
    section = text[start:end]
    updated = editor(section)
    if updated == section:
        raise SystemExit(f"{label}: section did not change")
    changes.append(label)
    return text[:start] + updated + text[end:]


# ---------------------------------------------------------------------------
# Data: lazy movie metadata. Never bulk-fetch details for the catalog.
# ---------------------------------------------------------------------------
media_model = '''data class MediaInfo(\n    val description: String = "",\n    val year: String = "",\n    val rating: String = "",\n    val genre: String = "",\n    val duration: String = ""\n)\n\n'''
data = replace_once(
    data,
    '''data class EpgEntry(\n    val title: String,\n    val desc: String,\n    val startMs: Long,\n    val endMs: Long\n)\n\n''',
    '''data class EpgEntry(\n    val title: String,\n    val desc: String,\n    val startMs: Long,\n    val endMs: Long\n)\n\n''' + media_model,
    "MediaInfo model",
)

data = replace_once(
    data,
    '''    suspend fun epg(channelId: String, limit: Int): List<EpgEntry>\n    suspend fun seriesEpisodes(seriesId: String): Map<Int, List<Episode>>\n''',
    '''    suspend fun epg(channelId: String, limit: Int): List<EpgEntry>\n    /** Lazy detail call. M3U/default sources simply have no separate metadata endpoint. */\n    suspend fun mediaInfo(movieId: String): MediaInfo? = null\n    suspend fun seriesEpisodes(seriesId: String): Map<Int, List<Episode>>\n''',
    "Source mediaInfo interface",
)

xtream_info = r'''    override suspend fun mediaInfo(movieId: String): MediaInfo? = withContext(Dispatchers.IO) {
        try {
            val id = URLEncoder.encode(movieId, "UTF-8")
            val root = JSONObject(Net.get(api("get_vod_info") + "&vod_id=$id"))
            val info = root.optJSONObject("info") ?: JSONObject()
            val movie = root.optJSONObject("movie_data") ?: JSONObject()

            fun pick(vararg keys: String): String {
                for (k in keys) {
                    val a = info.optString(k, "").trim()
                    if (a.isNotBlank() && a != "null") return a
                    val b = movie.optString(k, "").trim()
                    if (b.isNotBlank() && b != "null") return b
                }
                return ""
            }

            MediaInfo(
                description = pick("plot", "description", "overview"),
                year = pick("year", "releasedate", "releaseDate"),
                rating = pick("rating", "rating_5based", "tmdb_rating"),
                genre = pick("genre"),
                duration = pick("duration", "duration_secs")
            )
        } catch (_: Exception) {
            null
        }
    }

'''
data = replace_once(
    data,
    '''    override suspend fun seriesEpisodes(seriesId: String): Map<Int, List<Episode>> =\n''',
    xtream_info + '''    override suspend fun seriesEpisodes(seriesId: String): Map<Int, List<Episode>> =\n''',
    "Xtream lazy movie info",
)

# ---------------------------------------------------------------------------
# Main imports + palette + lifecycle memory trim.
# ---------------------------------------------------------------------------
main = replace_once(
    main,
    'import android.content.SharedPreferences\n',
    'import android.content.SharedPreferences\nimport android.content.res.Configuration\n',
    "touch configuration import",
)
main = replace_once(
    main,
    'private val Muted = Color(0xFF8A8F9A)\nprivate val Accent = Color(0xFFF5B944)\nprivate val Live = Color(0xFFFF3B5C)\n',
    'private val Muted = Color(0xFFC8CDD6)\nprivate val Accent = Color(0xFFF5B944)\nprivate val Live = Color(0xFFFF3B5C)\nprivate val ProgramCyan = Color(0xFF58D9FF)\nprivate val DownloadGreen = Color(0xFF35F06F)\n',
    "high contrast palette",
)
main = replace_once(
    main,
    '''    override fun onDestroy() {\n        Playback.releaseAll()\n        super.onDestroy()\n    }\n''',
    '''    override fun onTrimMemory(level: Int) {\n        super.onTrimMemory(level)\n        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {\n            // Posters can consume a meaningful chunk of RAM on Fire TV. They are\n            // disposable network/cache images, so drop only Coil's MEMORY cache\n            // when Android says pressure is building. Disk cache and playback stay.\n            runCatching { coil.Coil.imageLoader(this).memoryCache?.clear() }\n        }\n    }\n\n    override fun onDestroy() {\n        Playback.releaseAll()\n        super.onDestroy()\n    }\n''',
    "memory trim",
)

# ---------------------------------------------------------------------------
# v4.24's whole-content Left fallback swallowed grid Left after Compose moved
# focus. Remove it; individual panes own their boundary behavior instead.
# ---------------------------------------------------------------------------
broad_left = '''                Box(\n                    Modifier\n                        .weight(1f)\n                        .onKeyEvent { ev ->\n                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft) {\n                                runCatching { railFocus.requestFocus() }\n                                true\n                            } else false\n                        }\n                ) {\n'''
main = replace_once(
    main,
    broad_left,
    '''                Box(Modifier.weight(1f)) {\n                    // ZAKO_V425_FOCUS_FIX: panes own Left at their true boundary.\n''',
    "remove broad content Left fallback",
)

# Movies need the Source only for an on-demand Info click.
main = replace_once(
    main,
    'depth == 1 && section == "movies" -> MoviesPane(prefs, safeData, movieCat, onPlay, onLeftToRail = { runCatching { railFocus.requestFocus() } })',
    'depth == 1 && section == "movies" -> MoviesPane(source, prefs, safeData, movieCat, onPlay, onLeftToRail = { runCatching { railFocus.requestFocus() } })',
    "pass source to MoviesPane",
)

# ---------------------------------------------------------------------------
# Lazy movie Info dialog + Movies pane hook.
# ---------------------------------------------------------------------------
vod_dialog = r'''
@Composable
private fun VodInfoDialog(
    source: Source?,
    prefs: SharedPreferences,
    movie: Movie,
    onPlay: (Playable) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var info by remember(movie.id) { mutableStateOf<MediaInfo?>(null) }
    var loaded by remember(movie.id) { mutableStateOf(false) }

    LaunchedEffect(movie.id, source) {
        info = try { source?.mediaInfo(movie.id) } catch (_: Exception) { null }
        loaded = true
    }

    val meta = info
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = SurfaceCol,
        title = { Text(movie.name, color = Ink, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(
                    when {
                        !loaded -> "Loading info…"
                        meta?.description?.isNotBlank() == true -> meta.description
                        else -> "No description was supplied by this playlist/provider."
                    },
                    color = Ink, fontSize = 13.sp
                )
                if (loaded && meta != null) {
                    val facts = listOfNotNull(
                        meta.year.takeIf { it.isNotBlank() }?.let { "Year $it" },
                        meta.rating.takeIf { it.isNotBlank() }?.let { "Rating $it" },
                        meta.genre.takeIf { it.isNotBlank() },
                        meta.duration.takeIf { it.isNotBlank() }?.let { "Length $it" }
                    )
                    if (facts.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(facts.joinToString("  •  "), color = ProgramCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)),
                    onClick = {
                        toast(context, DownloadStore.start(context, prefs, movie.name, movie.url))
                    }
                ) {
                    Text("⬇ DOWNLOAD", color = DownloadGreen, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
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
    )
}

'''
main = replace_once(
    main,
    '/* ----------------------------- movies pane ----------------------------- */\n@Composable\nfun MoviesPane(\n',
    '/* ----------------------------- movies pane ----------------------------- */\n' + vod_dialog + '@Composable\nfun MoviesPane(\n',
    "insert movie info dialog",
)
main = replace_once(
    main,
    '''fun MoviesPane(\n    prefs: SharedPreferences,\n''',
    '''fun MoviesPane(\n    source: Source?,\n    prefs: SharedPreferences,\n''',
    "MoviesPane source parameter",
)


def patch_movies(section: str) -> str:
    section = replace_once(
        section,
        '    val context = LocalContext.current\n\n',
        '''    val context = LocalContext.current\n    var infoMovie by remember { mutableStateOf<Movie?>(null) }\n    infoMovie?.let { movie ->\n        VodInfoDialog(\n            source = source, prefs = prefs, movie = movie, onPlay = onPlay,\n            onClose = { infoMovie = null }\n        )\n    }\n\n''',
        "MoviesPane info state",
    )
    section = replace_once(
        section,
        '''                    onClick = {\n                        BrowseFocusMemory.movieCategory = selectedCat\n''',
        '''                    onInfo = { infoMovie = m },\n                    onClick = {\n                        BrowseFocusMemory.movieCategory = selectedCat\n''',
        "movie card Info action",
    )
    section = section.replace(
        '"OK plays • Hold OK to add a movie to Downloads",\n            color = Muted,\n            fontSize = 10.sp,',
        '"OK plays • Hold OK downloads • Tap ⓘ for Info",\n            color = Ink,\n            fontSize = 11.sp,',
        1,
    )
    return section


main = edit_section(
    main,
    '@Composable\nfun MoviesPane(',
    '\n/* ----------------------------- series pane ----------------------------- */',
    patch_movies,
    "MoviesPane info/readability",
)

# Series helper copy is also couch-readable.
def patch_series(section: str) -> str:
    return section.replace(
        '"OK opens a series • Hold OK on an episode to add it to Downloads",\n            color = Muted,\n            fontSize = 10.sp,',
        '"OK opens a series • Hold OK on an episode to download",\n            color = Ink,\n            fontSize = 11.sp,',
        1,
    )


main = edit_section(
    main,
    '@Composable\nfun SeriesPane(',
    '\n/* ----------------------------- settings pane ----------------------------- */',
    patch_series,
    "Series helper readability",
)

# Poster actions are touch-clickable but intentionally NOT separate D-pad focus
# targets. This is what preserves 1<->2<->3<->4<->5 horizontal grid movement.
def patch_poster(section: str) -> str:
    section = replace_once(
        section,
        '''    onClick: () -> Unit,\n    onDownload: (() -> Unit)? = null\n''',
        '''    onClick: () -> Unit,\n    onInfo: (() -> Unit)? = null,\n    onDownload: (() -> Unit)? = null\n''',
        "PosterGridCard onInfo signature",
    )
    section = replace_once(
        section,
        '''                        .background(Color(0xDD101217), CircleShape)\n                        .tvFocus(CircleShape)\n                        .clickable {\n''',
        '''                        .background(Color(0xDD101217), CircleShape)\n                        .focusProperties { canFocus = false }\n                        .clickable {\n''',
        "download overlay non-focusable",
    )
    section = replace_once(
        section,
        '''                        tint = Accent,\n                        modifier = Modifier.size(18.dp)\n''',
        '''                        tint = DownloadGreen,\n                        modifier = Modifier.size(18.dp)\n''',
        "poster download green",
    )
    section = replace_once(
        section,
        '''        Text(\n            name,\n            color = Ink,\n            fontSize = 12.sp,\n            fontWeight = FontWeight.SemiBold,\n            maxLines = 2,\n            overflow = TextOverflow.Ellipsis\n        )\n''',
        '''        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {\n            Text(\n                name,\n                color = Ink,\n                fontSize = 12.sp,\n                fontWeight = FontWeight.SemiBold,\n                maxLines = 2,\n                overflow = TextOverflow.Ellipsis,\n                modifier = Modifier.weight(1f)\n            )\n            if (onInfo != null) {\n                Text(\n                    "ⓘ",\n                    color = ProgramCyan, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,\n                    modifier = Modifier\n                        .focusProperties { canFocus = false }\n                        .clickable { onInfo() }\n                        .padding(start = 4.dp, end = 2.dp)\n                )\n            }\n        }\n''',
        "poster title info icon",
    )
    return section


main = edit_section(
    main,
    '@Composable\nprivate fun PosterGridCard(',
    '\n@Composable\nprivate fun PosterCard(',
    patch_poster,
    "PosterGridCard actions",
)

# Episode download arrow, too.
def patch_series_detail(section: str) -> str:
    old = 'Icon(Icons.Filled.Download, contentDescription = "Download for offline", tint = Muted)'
    if old in section:
        section = section.replace(old, 'Icon(Icons.Filled.Download, contentDescription = "Download for offline", tint = DownloadGreen)', 1)
    return section


main = edit_section(
    main,
    '@Composable\nfun SeriesDetailScreen(',
    '\n/* ----------------------------- downloads ----------------------------- */',
    patch_series_detail,
    "series download green",
)

# ---------------------------------------------------------------------------
# Live list: grid-guide entry point and top/bottom wrap. A coroutine scrolls the
# off-screen target into composition before requesting focus, so wrapping works
# even in very large categories.
# ---------------------------------------------------------------------------
def patch_live(section: str) -> str:
    section = replace_once(
        section,
        '    var expandedId by remember { mutableStateOf<String?>(null) }\n',
        '    var expandedId by remember { mutableStateOf<String?>(null) }\n    var showGridGuide by remember { mutableStateOf(false) }\n',
        "live grid-guide state",
    )
    section = replace_once(
        section,
        '''    val currentRowFocus = remember { FocusRequester() }\n    LaunchedEffect(selectedCat) {\n        if (currentIdxInList >= 0) {\n            kotlinx.coroutines.delay(120)\n            runCatching { listState.scrollToItem(currentIdxInList) }\n            kotlinx.coroutines.delay(120)\n            runCatching { currentRowFocus.requestFocus() }\n        }\n    }\n''',
        '''    // ZAKO_V425_LIVE_WRAP: one requester per row lets first/last wrap reliably.\n    val rowFocusers = remember(selectedCat, filtered.size) {\n        List(filtered.size.coerceAtLeast(1)) { FocusRequester() }\n    }\n    val liveNavScope = rememberCoroutineScope()\n    LaunchedEffect(selectedCat, currentIdxInList, filtered.size) {\n        if (currentIdxInList >= 0 && currentIdxInList < rowFocusers.size) {\n            kotlinx.coroutines.delay(120)\n            runCatching { listState.scrollToItem(currentIdxInList) }\n            kotlinx.coroutines.delay(80)\n            runCatching { rowFocusers[currentIdxInList].requestFocus() }\n        }\n    }\n''',
        "live row focusers",
    )
    section = replace_once(
        section,
        '''    Column(Modifier.fillMaxSize()) {\n        if (guideLoading) {\n''',
        '''    Column(Modifier.fillMaxSize()) {\n        Row(\n            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),\n            verticalAlignment = Alignment.CenterVertically,\n            horizontalArrangement = Arrangement.spacedBy(8.dp)\n        ) {\n            Chip(if (showGridGuide) "CHANNEL LIST" else "GRID GUIDE", showGridGuide) {\n                showGridGuide = !showGridGuide\n            }\n            Text(\n                if (showGridGuide) "2-hour window • arrows move like a normal TV guide" else "Guide data stays lightweight on Fire TV",\n                color = Ink, fontSize = 10.sp\n            )\n        }\n        if (guideLoading) {\n''',
        "grid guide toggle",
    )
    section = replace_once(
        section,
        '''        if (filtered.isEmpty()) {\n''',
        '''        if (showGridGuide && filtered.isNotEmpty()) {\n            LiveGridGuide(\n                prefs = prefs, channels = filtered,\n                onPlayLive = onPlayLive,\n                onClose = { showGridGuide = false }\n            )\n        } else if (filtered.isEmpty()) {\n''',
        "grid guide body",
    )
    old_focus = '''                            .then(\n                                if (chIdx == currentIdxInList) Modifier.focusRequester(currentRowFocus)\n                                else Modifier\n                            )\n                            .onPreviewKeyEvent { ev ->\n                                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft) {\n                                    onLeftToRail(); true\n                                } else false\n                            }\n'''
    new_focus = '''                            .focusRequester(rowFocusers[chIdx])\n                            .onPreviewKeyEvent { ev ->\n                                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false\n                                when {\n                                    ev.key == Key.DirectionLeft -> { onLeftToRail(); true }\n                                    ev.key == Key.DirectionUp && chIdx == 0 && filtered.size > 1 -> {\n                                        liveNavScope.launch {\n                                            val target = filtered.lastIndex\n                                            listState.scrollToItem(target)\n                                            kotlinx.coroutines.delay(40)\n                                            runCatching { rowFocusers[target].requestFocus() }\n                                        }\n                                        true\n                                    }\n                                    ev.key == Key.DirectionDown && chIdx == filtered.lastIndex && filtered.size > 1 -> {\n                                        liveNavScope.launch {\n                                            listState.scrollToItem(0)\n                                            kotlinx.coroutines.delay(40)\n                                            runCatching { rowFocusers[0].requestFocus() }\n                                        }\n                                        true\n                                    }\n                                    else -> false\n                                }\n                            }\n'''
    section = replace_once(section, old_focus, new_focus, "live wrap key handling")
    # Existing now-show line uses gold; make it explicit program cyan.
    section = section.replace(
        'color = Accent, fontSize = 12.sp,',
        'color = ProgramCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,',
        1,
    )
    return section


main = edit_section(
    main,
    '@Composable\nfun LivePane(',
    '\n/* The timeshift DVR records the classic (.ts) live stream',
    patch_live,
    "LivePane wrap/grid/readability",
)

live_grid = r'''

@Composable
private fun LiveGridGuide(
    prefs: SharedPreferences,
    channels: List<LiveChannel>,
    onPlayLive: (List<Playable>, Int) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val fmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    var page by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Pair<LiveChannel, EpgEntry>?>(null) }
    BackHandler { onClose() }

    val now = System.currentTimeMillis()
    val halfHour = 30L * 60L * 1000L
    val base = now - (now % halfHour)
    val windowStart = base + page * 4L * halfHour

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CHANNEL", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(142.dp))
            repeat(4) { slot ->
                Text(
                    fmt.format(Date(windowStart + slot * halfHour)),
                    color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(14.dp)), onClick = { page = (page - 1).coerceAtLeast(-1) }) {
                Text("◀ EARLIER", color = Ink, fontSize = 10.sp)
            }
            TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(14.dp)), onClick = { page = 0 }) {
                Text("NOW", color = ProgramCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(14.dp)), onClick = { page = (page + 1).coerceAtMost(23) }) {
                Text("LATER ▶", color = Ink, fontSize = 10.sp)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(channels, key = { _, ch -> ch.id }) { chIndex, ch ->
                val schedule = EpgStore.guide(ch.epgId, ch.name)
                Row(
                    Modifier.fillMaxWidth().height(58.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier.width(142.dp).fillMaxHeight().background(SurfaceCol, RoundedCornerShape(8.dp)).padding(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelIcon(ch.name, ch.icon, 30.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(ch.name, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    repeat(4) { slot ->
                        val slotStart = windowStart + slot * halfHour
                        val slotEnd = slotStart + halfHour
                        val entry = schedule.firstOrNull { slotStart in it.startMs until it.endMs }
                            ?: schedule.firstOrNull { it.startMs in slotStart until slotEnd }
                        val airing = entry != null && now in entry.startMs until entry.endMs
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(start = 3.dp)
                                .tvFocus(RoundedCornerShape(8.dp))
                                .background(
                                    if (airing) ProgramCyan.copy(alpha = 0.20f) else Surface2,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = entry != null) {
                                    if (entry != null) selected = ch to entry
                                }
                                .padding(horizontal = 6.dp, vertical = 5.dp)
                        ) {
                            if (entry == null) {
                                Text("—", color = Muted, fontSize = 10.sp)
                            } else {
                                Column {
                                    Text(
                                        entry.title,
                                        color = if (airing) ProgramCyan else Ink,
                                        fontSize = 10.sp,
                                        fontWeight = if (airing) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${fmt.format(Date(entry.startMs))}–${fmt.format(Date(entry.endMs))}",
                                        color = Ink, fontSize = 8.sp, maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { pair ->
        val ch = pair.first
        val entry = pair.second
        val airing = System.currentTimeMillis() in entry.startMs until entry.endMs
        val recordable = ch.url.endsWith(".ts")
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = SurfaceCol,
            title = { Text(entry.title, color = Ink, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    Text(ch.name, color = ProgramCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${fmt.format(Date(entry.startMs))}–${fmt.format(Date(entry.endMs))}",
                        color = Ink, fontSize = 11.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        entry.desc.ifBlank { "No description was supplied in the lightweight guide." },
                        color = Ink, fontSize = 12.sp
                    )
                    if (recordable) {
                        Spacer(Modifier.height(10.dp))
                        TextButton(
                            modifier = Modifier.tvFocus(RoundedCornerShape(16.dp)),
                            onClick = {
                                if (airing) {
                                    if (prefs.getBoolean("simple_mode", true)) {
                                        toast(context, "Recording needs DVR Live. Switch off Smooth Live first.")
                                    } else {
                                        Recorder.start(context, ch.url, "${entry.title} (${ch.name})", entry.endMs + 2 * 60 * 1000)
                                        toast(context, "Recording ${entry.title}.")
                                        selected = null
                                    }
                                } else {
                                    toast(context, ScheduleStore.add(context, prefs, entry.title, ch.name, ch.url, entry.startMs, entry.endMs))
                                    selected = null
                                }
                            }
                        ) {
                            Text(if (airing) "● RECORD NOW" else "● SCHEDULE RECORDING", color = Live, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                if (airing) {
                    TextButton(
                        modifier = Modifier.tvFocus(RoundedCornerShape(16.dp)),
                        onClick = {
                            val queue = channels.map { livePlayable(prefs, it) }
                            selected = null
                            onPlayLive(queue, chIndexOf(channels, ch))
                        }
                    ) { Text("▶ WATCH", color = ProgramCyan, fontWeight = FontWeight.Bold) }
                }
            },
            dismissButton = {
                TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(16.dp)), onClick = { selected = null }) {
                    Text("CLOSE", color = Ink)
                }
            }
        )
    }
}

private fun chIndexOf(channels: List<LiveChannel>, target: LiveChannel): Int =
    channels.indexOfFirst { it.id == target.id && it.url == target.url }.coerceAtLeast(0)
'''
main = replace_once(
    main,
    '\n/* The timeshift DVR records the classic (.ts) live stream',
    live_grid + '\n\n/* The timeshift DVR records the classic (.ts) live stream',
    "insert lightweight LiveGridGuide",
)

# ---------------------------------------------------------------------------
# Mini Guide: preserve the three visible rows, but each row now includes the
# current show and time. This reads only rows Compose is currently composing.
# ---------------------------------------------------------------------------
def patch_mini(section: str) -> str:
    section = section.replace('modifier = Modifier.fillMaxWidth().height(66.dp)', 'modifier = Modifier.fillMaxWidth().height(98.dp)', 1)
    section = section.replace('.height(21.dp)', '.height(31.dp)', 1)
    section = replace_once(
        section,
        '''                    val selectedNow = itemIndex == currentIdx\n                    Row(\n''',
        '''                    val selectedNow = itemIndex == currentIdx\n                    // ZAKO_V425_MINI_EPG: lookup only this composed row.\n                    val rowProgram = remember(ch.guideKey, ch.name, EpgStore.loaded.value) {\n                        val t = System.currentTimeMillis()\n                        EpgStore.guide(ch.guideKey, ch.name).firstOrNull { t in it.startMs until it.endMs }\n                    }\n                    Row(\n''',
        "mini row EPG lookup",
    )
    section = section.replace(
        'if (selectedNow) Accent.copy(alpha = 0.15f) else Color(0x33171922)',
        'if (selectedNow) ProgramCyan.copy(alpha = 0.22f) else Color(0x33171922)',
        1,
    )
    old_channel = '''                        Text(\n                            ch.name,\n                            color = Ink,\n                            fontSize = 9.sp,\n                            fontWeight = if (selectedNow) FontWeight.Bold else FontWeight.Medium,\n                            maxLines = 1,\n                            overflow = TextOverflow.Ellipsis\n                        )\n'''
    new_channel = '''                        Column(Modifier.weight(1f)) {\n                            Text(\n                                ch.name,\n                                color = Ink, fontSize = 9.sp,\n                                fontWeight = if (selectedNow) FontWeight.ExtraBold else FontWeight.SemiBold,\n                                maxLines = 1, overflow = TextOverflow.Ellipsis\n                            )\n                            if (rowProgram != null) {\n                                Text(\n                                    rowProgram.title,\n                                    color = ProgramCyan, fontSize = 8.sp, fontWeight = FontWeight.SemiBold,\n                                    maxLines = 1, overflow = TextOverflow.Ellipsis\n                                )\n                            }\n                        }\n                        if (rowProgram != null) {\n                            Text(\n                                "${fmt.format(Date(rowProgram.startMs))}–${fmt.format(Date(rowProgram.endMs))}",\n                                color = Ink, fontSize = 8.sp, fontWeight = FontWeight.SemiBold,\n                                modifier = Modifier.padding(start = 6.dp)\n                            )\n                        }\n'''
    section = replace_once(section, old_channel, new_channel, "mini row program UI")
    section = section.replace(
        'Text("↑/↓ browse • OK tunes • only 3 rows stay on screen", color = Muted, fontSize = 8.sp)',
        'Text("↑/↓ browse • OK tunes • 3 rows stay on screen", color = Ink, fontSize = 9.sp)',
        1,
    )
    section = section.replace(
        'color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis',
        'color = ProgramCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis',
        1,
    )
    section = section.replace(
        'Text("OK tunes • Back closes", color = Muted, fontSize = 8.sp)',
        'Text("OK tunes • Back closes", color = Ink, fontSize = 9.sp)',
        1,
    )
    section = section.replace(
        'Text(recentNow ?: "Press OK to watch", color = if (recentNow != null) Muted else Accent, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)',
        'Text(recentNow ?: "Press OK to watch", color = if (recentNow != null) ProgramCyan else Ink, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)',
        1,
    )
    # Timeline labels are currently tiny gray. There are exactly two in this section.
    section = section.replace('color = Muted, fontSize = 8.sp\n                    )', 'color = Ink, fontSize = 9.sp\n                    )', 1)
    section = section.replace('else "LIVE", color = Muted, fontSize = 8.sp)', 'else "LIVE", color = Ink, fontSize = 9.sp)', 1)
    return section


main = edit_section(
    main,
    '@Composable\nprivate fun MiniGuide(',
    '\n@Composable\nprivate fun MiniGuideControl(',
    patch_mini,
    "Mini Guide EPG/readability",
)

# ---------------------------------------------------------------------------
# Android touch live DVR: one overlay, same Playback/Timeshift/Recorder engine.
# Fire TV has TOUCHSCREEN_NOTOUCH, so its remote path is unchanged.
# ---------------------------------------------------------------------------
def patch_player(section: str) -> str:
    section = replace_once(
        section,
        '    var miniGuideOpen by remember { mutableStateOf(false) }\n',
        '''    var miniGuideOpen by remember { mutableStateOf(false) }\n    // ZAKO_V425_TOUCH_DVR: phones/tablets expose the SAME live DVR engine by touch.\n    val hasTouchDvr = remember {\n        context.resources.configuration.touchscreen != Configuration.TOUCHSCREEN_NOTOUCH\n    }\n    var touchDvrOpen by remember { mutableStateOf(false) }\n    var touchDvrTick by remember { mutableLongStateOf(0L) }\n    LaunchedEffect(touchDvrOpen, touchDvrTick) {\n        if (touchDvrOpen) {\n            val seen = touchDvrTick\n            kotlinx.coroutines.delay(6_000)\n            if (touchDvrTick == seen) touchDvrOpen = false\n        }\n    }\n''',
        "touch DVR state",
    )
    marker = '''        // Cable-box clock (Settings › Clock while watching).\n        // ---- X1-style mini guide overlay (live only) ----\n'''
    touch_ui = r'''        if (current.isLive && hasTouchDvr && !miniGuideOpen) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(current.url) {
                        detectTapGestures {
                            touchDvrOpen = !touchDvrOpen
                            touchDvrTick = System.currentTimeMillis()
                        }
                    }
            )
        }
        if (current.isLive && hasTouchDvr && touchDvrOpen && !miniGuideOpen) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .background(Color(0xE6171922), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniGuideControl("⏪ REW") {
                    touchDvrTick = System.currentTimeMillis()
                    if (!Playback.seekDvrBy(-30_000L)) toast(context, "Rewind needs DVR Live and a little recorded history.")
                }
                MiniGuideControl(if (exo.isPlaying) "❚❚ PAUSE" else "▶ PLAY") {
                    touchDvrTick = System.currentTimeMillis()
                    if (Playback.simpleRaw) toast(context, "Pause needs DVR Live.")
                    else exo.playWhenReady = !exo.playWhenReady
                }
                MiniGuideControl("FF ⏩") {
                    touchDvrTick = System.currentTimeMillis()
                    if (!Playback.seekDvrBy(30_000L)) toast(context, "Fast forward needs DVR Live.")
                }
                MiniGuideControl("LIVE", activeColor = ProgramCyan) {
                    touchDvrTick = System.currentTimeMillis()
                    if (!Playback.seekDvrBy(DVR_HISTORY_MS)) toast(context, "Already live, or DVR Live is not active.")
                }
                MiniGuideControl("● REC", enabled = current.canRecord, activeColor = Live) {
                    touchDvrTick = System.currentTimeMillis()
                    if (!current.canRecord) toast(context, "Recording is not available for this channel.")
                    else if (Playback.simpleRaw) toast(context, "Recording needs DVR Live.")
                    else showRecordChoice = true
                }
            }
        }

'''
    section = replace_once(section, marker, touch_ui + marker, "touch DVR controls")
    return section


main = edit_section(
    main,
    '@OptIn(UnstableApi::class)\n@Composable\nfun PlayerScreen(',
    '\n/* ---------------------------------------------------------------------------\n * X1-STYLE MINI GUIDE',
    patch_player,
    "Android touch DVR",
)

# ---------------------------------------------------------------------------
# Recording screen hardening: scanning/sorting the directory on the Compose UI
# thread every two seconds is unnecessary under recording load.
# ---------------------------------------------------------------------------
def patch_recordings(section: str) -> str:
    section = replace_once(
        section,
        '''    var files by remember {\n        mutableStateOf(\n            Recorder.recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()\n        )\n    }\n''',
        '''    var files by remember { mutableStateOf<List<File>>(emptyList()) }\n    suspend fun scanRecordings(): List<File> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {\n        Recorder.recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()\n    }\n''',
        "recordings async initial state",
    )
    section = replace_once(
        section,
        '''    LaunchedEffect(activeRecording) {\n        while (Recorder.activeName.value != null) {\n            kotlinx.coroutines.delay(2_000)\n            files = Recorder.recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()\n        }\n    }\n''',
        '''    LaunchedEffect(activeRecording) {\n        // scanRecordings runs on IO; Compose receives only the tiny final file list.\n        files = scanRecordings()\n        while (Recorder.activeName.value != null) {\n            kotlinx.coroutines.delay(5_000)\n            files = scanRecordings()\n        }\n    }\n''',
        "recordings IO refresh",
    )
    section = section.replace(
        '''                    Recorder.stop(context)\n                    files = Recorder.recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()\n''',
        '''                    Recorder.stop(context)\n''',
        1,
    )
    section = section.replace(
        '''                                f.delete()\n                                files = Recorder.recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()\n''',
        '''                                runCatching { f.delete() }\n                                files = files.filterNot { it.absolutePath == f.absolutePath }\n''',
        1,
    )
    return section


main = edit_section(
    main,
    '@Composable\nfun RecordingsPane(',
    '\n/* ----------------------------- playlists ----------------------------- */',
    patch_recordings,
    "recording screen IO hardening",
)

# Do not render a second SurfaceView preview while the foreground service is
# already recording under load. Playback/recording continues; only the preview
# view is skipped.
main = replace_once(
    main,
    'if (mini != null && Playback.player != null) {',
    'if (mini != null && Playback.player != null && Recorder.activeName.value == null) {',
    "suppress preview SurfaceView while recording",
)

# Rail categories should read white, not gray.
def patch_rail_item(section: str) -> str:
    return section.replace(
        'color = if (active) Ink else Muted,',
        'color = Ink,',
        1,
    )


main = edit_section(
    main,
    '@Composable\nprivate fun RailItem(',
    '\n@Composable\nprivate fun LoadingBox(',
    patch_rail_item,
    "bright category rail",
)

MAIN.write_text(main, encoding="utf-8")
DATA.write_text(data, encoding="utf-8")

print("Applied Zako v4.25 patches:")
for change in changes:
    print(f" - {change}")
