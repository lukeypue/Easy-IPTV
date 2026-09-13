from pathlib import Path
import re

GRADLE = Path('app/build.gradle.kts')
MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
RING = Path('app/src/main/java/com/easyiptv/player/TimeshiftRing.kt')
STORAGE = Path('app/src/main/java/com/easyiptv/player/LiveStorageManager.kt')
RING_TEMPLATE = Path('tools/v440/TimeshiftRing.kt.template')
STORAGE_TEMPLATE = Path('tools/v440/LiveStorageManager.kt.template')

gradle = GRADLE.read_text(encoding='utf-8')
gradle, n = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 65', gradle, count=1)
if n != 1:
    raise SystemExit('versionCode bump failed')
gradle, n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.40"', gradle, count=1)
if n != 1:
    raise SystemExit('versionName bump failed')
GRADLE.write_text(gradle, encoding='utf-8')

RING.write_text(RING_TEMPLATE.read_text(encoding='utf-8'), encoding='utf-8')
STORAGE.write_text(STORAGE_TEMPLATE.read_text(encoding='utf-8'), encoding='utf-8')

main = MAIN.read_text(encoding='utf-8')

old_call = '''section == "search" -> SearchTab(
                            prefs, safeData, searchQuery, onSearchQuery, onPlay, onPlayLive, onSeries,
                            onDemandWarning = if (!catalogLoading) catalogError else null
                        )'''
new_call = '''section == "search" -> SearchTab(
                            source, prefs, safeData, searchQuery, onSearchQuery, onPlay, onPlayLive, onSeries,
                            onDemandWarning = if (!catalogLoading) catalogError else null
                        )'''
if old_call not in main:
    raise SystemExit('SearchTab call target not found')
main = main.replace(old_call, new_call, 1)

old_signature = '''fun SearchTab(
    prefs: SharedPreferences,'''
new_signature = '''fun SearchTab(
    source: Source?,
    prefs: SharedPreferences,'''
if old_signature not in main:
    raise SystemExit('SearchTab signature target not found')
main = main.replace(old_signature, new_signature, 1)

old_state = '''    onDemandWarning: String? = null
) {
    var recents by remember { mutableStateOf(loadRecents(prefs)) }'''
new_state = '''    onDemandWarning: String? = null
) {
    // ZAKO_V440_SEARCH_MOVIE_DETAILS: Search movies use the same details dialog as Movies.
    var searchInfoMovie by remember { mutableStateOf<Movie?>(null) }
    searchInfoMovie?.let { movie ->
        VodInfoDialog(
            source = source, prefs = prefs, movie = movie, onPlay = onPlay,
            onClose = { searchInfoMovie = null }
        )
    }
    var recents by remember { mutableStateOf(loadRecents(prefs)) }'''
if old_state not in main:
    raise SystemExit('SearchTab state target not found')
main = main.replace(old_state, new_state, 1)

old_movie_click = '''                        onClick = {
                            saveRecent(q)
                            onPlay(Playable(m.name, m.url, isLive = false, artwork = m.icon))
                        },'''
new_movie_click = '''                        onClick = {
                            saveRecent(q)
                            searchInfoMovie = m
                        },'''
if old_movie_click not in main:
    raise SystemExit('Search movie click target not found')
main = main.replace(old_movie_click, new_movie_click, 1)

MAIN.write_text(main, encoding='utf-8')
print('Applied Zako 4.40 rolling-ring foundation + Search movie details fix')
