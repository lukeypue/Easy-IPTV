#!/usr/bin/env python3
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
nav_path = root / 'app/src/main/java/com/easyiptv/player/TvNavigationPolicy.kt'
resource_path = root / 'app/src/main/java/com/easyiptv/player/PlaybackResourcePolicy.kt'
startup_path = root / 'app/src/main/java/com/easyiptv/player/StartupPolicy.kt'
startup = startup_path.read_text() if startup_path.exists() else ''
nav = nav_path.read_text() if nav_path.exists() else ''
resource = resource_path.read_text() if resource_path.exists() else ''
downloads = (root / 'app/src/main/java/com/easyiptv/player/Downloads.kt').read_text()

checks = {
    '4.47 full redesign marker': 'ZAKO_V447_FULL_REDESIGN' in main,
    'navigation policy exists': nav_path.exists() and 'object TvNavigationPolicy' in nav,
    'deterministic remote focus policy': 'ZAKO_V447_DETERMINISTIC_REMOTE_FOCUS' in nav,
    'navigation outward action': 'OUTWARD' in nav,
    'navigation inward action': 'INWARD' in nav,
    'playback resource policy exists': resource_path.exists() and 'object PlaybackResourcePolicy' in resource,
    'playback priority marker': 'ZAKO_V447_PLAYBACK_PRIORITY_POLICY' in resource,
    'live playback can throttle background work': 'allowBackgroundHeavyWork' in resource,
    'startup gate state': 'StartupGateState' in startup,
    'startup input gate marker': 'ZAKO_V447_STARTUP_INPUT_GATE' in startup,
    'startup message': 'Please wait while we load your content fresh for a better experience' in startup,
    'compact mini guide': 'ZAKO_V447_COMPACT_MINI_GUIDE' in main,
    'three-row mini guide retained': 'ZAKO_V425_MINI_EPG' in main and 'rowProgram.title' in main,
    'mini guide OK tunes': '.clickable { touch(); onTune(ch) }' in main,
    'fluid LEFT navigation retained': 'onBackToRoot()' in main and 'externalFocus.requestFocus()' in main,
    'SBS compatibility retained': 'ZAKO_V442_SBS' in main or 'side-by-side' in main.lower() or 'sbs' in main.lower(),
    'persistent updater retained': 'ZAKO_V446_PERSISTENT_UPDATE_BUTTON' in main and 'Chip("Check for updates", false)' in main,
    'native updater retained': 'ZAKO_V426_NATIVE_UPDATER' in main,
    'resumable downloads retained': 'Range' in downloads and '.part' in downloads,
    'versionCode 72': re.search(r'versionCode\s*=\s*72\b', gradle) is not None,
    'versionName 4.47': re.search(r'versionName\s*=\s*"4\.47"', gradle) is not None,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    for name in failed:
        print(f'v4.47 missing: {name}')
    raise SystemExit(1)

print('Zako 4.47 redesign contract verification passed')
for name in checks:
    print(f'PASS: {name}')
