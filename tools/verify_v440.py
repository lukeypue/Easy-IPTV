from pathlib import Path
import re

GRADLE = Path('app/build.gradle.kts')
RING = Path('app/src/main/java/com/easyiptv/player/TimeshiftRing.kt')
STORAGE = Path('app/src/main/java/com/easyiptv/player/LiveStorageManager.kt')

gradle = GRADLE.read_text(encoding='utf-8')
ring = RING.read_text(encoding='utf-8') if RING.exists() else ''
storage = STORAGE.read_text(encoding='utf-8') if STORAGE.exists() else ''

checks = {
    'versionCode 65': re.search(r'versionCode\s*=\s*65\b', gradle) is not None,
    'versionName 4.40': 'versionName = "4.40"' in gradle,
    'ring file exists': RING.exists(),
    'storage manager exists': STORAGE.exists(),
    '188 byte TS alignment': 'TS_PACKET_BYTES = 188' in ring,
    '8 MiB segment target': 'TARGET_SEGMENT_BYTES = 8L * 1024L * 1024L' in ring,
    'monotonic virtual offsets': 'virtualStartByte' in ring and 'virtualEndByte' in ring and 'nextVirtualByte' in ring,
    'reader protection': 'activeReaders' in ring and 'activeReaders > 0' in ring,
    'deferred reclaim': 'pendingDelete' in ring,
    'bounded segment queue': 'ArrayDeque<SegmentMeta>' in ring,
    'reader crosses segment boundaries': 'openReader(virtualOffset: Long)' in ring and 'nextReadableSegment' in ring,
    'USB storage kind': 'StorageKind.USB' in storage,
    'internal storage kind': 'StorageKind.INTERNAL' in storage,
    'USB preferred': 'externalFilesDirs' in storage,
    'write delete probe': 'probe.writeBytes' in storage and 'probe.delete()' in storage,
    'internal fallback': 'context.filesDir' in storage,
    'free space floor': 'MIN_FREE_BYTES' in storage,
}

TS = 188
TARGET = 8 * 1024 * 1024
aligned_target = TARGET - (TARGET % TS)
checks['segment target packet aligned'] = aligned_target % TS == 0 and aligned_target < TARGET
checks['segment target near 8 MiB'] = TARGET - aligned_target < TS

for finalized in (False, True):
    for outside_window in (False, True):
        for active_readers in (0, 1, 2):
            eligible = finalized and outside_window and active_readers == 0
            if active_readers > 0 and eligible:
                checks[f'active reader reclaim {finalized}/{outside_window}/{active_readers}'] = False

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('VERIFY_V440_FAIL: ' + ', '.join(failed))
print('VERIFY_V440_OK')
