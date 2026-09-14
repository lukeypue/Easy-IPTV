from pathlib import Path
import re
import runpy

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
GRADLE = Path('app/build.gradle.kts')
PATCHER = Path('scripts/apply_v442.py')

main = MAIN.read_text(encoding='utf-8')

# v4.41 intentionally made Live search use tall movie posters. v4.42 changes only
# that Live shelf to compact channel cards, while keeping the v4.41 movie-info flow.
v441_live = '''            // ZAKO_V441_SEARCH_VISUAL_PARITY: Search now looks like the Movies/Series shelves.
            if (liveHits.isNotEmpty()) {
                item { SectionHeader("Live TV") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(liveHits) { ch ->
                            PosterCard(ch.name, ch.icon) { saveRecent(q); playLiveHit(ch) }
                        }
                    }
                }
            }
'''
base_live = '''            if (liveHits.isNotEmpty()) {
                item { SectionHeader("Live TV") }
                items(liveHits) { ch ->
                    MediaRow(ch.name, ch.icon, onClick = { saveRecent(q); playLiveHit(ch) })
                }
            }
'''
if v441_live not in main:
    raise SystemExit('Could not locate generated v4.41 Live search shelf')
MAIN.write_text(main.replace(v441_live, base_live, 1), encoding='utf-8')

# The base-source comment grew after the original patcher was authored. Make the
# one-time patcher match the current generated baseline without weakening checks.
patcher = PATCHER.read_text(encoding='utf-8')
patcher = patcher.replace(
    '    // XMLTV can be huge.\n',
    '    // XMLTV can be huge. Never download/parse the full guide behind full-screen\n'
)
PATCHER.write_text(patcher, encoding='utf-8')

runpy.run_path(str(PATCHER), run_name='__main__')

# v4.42 is the next installable Android/update version after v4.41 (66).
gradle = GRADLE.read_text(encoding='utf-8')
gradle, n1 = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 67', gradle, count=1)
gradle, n2 = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.42"', gradle, count=1)
if n1 != 1 or n2 != 1:
    raise SystemExit('v4.42 version bump failed')
GRADLE.write_text(gradle, encoding='utf-8')

main = MAIN.read_text(encoding='utf-8')
main = main.replace(
    'Zako 4.23 — plays the playlists you provide.',
    'Zako 4.42 — plays the playlists you provide.'
)
MAIN.write_text(main, encoding='utf-8')

required = [
    'Preparing your TV experience',
    'private fun SearchLiveCard',
    'Text("LIVE"',
    'AppInputGate.startupLocked',
    '3D side-by-side correction on',
    'ZAKO_V441_TS_RESYNC',
]
final_main = MAIN.read_text(encoding='utf-8')
missing = [marker for marker in required if marker not in final_main]
if missing:
    raise SystemExit('v4.42 verification missing: ' + ', '.join(missing))
final_gradle = GRADLE.read_text(encoding='utf-8')
if 'versionCode = 67' not in final_gradle or 'versionName = "4.42"' not in final_gradle:
    raise SystemExit('v4.42 version verification failed')

print('Zako 4.42 generated source verified GREEN')
