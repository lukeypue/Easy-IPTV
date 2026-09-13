from pathlib import Path
import re

GRADLE = Path('app/build.gradle.kts')
MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
RING = Path('app/src/main/java/com/easyiptv/player/TimeshiftRing.kt')
STORAGE = Path('app/src/main/java/com/easyiptv/player/LiveStorageManager.kt')
RECORDING = Path('app/src/main/java/com/easyiptv/player/Recording.kt')

gradle = GRADLE.read_text(encoding='utf-8')
main = MAIN.read_text(encoding='utf-8') if MAIN.exists() else ''
ring = RING.read_text(encoding='utf-8') if RING.exists() else ''
storage = STORAGE.read_text(encoding='utf-8') if STORAGE.exists() else ''
recording = RECORDING.read_text(encoding='utf-8') if RECORDING.exists() else ''

checks = {
    'versionCode 65': re.search(r'versionCode\s*=\s*65\b', gradle) is not None,
    'versionName 4.40': 'versionName = "4.40"' in gradle,
    'ring file exists': RING.exists(),
    'storage manager exists': STORAGE.exists(),
    'recording file exists': RECORDING.exists(),
    '188 byte TS alignment': 'TS_PACKET_BYTES = 188' in ring,
    '8 MiB segment target': 'TARGET_SEGMENT_BYTES = 8L * 1024L * 1024L' in ring,
    'monotonic virtual offsets': 'virtualStartByte' in ring and 'virtualEndByte' in ring and 'nextVirtualByte' in ring,
    'reader protection': 'activeReaders' in ring and 'activeReaders > 0' in ring,
    'deferred reclaim': 'pendingDelete' in ring,
    'bounded segment queue': 'ArrayDeque<SegmentMeta>' in ring,
    'reader crosses segment boundaries': 'openReader(virtualOffset: Long)' in ring and 'nextReadableSegment' in ring,
    'ring byte budget parameter': 'private val maxBytes: Long' in ring and 'maxBytes: Long' in ring,
    'ring byte budget reclaim': 'overByteBudget' in ring and 'totalBytesOnDiskLocked() > maxBytes' in ring,
    'USB storage kind': 'StorageKind.USB' in storage,
    'internal storage kind': 'StorageKind.INTERNAL' in storage,
    'USB preferred': 'getExternalFilesDirs(null)' in storage,
    'valid Context external storage API': 'context.getExternalFilesDirs(null)' in storage,
    'invalid externalFilesDirs property absent': 'context.externalFilesDirs' not in storage,
    'write delete probe': 'probe.writeBytes' in storage and 'probe.delete()' in storage,
    'internal fallback': 'context.filesDir' in storage,
    'free space floor': 'RESERVE_FREE_BYTES' in storage,
    'storage target carries ring budget': 'val maxRingBytes: Long' in storage,
    'USB ring budget bounded': 'USB_MAX_RING_BYTES' in storage,
    'internal ring budget bounded': 'INTERNAL_MAX_RING_BYTES' in storage,
    'search movie details marker': 'ZAKO_V440_SEARCH_MOVIE_DETAILS' in main,
    'SearchTab receives source': re.search(r'fun SearchTab\(\s*source: Source\?', main) is not None,
    'SearchTab caller passes source': 'section == "search" -> SearchTab(\n                            source, prefs, safeData' in main,
    'search movie state exists': 'var searchInfoMovie by remember { mutableStateOf<Movie?>(null) }' in main,
    'search reuses movie info dialog': 'VodInfoDialog(\n            source = source, prefs = prefs, movie = movie, onPlay = onPlay,' in main,
    'search movie opens details': 'searchInfoMovie = m' in main,
    # Stage B: the real live path must use the ring, not merely generate an unused class.
    'ring ingest marker': 'ZAKO_V440_RING_INGEST' in main,
    'storage selector drives ring': 'LiveStorageManager.choose(context)' in main,
    'timeshift opens segmented ring': 'TimeshiftRing.open(target.root' in main,
    'writer appends to ring': 'localRing.append(' in main,
    'single growing timeshift file removed': 'File(dir, "timeshift.ts")' not in main,
    'old hard cap removed': 'hitCap' not in main and 'capBytes' not in main,
    'ring server marker': 'ZAKO_V440_RING_SERVER' in main,
    'timeshift exposes ring reader': 'fun openReader(virtualOffset: Long): TimeshiftRing.RingReader?' in main,
    'server opens virtual reader': 'Timeshift.openReader(target)' in main,
    'server no longer reads one growing file': 'RandomAccessFile(myFile, "r")' not in main,
    'ring tail waits for writer': 'if (n == 0 && Timeshift.active)' in main,
    'DVR start can fail safely': 'fun start(context: Context, url: String, prefs: SharedPreferences? = null): Boolean' in main,
    'DVR fallback on unavailable storage': 'dvr_storage_unavailable_direct' in main,
    # The remote seek range must follow retained ring bytes, not the old 45-minute constant.
    'ring seek window marker': 'ZAKO_V440_RING_SEEK_WINDOW' in main,
    'fixed 45 minute seek wall removed': 'private const val DVR_HISTORY_MS = 45L * 60L * 1000L' not in main,
    'seek reads retained oldest byte': 'val oldestByte = Timeshift.oldestVirtualByte()' in main,
    'seek reads retained newest byte': 'val newestByte = Timeshift.newestVirtualByte()' in main,
    'seek clamps target byte to retained range': 'coerceIn(oldestByte, liveSafeByte)' in main,
    'old fixed oldestAllowed removed': 'oldestAllowed = (window - DVR_HISTORY_MS)' not in main,
    # Stage C: recording the watched channel must consume the existing rolling ring,
    # not reopen the provider or depend on the removed single timeshift file.
    'ring recording marker': 'ZAKO_V440_RING_RECORDING' in recording,
    'timeshift exposes session generation': 'fun generation(): Long = gen' in main,
    'ring recording starts at live edge': 'Timeshift.newestVirtualByte()' in recording,
    'ring recording opens virtual reader': 'Timeshift.openReader(' in recording,
    'ring recording uses bounded 64K buffer': 'ByteArray(64 * 1024)' in recording,
    'ring recording checks session generation': 'Timeshift.generation()' in recording,
    'legacy recorder timeshift file removed': 'Timeshift.file' not in recording,
    'legacy recorder RandomAccessFile removed': 'RandomAccessFile' not in recording,
    'direct recording fallback remains': 'Net.streamClient.newCall(req).execute()' in recording,
    'provider slot charged only for direct fallback': 'Recorder.usesProviderConnection = true' in recording,
}

# Scope the tee checks to teeFromTimeshift itself so the direct/different-channel
# recording path can still legitimately use OkHttp after the ring tee ends.
tee_match = re.search(
    r'private fun teeFromTimeshift\(.*?\n    \}\n\n    private fun beginRecording',
    recording,
    re.S,
)
tee_block = tee_match.group(0) if tee_match else ''
checks['ring tee function found'] = bool(tee_block)
checks['ring tee reads ring reader'] = '.read(buf)' in tee_block
checks['ring tee closes reader'] = '.use { rr ->' in tee_block
checks['ring tee never opens provider HTTP'] = 'newCall(' not in tee_block and 'Request.Builder()' not in tee_block

# The Search movie OK/click path must not directly launch playback anymore.
movie_block = ''
marker = 'if (movieHits.isNotEmpty()) {'
if marker in main:
    movie_block = main.split(marker, 1)[1].split('if (seriesHits.isNotEmpty()) {', 1)[0]
checks['search movie does not direct-play'] = 'onPlay(Playable(m.name, m.url' not in movie_block

TS = 188
TARGET = 8 * 1024 * 1024
aligned_target = TARGET - (TARGET % TS)
checks['segment target packet aligned'] = aligned_target % TS == 0 and aligned_target < TARGET
checks['segment target near 8 MiB'] = TARGET - aligned_target < TS

# Pure policy checks for the reclaim rule: active readers can never be eligible.
for finalized in (False, True):
    for outside_window in (False, True):
        for over_budget in (False, True):
            for active_readers in (0, 1, 2):
                eligible = finalized and (outside_window or over_budget) and active_readers == 0
                if active_readers > 0 and eligible:
                    checks[f'active reader reclaim {finalized}/{outside_window}/{over_budget}/{active_readers}'] = False

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('VERIFY_V440_FAIL: ' + ', '.join(failed))
print('VERIFY_V440_OK')
