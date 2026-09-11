from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
DATA = Path('app/src/main/java/com/easyiptv/player/Data.kt')
GRADLE = Path('app/build.gradle.kts')
main = MAIN.read_text(encoding='utf-8')
data = DATA.read_text(encoding='utf-8')
gradle = GRADLE.read_text(encoding='utf-8')
changes=[]

def once(text, old, new, label):
    n=text.count(old)
    if n != 1: raise SystemExit(f'{label}: expected 1 match, found {n}')
    changes.append(label)
    return text.replace(old,new,1)

# Version identity
gradle,n=re.subn(r'versionCode\s*=\s*\d+','versionCode = 55',gradle,count=1)
if n!=1: raise SystemExit('versionCode missing')
gradle,n=re.subn(r'versionName\s*=\s*"[^"]+"','versionName = "4.30"',gradle,count=1)
if n!=1: raise SystemExit('versionName missing')
changes += ['versionCode 55','versionName 4.30']

# Episode descriptions from Xtream get_series_info.
data = once(data,
'''data class Episode(\n    val id: String,\n    val title: String,\n    val season: Int,\n    val episodeNum: Int,\n    val url: String\n)''',
'''data class Episode(\n    val id: String,\n    val title: String,\n    val season: Int,\n    val episodeNum: Int,\n    val url: String,\n    val plot: String = ""\n)''','Episode plot model')

data = once(data,
'''                val title = o.optString("title", info?.optString("title", "") ?: "").ifBlank { "Episode $num" }\n                out.getOrPut(season) { ArrayList() }.add(\n                    Episode(id, title, season, num, "$base/series/$user/$pass/$id.$ext")\n                )''',
'''                val title = o.optString("title", info?.optString("title", "") ?: "").ifBlank { "Episode $num" }\n                val infoPlot = info?.optString("plot", info?.optString("description", "") ?: "") ?: ""\n                val plot = o.optString("plot", o.optString("description", infoPlot)).trim()\n                out.getOrPut(season) { ArrayList() }.add(\n                    Episode(id, title, season, num, "$base/series/$user/$pass/$id.$ext", plot)\n                )''','parse episode plot')

# Avoid a second giant in-memory JSON copy while Live TV may still own decoder buffers.
main = once(main,
'''            if (cacheKey != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {\n                DataCache.save(context, cacheKey, merged)\n            }''',
'''            // ZAKO_V430_LOW_MEMORY_CATALOG: skip serializing the giant merged VOD catalog\n            // here. Re-encoding it duplicates the catalog in RAM at the worst possible moment.\n''','remove VOD cache serialization spike')

main = once(main,
'''private const val DVR_PRIME_BYTES = 512L * 1024L\n''',
'''private const val DVR_PRIME_BYTES = 512L * 1024L\nprivate const val BROWSE_PAGE_SIZE = 40\n''','browse page size')

# Movies: reveal current page plus one page ahead; add another 40 as the user nears the end.
main = once(main,
'''    val targetFocus = remember(selectedCat, restoreUrl) { FocusRequester() }\n    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()\n''',
'''    val targetFocus = remember(selectedCat, restoreUrl) { FocusRequester() }\n    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()\n    var visibleMovieCount by remember(selectedCat, filtered.size) {\n        mutableIntStateOf(minOf(filtered.size, maxOf(BROWSE_PAGE_SIZE * 2, targetIdx + BROWSE_PAGE_SIZE)))\n    }\n    val visibleMovies = filtered.take(visibleMovieCount)\n    LaunchedEffect(gridState, filtered.size) {\n        androidx.compose.runtime.snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }\n            .collect { last ->\n                if (last >= visibleMovieCount - 10 && visibleMovieCount < filtered.size) {\n                    visibleMovieCount = minOf(filtered.size, visibleMovieCount + BROWSE_PAGE_SIZE)\n                }\n            }\n    }\n''','movies progressive reveal')
main = once(main,'gridItemsIndexed(filtered, key = { _, item -> item.url }) { index, m ->','gridItemsIndexed(visibleMovies, key = { _, item -> item.url }) { index, m ->','movies visible page')

# Series: same paging; include index in key so duplicate provider IDs cannot crash Compose.
main = once(main,
'''    val targetFocus = remember(selectedCat, restoreId) { FocusRequester() }\n    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()\n''',
'''    val targetFocus = remember(selectedCat, restoreId) { FocusRequester() }\n    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()\n    var visibleSeriesCount by remember(selectedCat, filtered.size) {\n        mutableIntStateOf(minOf(filtered.size, maxOf(BROWSE_PAGE_SIZE * 2, targetIdx + BROWSE_PAGE_SIZE)))\n    }\n    val visibleSeries = filtered.take(visibleSeriesCount)\n    LaunchedEffect(gridState, filtered.size) {\n        androidx.compose.runtime.snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }\n            .collect { last ->\n                if (last >= visibleSeriesCount - 10 && visibleSeriesCount < filtered.size) {\n                    visibleSeriesCount = minOf(filtered.size, visibleSeriesCount + BROWSE_PAGE_SIZE)\n                }\n            }\n    }\n''','series progressive reveal')
main = once(main,'gridItemsIndexed(filtered, key = { _, item -> item.id }) { index, item ->','gridItemsIndexed(visibleSeries, key = { index, item -> "${item.id}:$index" }) { index, item ->','series visible page duplicate-safe key')

# Show episode information without changing the shared MediaRow API used elsewhere.
main = once(main,
'''                        MediaRow(\n                            name = label,\n                            icon = null,\n''',
'''                        MediaRow(\n                            name = if (ep.plot.isBlank()) label else "$label  •  ${ep.plot}",\n                            icon = null,\n''','episode description row')

# Bright yellow program titles separate them from the blue guide chrome.
main = once(main,
'color = ProgramCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,',
'color = Color(0xFFFFE45C), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,',
'bright live list program title')
main = once(main,
'color = if (airing) ProgramCyan else Ink,\n                                        fontSize = 10.sp,',
'color = Color(0xFFFFE45C),\n                                        fontSize = 10.sp,',
'bright grid guide program title')
main = once(main,
'color = ProgramCyan, fontSize = 8.sp, fontWeight = FontWeight.SemiBold,',
'color = Color(0xFFFFE45C), fontSize = 8.sp, fontWeight = FontWeight.SemiBold,',
'bright mini-guide program title')

# LEFT on full-screen Live TV opens Zako's guide instead of stock player controls.
old='''                android.view.KeyEvent.KEYCODE_DPAD_UP,\n                android.view.KeyEvent.KEYCODE_DPAD_DOWN,\n                android.view.KeyEvent.KEYCODE_DPAD_LEFT,\n                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {\n                    // Let the recent-channel LazyRow own D-pad focus.\n                    if (miniGuideOpen) false else { pvRef?.showController(); true }\n                }'''
new='''                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {\n                    if (miniGuideOpen) false\n                    else if (current.isLive) {\n                        pvRef?.hideController()\n                        overlayVisible = false\n                        miniGuideOpen = true\n                        true\n                    } else { pvRef?.showController(); true }\n                }\n                android.view.KeyEvent.KEYCODE_DPAD_UP,\n                android.view.KeyEvent.KEYCODE_DPAD_DOWN,\n                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {\n                    if (miniGuideOpen) false else { pvRef?.showController(); true }\n                }'''
main = once(main,old,new,'Left opens live guide')

main = main.replace('Zako 4.29','Zako 4.30')
MAIN.write_text(main,encoding='utf-8')
DATA.write_text(data,encoding='utf-8')
GRADLE.write_text(gradle,encoding='utf-8')
print('Applied Zako 4.30:')
for c in changes: print(' -',c)
