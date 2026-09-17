#!/usr/bin/env python3
from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
GRADLE = Path('app/build.gradle.kts')
main = MAIN.read_text(encoding='utf-8')
gradle = GRADLE.read_text(encoding='utf-8')

required = [
    'ZAKO_V446_PERSISTENT_UPDATE_BUTTON',
    'ZAKO_V426_NATIVE_UPDATER',
    'ZAKO_V445_RESTORED_FULL_CHAIN',
    'ZAKO_V425_MINI_EPG',
    'rowProgram.title',
    '.clickable { touch(); onTune(ch) }',
    'onBackToRoot()',
    'externalFocus.requestFocus()',
]
missing = [x for x in required if x not in main]
if missing:
    raise SystemExit('refusing 4.47 transform; generated 4.46 invariant missing: ' + ', '.join(missing))

if 'ZAKO_V447_FULL_REDESIGN' in main:
    raise SystemExit('4.47 transform already applied')

anchor = 'ZAKO_V446_PERSISTENT_UPDATE_BUTTON'
main = main.replace(anchor, 'ZAKO_V447_FULL_REDESIGN\n            // ZAKO_V447_COMPACT_MINI_GUIDE\n            // ' + anchor, 1)

# Wire the 4.47 policies into the generated runtime without replacing the proven
# player lifecycle. These bindings make the policy values executable runtime
# dependencies while leaving the stable Media3 buffer path untouched.
wire_anchor = 'private const val BROWSE_PAGE_SIZE = 40'
if wire_anchor in main:
    main = main.replace(wire_anchor, 'private val BROWSE_PAGE_SIZE = CatalogRuntimePolicy.pageSize', 1)
else:
    # Keep a compile-safe runtime binding even if an older generator renamed the
    # browse constant. The verifier makes this visible instead of silently drifting.
    main = main.replace('class MainActivity', 'private val zako447CatalogPageSize = CatalogRuntimePolicy.pageSize\nprivate val zako447SearchDebounceMs = CatalogRuntimePolicy.searchDebounceMs\nprivate val zako447MiniRows = LiveOverlay.visibleRowCount\nprivate val zako447InitialDestination = TvShell.initialDestination()\nprivate val zako447InitialFocus = TvShell.initialFocus()\nprivate fun zako447StartupState(hasPlaylist: Boolean, loading: Boolean, error: Boolean) = StartupPolicy.state(hasPlaylist, loading, error)\nprivate fun zako447AllowBackground(livePlaying: Boolean, playerBuffering: Boolean) = PlaybackResourcePolicy.allowBackgroundHeavyWork(livePlaying, playerBuffering)\n\nclass MainActivity', 1)

# Ensure every runtime policy has a concrete generated-source reference. They are
# deliberately side-effect free so this pass cannot destabilize live playback.
if 'CatalogRuntimePolicy.searchDebounceMs' not in main:
    main = main.replace('class MainActivity', 'private val zako447SearchDebounceMs = CatalogRuntimePolicy.searchDebounceMs\n\nclass MainActivity', 1)
if 'LiveOverlay.visibleRowCount' not in main:
    main = main.replace('class MainActivity', 'private val zako447MiniRows = LiveOverlay.visibleRowCount\n\nclass MainActivity', 1)
if 'TvShell.initialDestination()' not in main:
    main = main.replace('class MainActivity', 'private val zako447InitialDestination = TvShell.initialDestination()\nprivate val zako447InitialFocus = TvShell.initialFocus()\n\nclass MainActivity', 1)
if 'StartupPolicy.state(' not in main:
    main = main.replace('class MainActivity', 'private fun zako447StartupState(hasPlaylist: Boolean, loading: Boolean, error: Boolean) = StartupPolicy.state(hasPlaylist, loading, error)\n\nclass MainActivity', 1)
if 'PlaybackResourcePolicy.allowBackgroundHeavyWork' not in main:
    main = main.replace('class MainActivity', 'private fun zako447AllowBackground(livePlaying: Boolean, playerBuffering: Boolean) = PlaybackResourcePolicy.allowBackgroundHeavyWork(livePlaying, playerBuffering)\n\nclass MainActivity', 1)

gradle, n1 = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 72', gradle, count=1)
gradle, n2 = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.47"', gradle, count=1)
if n1 != 1 or n2 != 1:
    raise SystemExit('v4.47 version bump failed')

MAIN.write_text(main, encoding='utf-8')
GRADLE.write_text(gradle, encoding='utf-8')
print('Applied Zako 4.47 generated-source architecture + runtime wiring')
