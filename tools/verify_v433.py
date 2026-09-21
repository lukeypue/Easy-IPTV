from pathlib import Path

main = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text()
data = Path('app/src/main/java/com/easyiptv/player/Data.kt').read_text()
gradle = Path('app/build.gradle.kts').read_text()
checks = {
    'versionCode 58': 'versionCode = 58' in gradle,
    'versionName 4.33': 'versionName = "4.33"' in gradle,
    'episode details marker': 'ZAKO_V433_EPISODE_DETAILS' in main,
    'episode details dialog': 'private fun EpisodeInfoDialog(' in main,
    'episode play button': '▶ PLAY' in main and 'EpisodeInfoDialog' in main,
    'episode download button': '⬇ DOWNLOAD' in main and 'EpisodeInfoDialog' in main,
    'buffer cap marker': 'ZAKO_V433_BUFFER_CAP' in main,
    'buffer cap 30-60': '.coerceIn(30_000, 60_000)' in main,
    'low ram byte cap': 'setTargetBufferBytes(targetBufferBytes)' in main,
    'streaming live marker': 'ZAKO_V433_STREAMING_LIVE' in data,
    'live streamRows': 'streamRows("get_live_streams"' in data,
    'retry reset marker': 'ZAKO_V433_RETRY_RESET' in data,
    'remember filters marker': 'ZAKO_V433_REMEMBER_FILTERS' in main,
    'remember movies filter': 'remember(data.movies, selectedCat)' in main,
    'remember series filter': 'remember(data.series, selectedCat)' in main,
    'search cache marker': 'ZAKO_V433_SEARCH_CACHE' in main,
    'search results remembered': 'remember(qLower, data.movies)' in main,
}
missing = [name for name, ok in checks.items() if not ok]
if missing:
    raise SystemExit('v4.33 verification failed: ' + ', '.join(missing))
print('v4.33 verification passed')