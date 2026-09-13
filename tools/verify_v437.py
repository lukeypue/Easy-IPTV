from pathlib import Path

main = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text()
gradle = Path('app/build.gradle.kts').read_text()
checks = {
    'versionCode 62': 'versionCode = 62' in gradle,
    'versionName 4.37': 'versionName = "4.37"' in gradle,
    'weak channel marker': 'ZAKO_V437_WEAK_CHANNEL_CORE' in main,
    'bounded stream reconnect': 'ZAKO_V437_STREAM_RECONNECT' in main and '.readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)' in main,
    'live edge recovery': 'ZAKO_V437_LIVE_EDGE_RECOVERY' in main and 'ERROR_CODE_BEHIND_LIVE_WINDOW' in main,
    'no speed governor marker': 'ZAKO_V437_NO_SPEED_GOVERNOR' in main,
    'old 0.95 speed hack removed': 'setPlaybackSpeed(0.95f)' not in main,
    'hls helper': 'private fun hlsUrl(url: String)' in main,
    'steady hls fallback state': 'steadyHlsFailed' in main and 'directUsingHls' in main,
    'steady direct hls candidate': 'val steadyCandidate = steadyRecovery && !steadyHlsFailed' in main,
    'hls fallback to same channel': 'zapTo(currentIdxC.intValue, preserveDirect = true)' in main,
    'deeper steady rebuffer': 'steadyRebufferMs' in main and '12_000' in main,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('v4.37 verification failed: ' + ', '.join(failed))
print('Zako 4.37 verification passed')
for name in checks:
    print('PASS', name)
