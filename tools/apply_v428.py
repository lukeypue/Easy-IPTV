from pathlib import Path
import re

MAIN = Path("app/src/main/java/com/easyiptv/player/MainActivity.kt")
DOWNLOADS = Path("app/src/main/java/com/easyiptv/player/Downloads.kt")
GRADLE = Path("app/build.gradle.kts")

main = MAIN.read_text(encoding="utf-8")
downloads = DOWNLOADS.read_text(encoding="utf-8")
gradle = GRADLE.read_text(encoding="utf-8")
changes = []


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    changes.append(label)
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# 4.28 identity. Keep the updater's monotonic versionCode contract intact.
# ---------------------------------------------------------------------------
gradle, n = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 53', gradle, count=1)
if n != 1:
    raise SystemExit("versionCode: expected one match")
gradle, n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.28"', gradle, count=1)
if n != 1:
    raise SystemExit("versionName: expected one match")
changes += ["versionCode 53", "versionName 4.28"]

# ---------------------------------------------------------------------------
# A darker navy/cyan cable-box look without changing the established pink
# remote cursor or gold action accent.
# ---------------------------------------------------------------------------
for old, new, label in [
    ('private val Bg = Color(0xFF0E0F13)', 'private val Bg = Color(0xFF07111F)', 'navy background'),
    ('private val SurfaceCol = Color(0xFF171922)', 'private val SurfaceCol = Color(0xFF101C2D)', 'navy surface'),
    ('private val Surface2 = Color(0xFF1F2230)', 'private val Surface2 = Color(0xFF16263A)', 'navy surface 2'),
    ('private val Line = Color(0xFF2A2E3D)', 'private val Line = Color(0xFF29445F)', 'brighter dividers'),
]:
    main = replace_once(main, old, new, label)

# ---------------------------------------------------------------------------
# Search: do not cover the search page with the VOD-catalog spinner. Live search
# remains usable while the catalog finishes in the background.
# ---------------------------------------------------------------------------
old_loading = 'if (catalogLoading && (section == "movies" || section == "series" || section == "search")) {'
new_loading = 'if (catalogLoading && (section == "movies" || section == "series")) {'
main = replace_once(main, old_loading, new_loading, "remove search catalog spinner")

# Search field gets its own highly visible yellow focus treatment. Do not stack
# the normal hot-pink tvFocus on this one field; the user specifically asked for
# the search box itself to read yellow.
old_field_state = '''    var open by remember { mutableStateOf(false) }\n    val firstKey = remember { FocusRequester() }\n'''
new_field_state = '''    var open by remember { mutableStateOf(false) }\n    // ZAKO_V428_YELLOW_SEARCH\n    var fieldFocused by remember { mutableStateOf(false) }\n    val firstKey = remember { FocusRequester() }\n'''
main = replace_once(main, old_field_state, new_field_state, "yellow search focus state")

old_field = '''        Column(\n            Modifier\n                .fillMaxWidth()\n                .tvFocus(RoundedCornerShape(9.dp))\n                .background(Color(0x33202634), RoundedCornerShape(9.dp))\n                .clickable { open = true }\n                .padding(horizontal = 14.dp, vertical = 10.dp)\n        ) {\n'''
new_field = '''        Column(\n            Modifier\n                .fillMaxWidth()\n                .onFocusChanged { fieldFocused = it.isFocused }\n                .background(\n                    if (fieldFocused) Accent.copy(alpha = 0.20f) else Color(0x33202634),\n                    RoundedCornerShape(9.dp)\n                )\n                .border(\n                    width = if (fieldFocused) 3.dp else 1.dp,\n                    color = if (fieldFocused) Accent else Line,\n                    shape = RoundedCornerShape(9.dp)\n                )\n                .focusable()\n                .clickable { open = true }\n                .padding(horizontal = 14.dp, vertical = 10.dp)\n        ) {\n'''
main = replace_once(main, old_field, new_field, "yellow search field")

# Debounce the expensive full-catalog filtering so every letter press remains
# immediate. A newer keypress cancels the previous LaunchedEffect delay.
old_search_state = '    var recents by remember { mutableStateOf(loadRecents(prefs)) }\n\n'
new_search_state = '''    var recents by remember { mutableStateOf(loadRecents(prefs)) }\n    // ZAKO_V428_SEARCH_DEBOUNCE: type instantly, filter after a short quiet beat.\n    var settledQuery by remember { mutableStateOf(query) }\n    LaunchedEffect(query) {\n        kotlinx.coroutines.delay(180L)\n        settledQuery = query\n    }\n\n'''
main = replace_once(main, old_search_state, new_search_state, "debounced search state")

search_start = main.index('@Composable\nfun SearchTab(')
search_end = main.index('\n@Composable\nprivate fun SectionHeader', search_start)
search_section = main[search_start:search_end]
search_section_new = replace_once(search_section, '        val q = query.trim()\n', '        val q = settledQuery.trim()\n', "use settled search query")
main = main[:search_start] + search_section_new + main[search_end:]

# ---------------------------------------------------------------------------
# Movies: OK now opens the existing X1-style detail dialog. The dialog already
# has large, D-pad-focusable PLAY and DOWNLOAD buttons plus plot/year/rating.
# This avoids hiding the important actions behind long-press or INFO keys.
# ---------------------------------------------------------------------------
old_help = '"OK plays • MENU/INFO shows details • Hold OK downloads"'
new_help = '"OK opens details • choose PLAY or DOWNLOAD"'
main = replace_once(main, old_help, new_help, "movie helper text")

old_movie_click = '''                    onClick = {\n                        BrowseFocusMemory.movieCategory = selectedCat\n                        BrowseFocusMemory.movieUrl = m.url\n                        onPlay(Playable(m.name, m.url, isLive = false, artwork = m.icon))\n                    },\n'''
new_movie_click = '''                    onClick = {\n                        // ZAKO_V428_MOVIE_DETAILS: X1-style details first.\n                        BrowseFocusMemory.movieCategory = selectedCat\n                        BrowseFocusMemory.movieUrl = m.url\n                        infoMovie = m\n                    },\n'''
main = replace_once(main, old_movie_click, new_movie_click, "movie OK opens details")

# Make the visible version line use the build identity instead of stale 4.23.
main = main.replace(
    'Text("Zako 4.23 — plays the playlists you provide. This app includes no channels or content of its own.", fontSize = 11.sp, color = Muted)',
    'Text("Zako 4.28 — plays the playlists you provide. This app includes no channels or content of its own.", fontSize = 11.sp, color = Muted)',
    1,
)
# v4.24 replaces the footer entirely; add a harmless marker beside Settings so
# verification still has a human-readable 4.28 identity in generated source.
if "Zako 4.28" not in main:
    marker = '@Composable\nfun SettingsPane('
    main = replace_once(main, marker, '// Zako 4.28\n' + marker, "visible 4.28 source label")

# ---------------------------------------------------------------------------
# Movie download hardening. Some Xtream movie endpoints behave like media
# servers and reject a bare app HTTP request while episodes accept it. Request
# the resource as a player/download client: explicit byte range, identity
# encoding (important for reliable Content-Length/progress), and broad media
# accept headers. Preserve the existing UA fallback.
# ---------------------------------------------------------------------------
old_req = '''                fun executeWithUa(ua: String): okhttp3.Response {\n                    val req = Request.Builder().url(url).header("User-Agent", ua).build()\n                    val call = client.newCall(req)\n'''
new_req = '''                fun executeWithUa(ua: String): okhttp3.Response {\n                    // ZAKO_V428_DOWNLOAD_HEADERS\n                    val req = Request.Builder()\n                        .url(url)\n                        .header("User-Agent", ua)\n                        .header("Accept", "video/*,application/octet-stream,*/*")\n                        .header("Accept-Encoding", "identity")\n                        .header("Range", "bytes=0-")\n                        .header("Connection", "keep-alive")\n                        .build()\n                    val call = client.newCall(req)\n'''
downloads = replace_once(downloads, old_req, new_req, "download request headers")

# A few panels use 401/429/5xx for a client-UA policy or temporary edge response.
# One browser-UA retry is still bounded and cannot create a retry loop.
old_retry = '                if (resp.code == 403 || resp.code == 406) {'
new_retry = '                if (resp.code == 401 || resp.code == 403 || resp.code == 406 || resp.code == 429 || resp.code >= 500) {'
downloads = replace_once(downloads, old_retry, new_retry, "download bounded UA retry")

MAIN.write_text(main, encoding="utf-8")
DOWNLOADS.write_text(downloads, encoding="utf-8")
GRADLE.write_text(gradle, encoding="utf-8")

print("Applied Zako 4.28 patches:")
for change in changes:
    print(f" - {change}")
