from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
GRADLE = Path('app/build.gradle.kts')
main = MAIN.read_text(encoding='utf-8')
gradle = GRADLE.read_text(encoding='utf-8')

checks = {
    'versionCode 64': re.search(r'versionCode\s*=\s*64\b', gradle) is not None,
    'versionName 4.39': 'versionName = "4.39"' in gradle,
    'safe buffer marker': 'ZAKO_V439_SAFE_BUFFER_INVARIANTS' in main,
    'safe min buffer': 'val minBufferMs = (bufferSec * 1000).coerceAtMost(60_000)' in main,
    'safe rebuffer clamp': 'val safeRebufferMs = minOf(requestedRebufferMs, minBufferMs)' in main,
    'safe startup clamp': 'val safeStartMs = minOf(requestedStartMs, minBufferMs)' in main,
    'load control uses safe min': '                minBufferMs,' in main,
    'load control uses safe rebuffer': '                safeRebufferMs' in main,
    'diagnostic note': 'v439_buffer_policy' in main,
    'keeps 4.38 nonrecursive steady handling': 'steady_player_error_safe' in main,
    'keeps 4.37 reconnect protection': 'ZAKO_V437_STREAM_RECONNECT' in main,
    'keeps 4.37 no speed governor': 'ZAKO_V437_NO_SPEED_GOVERNOR' in main,
}

# Behavioral policy check for every supported UI buffer size and representative
# lock-in values. This mirrors the tiny clamp that production Kotlin must contain.
for buffer_sec in (10, 30, 60):
    for lock_ms in (2_000, 4_000, 12_000):
        for steady in (False, True):
            min_ms = min(buffer_sec * 1000, 60_000)
            max_ms = max(30_000, min(buffer_sec * 1000 * 3, 60_000))
            requested_start = max(lock_ms, 6_000) if steady else lock_ms
            requested_rebuffer = min(max(lock_ms * 3, 12_000), 20_000) if steady else lock_ms
            safe_start = min(requested_start, min_ms)
            safe_rebuffer = min(requested_rebuffer, min_ms)
            if not (0 <= safe_start <= min_ms <= max_ms):
                checks[f'buffer tuple start {buffer_sec}/{lock_ms}/{steady}'] = False
            if not (0 <= safe_rebuffer <= min_ms <= max_ms):
                checks[f'buffer tuple rebuffer {buffer_sec}/{lock_ms}/{steady}'] = False

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('VERIFY_V439_FAIL: ' + ', '.join(failed))

print('VERIFY_V439_OK')
