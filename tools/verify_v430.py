from pathlib import Path

main = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text(encoding='utf-8')
data = Path('app/src/main/java/com/easyiptv/player/Data.kt').read_text(encoding='utf-8')
gradle = Path('app/build.gradle.kts').read_text(encoding='utf-8')

checks = {
    'version 4.30': 'versionName = "4.30"' in gradle and 'versionCode = 55' in gradle,
    'low-memory catalog': 'ZAKO_V430_LOW_MEMORY_CATALOG' in main,
    'movie paging': 'visibleMovieCount' in main and 'gridItemsIndexed(visibleMovies' in main,
    'series paging': 'visibleSeriesCount' in main and 'gridItemsIndexed(visibleSeries' in main,
    'duplicate-safe series key': '"${item.id}:$index"' in main,
    'episode plot': 'val plot: String = ""' in data and 'subtitle = ep.plot.ifBlank' in main,
    'left opens live guide': 'android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {' in main and 'miniGuideOpen = true' in main,
    'bright guide titles': '0xFFFFE45C' in main,
}

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL'), name)
if failed:
    raise SystemExit('v4.30 verification failed: ' + ', '.join(failed))
