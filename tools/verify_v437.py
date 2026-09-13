from pathlib import Path

main = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text(encoding='utf-8')
gradle = Path('app/build.gradle.kts').read_text(encoding='utf-8')

checks = {
    'versionCode 62': 'versionCode = 62' in gradle,
    'versionName 4.37': 'versionName = "4.37"' in gradle,
    'bounded Media3 allocator': '.setTargetBufferBytes(32 * 1024 * 1024)' in main,
    'size-aware load control': '.setPrioritizeTimeOverSizeThresholds(false)' in main,
    'steady path no raw Media3 rewind': 'p.seekTo((p.currentPosition - 8_000)' not in main,
    'steady uses packet reopen': 'steady_recovery packet_reopen' in main and 'seekDvrBy(-8_000L)' in main,
    'healthy writer preservation': 'live_player_error code=${error.errorCode}' in main and 'writerHealthy && canSeekDvr()' in main,
    'adaptive DVR prime': 'throughputPrime' in main and 'Timeshift.throughputBps' in main,
}

bad = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL'), name)
if bad:
    raise SystemExit('v4.37 verification failed: ' + ', '.join(bad))
print('Zako 4.37 buffering recovery contracts verified')
