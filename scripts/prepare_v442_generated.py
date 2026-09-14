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

# The v4.42 patcher was authored against the raw baseline. Earlier generated
# versions deliberately changed exact source shapes, so align ONLY those known
# targets before running the strict patcher.
patcher = PATCHER.read_text(encoding='utf-8')
patcher = patcher.replace(
    '    // XMLTV can be huge.\n',
    '    // XMLTV can be huge. Never download/parse the full guide behind full-screen\n'
)

# v4.24 made refresh failures visible even when cached data exists. Preserve that
# behavior and teach both sides of the v4.42 finally-block replacement to expect it.
old_refresh_line = '                if (data == null) loadError = e.message ?: "error"'
new_refresh_line = '                loadError = e.message ?: "Provider refresh unavailable"'
if patcher.count(old_refresh_line) != 2:
    raise SystemExit(f'Expected two raw refresh targets in v4.42 patcher, found {patcher.count(old_refresh_line)}')
patcher = patcher.replace(old_refresh_line, new_refresh_line)

# v4.31 added a real INFO control between SIZE and PREVIOUS. Keep it, and insert
# the new per-channel 3D control after INFO rather than replacing/bypassing INFO.
raw_focus_target = '''    val modeFocus = remember { FocusRequester() }
    val sizeFocus = remember { FocusRequester() }
    val previousFocus = remember { FocusRequester() }
'''
generated_focus_target = '''    val modeFocus = remember { FocusRequester() }
    val sizeFocus = remember { FocusRequester() }
    val infoFocus = remember { FocusRequester() }
    val previousFocus = remember { FocusRequester() }
'''
raw_focus_replacement = '''    val modeFocus = remember { FocusRequester() }
    val sizeFocus = remember { FocusRequester() }
    val sbsFocus = remember { FocusRequester() }
    val previousFocus = remember { FocusRequester() }
'''
generated_focus_replacement = '''    val modeFocus = remember { FocusRequester() }
    val sizeFocus = remember { FocusRequester() }
    val infoFocus = remember { FocusRequester() }
    val sbsFocus = remember { FocusRequester() }
    val previousFocus = remember { FocusRequester() }
'''
if patcher.count(raw_focus_target) != 1 or patcher.count(raw_focus_replacement) != 1:
    raise SystemExit('Unexpected v4.42 focus patcher shape')
patcher = patcher.replace(raw_focus_target, generated_focus_target, 1)
patcher = patcher.replace(raw_focus_replacement, generated_focus_replacement, 1)

raw_controls_target = '''            MiniGuideControl(
                "SIZE",
                modifier = Modifier.weight(0.8f).focusRequester(sizeFocus).focusProperties {
                    left = modeFocus; right = previousFocus; down = timelineFocus
                }
            ) { touch(); onResize() }

            MiniGuideControl(
                "PREVIOUS",
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(previousFocus)
                    .focusProperties { left = sizeFocus; right = settingsFocus; down = timelineFocus }
'''
generated_controls_target = '''            MiniGuideControl(
                "SIZE",
                modifier = Modifier.weight(0.72f).focusRequester(sizeFocus).focusProperties {
                    left = modeFocus; right = infoFocus; down = timelineFocus
                }
            ) { touch(); onResize() }

            MiniGuideControl(
                "ⓘ INFO",
                enabled = nowShow != null,
                activeColor = Color(0xFFFFE45C),
                modifier = Modifier.weight(0.82f).focusRequester(infoFocus).focusProperties {
                    left = sizeFocus; right = previousFocus; down = timelineFocus
                }
            ) {
                touch()
                if (nowShow != null) showNowInfo = true
                else toast(context, "Program information is not available right now.")
            }

            MiniGuideControl(
                "PREVIOUS",
                modifier = Modifier
                    .weight(0.92f)
                    .focusRequester(previousFocus)
                    .focusProperties { left = infoFocus; right = settingsFocus; down = timelineFocus }
'''
raw_controls_replacement = '''            MiniGuideControl(
                "SIZE",
                modifier = Modifier.weight(0.8f).focusRequester(sizeFocus).focusProperties {
                    left = modeFocus; right = sbsFocus; down = timelineFocus
                }
            ) { touch(); onResize() }

            MiniGuideControl(
                if (sbs2d) "3D→2D" else "3D",
                modifier = Modifier.weight(0.78f).focusRequester(sbsFocus).focusProperties {
                    left = sizeFocus; right = previousFocus; down = timelineFocus
                },
                activeColor = if (sbs2d) Accent else Ink
            ) { touch(); onToggleSbs() }

            MiniGuideControl(
                "PREVIOUS",
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(previousFocus)
                    .focusProperties { left = sbsFocus; right = settingsFocus; down = timelineFocus }
'''
generated_controls_replacement = '''            MiniGuideControl(
                "SIZE",
                modifier = Modifier.weight(0.72f).focusRequester(sizeFocus).focusProperties {
                    left = modeFocus; right = infoFocus; down = timelineFocus
                }
            ) { touch(); onResize() }

            MiniGuideControl(
                "ⓘ INFO",
                enabled = nowShow != null,
                activeColor = Color(0xFFFFE45C),
                modifier = Modifier.weight(0.82f).focusRequester(infoFocus).focusProperties {
                    left = sizeFocus; right = sbsFocus; down = timelineFocus
                }
            ) {
                touch()
                if (nowShow != null) showNowInfo = true
                else toast(context, "Program information is not available right now.")
            }

            MiniGuideControl(
                if (sbs2d) "3D→2D" else "3D",
                modifier = Modifier.weight(0.72f).focusRequester(sbsFocus).focusProperties {
                    left = infoFocus; right = previousFocus; down = timelineFocus
                },
                activeColor = if (sbs2d) Accent else Ink
            ) { touch(); onToggleSbs() }

            MiniGuideControl(
                "PREVIOUS",
                modifier = Modifier
                    .weight(0.92f)
                    .focusRequester(previousFocus)
                    .focusProperties { left = sbsFocus; right = settingsFocus; down = timelineFocus }
'''
if patcher.count(raw_controls_target) != 1 or patcher.count(raw_controls_replacement) != 1:
    raise SystemExit('Unexpected v4.42 mini-guide control patcher shape')
patcher = patcher.replace(raw_controls_target, generated_controls_target, 1)
patcher = patcher.replace(raw_controls_replacement, generated_controls_replacement, 1)

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
    'val infoFocus = remember { FocusRequester() }',
    'left = sizeFocus; right = sbsFocus',
    'left = infoFocus; right = previousFocus',
]
final_main = MAIN.read_text(encoding='utf-8')
missing = [marker for marker in required if marker not in final_main]
if missing:
    raise SystemExit('v4.42 verification missing: ' + ', '.join(missing))
final_gradle = GRADLE.read_text(encoding='utf-8')
if 'versionCode = 67' not in final_gradle or 'versionName = "4.42"' not in final_gradle:
    raise SystemExit('v4.42 version verification failed')

print('Zako 4.42 generated source verified GREEN')
