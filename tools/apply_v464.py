#!/usr/bin/env python3
"""Apply final, checked edits after the known 4.63 generation chain."""
from pathlib import Path
import re
import shutil

root = Path('app/src/main/java/com/easyiptv/player')
p = root / 'MainActivity.kt'
m = p.read_text()
templates = Path('tools/v464')

def replace_once(old, new):
    global m
    if m.count(old) != 1:
        raise SystemExit(f'Expected one patch target, found {m.count(old)}: {old[:90]}')
    m = m.replace(old, new, 1)

def replace_section(start, end, replacement):
    global m
    a = m.index(start)
    b = m.index(end, a)
    m = m[:a] + replacement + '\n\n' + m[b:]

replace_section('// ZAKO_V432_UNIFIED_KEYBOARD:', '@Composable\nfun SearchTab(',
                (templates / 'SharedKeyboard.kt.fragment').read_text())
replace_section('@Composable\nprivate fun TvTextField(', '@Composable\nprivate fun ChannelIcon(',
                (templates / 'TvTextField.kt.fragment').read_text())
if 'import androidx.compose.ui.focus.focusProperties\n' not in m:
    m = m.replace('import androidx.compose.foundation.focusable\n',
        'import androidx.compose.foundation.focusable\nimport androidx.compose.ui.focus.focusProperties\n')
if 'import androidx.compose.foundation.focusGroup\n' not in m:
    m = m.replace('import androidx.compose.foundation.focusable\n',
        'import androidx.compose.foundation.focusable\nimport androidx.compose.foundation.focusGroup\n')

# Same matcher for catalog, channels and EPG. Provider strings are never changed.
replace_once('val qLower = remember(q) { q.lowercase() }', 'val titleQuery = remember(q) { TitleQuery(q) }')
m = m.replace('remember(qLower, data.', 'remember(titleQuery, data.')
m = m.replace('it.name.lowercase().contains(qLower)', 'titleQuery.matches(it.name)')
m = m.replace('it.searchMeta.lowercase().contains(qLower)', 'titleQuery.matches(it.searchMeta)')
replace_once('data.live.asSequence().filter { titleQuery.matches(it.name) }.take(30).toList()',
             'titleQuery.find(data.live, { it.name })')
for collection in ('movies', 'series'):
    replace_once('''data.'''+collection+'''.asSequence().filter {
                titleQuery.matches(it.name) || titleQuery.matches(it.searchMeta)
            }.take(30).toList()''',
        'titleQuery.find(data.'+collection+', { it.name }, { it.searchMeta })')
replace_once('@Composable\nfun SearchTab(', '''private data class SearchMatches(
    val query: String,
    val live: List<LiveChannel>, val movies: List<Movie>, val series: List<SeriesItem>,
    val guide: List<EpgStore.GuideHit>
)

@Composable
fun SearchTab(''')
replace_section('        // ZAKO_V433_SEARCH_CACHE:', '        // Match guide channels', '''        // Rank off the UI thread so a large provider catalog cannot stall typing.
        val guideLoaded = EpgStore.loaded.value
        val matches by androidx.compose.runtime.produceState<SearchMatches?>(
            initialValue = null, key1 = q, key2 = data, key3 = guideLoaded
        ) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val queryMatcher = TitleQuery(q)
                SearchMatches(q,
                    queryMatcher.find(data.live, { it.name }),
                    queryMatcher.find(data.movies, { it.name }, { it.searchMeta }),
                    queryMatcher.find(data.series, { it.name }, { it.searchMeta }),
                    if (guideLoaded) EpgStore.search(q, 30) else emptyList())
            }
        }
        val currentMatches = matches
        if (currentMatches == null || currentMatches.query != q) {
            Text("Searching…", color = Muted, modifier = Modifier.padding(16.dp))
            return
        }
        val liveHits = currentMatches.live
        val movieHits = currentMatches.movies
        val seriesHits = currentMatches.series
        val guideHits = currentMatches.guide
''')
d = root / 'Data.kt'
data = d.read_text()
assert data.count('val query = q.trim()') == 1
data = data.replace('val query = q.trim()', 'val query = q.trim()\n        val titleQuery = TitleQuery(query)', 1)
assert data.count('e.title.contains(query, ignoreCase = true)') == 1
data = data.replace('e.title.contains(query, ignoreCase = true)', 'titleQuery.matches(e.title)', 1)
data = data.replace('val out = ArrayList<GuideHit>()', 'val out = ArrayList<Pair<Int, GuideHit>>()', 1)
data = data.replace('''if (e.endMs >= now && titleQuery.matches(e.title)) {
                    out.add(GuideHit(cid, cname, e))
                }''', '''if (e.endMs >= now) {
                    titleQuery.rank(e.title)?.let { rank -> out.add(rank to GuideHit(cid, cname, e)) }
                }''', 1)
data = data.replace('return out.sortedBy { it.entry.startMs }.take(limit)',
    'return out.sortedWith(compareBy({ it.first }, { it.second.entry.startMs })).take(limit).map { it.second }', 1)

# Request focus only after a lazy category row is back on screen.
replace_once('    val railFocus = remember { FocusRequester() }',
    '    val railFocus = remember { FocusRequester() }\n    var railReturnRequest by remember { mutableIntStateOf(0) }')
replace_once('                    externalFocus = railFocus,',
    '                    externalFocus = railFocus,\n                    returnRequest = railReturnRequest,')
m = m.replace('runCatching { railFocus.requestFocus() }', 'railReturnRequest++')
replace_once('    externalFocus: FocusRequester,\n    onCat:',
    '    externalFocus: FocusRequester,\n    returnRequest: Int,\n    onCat:')
a = m.index('private fun HomeRail(')
b = m.index('@Composable\nprivate fun RailItem', a)
rail = m[a:b]
old_start = rail.index('    // Simple Mode')
old_end = rail.index('    LazyColumn(', old_start)
rail = rail[:old_start] + '''    val railState = androidx.compose.foundation.lazy.rememberLazyListState()
    val cats = when (section) { "live" -> data.liveCats; "movies" -> data.vodCats; else -> data.seriesCats }
    val extras = when (section) {
        "live" -> listOf("fav" to "★ Favorites", "all" to "All channels")
        "movies" -> listOf("all" to "All movies")
        else -> listOf("all" to "All series")
    }
    val selected = when (section) { "live" -> liveCat; "movies" -> movieCat; else -> seriesCat }
    val categoryIds = extras.map { it.first } + cats.map { it.id }
    val selectedIndex = categoryIds.indexOf(selected).coerceAtLeast(0)
    suspend fun restoreRailFocus() {
        val index = if (depth == 0) RootItems.indexOfFirst { it.first == section }.coerceAtLeast(0) else selectedIndex + 1
        railState.scrollToItem(index)
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        runCatching { externalFocus.requestFocus() }
    }
    LaunchedEffect(depth, section) { if (depth == 0) restoreRailFocus() }
    LaunchedEffect(returnRequest) { if (returnRequest > 0) restoreRailFocus() }
''' + rail[old_end:]
rail = rail.replace('    LazyColumn(\n', '    LazyColumn(\n        state = railState,\n', 1)
c = rail.index('            val cats: List<Category>')
e = rail.index('            items(extras)', c)
rail = rail[:c] + rail[e:]
rail = rail.replace('items(extras) { p ->', 'itemsIndexed(extras) { ix, p ->')
rail = rail.replace('if (selected == p.first) Modifier.focusRequester(externalFocus)', 'if (selectedIndex == ix) Modifier.focusRequester(externalFocus)')
rail = rail.replace('items(cats) { c ->', 'itemsIndexed(cats) { ix, c ->')
rail = rail.replace('if (selected == c.id) Modifier.focusRequester(externalFocus)', 'if (selectedIndex == extras.size + ix) Modifier.focusRequester(externalFocus)')
m = m[:a] + rail + m[b:]

# Both grids offer touch access and consume Back while their content owns focus.
for start, end in [('fun MoviesPane(', '/* ----------------------------- series pane'),
                   ('fun SeriesPane(', '/* ----------------------------- settings pane')]:
    a = m.index(start); b = m.index(end, a); pane = m[a:b]
    body = pane.index(') {') + len(') {')
    pane = pane[:body] + '''
    var paneHasFocus by remember { mutableStateOf(false) }
    BackHandler(enabled = paneHasFocus) { onLeftToRail() }
''' + pane[body:]
    target = '    Column(Modifier.fillMaxSize()) {'
    assert pane.count(target) == 1
    pane = pane.replace(target, '''    Column(Modifier.fillMaxSize().onFocusChanged { paneHasFocus = it.hasFocus }.focusGroup()) {
        TextButton(onClick = onLeftToRail, modifier = Modifier.tvFocus(RoundedCornerShape(10.dp))) {
            Text("← CATEGORIES", color = Accent, fontWeight = FontWeight.Bold)
        }
''', 1)
    m = m[:a] + pane + m[b:]

# The supplied PNG stays byte-for-byte unchanged. Android sizes it via drawables.
replace_section('@Composable\nprivate fun RyzodBrandMark(', '@Composable\nprivate fun RyzodGridBackground(', '''@Composable
private fun RyzodBrandMark(compact: Boolean = true) {
    AsyncImage(
        model = R.drawable.ryzod_artwork_464,
        contentDescription = "RYZOD Media Player", contentScale = ContentScale.Fit,
        modifier = Modifier.size(if (compact) 46.dp else 62.dp)
    )
}''')
m = m.replace('Text("RYZOD", fontWeight = FontWeight.Black, fontSize = 19.sp, color = ElectricCyan)', 'RyzodBrandMark(compact = true)')
m = m.replace('Text("RYZOD", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)', 'RyzodBrandMark(compact = false)')

p.write_text('// RYZOD_V464_SHARED_KEYBOARD_SEARCH_CATEGORY_ICONS\n' + '\n'.join(line.rstrip() for line in m.splitlines()) + '\n')
d.write_text(data)
shutil.copyfile(templates / 'InputSearch.kt', root / 'InputSearch.kt')
g = Path('app/build.gradle.kts')
gradle = g.read_text()
gradle = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 88', gradle, count=1)
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.64"', gradle, count=1)
gradle = gradle.replace('dependencies {', 'dependencies {\n    testImplementation("junit:junit:4.13.2")', 1)
g.write_text(gradle)
tests = Path('app/src/test/java/com/easyiptv/player')
tests.mkdir(parents=True, exist_ok=True)
shutil.copyfile('tools/tests/InputSearchRegression.kt', tests / 'InputSearchRegression.kt')
(tests / 'InputSearchTest.kt').write_text('''package com.easyiptv.player
class InputSearchTest {
    @org.junit.Test fun keyboardAndSearchRegressions() { main() }
}
''')
manifest = Path('app/src/main/AndroidManifest.xml')
xml = manifest.read_text().replace('android:icon="@drawable/ryzod_official"', 'android:icon="@mipmap/ryzod_launcher_464"')
xml = xml.replace('android:roundIcon="@drawable/ryzod_official"', 'android:roundIcon="@mipmap/ryzod_launcher_464"')
xml = xml.replace('android:banner="@drawable/ryzod_official"', 'android:banner="@drawable/ryzod_tv_banner_464"')
assert '@drawable/ryzod_official' not in xml
manifest.write_text(xml)
print('Applied RYZOD 4.64 keyboard, punctuation search, category return and original artwork')
