from pathlib import Path
import re

GRADLE = Path('app/build.gradle.kts')
MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')

gradle = GRADLE.read_text(encoding='utf-8')
main = MAIN.read_text(encoding='utf-8') if MAIN.exists() else ''

checks = {
    'versionCode 66': re.search(r'versionCode\s*=\s*66\b', gradle) is not None,
    'versionName 4.41': 'versionName = "4.41"' in gradle,
    'search visual parity marker': 'ZAKO_V441_SEARCH_VISUAL_PARITY' in main,
    'weak channel diagnostics marker': 'ZAKO_V441_WEAK_CHANNEL_DIAGNOSTICS' in main,
    'weak channel resync marker': 'ZAKO_V441_TS_RESYNC' in main,
    'steady cushion marker': 'ZAKO_V441_STEADY_CUSHION' in main,
    'input gap counter': 'weakGapEvents' in main,
    'sync resync counter': 'syncResyncEvents' in main,
    'reconnect counter': 'reconnectEvents' in main,
    'input gap diagnostic': 'dvr_input_gap' in main,
    'sync loss diagnostic': 'dvr_ts_sync_lost' in main,
    'reconnect diagnostic': 'dvr_provider_reconnect' in main,
    'steady minimum cushion': 'waited >= 4_000L' in main,
    'steady hard timeout': 'waited >= 7_000L' in main,
    'natural prime path preserved': 'Timeshift.bytesWritten >= DVR_PRIME_BYTES || waited >= 2_000L' in main,
}

live_block = ''
movie_block = ''
series_block = ''
if 'if (liveHits.isNotEmpty()) {' in main:
    live_block = main.split('if (liveHits.isNotEmpty()) {', 1)[1].split('if (movieHits.isNotEmpty()) {', 1)[0]
if 'if (movieHits.isNotEmpty()) {' in main:
    movie_block = main.split('if (movieHits.isNotEmpty()) {', 1)[1].split('if (seriesHits.isNotEmpty()) {', 1)[0]
if 'if (seriesHits.isNotEmpty()) {' in main:
    series_block = main.split('if (seriesHits.isNotEmpty()) {', 1)[1].split('if (guideHits.isNotEmpty()) {', 1)[0]

checks['live search uses poster row'] = 'LazyRow(' in live_block and 'PosterCard(ch.name, ch.icon)' in live_block
checks['live search no MediaRow'] = 'MediaRow(' not in live_block
checks['movie search uses poster row'] = 'LazyRow(' in movie_block and 'PosterCard(m.name, m.icon)' in movie_block
checks['movie search opens details'] = 'searchInfoMovie = m' in movie_block
checks['movie search no MediaRow'] = 'MediaRow(' not in movie_block
checks['series poster row preserved'] = 'LazyRow(' in series_block and 'PosterCard(s.name, s.icon)' in series_block

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('VERIFY_V441_FAIL: ' + ', '.join(failed))
print('VERIFY_V441_OK')
