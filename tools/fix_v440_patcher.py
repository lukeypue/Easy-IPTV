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

# Stage B seek integration: the generated player must seek across whatever
# bytes the rolling ring still retains. This replaces the legacy fixed 45-min
# UI wall while keeping the same bitrate-estimated cable-box controls.
anchor = "MAIN.write_text(main, encoding='utf-8')\n"
if anchor not in t:
    raise SystemExit('v440 final write anchor not found')

seek_patch = r"""
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
"""

t = t.replace(anchor, seek_patch + '\n' + anchor, 1)
p.write_text(t, encoding='utf-8')
print('Fixed v4.40 generator escapes + generated retained-ring DVR seek')
