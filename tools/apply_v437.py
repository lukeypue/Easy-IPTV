from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
RECORDING = Path('app/src/main/java/com/easyiptv/player/Recording.kt')
GRADLE = Path('app/build.gradle.kts')
main = MAIN.read_text(encoding='utf-8')
recording = RECORDING.read_text(encoding='utf-8')
gradle = GRADLE.read_text(encoding='utf-8')
changes = []

def once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1, found {n}')
    changes.append(label)
    return text.replace(old, new, 1)

gradle, n = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 62', gradle, count=1)
if n != 1: raise SystemExit('versionCode')
gradle, n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.37"', gradle, count=1)
if n != 1: raise SystemExit('versionName')
changes += ['versionCode 62', 'versionName 4.37']

main = once(main,
'''            .setTargetBufferBytes(targetBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(true)
''',
'''            // ZAKO_V437_FIRETV_BUFFER_BUDGET: the DVR file is the long cushion.
            // Keep Media3 bounded even when Fire OS does not classify the stick as
            // low-RAM; this avoids duplicate 60-90s memory buffering on ~1 GB units.
            .setTargetBufferBytes(32 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
''',
'bounded Media3 allocator')

timeshift_start = main.index('internal object Timeshift {')
timeshift_end = main.index('\n/* Serves the growing DVR file', timeshift_start)
rolling_timeshift = r'''internal object RollingDvrStore {
    data class SegmentSnapshot(
        val file: File,
        val startByte: Long,
        val endByte: Long,
        val startElapsedMs: Long
    )

    private data class Segment(
        val file: File,
        val startByte: Long,
        val startElapsedMs: Long,
        var endByte: Long
    )

    private const val SEGMENT_MS = 60_000L
    private const val SEGMENT_MAX_BYTES = 96L * 1024L * 1024L
    private val segments = ArrayList<Segment>()
    private var dir: File? = null
    private var out: java.io.FileOutputStream? = null
    private var total: Long = 0L
    private var budgetBytes: Long = 1_000_000_000L
    private var serial: Long = 0L

    @Synchronized
    fun begin(baseDir: File, byteBudget: Long) {
        closeOutput()
        segments.forEach { runCatching { it.file.delete() } }
        segments.clear()
        runCatching {
            baseDir.listFiles()?.filter {
                it.name.startsWith("timeshift-roll-") && it.name.endsWith(".ts")
            }?.forEach { it.delete() }
        }
        dir = baseDir
        budgetBytes = byteBudget.coerceAtLeast(256L * 1024L * 1024L)
        total = 0L
        serial++
        createSegment(0L)
    }

    @Synchronized
    fun reset(deleteFiles: Boolean = true) {
        closeOutput()
        if (deleteFiles) segments.forEach { runCatching { it.file.delete() } }
        segments.clear()
        dir = null
        total = 0L
        serial++
    }

    @Synchronized
    fun sessionSerial(): Long = serial

    @Synchronized
    private fun closeOutput() {
        runCatching { out?.flush() }
        runCatching { out?.close() }
        out = null
    }

    @Synchronized
    private fun createSegment(elapsedMs: Long) {
        val base = dir ?: return
        closeOutput()
        val f = File(base, "timeshift-roll-${serial}-${segments.size}-${System.nanoTime()}.ts")
        runCatching { f.delete() }
        val s = Segment(f, total, elapsedMs.coerceAtLeast(0L), total)
        segments.add(s)
        out = java.io.FileOutputStream(f, true)
    }

    @Synchronized
    fun appendPackets(data: ByteArray, off: Int, len: Int, elapsedMs: Long): Long {
        if (len <= 0) return total
        if (segments.isEmpty()) createSegment(elapsedMs)
        var current = segments.last()
        val currentBytes = current.endByte - current.startByte
        if ((elapsedMs - current.startElapsedMs) >= SEGMENT_MS ||
            currentBytes + len > SEGMENT_MAX_BYTES) {
            createSegment(elapsedMs)
            current = segments.last()
        }
        val stream = out ?: return total
        stream.write(data, off, len)
        total += len.toLong()
        current.endByte = total
        prune(elapsedMs)
        return total
    }

    @Synchronized
    private fun prune(elapsedMs: Long) {
        val cutoff = (elapsedMs - DVR_HISTORY_MS).coerceAtLeast(0L)
        while (segments.size > 1 && segments[1].startElapsedMs <= cutoff) {
            val old = segments.removeAt(0)
            runCatching { old.file.delete() }
        }
        while (segments.size > 1 &&
            total - segments.first().startByte > budgetBytes) {
            val old = segments.removeAt(0)
            runCatching { old.file.delete() }
        }
    }

    @Synchronized
    fun currentFile(): File? = segments.lastOrNull()?.file

    @Synchronized
    fun totalBytes(): Long = total

    @Synchronized
    fun retainedBytes(): Long =
        if (segments.isEmpty()) 0L else (total - segments.first().startByte).coerceAtLeast(0L)

    @Synchronized
    fun oldestByte(): Long = segments.firstOrNull()?.startByte ?: total

    @Synchronized
    fun oldestElapsedMs(): Long = segments.firstOrNull()?.startElapsedMs ?: 0L

    @Synchronized
    fun windowMs(channelElapsedMs: Long): Long =
        (channelElapsedMs - oldestElapsedMs()).coerceIn(0L, DVR_HISTORY_MS)

    @Synchronized
    fun clampAbsolute(position: Long): Long =
        position.coerceIn(oldestByte(), total)

    @Synchronized
    fun segmentForAbsolute(position: Long): SegmentSnapshot? {
        if (segments.isEmpty()) return null
        val p = position.coerceAtLeast(0L)
        val hit = segments.firstOrNull { p >= it.startByte && p < it.endByte }
            ?: if (p == total) segments.lastOrNull() else null
        return hit?.let { SegmentSnapshot(it.file, it.startByte, it.endByte, it.startElapsedMs) }
    }

    @Synchronized
    fun byteForElapsed(targetElapsedMs: Long, channelElapsedMs: Long): Long {
        if (segments.isEmpty()) return 0L
        val oldest = segments.first()
        val target = targetElapsedMs.coerceIn(
            oldest.startElapsedMs,
            channelElapsedMs.coerceAtLeast(oldest.startElapsedMs)
        )
        for (i in segments.indices) {
            val s = segments[i]
            val next = segments.getOrNull(i + 1)
            val endElapsed = next?.startElapsedMs ?: channelElapsedMs.coerceAtLeast(s.startElapsedMs + 1L)
            val endByte = next?.startByte ?: total
            if (target < endElapsed || i == segments.lastIndex) {
                val spanMs = (endElapsed - s.startElapsedMs).coerceAtLeast(1L)
                val spanBytes = (endByte - s.startByte).coerceAtLeast(0L)
                val frac = (target - s.startElapsedMs).coerceIn(0L, spanMs).toDouble() / spanMs.toDouble()
                val raw = s.startByte + (spanBytes.toDouble() * frac).toLong()
                return ((raw / 188L) * 188L).coerceIn(oldest.startByte, total)
            }
        }
        return total
    }

    @Synchronized
    fun patAlignedAbsolute(requested: Long): Long {
        if (segments.isEmpty()) return 0L
        val absolute = clampAbsolute(requested)
        val s = segmentForAbsolute(absolute) ?: return absolute
        val localRequested = (absolute - s.startByte).coerceAtLeast(0L)
        val fileLen = runCatching { s.file.length() }.getOrDefault(0L)
        if (fileLen < 188L * 3L) return ((absolute / 188L) * 188L)
        val scanBytes = 1024L * 1024L
        val scanStart = if (localRequested == 0L) 0L else (localRequested - scanBytes).coerceAtLeast(0L)
        val scanEnd = if (localRequested == 0L) minOf(fileLen, scanBytes)
            else minOf(fileLen, localRequested + 188L)
        val length = (scanEnd - scanStart).coerceAtLeast(0L).toInt()
        if (length < 188) return absolute
        return runCatching {
            java.io.RandomAccessFile(s.file, "r").use { raf ->
                raf.seek(scanStart)
                val b = ByteArray(length)
                val n = raf.read(b)
                if (n < 188) return@use absolute
                var firstPat = -1
                var lastPat = -1
                var i = 0
                while (i + 188 <= n) {
                    if (b[i] == 0x47.toByte()) {
                        val pid = ((b[i + 1].toInt() and 0x1F) shl 8) or (b[i + 2].toInt() and 0xFF)
                        val payloadStart = (b[i + 1].toInt() and 0x40) != 0
                        if (pid == 0 && payloadStart) {
                            if (firstPat < 0) firstPat = i
                            lastPat = i
                        }
                    }
                    i += 188
                }
                val chosen = if (localRequested == 0L) firstPat else lastPat
                if (chosen >= 0) s.startByte + scanStart + chosen else absolute
            }
        }.getOrDefault(absolute)
    }
}

internal object Timeshift {
    @Volatile var bytesWritten: Long = 0L
    @Volatile var active: Boolean = false
    @Volatile var file: File? = null
    @Volatile var startedAtElapsedMs: Long = 0L
        private set
    @Volatile var startedAtWallMs: Long = 0L
        private set

    fun channelElapsedMs(): Long = if (startedAtElapsedMs > 0L)
        (android.os.SystemClock.elapsedRealtime() - startedAtElapsedMs).coerceAtLeast(0L)
    else 0L

    fun windowMs(): Long = RollingDvrStore.windowMs(channelElapsedMs())
    fun oldestElapsedMs(): Long = RollingDvrStore.oldestElapsedMs()
    fun oldestByteOffset(): Long = RollingDvrStore.oldestByte()
    fun byteOffsetForElapsedMs(targetElapsedMs: Long): Long =
        RollingDvrStore.byteForElapsed(targetElapsedMs, channelElapsedMs())
    fun sessionId(): Long = RollingDvrStore.sessionSerial()

    @Volatile var lastByteAt: Long = 0L
    @Volatile var throughputBps: Double = 0.0

    @Volatile private var gen = 0L
    @Volatile private var currentCall: okhttp3.Call? = null
    @Volatile var capBytes: Long = 1_000_000_000L
        private set
    @Volatile var hitCap: Boolean = false
        private set

    private fun findTsSync(b: ByteArray, len: Int): Int {
        var i = 0
        while (i + 376 < len) {
            if (b[i] == 0x47.toByte() && b[i + 188] == 0x47.toByte() && b[i + 376] == 0x47.toByte()) return i
            i++
        }
        return -1
    }

    @Synchronized
    fun start(context: Context, url: String, prefs: SharedPreferences? = null) {
        stopInternal()
        val dir = if (prefs != null) Storage.timeshiftDir(context, prefs) else context.cacheDir
        capBytes = if (prefs != null && Storage.usingDrive(context, prefs)) 3_500_000_000L else 1_000_000_000L
        startedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        startedAtWallMs = System.currentTimeMillis()
        RollingDvrStore.begin(dir, capBytes)
        file = RollingDvrStore.currentFile()
        bytesWritten = 0L
        hitCap = false
        active = true
        lastByteAt = System.currentTimeMillis()
        throughputBps = 0.0
        val myGen = ++gen
        Thread {
            var rateBytes = 0L
            var rateAt = android.os.SystemClock.elapsedRealtime()
            while (active && gen == myGen) {
                try {
                    val req = Request.Builder().url(url).header("User-Agent", Net.UA).build()
                    val c = Net.streamClient.newCall(req)
                    currentCall = c
                    c.execute().use { resp ->
                        if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
                        val inp = resp.body?.byteStream()
                        if (inp != null) {
                            val buf = ByteArray(64 * 1024)
                            var sinceCheck = 0L
                            val carry = ByteArray(188)
                            var carryLen = 0
                            var aligned = false
                            var pend = java.io.ByteArrayOutputStream()

                            fun publishPackets(data: ByteArray, off: Int, len: Int) {
                                if (len <= 0) return
                                val elapsed = channelElapsedMs()
                                bytesWritten = RollingDvrStore.appendPackets(data, off, len, elapsed)
                                file = RollingDvrStore.currentFile()
                                lastByteAt = System.currentTimeMillis()
                                rateBytes += len.toLong()
                                val nowElapsed = android.os.SystemClock.elapsedRealtime()
                                val rateSpan = nowElapsed - rateAt
                                if (rateSpan >= 750L) {
                                    val instant = rateBytes.toDouble() * 1000.0 / rateSpan.toDouble()
                                    throughputBps = if (throughputBps <= 0.0) instant
                                        else throughputBps * 0.72 + instant * 0.28
                                    rateBytes = 0L
                                    rateAt = nowElapsed
                                }
                            }

                            fun writePackets(data: ByteArray, off0: Int, len0: Int) {
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
                                    publishPackets(carry, 0, 188)
                                    carryLen = 0
                                    off += need
                                    len -= need
                                }
                                val whole = (len / 188) * 188
                                if (whole > 0) publishPackets(data, off, whole)
                                val rem = len - whole
                                if (rem > 0) {
                                    System.arraycopy(data, off + whole, carry, 0, rem)
                                    carryLen = rem
                                }
                            }

                            while (active && gen == myGen) {
                                val n = inp.read(buf)
                                if (n < 0) break
                                if (!active || gen != myGen) break
                                if (!aligned) {
                                    pend.write(buf, 0, n)
                                    val pb = pend.toByteArray()
                                    val sync = findTsSync(pb, pb.size)
                                    if (sync >= 0) {
                                        aligned = true
                                        writePackets(pb, sync, pb.size - sync)
                                        pend = java.io.ByteArrayOutputStream()
                                    } else if (pb.size > 8192) {
                                        val keep = pb.copyOfRange(pb.size - 512, pb.size)
                                        pend = java.io.ByteArrayOutputStream()
                                        pend.write(keep)
                                    }
                                    continue
                                }
                                writePackets(buf, 0, n)
                                sinceCheck += n
                                if (sinceCheck > 32_000_000) {
                                    sinceCheck = 0
                                    val free = runCatching {
                                        android.os.StatFs(dir.absolutePath).availableBytes
                                    }.getOrDefault(Long.MAX_VALUE)
                                    if (free < 1_500_000_000L) {
                                        hitCap = true
                                        active = false
                                        StabilityCore.note("dvr_storage_floor free=$free retained=${RollingDvrStore.retainedBytes()}")
                                        break
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (active && gen == myGen) {
                        StabilityCore.note("provider_read_retry type=${e.javaClass.simpleName} msg=${e.message ?: ""}")
                    }
                }
                if (active && gen == myGen) {
                    try { Thread.sleep(1_000) } catch (_: InterruptedException) { break }
                }
            }
            if (gen == myGen) active = false
        }.apply { isDaemon = true; name = "timeshift-writer" }.start()
    }

    fun teeUrlAtLiveEdge(): String? {
        if (!active || TimeshiftServer.port <= 0) return null
        return "http://127.0.0.1:${TimeshiftServer.port}/live/tee-${System.nanoTime()}?offset=$bytesWritten"
    }

    @Synchronized
    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        active = false
        gen++
        bytesWritten = 0L
        hitCap = false
        startedAtElapsedMs = 0L
        startedAtWallMs = 0L
        throughputBps = 0.0
        runCatching { currentCall?.cancel() }
        currentCall = null
        RollingDvrStore.reset(deleteFiles = true)
        file = null
    }
}
'''
main = main[:timeshift_start] + rolling_timeshift + main[timeshift_end:]
changes.append('segmented 45-minute rolling DVR store')

server_start = main.index('private object TimeshiftServer {')
server_end = main.index('\n/* ---------------------------------------------------------------------------\n * PLAYBACK STREAM OWNERSHIP.', server_start)
rolling_server = r'''private object TimeshiftServer {
    @Volatile var port = 0
    private var server: java.net.ServerSocket? = null

    @Synchronized
    fun ensureStarted() {
        if (server != null) return
        val latch = java.util.concurrent.CountDownLatch(1)
        Thread {
            try {
                val ss = java.net.ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))
                server = ss
                port = ss.localPort
                latch.countDown()
                while (true) {
                    val sock = try { ss.accept() } catch (_: Exception) { break }
                    Thread { handle(sock) }.apply { isDaemon = true; name = "tshift-client" }.start()
                }
            } catch (_: Exception) {
                latch.countDown()
            }
        }.apply { isDaemon = true; name = "tshift-server" }.start()
        runCatching { latch.await(2, java.util.concurrent.TimeUnit.SECONDS) }
    }

    private fun handle(sock: java.net.Socket) {
        try {
            sock.tcpNoDelay = true
            val reader = java.io.BufferedReader(java.io.InputStreamReader(sock.getInputStream()))
            val requestLine = reader.readLine() ?: return
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val requested = runCatching {
                val path = requestLine.substringAfter(' ').substringBefore(' ')
                val query = path.substringAfter('?', "")
                query.split('&').firstOrNull { it.startsWith("offset=") }
                    ?.substringAfter('=')?.toLongOrNull() ?: 0L
            }.getOrDefault(0L)

            val session = Timeshift.sessionId()
            var pos = RollingDvrStore.patAlignedAbsolute(
                RollingDvrStore.clampAbsolute(requested)
            )

            val out = java.io.BufferedOutputStream(sock.getOutputStream())
            out.write(buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: video/mp2t\r\n")
                append("Cache-Control: no-store\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray())
            out.flush()

            var raf: java.io.RandomAccessFile? = null
            var openFile: File? = null
            val buf = ByteArray(64 * 1024)
            var idleTicks = 0
            try {
                while (Timeshift.sessionId() == session) {
                    val oldest = RollingDvrStore.oldestByte()
                    if (pos < oldest) {
                        StabilityCore.note("dvr_reader_expired pos=$pos oldest=$oldest")
                        pos = RollingDvrStore.patAlignedAbsolute(oldest)
                        runCatching { raf?.close() }
                        raf = null
                        openFile = null
                    }

                    val seg = RollingDvrStore.segmentForAbsolute(pos)
                    if (seg == null) {
                        if (!Timeshift.active) break
                        Thread.sleep(50)
                        continue
                    }
                    if (openFile != seg.file) {
                        runCatching { raf?.close() }
                        raf = java.io.RandomAccessFile(seg.file, "r")
                        openFile = seg.file
                    }
                    val available = (seg.endByte - pos).coerceAtLeast(0L)
                    if (available > 0L) {
                        idleTicks = 0
                        val local = (pos - seg.startByte).coerceAtLeast(0L)
                        raf?.seek(local)
                        val want = minOf(buf.size.toLong(), available).toInt()
                        val n = raf?.read(buf, 0, want) ?: -1
                        if (n > 0) {
                            out.write(buf, 0, n)
                            out.flush()
                            pos += n.toLong()
                            continue
                        }
                    }
                    if (!Timeshift.active && pos >= RollingDvrStore.totalBytes()) break
                    Thread.sleep(50)
                    if (++idleTicks >= 40) {
                        idleTicks = 0
                        val gone = try {
                            sock.soTimeout = 1
                            sock.getInputStream().read() == -1
                        } catch (_: java.net.SocketTimeoutException) {
                            false
                        } catch (_: Exception) {
                            true
                        }
                        if (gone) break
                    }
                }
            } finally {
                runCatching { raf?.close() }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { sock.close() }
        }
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
        port = 0
    }
}
'''
main = main[:server_start] + rolling_server + main[server_end:]
changes.append('absolute-offset rolling DVR localhost reader')

main = once(main,
'''                                stallRestarts == 0 && !directLive && p.currentPosition > 12_000 -> {
                                    stallRestarts = 1
                                    bufferingSince = 0L; lastBufMs = -1L
                                    p.seekTo((p.currentPosition - 8_000).coerceAtLeast(0))
                                    p.play()
                                }
''',
'''                                stallRestarts == 0 && !directLive && p.currentPosition > 12_000 -> {
                                    stallRestarts = 1
                                    bufferingSince = 0L; lastBufMs = -1L
                                    StabilityCore.note("steady_recovery packet_reopen cushion=${cushionMs}ms")
                                    if (!seekDvrBy(-8_000L)) {
                                        StabilityCore.note("steady_recovery packet_reopen_unavailable")
                                        zapTo(currentIdxC.intValue, preserveDirect = directLive)
                                    }
                                }
''',
'steady recovery uses DVR-safe seek')

old_retry = '''                        if (liveMode) {
                            // Timeshift trouble? After 3 strikes, flip to direct
                            // provider playback so video ALWAYS works.
                            noteLiveFail()
                            zapTo(currentIdxC.intValue, preserveDirect = directLive)
                        } else {
'''
new_retry = '''                        if (liveMode) {
                            val now = System.currentTimeMillis()
                            val writerHealthy = !directLive && Timeshift.active &&
                                Timeshift.bytesWritten > Timeshift.oldestByteOffset() + 188L * 50L &&
                                (now - Timeshift.lastByteAt) < 5_000L
                            StabilityCore.note(
                                "live_player_error code=${error.errorCode} writerHealthy=$writerHealthy direct=$directLive " +
                                    "bytes=${Timeshift.bytesWritten} oldest=${Timeshift.oldestByteOffset()} buffered=${p.totalBufferedDuration}"
                            )
                            if (writerHealthy && canSeekDvr()) {
                                if (!seekDvrBy(-2_000L)) {
                                    noteLiveFail()
                                    zapTo(currentIdxC.intValue, preserveDirect = directLive)
                                }
                            } else {
                                noteLiveFail()
                                zapTo(currentIdxC.intValue, preserveDirect = directLive)
                            }
                        } else {
'''
main = once(main, old_retry, new_retry, 'preserve healthy writer on player error')

main = once(main,
'''                val waited = android.os.SystemClock.elapsedRealtime() - started
                val ready = Timeshift.bytesWritten >= DVR_PRIME_BYTES || waited >= 2_000L
                if (ready) {
''',
'''                val waited = android.os.SystemClock.elapsedRealtime() - started
                val observedBps = Timeshift.throughputBps.coerceAtLeast(0.0)
                val throughputPrime = if (observedBps > 0.0)
                    (observedBps * 2.5).toLong().coerceIn(DVR_PRIME_BYTES, 3L * 1024L * 1024L)
                else DVR_PRIME_BYTES
                val availableBytes = (Timeshift.bytesWritten - Timeshift.oldestByteOffset()).coerceAtLeast(0L)
                val ready = availableBytes >= throughputPrime || waited >= 3_500L
                if (ready) {
                    StabilityCore.note("dvr_prime bytes=$availableBytes target=$throughputPrime bps=${observedBps.toLong()}")
''',
'adaptive DVR prime')

dvr_math_start = main.index('    private fun dvrBytesPerMs(): Double {')
dvr_math_end = main.index('\n    private fun setGrowingDvrSource(', dvr_math_start)
new_dvr_math = r'''    private fun dvrBytesPerMs(): Double {
        val ms = Timeshift.channelElapsedMs()
        val bytes = Timeshift.bytesWritten
        return if (ms >= 2_000L && bytes >= 188L * 20L) bytes.toDouble() / ms.toDouble() else 0.0
    }

    private fun dvrChannelPositionMs(): Long {
        val elapsed = Timeshift.channelElapsedMs()
        val relativeMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
        return (dvrSourceBaseMs + relativeMs).coerceIn(
            Timeshift.oldestElapsedMs(),
            elapsed.coerceAtLeast(Timeshift.oldestElapsedMs())
        )
    }

    fun dvrAbsolutePositionMs(): Long {
        if (!liveMode || simpleRaw || directLive) return player?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val oldest = Timeshift.oldestElapsedMs()
        return (dvrChannelPositionMs() - oldest).coerceIn(0L, Timeshift.windowMs())
    }

    fun canSeekDvr(): Boolean =
        liveMode && !simpleRaw && !directLive && Timeshift.active &&
            Timeshift.windowMs() >= 2_000L && dvrBytesPerMs() > 0.0

    fun seekDvrBy(deltaMs: Long): Boolean {
        if (!canSeekDvr()) return false
        val elapsed = Timeshift.channelElapsedMs()
        val oldestAllowed = Timeshift.oldestElapsedMs()
        val current = dvrChannelPositionMs()
        val liveSafe = (elapsed - 500L).coerceAtLeast(oldestAllowed)
        val targetMs = (current + deltaMs).coerceIn(oldestAllowed, liveSafe)
        var offset = Timeshift.byteOffsetForElapsedMs(targetMs)
        offset = offset.coerceIn(Timeshift.oldestByteOffset(), Timeshift.bytesWritten)
        offset = (offset / 188L) * 188L
        reopenDvrAtOffset(offset, targetMs)
        return true
    }
'''
main = main[:dvr_math_start] + new_dvr_math + main[dvr_math_end:]
changes.append('rolling-window DVR time and seek coordinates')

old_preroll = '''        val prerollMs = 1_500L.coerceAtMost(targetMs)
        val rate = dvrBytesPerMs()
        var safeOffset = offset
        if (rate > 0.0 && prerollMs > 0L) {
            safeOffset = (offset - (prerollMs * rate).toLong()).coerceAtLeast(0L)
            safeOffset = (safeOffset / 188L) * 188L
        }
        setGrowingDvrSource(p, ch, safeOffset, (targetMs - prerollMs).coerceAtLeast(0L), keepPlaying)
'''
new_preroll = '''        val oldestMs = Timeshift.oldestElapsedMs()
        val prerollMs = 1_500L.coerceAtMost((targetMs - oldestMs).coerceAtLeast(0L))
        val baseMs = (targetMs - prerollMs).coerceAtLeast(oldestMs)
        var safeOffset = if (prerollMs > 0L) Timeshift.byteOffsetForElapsedMs(baseMs) else offset
        safeOffset = safeOffset.coerceIn(Timeshift.oldestByteOffset(), Timeshift.bytesWritten)
        safeOffset = (safeOffset / 188L) * 188L
        setGrowingDvrSource(p, ch, safeOffset, baseMs, keepPlaying)
'''
main = once(main, old_preroll, new_preroll, 'variable-bitrate-safe DVR preroll')

main = once(main,
'''        val writerHealthy = Timeshift.active &&
            Timeshift.bytesWritten > 188L * 50L &&
            (System.currentTimeMillis() - Timeshift.lastByteAt) < 5_000L
''',
'''        val writerHealthy = Timeshift.active &&
            (Timeshift.bytesWritten - Timeshift.oldestByteOffset()) > 188L * 50L &&
            (System.currentTimeMillis() - Timeshift.lastByteAt) < 5_000L
''',
'rolling writer health check')

tee_start = recording.index('    private fun teeFromTimeshift(')
tee_end = recording.index('\n    private fun beginRecording(', tee_start)
new_tee = r'''    private fun teeFromTimeshift(out: FileOutputStream, stopAt: Long?, isActive: () -> Boolean): Boolean {
        val local = Timeshift.teeUrlAtLiveEdge() ?: return true
        val session = Timeshift.sessionId()
        var conn: java.net.HttpURLConnection? = null
        return try {
            conn = (java.net.URL(local).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 2_000
                readTimeout = 1_000
                useCaches = false
                setRequestProperty("Connection", "close")
            }
            if (conn.responseCode !in 200..299) return true
            conn.inputStream.use { inp ->
                val buf = ByteArray(64 * 1024)
                var sinceCheck = 0L
                while (isActive() && (stopAt == null || System.currentTimeMillis() < stopAt)) {
                    if (Timeshift.sessionId() != session) return true
                    val n = try {
                        inp.read(buf)
                    } catch (_: java.net.SocketTimeoutException) {
                        continue
                    }
                    if (n < 0) return true
                    if (n == 0) continue
                    out.write(buf, 0, n)
                    sinceCheck += n
                    if (sinceCheck > 32_000_000) {
                        sinceCheck = 0
                        val freeNow = runCatching {
                            android.os.StatFs(Recorder.recordingsDir(this).absolutePath).availableBytes
                        }.getOrDefault(Long.MAX_VALUE)
                        if (freeNow < 2_000_000_000L) return false
                    }
                }
            }
            false
        } catch (_: Exception) {
            true
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
'''
recording = recording[:tee_start] + new_tee + recording[tee_end:]
changes.append('recording tee follows rolling DVR localhost stream')

MAIN.write_text(main, encoding='utf-8')
RECORDING.write_text(recording, encoding='utf-8')
GRADLE.write_text(gradle, encoding='utf-8')
print('Applied Zako 4.37 buffering/DVR stability core:')
for c in changes:
    print(' -', c)
