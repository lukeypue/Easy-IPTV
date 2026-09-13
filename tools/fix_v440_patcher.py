from pathlib import Path

p = Path('tools/apply_v440.py')
t = p.read_text(encoding='utf-8')

# Python re.sub replacement strings interpret backslash escapes. Use a callback
# so Kotlin HTTP CRLF escapes stay literal in generated MainActivity.kt.
old = 'main, n = pattern.subn(ring_runtime, main, count=1)'
new = 'main, n = pattern.subn(lambda _m: ring_runtime, main, count=1)'
if old not in t:
    raise SystemExit('v440 regex replacement target not found')
t = t.replace(old, new, 1)

# Expose a monotonic session generation to readers such as same-channel
# recording. A channel change/stop increments gen and safely ends the old tee.
old_reader_api = '''    fun oldestVirtualByte(): Long = snapshot()?.oldestVirtualByte ?: bytesWritten
    fun newestVirtualByte(): Long = snapshot()?.newestVirtualByte ?: bytesWritten

    fun openReader(virtualOffset: Long): TimeshiftRing.RingReader? ='''
new_reader_api = '''    fun oldestVirtualByte(): Long = snapshot()?.oldestVirtualByte ?: bytesWritten
    fun newestVirtualByte(): Long = snapshot()?.newestVirtualByte ?: bytesWritten
    fun generation(): Long = gen

    fun openReader(virtualOffset: Long): TimeshiftRing.RingReader? ='''
if old_reader_api not in t:
    raise SystemExit('v440 Timeshift reader API target not found')
t = t.replace(old_reader_api, new_reader_api, 1)

# Stage B seek integration: the generated player must seek across whatever
# bytes the rolling ring still retains. This replaces the legacy fixed 45-min
# UI wall while keeping the same bitrate-estimated cable-box controls.
anchor = "MAIN.write_text(main, encoding='utf-8')\n"
if anchor not in t:
    raise SystemExit('v440 final write anchor not found')

stage_patch = r"""
# ---------------------------------------------------------------------------
# Stage B: make remote DVR seek follow the actual retained ring window.
# ---------------------------------------------------------------------------
old_history = 'private const val DVR_HISTORY_MS = 45L * 60L * 1000L\n'
if old_history not in main:
    raise SystemExit('legacy DVR history constant not found')
main = main.replace(
    old_history,
    '// ZAKO_V440_RING_SEEK_WINDOW: seek bounds come from retained ring bytes.\n',
    1,
)

old_seek = '''    fun seekDvrBy(deltaMs: Long): Boolean {
        if (!canSeekDvr()) return false
        val p = player ?: return false
        val rate = dvrBytesPerMs()
        if (rate <= 0.0) return false
        val window = Timeshift.windowMs()
        val current = dvrAbsolutePositionMs()
        // Cable-box style temporary history: expose only the most recent 45
        // minutes even if the append-only USB file happens to contain more.
        // Channel changes already reset Timeshift, matching the expected DVR UX.
        val oldestAllowed = (window - DVR_HISTORY_MS).coerceAtLeast(0L)
        // Stay a fraction behind the byte currently being appended so a forward
        // jump can relock cleanly. If the viewer paused only a few seconds, one
        // FF press still lands essentially at LIVE.
        val liveSafe = (window - 500L).coerceAtLeast(oldestAllowed)
        val targetMs = (current + deltaMs).coerceIn(oldestAllowed, liveSafe)
        var offset = (targetMs * rate).toLong().coerceAtLeast(0L)
        val maxWritten = Timeshift.bytesWritten.coerceAtLeast(0L)
        offset = offset.coerceAtMost(maxWritten)
        offset = (offset / 188L) * 188L
        reopenDvrAtOffset(offset, targetMs)
        return true
    }
'''
new_seek = '''    fun seekDvrBy(deltaMs: Long): Boolean {
        if (!canSeekDvr()) return false
        val p = player ?: return false
        val rate = dvrBytesPerMs()
        if (rate <= 0.0) return false
        val current = dvrAbsolutePositionMs()
        val oldestByte = Timeshift.oldestVirtualByte()
        val newestByte = Timeshift.newestVirtualByte()
        if (newestByte <= oldestByte) return false

        // ZAKO_V440_RING_SEEK_WINDOW: the rewind limit is the oldest segment
        // still retained on USB/internal storage, not a hard-coded 45 minutes.
        // Keep ~500 ms behind the writer tail so Media3 can relock cleanly.
        val liveCushionBytes = (500.0 * rate).toLong().coerceAtLeast(188L)
        val liveSafeByte = (newestByte - liveCushionBytes).coerceAtLeast(oldestByte)
        val requestedByte = ((current + deltaMs).coerceAtLeast(0L) * rate).toLong()
        var offset = requestedByte.coerceIn(oldestByte, liveSafeByte)
        offset = (offset / 188L) * 188L
        if (offset < oldestByte) {
            offset = ((oldestByte + 187L) / 188L) * 188L
            if (offset > liveSafeByte) offset = liveSafeByte
        }
        val targetMs = (offset.toDouble() / rate).toLong().coerceAtLeast(0L)
        reopenDvrAtOffset(offset, targetMs)
        return true
    }
'''
if old_seek not in main:
    raise SystemExit('legacy seekDvrBy block not found')
main = main.replace(old_seek, new_seek, 1)

# The legacy 45-minute constant was also reused by three UI actions (including
# the LIVE button) as a large seek delta. Let those actions use the current
# timeshift session length instead, so they naturally follow USB/internal ring
# retention rather than preserving a hidden 45-minute cap.
remaining_history_refs = main.count('DVR_HISTORY_MS')
if remaining_history_refs != 3:
    raise SystemExit(f'expected 3 remaining DVR_HISTORY_MS refs, found {remaining_history_refs}')
main = main.replace('DVR_HISTORY_MS', 'Timeshift.windowMs()')

# ---------------------------------------------------------------------------
# Stage C: same-channel recording reads the rolling ring at its live edge.
# This consumes zero additional provider connections and follows segment
# rollover using TimeshiftRing.RingReader rather than the removed timeshift.ts.
# ---------------------------------------------------------------------------
recording_path = Path('app/src/main/java/com/easyiptv/player/Recording.kt')
recording = recording_path.read_text(encoding='utf-8')
tee_start = recording.find('    private fun teeFromTimeshift(')
tee_end = recording.find('    private fun beginRecording', tee_start)
if tee_start < 0 or tee_end < 0:
    raise SystemExit('legacy teeFromTimeshift block not found')
legacy_tee = recording[tee_start:tee_end]
if 'Timeshift.file' not in legacy_tee or 'RandomAccessFile' not in legacy_tee:
    raise SystemExit('legacy single-file tee shape changed unexpectedly')

ring_tee = '''    /**
     * ZAKO_V440_RING_RECORDING: record the watched channel from the same rolling
     * DVR ring the player already owns. Start at the current live edge; the
     * RingReader transparently crosses physical segment boundaries and holds a
     * reader lease so an in-use segment cannot be reclaimed underneath us.
     * Returns true only when the live DVR session changed/stopped so the caller
     * can decide whether a direct provider fallback is still appropriate.
     */
    private fun teeFromTimeshift(out: FileOutputStream, stopAt: Long?, isActive: () -> Boolean): Boolean {
        val sessionGen = Timeshift.generation()
        if (!Timeshift.active) return true
        val liveEdge = Timeshift.newestVirtualByte()
        val reader = Timeshift.openReader(liveEdge) ?: return true
        return try {
            reader.use { rr ->
                val buf = ByteArray(64 * 1024)
                var sinceCheck = 0L
                while (isActive() && (stopAt == null || System.currentTimeMillis() < stopAt)) {
                    if (!Timeshift.active || Timeshift.generation() != sessionGen) return true
                    val n = rr.read(buf)
                    if (n > 0) {
                        out.write(buf, 0, n)
                        sinceCheck += n
                        if (sinceCheck > 32_000_000L) {
                            sinceCheck = 0L
                            val freeNow = runCatching {
                                android.os.StatFs(Recorder.recordingsDir(this).absolutePath).availableBytes
                            }.getOrDefault(Long.MAX_VALUE)
                            if (freeNow < 2_000_000_000L) return false
                        }
                    } else if (n == 0 && Timeshift.active && Timeshift.generation() == sessionGen) {
                        Thread.sleep(50)
                    } else {
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            true
        }
    }

'''
recording = recording[:tee_start] + ring_tee + recording[tee_end:]
recording_path.write_text(recording, encoding='utf-8')
"""

t = t.replace(anchor, stage_patch + '\n' + anchor, 1)
p.write_text(t, encoding='utf-8')
print('Fixed v4.40 generator + retained-ring seek + same-channel ring recording')
