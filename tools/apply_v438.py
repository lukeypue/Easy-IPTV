from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
GRADLE = Path('app/build.gradle.kts')
main = MAIN.read_text(encoding='utf-8')
gradle = GRADLE.read_text(encoding='utf-8')
changes = []

def once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1, found {n}')
    changes.append(label)
    return text.replace(old, new, 1)

gradle, n = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 63', gradle, count=1)
if n != 1: raise SystemExit('versionCode')
gradle, n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.38"', gradle, count=1)
if n != 1: raise SystemExit('versionName')
changes += ['versionCode 63', 'versionName 4.38']

main = once(main,
'''    private var liveFails = 0
    private var retriesP = 0
    @Volatile private var steadyHlsFailed = false
    @Volatile private var directUsingHls = false
''',
'''    private var liveFails = 0
    private var retriesP = 0
    // ZAKO_V438_STEADY_CRASH_HOTFIX: Steady must never force a guessed transport
    // or recursively rebuild the player from inside Media3's error callback.
''',
'steady state cleanup')

main = once(main,
'''                // Steady weak-channel tries HLS first when the provider exposes
                // an Xtream-style .ts endpoint. If that exact provider rejects or
                // fails HLS, retry the same channel once as classic TS.
                if (liveMode && directUsingHls && !steadyHlsFailed) {
                    StabilityCore.note("steady_hls_fallback")
                    steadyHlsFailed = true
                    directUsingHls = false
                    zapTo(currentIdxC.intValue, preserveDirect = true)
                    return
                }
                // VOD-only compatibility retry: if the provider explicitly
''',
'''                if (liveMode && prefsRef?.getBoolean("live_steady_recovery", false) == true) {
                    StabilityCore.note("steady_player_error_safe code=${error.errorCode} direct=$directLive")
                }
                // VOD-only compatibility retry: if the provider explicitly
''',
'nonrecursive steady player error')

main = once(main,
'''        if (!preserveDirect) {
            directLive = false
            steadyHlsFailed = false
            directUsingHls = false
            // A new viewer-selected channel gets a clean DVR attempt. Failure
''',
'''        if (!preserveDirect) {
            directLive = false
            // A new viewer-selected channel gets a clean DVR attempt. Failure
''',
'reset state cleanup')

main = once(main,
'''        val steadyRecovery = prefsRef?.getBoolean("live_steady_recovery", false) == true
        if (steadyRecovery) directLive = true
        if (directLive || simpleRaw) {
            // Direct from the provider. Steady mode prefers HLS because segmented
            // delivery recovers from overloaded sports servers without one giant
            // socket stalling forever; unsupported HLS falls back to TS on error.
            Timeshift.stop()
            val steadyCandidate = steadyRecovery && !steadyHlsFailed && ch.url.endsWith(".ts", ignoreCase = true)
            directUsingHls = steadyCandidate
            val directUrl = if (steadyCandidate) hlsUrl(ch.url) else tsUrl(ch.url)
            StabilityCore.note(if (steadyCandidate) "steady_live_hls" else "steady_live_ts")
            val item = MediaItem.Builder()
                .setUri(Uri.parse(directUrl))
''',
'''        val steadyRecovery = prefsRef?.getBoolean("live_steady_recovery", false) == true
        if (steadyRecovery) StabilityCore.note("steady_start_safe_ts direct=$directLive simple=$simpleRaw")
        if (directLive || simpleRaw) {
            // Direct fallback always uses the provider's proven TS endpoint. Steady
            // normally stays on the disk-backed timeshift path so it keeps its
            // recovery cushion without guessing that an HLS endpoint exists.
            Timeshift.stop()
            val item = MediaItem.Builder()
                .setUri(Uri.parse(tsUrl(ch.url)))
''',
'safe steady channel start')

MAIN.write_text(main, encoding='utf-8')
GRADLE.write_text(gradle, encoding='utf-8')
print('Applied Zako 4.38 Steady crash hotfix:')
for c in changes: print(' -', c)
