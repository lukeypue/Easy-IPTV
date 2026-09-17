#!/usr/bin/env python3
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
base = root / 'app/src/main/java/com/easyiptv/player'
nav_path = base / 'TvNavigationPolicy.kt'
resource_path = base / 'PlaybackResourcePolicy.kt'
startup_path = base / 'StartupPolicy.kt'
shell_path = base / 'TvShell.kt'
overlay_path = base / 'LiveOverlay.kt'
catalog_path = base / 'CatalogRuntimePolicy.kt'
runtime_path = base / 'MediaRuntimePolicy.kt'
startup = startup_path.read_text() if startup_path.exists() else ''
nav = nav_path.read_text() if nav_path.exists() else ''
resource = resource_path.read_text() if resource_path.exists() else ''
shell = shell_path.read_text() if shell_path.exists() else ''
overlay = overlay_path.read_text() if overlay_path.exists() else ''
catalog = catalog_path.read_text() if catalog_path.exists() else ''
runtime = runtime_path.read_text() if runtime_path.exists() else ''
downloads = (base / 'Downloads.kt').read_text()
storage = (base / 'Storage.kt').read_text()
streams = (base / 'ProviderStreams.kt').read_text()
live_storage = (base / 'LiveStorageManager.kt').read_text()

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
    'TV shell exists': shell_path.exists() and 'ZAKO_V447_TV_SHELL' in shell,
    'TV shell destinations': all(x in shell for x in ['LIVE', 'GUIDE', 'MOVIES', 'SERIES', 'SEARCH', 'LIBRARY', 'SETTINGS']),
    'TV shell focus tokens': 'FocusToken' in shell and 'MAIN_NAV' in shell and 'CONTENT' in shell,
    'live overlay policy exists': overlay_path.exists() and 'ZAKO_V447_LIVE_OVERLAY' in overlay,
    'compact three row policy': 'visibleRowCount = 3' in overlay,
    'accelerating seek policy': 'seekMultiplier' in overlay and '5 -> 1' in overlay,
    'previous channel action': 'PREVIOUS_CHANNEL' in overlay,
    'record action': 'RECORD' in overlay,
    'captions action': 'CAPTIONS' in overlay,
    'catalog runtime policy exists': catalog_path.exists() and 'ZAKO_V447_CATALOG_RUNTIME' in catalog,
    'catalog page bounded': 'pageSize = 40' in catalog,
    'search debounce bounded': 'searchDebounceMs = 250L' in catalog,
    'live catalog prefetch bounded': 'livePrefetchPages = 1' in catalog,
    'idle catalog prefetch bounded': 'idlePrefetchPages = 2' in catalog,
    'catalog pauses on live buffering': 'allowCatalogWork' in catalog and 'playerBuffering' in catalog,
    'media runtime policy exists': runtime_path.exists() and 'ZAKO_V447_MEDIA_RUNTIME' in runtime,
    'one background download': 'maxConcurrentDownloads = 1' in runtime,
    'one stream plan warning': 'oneStreamRecordingMessage' in runtime and '1-stream IPTV plan' in runtime,
    'USB fallback policy': 'storageFallbackMessage' in runtime and 'internal storage' in runtime,
    'runtime catalog policy wired': 'CatalogRuntimePolicy.pageSize' in main and 'CatalogRuntimePolicy.searchDebounceMs' in main,
    'runtime live overlay wired': 'LiveOverlay.visibleRowCount' in main,
    'runtime shell wired': 'TvShell.initialDestination()' in main and 'TvShell.initialFocus()' in main,
    'runtime startup state wired': 'StartupPolicy.state(' in main,
    'runtime playback priority wired': 'PlaybackResourcePolicy.allowBackgroundHeavyWork' in main,
    'download resume Range retained': 'header("Range", "bytes=$resumeFrom-")' in downloads,
    'download 206 append retained': 'r.code == 206' in downloads and 'FileOutputStream(part, append)' in downloads,
    'download 64KiB buffer retained': 'ByteArray(64 * 1024)' in downloads,
    'failed partial preserved': 'STATE_FAILED' in downloads and '.part' in downloads,
    'provider defaults one stream': 'prefs.getInt(KEY, 1)' in streams,
    'same channel recording costs zero': 'if (Recorder.usesProviderConnection) 1 else 0' in streams,
    'USB requires write probe': '.eztv_write_test' in storage and 'isWritable' in storage,
    'saved media internal fallback': 'context.getExternalFilesDir(null) ?: context.filesDir' in storage,
    'timeshift internal fallback': 'return context.cacheDir' in storage,
    'bounded USB DVR ring': 'USB_MAX_RING_BYTES' in live_storage,
    'compact mini guide marker': 'ZAKO_V447_COMPACT_MINI_GUIDE' in main,
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
