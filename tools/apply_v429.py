from pathlib import Path
import re

MAIN = Path("app/src/main/java/com/easyiptv/player/MainActivity.kt")
DATA = Path("app/src/main/java/com/easyiptv/player/Data.kt")
GRADLE = Path("app/build.gradle.kts")

main = MAIN.read_text(encoding="utf-8")
data = DATA.read_text(encoding="utf-8")
gradle = GRADLE.read_text(encoding="utf-8")
changes = []


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    changes.append(label)
    return text.replace(old, new, 1)

# ---------------------------------------------------------------------------
# 4.29 identity
# ---------------------------------------------------------------------------
gradle, n = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 54', gradle, count=1)
if n != 1: raise SystemExit("versionCode marker missing")
gradle, n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.29"', gradle, count=1)
if n != 1: raise SystemExit("versionName marker missing")
changes += ["versionCode 54", "versionName 4.29"]

# ---------------------------------------------------------------------------
# Search metadata carried in the existing catalog payload. Many Xtream panels
# already include cast/director/genre/plot on list rows; keeping those strings
# costs almost nothing and avoids thousands of per-title metadata requests.
# ---------------------------------------------------------------------------
data = replace_once(
    data,
    '''data class Movie(\n    val id: String,\n    val name: String,\n    val icon: String?,\n    val categoryId: String?,\n    val url: String\n)\n''',
    '''data class Movie(\n    val id: String,\n    val name: String,\n    val icon: String?,\n    val categoryId: String?,\n    val url: String,\n    val searchMeta: String = ""\n)\n''',
    "Movie search metadata",
)
data = replace_once(
    data,
    '''data class SeriesItem(\n    val id: String,\n    val name: String,\n    val icon: String?,\n    val categoryId: String?\n)\n''',
    '''data class SeriesItem(\n    val id: String,\n    val name: String,\n    val icon: String?,\n    val categoryId: String?,\n    val searchMeta: String = ""\n)\n''',
    "Series search metadata",
)

# Persist the metadata in the lightweight startup cache.
data = replace_once(
    data,
    '''                    JSONObject().put("i", m.id).put("n", m.name).put("ic", m.icon ?: "")\n                        .put("c", m.categoryId ?: "").put("u", m.url)\n''',
    '''                    JSONObject().put("i", m.id).put("n", m.name).put("ic", m.icon ?: "")\n                        .put("c", m.categoryId ?: "").put("u", m.url).put("sm", m.searchMeta)\n''',
    "cache movie metadata",
)
data = replace_once(
    data,
    '''                    JSONObject().put("i", s.id).put("n", s.name).put("ic", s.icon ?: "")\n                        .put("c", s.categoryId ?: "")\n''',
    '''                    JSONObject().put("i", s.id).put("n", s.name).put("ic", s.icon ?: "")\n                        .put("c", s.categoryId ?: "").put("sm", s.searchMeta)\n''',
    "cache series metadata",
)
data = replace_once(
    data,
    '''                        categoryId = o.optString("c").ifBlank { null },\n                        url = o.optString("u")\n''',
    '''                        categoryId = o.optString("c").ifBlank { null },\n                        url = o.optString("u"),\n                        searchMeta = o.optString("sm", "")\n''',
    "restore movie metadata",
)
data = replace_once(
    data,
    '''                        icon = o.optString("ic").ifBlank { null },\n                        categoryId = o.optString("c").ifBlank { null }\n                    )\n''',
    '''                        icon = o.optString("ic").ifBlank { null },\n                        categoryId = o.optString("c").ifBlank { null },\n                        searchMeta = o.optString("sm", "")\n                    )\n''',
    "restore series metadata",
)

# Capture any provider-supplied people/genre fields from catalog rows.
movie_old = '''                    icon = o.optString("stream_icon", o.optString("movie_image", "")).ifBlank { null },\n                    categoryId = o.opt("category_id")?.toString(),\n                    url = "$base/movie/$user/$pass/$id.$ext"\n'''
movie_new = '''                    icon = o.optString("stream_icon", o.optString("movie_image", "")).ifBlank { null },\n                    categoryId = o.opt("category_id")?.toString(),\n                    url = "$base/movie/$user/$pass/$id.$ext",\n                    searchMeta = listOf(\n                        o.optString("cast", ""), o.optString("director", ""),\n                        o.optString("genre", ""), o.optString("plot", ""),\n                        o.optString("releaseDate", o.optString("releasedate", ""))\n                    ).filter { it.isNotBlank() }.joinToString(" • ")\n'''
data = replace_once(data, movie_old, movie_new, "Xtream movie people metadata")

series_old = '''                    icon = o.optString("cover", o.optString("stream_icon", "")).ifBlank { null },\n                    categoryId = o.opt("category_id")?.toString()\n'''
series_new = '''                    icon = o.optString("cover", o.optString("stream_icon", "")).ifBlank { null },\n                    categoryId = o.opt("category_id")?.toString(),\n                    searchMeta = listOf(\n                        o.optString("cast", ""), o.optString("director", ""),\n                        o.optString("genre", ""), o.optString("plot", ""),\n                        o.optString("releaseDate", o.optString("releasedate", ""))\n                    ).filter { it.isNotBlank() }.joinToString(" • ")\n'''
data = replace_once(data, series_old, series_new, "Xtream series people metadata")

# ---------------------------------------------------------------------------
# Premium visual shell: deeper cinematic gradient, brighter information cyan,
# subtle panel hierarchy, and stronger TV focus without heavy blur/shader work.
# ---------------------------------------------------------------------------
main = replace_once(
    main,
    'import androidx.compose.ui.graphics.Color\n',
    'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.Brush\n',
    "Brush import",
)
main = replace_once(
    main,
    '''private val ProgramCyan = Color(0xFF58D9FF)\nprivate val DownloadGreen = Color(0xFF35F06F)\n''',
    '''private val ProgramCyan = Color(0xFF58D9FF)\nprivate val ElectricCyan = Color(0xFF49E8FF)\nprivate val DeepBlue = Color(0xFF061424)\nprivate val PanelGlow = Color(0xFF1C3853)\nprivate val DownloadGreen = Color(0xFF35F06F)\n''',
    "premium palette",
)
main = replace_once(
    main,
    '''                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {\n                    App()\n                }\n''',
    '''                Box(\n                    modifier = Modifier\n                        .fillMaxSize()\n                        .background(\n                            Brush.verticalGradient(\n                                listOf(Color(0xFF0A1D33), DeepBlue, Bg)\n                            )\n                        )\n                ) {\n                    App()\n                }\n''',
    "cinematic app gradient",
)

# Make TV focus feel alive, still lightweight: tiny scale + cyan inner glow + pink edge.
old_focus = '''        this\n            .onFocusChanged { focused = it.isFocused }\n            .background(\n                color = if (focused) FocusPink.copy(alpha = 0.18f) else Color.Transparent,\n                shape = shape\n            )\n            .border(\n                width = if (focused) 3.dp else 0.dp,\n                color = if (focused) FocusPink else Color.Transparent,\n                shape = shape\n            )\n'''
new_focus = '''        this\n            .onFocusChanged { focused = it.isFocused }\n            .graphicsLayer {\n                scaleX = if (focused) 1.018f else 1f\n                scaleY = if (focused) 1.018f else 1f\n            }\n            .background(\n                color = if (focused) ElectricCyan.copy(alpha = 0.10f) else Color.Transparent,\n                shape = shape\n            )\n            .border(\n                width = if (focused) 3.dp else 0.dp,\n                color = if (focused) FocusPink else Color.Transparent,\n                shape = shape\n            )\n'''
main = replace_once(main, old_focus, new_focus, "premium TV focus")

# Stronger cable-box header / identity.
main = main.replace(
    'Text("Zako", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink)',
    'Text("ZAKO", fontWeight = FontWeight.Black, fontSize = 19.sp, color = ElectricCyan)',
    1,
)
main = main.replace(
    'Box(Modifier.size(7.dp).background(Accent, CircleShape))',
    'Box(Modifier.width(22.dp).height(3.dp).background(Accent, RoundedCornerShape(3.dp)))',
    1,
)

# Rail gets more depth and clearer active state.
main = main.replace(
    '.background(if (active) Surface2 else SurfaceCol)',
    '.background(if (active) PanelGlow else SurfaceCol.copy(alpha = 0.78f))',
    1,
)
main = main.replace(
    '.background(if (active) Accent else Color.Transparent)',
    '.background(if (active) ElectricCyan else Color.Transparent)',
    1,
)

# Section titles read like a premium guide instead of plain labels.
main = replace_once(
    main,
    '''        fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Accent,\n        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)\n''',
    '''        fontWeight = FontWeight.Black, fontSize = 15.sp, color = ElectricCyan,\n        letterSpacing = 0.5.sp,\n        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)\n''',
    "premium section headers",
)

# ---------------------------------------------------------------------------
# Search: permanent gold/yellow search target, with an even brighter focus.
# ---------------------------------------------------------------------------
old_field_colors = '''                .background(\n                    if (fieldFocused) Accent.copy(alpha = 0.20f) else Color(0x33202634),\n                    RoundedCornerShape(9.dp)\n                )\n                .border(\n                    width = if (fieldFocused) 3.dp else 1.dp,\n                    color = if (fieldFocused) Accent else Line,\n                    shape = RoundedCornerShape(9.dp)\n                )\n'''
new_field_colors = '''                .background(\n                    if (fieldFocused) Accent.copy(alpha = 0.28f) else Accent.copy(alpha = 0.12f),\n                    RoundedCornerShape(10.dp)\n                )\n                .border(\n                    width = if (fieldFocused) 4.dp else 2.dp,\n                    color = Accent,\n                    shape = RoundedCornerShape(10.dp)\n                )\n'''
main = replace_once(main, old_field_colors, new_field_colors, "always-yellow search box")
main = main.replace(
    'value.ifEmpty { "Press OK to type with the Zako TV keyboard" }',
    'value.ifEmpty { "SEARCH movies, shows, actors, directors & live TV" }',
    1,
)
main = main.replace(
    '"Matches any part of a name — \\"wars\\" finds Star Wars."',
    '"Search titles, actors, directors, genres and live TV — all from one box."',
    1,
)

# Actor/director/genre lookup uses only locally cached catalog text, so no keystroke
# network calls and no new Fire TV lag.
main = replace_once(
    main,
    '        val movieHits = data.movies.filter { it.name.contains(q, ignoreCase = true) }.take(30)\n        val seriesHits = data.series.filter { it.name.contains(q, ignoreCase = true) }.take(30)\n',
    '''        // ZAKO_V429_UNIVERSAL_SEARCH: title + provider-supplied cast/director/genre/plot.\n        val movieHits = data.movies.filter {\n            it.name.contains(q, ignoreCase = true) || it.searchMeta.contains(q, ignoreCase = true)\n        }.take(30)\n        val seriesHits = data.series.filter {\n            it.name.contains(q, ignoreCase = true) || it.searchMeta.contains(q, ignoreCase = true)\n        }.take(30)\n''',
    "universal local metadata search",
)

# Fix the actual Search movie path: visible Download button on both Android touch
# and Fire TV remote. This does not rely on hidden long-press behavior.
old_movie_results = '''            if (movieHits.isNotEmpty()) {\n                item { SectionHeader("Movies") }\n                item {\n                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {\n                        items(movieHits) { m ->\n                            PosterCard(m.name, m.icon) {\n                                saveRecent(q)\n                                onPlay(Playable(m.name, m.url, isLive = false, artwork = m.icon))\n                            }\n                        }\n                    }\n                }\n            }\n'''
new_movie_results = '''            if (movieHits.isNotEmpty()) {\n                item { SectionHeader("Movies • title / cast / director / genre") }\n                items(movieHits) { m ->\n                    MediaRow(\n                        name = m.name,\n                        icon = m.icon,\n                        onClick = {\n                            saveRecent(q)\n                            onPlay(Playable(m.name, m.url, isLive = false, artwork = m.icon))\n                        },\n                        onLongAction = {\n                            saveRecent(q)\n                            toast(context, DownloadStore.start(context, prefs, m.name, m.url))\n                        },\n                        trailing = { fm ->\n                            IconButton(\n                                modifier = fm.then(Modifier.tvFocus(RoundedCornerShape(24.dp))),\n                                onClick = {\n                                    saveRecent(q)\n                                    toast(context, DownloadStore.start(context, prefs, m.name, m.url))\n                                }\n                            ) {\n                                Icon(Icons.Filled.Download, contentDescription = "Download movie", tint = DownloadGreen)\n                            }\n                        }\n                    )\n                }\n            }\n'''
main = replace_once(main, old_movie_results, new_movie_results, "search movie download action")

# Series heading tells the viewer people-search can match here too.
main = main.replace(
    'item { SectionHeader("Series") }',
    'item { SectionHeader("Series • title / cast / director / genre") }',
    1,
)

# Version marker in generated source.
main = main.replace('Zako 4.28', 'Zako 4.29')

MAIN.write_text(main, encoding="utf-8")
DATA.write_text(data, encoding="utf-8")
GRADLE.write_text(gradle, encoding="utf-8")

print("Applied Zako 4.29 patches:")
for c in changes:
    print(" -", c)
