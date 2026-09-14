from pathlib import Path
import re

GRADLE = Path('app/build.gradle.kts')
MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')

gradle = GRADLE.read_text(encoding='utf-8')
gradle, n = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 66', gradle, count=1)
if n != 1:
    raise SystemExit('versionCode bump failed')
gradle, n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.41"', gradle, count=1)
if n != 1:
    raise SystemExit('versionName bump failed')
GRADLE.write_text(gradle, encoding='utf-8')

main = MAIN.read_text(encoding='utf-8')

# ---------------------------------------------------------------------------
# Search visual parity: Live, Movies and Series all use the same poster-store
# presentation in Search. Movies still open the existing movie details dialog.
# ---------------------------------------------------------------------------
search_pattern = re.compile(
    r'''            if \(liveHits\.isNotEmpty\(\)\) \{.*?            if \(seriesHits\.isNotEmpty\(\)\) \{''',
    re.S,
)
search_replacement = '''            // ZAKO_V441_SEARCH_VISUAL_PARITY: Search now looks like the Movies/Series shelves.
            if (liveHits.isNotEmpty()) {
                item { SectionHeader("Live TV") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(liveHits) { ch ->
                            PosterCard(ch.name, ch.icon) { saveRecent(q); playLiveHit(ch) }
                        }
                    }
                }
            }
            if (movieHits.isNotEmpty()) {
                item { SectionHeader("Movies • title / cast / director / genre") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(movieHits) { m ->
                            PosterCard(m.name, m.icon) {
                                saveRecent(q)
                                searchInfoMovie = m
                            }
                        }
                    }
                }
            }
            if (seriesHits.isNotEmpty()) {'''
main, n = search_pattern.subn(search_replacement, main, count=1)
if n != 1:
    raise SystemExit(f'Search Live/Movie shelf replacement failed: {n}')

# ---------------------------------------------------------------------------
# Weak-channel diagnostics + packet-boundary recovery.
# Root cause hypothesis from 4.40: once the TS writer gets aligned, it assumes
# every later 188-byte boundary remains valid until reconnect. Weak provider
# streams can inject a malformed/truncated TS packet without closing TCP. That
# leaves fast-motion video visibly blocky until the decoder catches up. Validate
# every packet boundary and re-acquire sync instead of feeding corrupt framing to
# Media3. Also count input stalls/reconnects so the next device test gives us
# evidence rather than guesses.
# ---------------------------------------------------------------------------
old_metrics = '''    @Volatile var throughputBps: Double = 0.0
    @Volatile var storageKind: StorageKind? = null'''
new_metrics = '''    @Volatile var throughputBps: Double = 0.0
    // ZAKO_V441_WEAK_CHANNEL_DIAGNOSTICS
    @Volatile var weakGapEvents: Long = 0L
    @Volatile var syncResyncEvents: Long = 0L
    @Volatile var reconnectEvents: Long = 0L
    @Volatile var storageKind: StorageKind? = null'''
if old_metrics not in main:
    raise SystemExit('Timeshift diagnostics field target not found')
main = main.replace(old_metrics, new_metrics, 1)

old_reset = '''        lastByteAt = System.currentTimeMillis()
        throughputBps = 0.0
        StabilityCore.note("dvr_ring_start kind=${target.kind} budget=${target.maxRingBytes}")'''
new_reset = '''        lastByteAt = System.currentTimeMillis()
        throughputBps = 0.0
        weakGapEvents = 0L
        syncResyncEvents = 0L
        reconnectEvents = 0L
        StabilityCore.note("dvr_ring_start kind=${target.kind} budget=${target.maxRingBytes}")'''
if old_reset not in main:
    raise SystemExit('Timeshift diagnostics reset target not found')
main = main.replace(old_reset, new_reset, 1)

old_vars = '''                            var carryLen = 0
                            var aligned = false
                            var pend = java.io.ByteArrayOutputStream()

                            fun appendToRing(data: ByteArray, off: Int, len: Int) {'''
new_vars = '''                            var carryLen = 0
                            var aligned = false
                            var pend = java.io.ByteArrayOutputStream()
                            var lastChunkAt = android.os.SystemClock.elapsedRealtime()

                            fun appendToRing(data: ByteArray, off: Int, len: Int) {'''
if old_vars not in main:
    raise SystemExit('Timeshift local vars target not found')
main = main.replace(old_vars, new_vars, 1)

old_write_packets = '''                            fun writePackets(data: ByteArray, off0: Int, len0: Int) {
                                var off = off0
                                var len = len0
                                if (carryLen > 0) {
                                    val need = 188 - carryLen
                                    if (len < need) {
                                        System.arraycopy(data, off, carry, carryLen, len)
                                        carryLen += len
                                        return
                                    }
                                    System.arraycopy(data, off, carry, carryLen, need)
                                    appendToRing(carry, 0, 188)
                                    carryLen = 0
                                    off += need
                                    len -= need
                                }
                                val whole = (len / 188) * 188
                                if (whole > 0) appendToRing(data, off, whole)
                                val rem = len - whole
                                if (rem > 0) {
                                    System.arraycopy(data, off + whole, carry, 0, rem)
                                    carryLen = rem
                                }
                            }'''
new_write_packets = '''                            fun writePackets(data: ByteArray, off0: Int, len0: Int) {
                                // ZAKO_V441_TS_RESYNC: never assume a weak TS feed stays aligned forever.
                                // If one expected packet boundary loses 0x47, stop feeding corrupt framing,
                                // preserve the remaining bytes, and let the existing three-sync detector
                                // re-lock on the next read.
                                var off = off0
                                var len = len0
                                if (carryLen > 0) {
                                    val need = 188 - carryLen
                                    if (len < need) {
                                        System.arraycopy(data, off, carry, carryLen, len)
                                        carryLen += len
                                        return
                                    }
                                    System.arraycopy(data, off, carry, carryLen, need)
                                    if (carry[0] != 0x47.toByte()) {
                                        syncResyncEvents++
                                        StabilityCore.note("dvr_ts_sync_lost source=carry count=$syncResyncEvents")
                                        aligned = false
                                        pend = java.io.ByteArrayOutputStream()
                                        pend.write(carry, 0, 188)
                                        pend.write(data, off + need, len - need)
                                        carryLen = 0
                                        return
                                    }
                                    appendToRing(carry, 0, 188)
                                    carryLen = 0
                                    off += need
                                    len -= need
                                }
                                val whole = (len / 188) * 188
                                if (whole > 0) {
                                    var badAt = -1
                                    var pos = off
                                    val end = off + whole
                                    while (pos < end) {
                                        if (data[pos] != 0x47.toByte()) {
                                            badAt = pos
                                            break
                                        }
                                        pos += 188
                                    }
                                    if (badAt >= 0) {
                                        val goodLen = badAt - off
                                        if (goodLen > 0) appendToRing(data, off, goodLen)
                                        syncResyncEvents++
                                        StabilityCore.note("dvr_ts_sync_lost source=body count=$syncResyncEvents")
                                        aligned = false
                                        pend = java.io.ByteArrayOutputStream()
                                        pend.write(data, badAt, (off + len) - badAt)
                                        carryLen = 0
                                        return
                                    }
                                    appendToRing(data, off, whole)
                                }
                                val rem = len - whole
                                if (rem > 0) {
                                    System.arraycopy(data, off + whole, carry, 0, rem)
                                    carryLen = rem
                                }
                            }'''
if old_write_packets not in main:
    raise SystemExit('writePackets target not found')
main = main.replace(old_write_packets, new_write_packets, 1)

old_read = '''                                val n = inp.read(buf)
                                if (n < 0) break
                                if (!active || gen != myGen) break
                                if (!aligned) {'''
new_read = '''                                val n = inp.read(buf)
                                if (n < 0) break
                                if (!active || gen != myGen) break
                                val nowChunkAt = android.os.SystemClock.elapsedRealtime()
                                val inputGapMs = nowChunkAt - lastChunkAt
                                if (inputGapMs >= 750L) {
                                    weakGapEvents++
                                    StabilityCore.note("dvr_input_gap ms=$inputGapMs count=$weakGapEvents bytes=$bytesWritten")
                                }
                                lastChunkAt = nowChunkAt
                                if (!aligned) {'''
if old_read not in main:
    raise SystemExit('input gap target not found')
main = main.replace(old_read, new_read, 1)

old_reconnect = '''                    StabilityCore.note("dvr_provider_reconnect msg=${e.message ?: ""}")'''
new_reconnect = '''                    reconnectEvents++
                    StabilityCore.note("dvr_provider_reconnect count=$reconnectEvents gaps=$weakGapEvents resyncs=$syncResyncEvents msg=${e.message ?: ""}")'''
if old_reconnect not in main:
    raise SystemExit('reconnect diagnostic target not found')
main = main.replace(old_reconnect, new_reconnect, 1)

# Steady mode is explicitly for weak feeds. Bank a real four-second cushion before
# attaching Media3 to the rolling ring. Natural mode keeps its existing behavior.
old_prime = '''                val waited = android.os.SystemClock.elapsedRealtime() - started
                val ready = Timeshift.bytesWritten >= DVR_PRIME_BYTES || waited >= 2_000L
                if (ready) {'''
new_prime = '''                val waited = android.os.SystemClock.elapsedRealtime() - started
                // ZAKO_V441_STEADY_CUSHION: weak-channel mode trades a few seconds of
                // tune latency for enough retained video to ride through short provider stalls.
                val steadyRecovery = prefsRef?.getBoolean("live_steady_recovery", false) == true
                val ready = if (steadyRecovery) {
                    (waited >= 4_000L && Timeshift.bytesWritten >= DVR_PRIME_BYTES) || waited >= 7_000L
                } else {
                    Timeshift.bytesWritten >= DVR_PRIME_BYTES || waited >= 2_000L
                }
                if (ready) {
                    if (steadyRecovery) StabilityCore.note(
                        "steady_prime waited=$waited bytes=${Timeshift.bytesWritten} gaps=${Timeshift.weakGapEvents} resyncs=${Timeshift.syncResyncEvents} reconnects=${Timeshift.reconnectEvents}"
                    )'''
if old_prime not in main:
    raise SystemExit('steady prime target not found')
main = main.replace(old_prime, new_prime, 1)

MAIN.write_text(main, encoding='utf-8')
print('Applied Zako 4.41 Search shelves + weak-channel TS recovery/diagnostics')
