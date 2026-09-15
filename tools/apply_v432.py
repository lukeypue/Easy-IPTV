from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
DATA = Path('app/src/main/java/com/easyiptv/player/Data.kt')
GRADLE = Path('app/build.gradle.kts')
main = MAIN.read_text(encoding='utf-8')
data = DATA.read_text(encoding='utf-8')
gradle = GRADLE.read_text(encoding='utf-8')
changes = []

def once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 match, found {n}')
    changes.append(label)
    return text.replace(old, new, 1)

def replace_section(text, start, end, body, label):
    a = text.index(start)
    b = text.index(end, a)
    changes.append(label)
    return text[:a] + body + text[b:]

# ---------------------------------------------------------------------------
# Identity
# ---------------------------------------------------------------------------
gradle, n = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 57', gradle, count=1)
if n != 1: raise SystemExit('versionCode missing')
gradle, n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.32"', gradle, count=1)
if n != 1: raise SystemExit('versionName missing')
changes += ['versionCode 57', 'versionName 4.32']

# ---------------------------------------------------------------------------
# Catalog: stream giant Xtream arrays one row at a time.
# The old body.string() + JSONArray path temporarily held the raw multi-MB
# response, JSONArray object graph and final Kotlin list at the same time.
# ---------------------------------------------------------------------------
data = once(
    data,
    'import android.util.Base64\n',
    'import android.util.Base64\nimport android.util.JsonReader\nimport android.util.JsonToken\n',
    'JsonReader imports'
)

net_anchor = '''    fun get(url: String): String {\n        val req = Request.Builder().url(url).header("User-Agent", UA).build()\n        client.newCall(req).execute().use { resp ->\n            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")\n            return resp.body?.string() ?: throw RuntimeException("Empty response")\n        }\n    }\n'''
net_new = net_anchor + '''\n    // ZAKO_V432_STREAMING_CATALOG: bounded-memory JSON access for huge catalogs.\n    fun <T> withJsonReader(url: String, block: (JsonReader) -> T): T {\n        val req = Request.Builder().url(url).header("User-Agent", UA).build()\n        client.newCall(req).execute().use { resp ->\n            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")\n            val body = resp.body ?: throw RuntimeException("Empty response")\n            body.charStream().use { chars ->\n                val reader = JsonReader(chars)\n                reader.isLenient = true\n                return block(reader)\n            }\n        }\n    }\n'''
data = once(data, net_anchor, net_new, 'streaming Net reader')

stream_parser = r'''    /**
     * Stream one Xtream catalog object at a time. Supports the direct array used
     * by normal panels and the common {data/results/streams/movies/vod/series}
     * wrappers without materializing the whole document.
     */
    private fun streamRows(action: String, consume: (Map<String, String>) -> Unit) {
        var last: Throwable? = null
        repeat(2) { attempt ->
            try {
                Net.withJsonReader(api(action)) { reader ->
                    val wrappers = when (action) {
                        "get_vod_streams" -> setOf("data", "results", "streams", "movies", "vod")
                        "get_series" -> setOf("data", "results", "series")
                        else -> setOf("data", "results")
                    }

                    fun readArray() {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                                reader.skipValue()
                                continue
                            }
                            val row = HashMap<String, String>(16)
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val key = reader.nextName()
                                val value: String? = when (reader.peek()) {
                                    JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                                    JsonToken.BOOLEAN -> reader.nextBoolean().toString()
                                    JsonToken.NULL -> { reader.nextNull(); null }
                                    else -> { reader.skipValue(); null }
                                }
                                if (value != null) row[key] = value
                            }
                            reader.endObject()
                            consume(row)
                        }
                        reader.endArray()
                    }

                    when (reader.peek()) {
                        JsonToken.BEGIN_ARRAY -> readArray()
                        JsonToken.BEGIN_OBJECT -> {
                            var found = false
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val key = reader.nextName()
                                if (!found && key in wrappers && reader.peek() == JsonToken.BEGIN_ARRAY) {
                                    readArray()
                                    found = true
                                } else {
                                    reader.skipValue()
                                }
                            }
                            reader.endObject()
                            if (!found) throw RuntimeException("$action returned an unexpected response")
                        }
                        else -> throw RuntimeException("$action returned an unexpected response")
                    }
                }
                return
            } catch (t: Throwable) {
                last = t
                if (attempt == 0) Thread.sleep(350)
            }
        }
        throw RuntimeException(last?.message ?: "$action failed")
    }

'''
start = data.index('    /** One retry, sequentially.')
end = data.index('    override suspend fun loadLiveOnly(): AppData', start)
data = data[:start] + stream_parser + data[end:]
changes.append('replace giant-array retry parser')

new_vod = r'''    override suspend fun loadOnDemandOnly(): AppData = withContext(Dispatchers.IO) {
        // ZAKO_V432_STREAMING_CATALOG: Movies and Series remain sequential, but
        // each network response is now decoded one object at a time. At no point
        // do we keep raw response String + JSONArray + final list together.
        var movieErr: Throwable? = null
        var seriesErr: Throwable? = null

        val movies = ArrayList<Movie>()
        try {
            streamRows("get_vod_streams") { o ->
                val id = o["stream_id"]?.takeIf { it.isNotBlank() } ?: return@streamRows
                val ext = o["container_extension"]?.ifBlank { "mp4" } ?: "mp4"
                val searchMeta = listOf(
                    o["cast"].orEmpty(), o["director"].orEmpty(), o["genre"].orEmpty(),
                    o["plot"].orEmpty(), o["releaseDate"] ?: o["releasedate"].orEmpty()
                ).filter { it.isNotBlank() }.joinToString(" • ")
                movies.add(
                    Movie(
                        id = id,
                        name = o["name"]?.ifBlank { "Movie" } ?: "Movie",
                        icon = (o["stream_icon"] ?: o["movie_image"]).orEmpty().ifBlank { null },
                        categoryId = o["category_id"],
                        url = "$base/movie/$user/$pass/$id.$ext",
                        searchMeta = searchMeta
                    )
                )
            }
        } catch (t: Throwable) {
            movieErr = t
        }

        val series = ArrayList<SeriesItem>()
        try {
            streamRows("get_series") { o ->
                val id = (o["series_id"] ?: o["id"])?.takeIf { it.isNotBlank() } ?: return@streamRows
                val searchMeta = listOf(
                    o["cast"].orEmpty(), o["director"].orEmpty(), o["genre"].orEmpty(),
                    o["plot"].orEmpty(), o["releaseDate"] ?: o["releasedate"].orEmpty()
                ).filter { it.isNotBlank() }.joinToString(" • ")
                series.add(
                    SeriesItem(
                        id = id,
                        name = o["name"]?.ifBlank { "Series" } ?: "Series",
                        icon = (o["cover"] ?: o["stream_icon"]).orEmpty().ifBlank { null },
                        categoryId = o["category_id"],
                        searchMeta = searchMeta
                    )
                )
            }
        } catch (t: Throwable) {
            seriesErr = t
        }

        val vodCats = runCatching { parseCats(Net.get(api("get_vod_categories"))) }.getOrDefault(emptyList())
        val seriesCats = runCatching { parseCats(Net.get(api("get_series_categories"))) }.getOrDefault(emptyList())

        if (movies.isEmpty() && series.isEmpty() && (movieErr != null || seriesErr != null)) {
            val parts = listOfNotNull(
                movieErr?.message?.let { "Movies: $it" },
                seriesErr?.message?.let { "Series: $it" }
            )
            throw RuntimeException(parts.joinToString("  •  ").ifBlank { "On-demand catalog request failed" })
        }

        AppData(
            liveCats = emptyList(), live = emptyList(),
            vodCats = vodCats, movies = movies,
            seriesCats = seriesCats, series = series
        )
    }
'''
start = data.index('    override suspend fun loadOnDemandOnly(): AppData')
end = data.index('\n    override suspend fun loadAll(): AppData', start)
data = data[:start] + new_vod + data[end:]
changes.append('stream Movies and Series rows')

# ---------------------------------------------------------------------------
# Startup/channel tune: give first-screen/live work priority and avoid rebuilding
# an entire category Playable list for every single click/zap.
# ---------------------------------------------------------------------------
startup_old = '''    LaunchedEffect(Unit) {\n        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {\n            DownloadStore.migrateLegacyRetention(prefs)\n            DownloadStore.migrateLegacyEngine(context, prefs)\n            DownloadStore.cleanup(context, prefs)\n            DownloadStore.kickQueue(context, prefs)\n            ScheduleStore.cleanup(prefs)\n        }\n    }\n'''
startup_new = '''    LaunchedEffect(Unit) {\n        // ZAKO_V432_STARTUP_POLISH: first paint + Live lineup win the startup race.\n        // Nonessential disk cleanup/queue maintenance starts after the UI settles.\n        kotlinx.coroutines.delay(2_200L)\n        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {\n            DownloadStore.migrateLegacyRetention(prefs)\n            DownloadStore.migrateLegacyEngine(context, prefs)\n            DownloadStore.cleanup(context, prefs)\n            DownloadStore.kickQueue(context, prefs)\n            ScheduleStore.cleanup(prefs)\n        }\n    }\n'''
main = once(main, startup_old, startup_new, 'defer startup housekeeping')

live_start = main.index('@Composable\nfun LivePane(')
live_end = main.index('\n/* The timeshift DVR records the classic (.ts) live stream', live_start)
live = main[live_start:live_end]
queue_anchor = '''    // Come back to Live TV and the list is scrolled right where you left it.\n'''
queue_insert = '''    // ZAKO_V432_LIVE_QUEUE: map this category once, not on every channel click.\n    val playableQueue = remember(filtered, activeIdx) { filtered.map { livePlayable(prefs, it) } }\n\n'''
if queue_anchor not in live: raise SystemExit('Live queue anchor missing')
live = live.replace(queue_anchor, queue_insert + queue_anchor, 1)
old_click = '''                                val q = filtered.map { livePlayable(prefs, it) }\n                                onPlayLive(q, filtered.indexOfFirst { it.id == ch.id }.coerceAtLeast(0))\n'''
new_click = '''                                onPlayLive(playableQueue, chIdx.coerceIn(0, (playableQueue.size - 1).coerceAtLeast(0)))\n'''
if old_click not in live: raise SystemExit('Live click queue marker missing')
live = live.replace(old_click, new_click, 1)
main = main[:live_start] + live + main[live_end:]
changes.append('remember Live playable queue')

# ---------------------------------------------------------------------------
# One Amazon/Fire-TV-style keyboard layout everywhere, including fresh setup.
# Search already owns the good remote keyboard; share its row geometry and make
# generic TvTextField use the same in-app keyboard instead of the device IME.
# ---------------------------------------------------------------------------
search_key_marker = '@Composable\nprivate fun SearchKey('
shared_rows = '''// ZAKO_V432_UNIFIED_KEYBOARD: one familiar Fire-TV-style layout on TV and phone.\nprivate val ZakoKeyboardRows = listOf("1234567890", "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")\n\n'''
if search_key_marker not in main: raise SystemExit('SearchKey marker missing')
main = main.replace(search_key_marker, shared_rows + search_key_marker, 1)
main = main.replace('                val rows = listOf("1234567890", "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")', '                val rows = ZakoKeyboardRows', 1)

new_tv_field = r'''@Composable
private fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    // ZAKO_V432_UNIFIED_KEYBOARD: never invoke a different phone/TV system IME.
    var editing by remember { mutableStateOf(false) }
    var upper by remember { mutableStateOf(false) }
    val firstKey = remember { FocusRequester() }
    BackHandler(enabled = editing) { editing = false }
    LaunchedEffect(editing) {
        if (editing) {
            kotlinx.coroutines.delay(80)
            runCatching { firstKey.requestFocus() }
        }
    }

    Column(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .tvFocus(RoundedCornerShape(12.dp))
                .background(Accent.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                .border(2.dp, Accent.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                .clickable { editing = true }
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            Text(label, color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                when {
                    value.isEmpty() -> placeholder.ifEmpty { "Press OK to type" }
                    password -> "•".repeat(value.length.coerceAtMost(16))
                    else -> value
                },
                color = if (value.isEmpty()) Muted else Ink,
                fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }

        if (editing) {
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xF20A2038), RoundedCornerShape(18.dp))
                    .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ZakoKeyboardRows.forEachIndexed { rowIndex, chars ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        chars.forEachIndexed { colIndex, raw ->
                            val shown = raw.toString()
                            SearchKey(
                                label = shown,
                                modifier = if (rowIndex == 0 && colIndex == 0) Modifier.focusRequester(firstKey) else Modifier
                            ) {
                                val c = if (raw.isLetter()) {
                                    if (upper) raw.uppercaseChar() else raw.lowercaseChar()
                                } else raw
                                onValueChange(value + c)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SearchKey("SHIFT") { upper = !upper }
                    SearchKey("@") { onValueChange(value + "@") }
                    SearchKey(".") { onValueChange(value + ".") }
                    SearchKey(":") { onValueChange(value + ":") }
                    SearchKey("/") { onValueChange(value + "/") }
                    SearchKey("-") { onValueChange(value + "-") }
                    SearchKey("_") { onValueChange(value + "_") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SearchKey("SPACE", weight = 2f) { onValueChange(value + " ") }
                    SearchKey("⌫") { if (value.isNotEmpty()) onValueChange(value.dropLast(1)) }
                    SearchKey("CLEAR") { onValueChange("") }
                    SearchKey("DONE", weight = 1.4f) { editing = false }
                }
                Text("Same layout on Fire TV and Android • D-pad/tap moves • OK types", color = Muted, fontSize = 9.sp)
            }
        }
    }
}

'''
field_start = main.index('@Composable\nprivate fun TvTextField(')
field_end = main.index('@Composable\nprivate fun ChannelIcon(', field_start)
main = main[:field_start] + new_tv_field + main[field_end:]
changes.append('unify setup/login keyboard')

# ---------------------------------------------------------------------------
# Brighter bubble design + older-viewer mini-guide readability.
# ---------------------------------------------------------------------------
palette_anchor = 'private val NeonGreen = Color(0xFF39FF88)\n'
main = once(main, palette_anchor, palette_anchor + 'private val LimeBubble = Color(0xFFB8FF3D)\n', 'lime bubble palette')

rail_start = main.index('@Composable\nprivate fun RailItem(')
rail_end = main.index('@Composable\nprivate fun LoadingBox(', rail_start)
new_rail = r'''@Composable
private fun RailItem(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // ZAKO_V432_LIME_BUBBLES: lightweight rounded category bubbles, no blur/shaders.
    Row(
        modifier = modifier
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .fillMaxWidth()
            .tvFocus(RoundedCornerShape(24.dp))
            .background(
                if (active) LimeBubble.copy(alpha = 0.22f) else PanelGlow.copy(alpha = 0.60f),
                RoundedCornerShape(24.dp)
            )
            .border(
                if (active) 2.dp else 1.dp,
                if (active) LimeBubble else ElectricCyan.copy(alpha = 0.22f),
                RoundedCornerShape(24.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(if (active) 8.dp else 6.dp)
                .background(if (active) LimeBubble else ElectricCyan.copy(alpha = 0.65f), CircleShape)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            color = if (active) LimeBubble else Ink,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 6.dp)
        )
    }
}

'''
main = main[:rail_start] + new_rail + main[rail_end:]
changes.append('bubble category rail')

mini_start = main.index('@Composable\nprivate fun MiniGuide(')
mini_end = main.index('\n@Composable\nprivate fun MiniGuideControl(', mini_start)
mini = main[mini_start:mini_end]
mini = mini.replace('{\n', '{\n    // ZAKO_V432_MINI_ACCESSIBILITY: larger three-row guide for across-room reading.\n', 1)
mini = mini.replace('modifier = Modifier.fillMaxWidth().height(98.dp)', 'modifier = Modifier.fillMaxWidth().height(120.dp)', 1)
mini = mini.replace('.height(31.dp)', '.height(38.dp)', 1)
mini = mini.replace('Text("↑/↓ browse • OK tunes • 3 rows stay on screen", color = Ink, fontSize = 9.sp)\n', '', 1)
# Larger text for the three channel/program rows.
mini = mini.replace('color = Ink, fontSize = 9.sp,\n                                fontWeight = if (selectedNow)', 'color = Ink, fontSize = 12.sp,\n                                fontWeight = if (selectedNow)', 1)
mini = mini.replace('color = Color(0xFFFFE45C), fontSize = 8.sp, fontWeight = FontWeight.SemiBold,', 'color = Color(0xFFFFE45C), fontSize = 11.sp, fontWeight = FontWeight.Bold, // ZAKO_V432_MINI_YELLOW', 1)
mini = mini.replace('color = Ink, fontSize = 8.sp, fontWeight = FontWeight.SemiBold,\n                                modifier = Modifier.padding(start = 6.dp)', 'color = LimeBubble, fontSize = 10.sp, fontWeight = FontWeight.Bold,\n                                modifier = Modifier.padding(start = 8.dp)', 1)
mini = mini.replace('if (selectedNow) ProgramCyan.copy(alpha = 0.22f) else Color(0x33171922)', 'if (selectedNow) ElectricCyan.copy(alpha = 0.22f) else Color(0xCC0A2642)', 1)
main = main[:mini_start] + mini + main[mini_end:]
changes.append('mini guide larger three-row readability')

# Give the mini-guide control bubbles the same modern blue/lime family.
control_start = main.index('@Composable\nprivate fun MiniGuideControl(')
# Limit styling replacement to this small composable when possible.
control_end_candidates = [i for i in [main.find('\n@Composable', control_start + 20), main.find('\nprivate fun', control_start + 20), main.find('\n/*', control_start + 20)] if i > control_start]
if control_end_candidates:
    control_end = min(control_end_candidates)
    ctrl = main[control_start:control_end]
    ctrl = ctrl.replace('RoundedCornerShape(8.dp)', 'RoundedCornerShape(18.dp)')
    ctrl = ctrl.replace('Surface2', 'PanelGlow')
    main = main[:control_start] + ctrl + main[control_end:]
    changes.append('modern mini guide control bubbles')

main = main.replace('Zako 4.31', 'Zako 4.32')

MAIN.write_text(main, encoding='utf-8')
DATA.write_text(data, encoding='utf-8')
GRADLE.write_text(gradle, encoding='utf-8')
print('Applied Zako 4.32:')
for c in changes:
    print(' -', c)
