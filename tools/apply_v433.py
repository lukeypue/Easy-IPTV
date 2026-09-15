from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
DATA = Path('app/src/main/java/com/easyiptv/player/Data.kt')
GRADLE = Path('app/build.gradle.kts')
main = MAIN.read_text()
data = DATA.read_text()
gradle = GRADLE.read_text()
changes=[]

def once(text, old, new, label):
    n=text.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 match, found {n}')
    changes.append(label)
    return text.replace(old,new,1)

# identity
gradle, n = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 58', gradle, count=1)
if n != 1: raise SystemExit('versionCode missing')
gradle, n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.33"', gradle, count=1)
if n != 1: raise SystemExit('versionName missing')
changes += ['version 4.33']

# streamRows: clear caller-owned destination before each retry so partial rows never duplicate.
data = once(data,
'''    private fun streamRows(action: String, consume: (Map<String, String>) -> Unit) {\n        var last: Throwable? = null\n        repeat(2) { attempt ->\n            try {\n''',
'''    private fun streamRows(\n        action: String,\n        resetBeforeAttempt: () -> Unit = {},\n        consume: (Map<String, String>) -> Unit\n    ) {\n        // ZAKO_V433_RETRY_RESET: a failed mid-stream response may have emitted rows.\n        // Clear the destination before retrying so the second attempt cannot duplicate them.\n        var last: Throwable? = null\n        repeat(2) { attempt ->\n            resetBeforeAttempt()\n            try {\n''', 'retry reset')

data = once(data,
'''            streamRows("get_vod_streams") { o ->\n''',
'''            streamRows("get_vod_streams", resetBeforeAttempt = { movies.clear() }) { o ->\n''', 'movie retry reset')
data = once(data,
'''            streamRows("get_series") { o ->\n''',
'''            streamRows("get_series", resetBeforeAttempt = { series.clear() }) { o ->\n''', 'series retry reset')

old_live = '''    override suspend fun loadLiveOnly(): AppData = withContext(Dispatchers.IO) {\n        coroutineScope {\n            val liveCatsD = async {\n                runCatching { parseCats(Net.get(api("get_live_categories"))) }.getOrDefault(emptyList())\n            }\n            val liveD = async {\n                val arr = JSONArray(Net.get(api("get_live_streams")))\n                (0 until arr.length()).map { i ->\n                    val o = arr.getJSONObject(i)\n                    val id = o.opt("stream_id")?.toString() ?: ""\n                    LiveChannel(\n                        id = id,\n                        name = o.optString("name", "Channel"),\n                        icon = o.optString("stream_icon", "").ifBlank { null },\n                        categoryId = o.opt("category_id")?.toString(),\n                        url = "$base/live/$user/$pass/$id.ts",\n                        epgId = o.optString("epg_channel_id", "").ifBlank { null }\n                    )\n                }\n            }\n            AppData(\n                liveCats = liveCatsD.await(),\n                live = liveD.await(),\n                vodCats = emptyList(),\n                movies = emptyList(),\n                seriesCats = emptyList(),\n                series = emptyList()\n            )\n        }\n    }\n'''
new_live = '''    override suspend fun loadLiveOnly(): AppData = withContext(Dispatchers.IO) {\n        coroutineScope {\n            val liveCatsD = async {\n                runCatching { parseCats(Net.get(api("get_live_categories"))) }.getOrDefault(emptyList())\n            }\n            val liveD = async {\n                // ZAKO_V433_STREAMING_LIVE: Live now uses the same bounded-memory parser\n                // as VOD/Series. LinkedHashMap preserves provider order and removes duplicate IDs.\n                val byId = LinkedHashMap<String, LiveChannel>()\n                streamRows("get_live_streams", resetBeforeAttempt = { byId.clear() }) { o ->\n                    val id = o["stream_id"]?.takeIf { it.isNotBlank() } ?: return@streamRows\n                    byId[id] = LiveChannel(\n                        id = id,\n                        name = o["name"]?.ifBlank { "Channel" } ?: "Channel",\n                        icon = o["stream_icon"].orEmpty().ifBlank { null },\n                        categoryId = o["category_id"],\n                        url = "$base/live/$user/$pass/$id.ts",\n                        epgId = o["epg_channel_id"].orEmpty().ifBlank { null }\n                    )\n                }\n                byId.values.toList()\n            }\n            AppData(\n                liveCats = liveCatsD.await(),\n                live = liveD.await(),\n                vodCats = emptyList(),\n                movies = emptyList(),\n                seriesCats = emptyList(),\n                series = emptyList()\n            )\n        }\n    }\n'''
data = once(data, old_live, new_live, 'stream live rows')

# Remember expensive category filters and visible slices.
main = once(main,
'''    val filtered = data.live.filter { c ->\n        when (selectedCat) {\n            "all" -> true\n            "fav" -> favs.contains(c.id)\n            else -> c.categoryId == selectedCat\n        }\n    }\n''',
'''    // ZAKO_V433_REMEMBER_FILTERS: category lists only rebuild when their inputs change.\n    val filtered = remember(data.live, selectedCat, favs) {\n        data.live.filter { c ->\n            when (selectedCat) {\n                "all" -> true\n                "fav" -> favs.contains(c.id)\n                else -> c.categoryId == selectedCat\n            }\n        }\n    }\n''', 'remember live filter')
main = once(main,
'''    val filtered = data.movies.filter { selectedCat == "all" || it.categoryId == selectedCat }\n''',
'''    // ZAKO_V433_REMEMBER_FILTERS\n    val filtered = remember(data.movies, selectedCat) {\n        data.movies.filter { selectedCat == "all" || it.categoryId == selectedCat }\n    }\n''', 'remember movies filter')
main = once(main,
'''    val visibleMovies = filtered.take(visibleMovieCount)\n''',
'''    val visibleMovies = remember(filtered, visibleMovieCount) { filtered.take(visibleMovieCount) }\n''', 'remember movie slice')
main = once(main,
'''    val filtered = data.series.filter { selectedCat == "all" || it.categoryId == selectedCat }\n''',
'''    // ZAKO_V433_REMEMBER_FILTERS\n    val filtered = remember(data.series, selectedCat) {\n        data.series.filter { selectedCat == "all" || it.categoryId == selectedCat }\n    }\n''', 'remember series filter')
main = once(main,
'''    val visibleSeries = filtered.take(visibleSeriesCount)\n''',
'''    val visibleSeries = remember(filtered, visibleSeriesCount) { filtered.take(visibleSeriesCount) }\n''', 'remember series slice')

# Search is already debounced 180ms; cache the expensive full-catalog scans so focus/clock recomposition does not rerun them.
old_search = '''        val liveHits = data.live.filter { it.name.contains(q, ignoreCase = true) }.take(30)\n        // ZAKO_V429_UNIVERSAL_SEARCH: title + provider-supplied cast/director/genre/plot.\n        val movieHits = data.movies.filter {\n            it.name.contains(q, ignoreCase = true) || it.searchMeta.contains(q, ignoreCase = true)\n        }.take(30)\n        val seriesHits = data.series.filter {\n            it.name.contains(q, ignoreCase = true) || it.searchMeta.contains(q, ignoreCase = true)\n        }.take(30)\n'''
new_search = '''        // ZAKO_V433_SEARCH_CACHE: settledQuery is already debounced; scan each catalog once\n        // per settled query instead of again on every focus/recomposition event.\n        val qLower = remember(q) { q.lowercase() }\n        val liveHits = remember(qLower, data.live) {\n            data.live.asSequence().filter { it.name.lowercase().contains(qLower) }.take(30).toList()\n        }\n        // ZAKO_V429_UNIVERSAL_SEARCH: title + provider-supplied cast/director/genre/plot.\n        val movieHits = remember(qLower, data.movies) {\n            data.movies.asSequence().filter {\n                it.name.lowercase().contains(qLower) || it.searchMeta.lowercase().contains(qLower)\n            }.take(30).toList()\n        }\n        val seriesHits = remember(qLower, data.series) {\n            data.series.asSequence().filter {\n                it.name.lowercase().contains(qLower) || it.searchMeta.lowercase().contains(qLower)\n            }.take(30).toList()\n        }\n'''
main = once(main, old_search, new_search, 'remember search hits')

# Bound Media3's ceiling on low-memory Fire TV while preserving the proven lock-in/rebuffer values.
old_buffer = '''        val steadyRecovery = prefs.getBoolean("live_steady_recovery", false)\n        val loadControl = DefaultLoadControl.Builder()\n            .setBufferDurationsMs(\n                (bufferSec * 1000).coerceAtMost(60_000),\n                (bufferSec * 1000 * 3).coerceIn(60_000, 90_000),\n                lockMs,                                    // collect the chosen cushion before starting\n                if (steadyRecovery) (lockMs * 2).coerceAtLeast(6_000) else lockMs\n            )\n            .setBackBuffer(10_000, false)\n            // Let Media3 size the byte target from the track (panel: the fixed\n            // 24MB cap could stop loading before enough SECONDS were banked,\n            // which showed up as the buffer % filling very slowly). Time is the\n            // controlling constraint, exactly like the smooth 4.9 build.\n            .setTargetBufferBytes(C.LENGTH_UNSET)\n            .setPrioritizeTimeOverSizeThresholds(true)\n            .build()\n'''
new_buffer = '''        val steadyRecovery = prefs.getBoolean("live_steady_recovery", false)\n        // ZAKO_V433_BUFFER_CAP: keep the proven startup/rebuffer cushion, but stop\n        // VOD or DVR-behind-live from reserving a 60-90s unbounded sample buffer.\n        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager\n        val lowRam = am?.isLowRamDevice == true || (am?.memoryClass ?: 512) <= 256\n        val maxBufferMs = (bufferSec * 1000 * 3).coerceIn(30_000, 60_000)\n        val targetBufferBytes = if (lowRam) 32 * 1024 * 1024 else C.LENGTH_UNSET\n        val loadControl = DefaultLoadControl.Builder()\n            .setBufferDurationsMs(\n                (bufferSec * 1000).coerceAtMost(60_000),\n                maxBufferMs,\n                lockMs,                                    // collect the chosen cushion before starting\n                if (steadyRecovery) (lockMs * 2).coerceAtLeast(6_000) else lockMs\n            )\n            .setBackBuffer(10_000, false)\n            .setTargetBufferBytes(targetBufferBytes)\n            .setPrioritizeTimeOverSizeThresholds(true)\n            .build()\n'''
main = once(main, old_buffer, new_buffer, 'buffer ceiling')

# Episode details dialog: episode row opens readable information first, matching Movies.
series_marker = '/* ----------------------------- series detail ----------------------------- */\n'
episode_dialog = r'''/* ZAKO_V433_EPISODE_DETAILS: readable episode information before playback. */
@Composable
private fun EpisodeInfoDialog(
    seriesName: String,
    season: Int,
    episode: Episode,
    prefs: SharedPreferences,
    onPlay: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val epName = "$seriesName S${season}E${episode.episodeNum}"
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = SurfaceCol,
        title = {
            Column {
                Text(
                    "S${season} • E${episode.episodeNum}",
                    color = Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    episode.title,
                    color = Color(0xFFFFE45C),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                Text(seriesName, color = ProgramCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    episode.plot.ifBlank { "No episode description was supplied by this playlist/provider." },
                    color = Ink,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
                Spacer(Modifier.height(14.dp))
                TextButton(
                    modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)),
                    onClick = { toast(context, DownloadStore.start(context, prefs, epName, episode.url)) }
                ) {
                    Text("⬇ DOWNLOAD", color = DownloadGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
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
    )
}

'''
if series_marker not in main: raise SystemExit('series marker missing')
main = main.replace(series_marker, series_marker + episode_dialog, 1)
changes.append('episode info dialog')

main = once(main,
'''    var watchTick by remember { mutableIntStateOf(0) }\n    BackHandler { onBack() }\n''',
'''    var watchTick by remember { mutableIntStateOf(0) }\n    var infoEpisode by remember { mutableStateOf<Pair<Int, Episode>?>(null) }\n    infoEpisode?.let { (season, ep) ->\n        EpisodeInfoDialog(\n            seriesName = s.name,\n            season = season,\n            episode = ep,\n            prefs = prefs,\n            onPlay = {\n                val idx = queue.indexOfFirst { it.url == ep.url }.coerceAtLeast(0)\n                onPlayQueue(queue, idx)\n            },\n            onClose = { infoEpisode = null }\n        )\n    }\n    BackHandler { if (infoEpisode != null) infoEpisode = null else onBack() }\n''', 'episode dialog state')

# The inserted state references queue, which is declared below in 4.32. Move the dialog rendering after queue creation.
# First remove the premature rendering block while keeping state/back handling.
premature = '''    var infoEpisode by remember { mutableStateOf<Pair<Int, Episode>?>(null) }\n    infoEpisode?.let { (season, ep) ->\n        EpisodeInfoDialog(\n            seriesName = s.name,\n            season = season,\n            episode = ep,\n            prefs = prefs,\n            onPlay = {\n                val idx = queue.indexOfFirst { it.url == ep.url }.coerceAtLeast(0)\n                onPlayQueue(queue, idx)\n            },\n            onClose = { infoEpisode = null }\n        )\n    }\n    BackHandler { if (infoEpisode != null) infoEpisode = null else onBack() }\n'''
replacement = '''    var infoEpisode by remember { mutableStateOf<Pair<Int, Episode>?>(null) }\n    BackHandler { if (infoEpisode != null) infoEpisode = null else onBack() }\n'''
main = once(main, premature, replacement, 'defer episode dialog render')

queue_end = '''        } ?: emptyList()\n    }\n\n    Column(Modifier.fillMaxSize()) {\n'''
queue_new = '''        } ?: emptyList()\n    }\n\n    infoEpisode?.let { (season, ep) ->\n        EpisodeInfoDialog(\n            seriesName = s.name, season = season, episode = ep, prefs = prefs,\n            onPlay = {\n                val idx = queue.indexOfFirst { it.url == ep.url }.coerceAtLeast(0)\n                onPlayQueue(queue, idx)\n            },\n            onClose = { infoEpisode = null }\n        )\n    }\n\n    Column(Modifier.fillMaxSize()) {\n'''
# Limit replacement to SeriesDetailScreen by finding after its queue comment
series_start = main.index('fun SeriesDetailScreen(')
pos = main.index(queue_end, series_start)
main = main[:pos] + main[pos:].replace(queue_end, queue_new, 1)
changes.append('render episode info after queue')

old_media = '''                        MediaRow(\n                            name = if (ep.plot.isBlank()) label else "$label  •  ${ep.plot}",\n                            icon = null,\n                            onClick = {\n                                val idx = queue.indexOfFirst { it.url == ep.url }.coerceAtLeast(0)\n                                onPlayQueue(queue, idx)\n                            },\n'''
new_media = '''                        MediaRow(\n                            name = label,\n                            icon = null,\n                            onClick = { infoEpisode = season to ep },\n'''
main = once(main, old_media, new_media, 'episode click opens details')

# visible version label if present
main = main.replace('Zako 4.32', 'Zako 4.33')

MAIN.write_text(main)
DATA.write_text(data)
GRADLE.write_text(gradle)
print('Applied v4.33:', ', '.join(changes))