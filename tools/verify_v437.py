from pathlib import Path

main = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text(encoding='utf-8')
recording = Path('app/src/main/java/com/easyiptv/player/Recording.kt').read_text(encoding='utf-8')
gradle = Path('app/build.gradle.kts').read_text(encoding='utf-8')

checks = {
    'versionCode 62': 'versionCode = 62' in gradle,
    'versionName 4.37': 'versionName = "4.37"' in gradle,
    'bounded Media3 allocator': '.setTargetBufferBytes(32 * 1024 * 1024)' in main,
    'size-aware load control': '.setPrioritizeTimeOverSizeThresholds(false)' in main,
    'steady path no raw Media3 rewind': 'p.seekTo((p.currentPosition - 8_000)' not in main,
    'steady uses packet reopen': 'steady_recovery packet_reopen' in main and 'seekDvrBy(-8_000L)' in main,
    'healthy writer preservation': 'live_player_error code=${error.errorCode}' in main and 'writerHealthy && canSeekDvr()' in main,
    'adaptive DVR prime': 'throughputPrime' in main and 'throughputBps = if (throughputBps <= 0.0) instant' in main,
    'rolling DVR store': 'internal object RollingDvrStore' in main and 'SEGMENT_MS = 60_000L' in main,
    '45 minute time pruning': 'segments[1].startElapsedMs <= cutoff' in main and 'DVR_HISTORY_MS' in main,
    'rolling byte budget': 'total - segments.first().startByte > budgetBytes' in main,
    'absolute byte continuity': 'fun oldestByteOffset()' in main and 'byteOffsetForElapsedMs' in main,
    'localhost reader crosses segments': 'segmentForAbsolute(pos)' in main and 'openFile != seg.file' in main,
    'no normal append-only cap stop': 'while (active && gen == myGen && bytesWritten < capBytes)' not in main,
    'recording tee uses rolling localhost stream': 'Timeshift.teeUrlAtLiveEdge()' in recording,
    'recording tee no direct timeshift RAF': 'RandomAccessFile(src, "r")' not in recording,
}

bad = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL'), name)
if bad:
    raise SystemExit('v4.37 verification failed: ' + ', '.join(bad))
print('Zako 4.37 buffering recovery + rolling DVR contracts verified')
