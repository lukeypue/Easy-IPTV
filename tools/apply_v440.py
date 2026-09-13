from pathlib import Path
import re

GRADLE = Path('app/build.gradle.kts')
MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
RING = Path('app/src/main/java/com/easyiptv/player/TimeshiftRing.kt')
STORAGE = Path('app/src/main/java/com/easyiptv/player/LiveStorageManager.kt')
RING_TEMPLATE = Path('tools/v440/TimeshiftRing.kt.template')
STORAGE_TEMPLATE = Path('tools/v440/LiveStorageManager.kt.template')

gradle = GRADLE.read_text(encoding='utf-8')
gradle, n = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 65', gradle, count=1)
if n != 1:
    raise SystemExit('versionCode bump failed')
gradle, n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.40"', gradle, count=1)
if n != 1:
    raise SystemExit('versionName bump failed')
GRADLE.write_text(gradle, encoding='utf-8')

RING.write_text(RING_TEMPLATE.read_text(encoding='utf-8'), encoding='utf-8')
STORAGE.write_text(STORAGE_TEMPLATE.read_text(encoding='utf-8'), encoding='utf-8')

main = MAIN.read_text(encoding='utf-8')

# ---------------------------------------------------------------------------
# Search movie details regression fix.
# ---------------------------------------------------------------------------
old_call = '''section == "search" -> SearchTab(
                            prefs, safeData, searchQuery, onSearchQuery, onPlay, onPlayLive, onSeries,
                            onDemandWarning = if (!catalogLoading) catalogError else null
                        )'''
new_call = '''section == "search" -> SearchTab(
                            source, prefs, safeData, searchQuery, onSearchQuery, onPlay, onPlayLive, onSeries,
                            onDemandWarning = if (!catalogLoading) catalogError else null
                        )'''
if old_call not in main:
    raise SystemExit('SearchTab call target not found')
main = main.replace(old_call, new_call, 1)

old_signature = '''fun SearchTab(
    prefs: SharedPreferences,'''
new_signature = '''fun SearchTab(
    source: Source?,
    prefs: SharedPreferences,'''
if old_signature not in main:
    raise SystemExit('SearchTab signature target not found')
main = main.replace(old_signature, new_signature, 1)

old_state = '''    onDemandWarning: String? = null
) {
    var recents by remember { mutableStateOf(loadRecents(prefs)) }'''
new_state = '''    onDemandWarning: String? = null
) {
    // ZAKO_V440_SEARCH_MOVIE_DETAILS: Search movies use the same details dialog as Movies.
    var searchInfoMovie by remember { mutableStateOf<Movie?>(null) }
    searchInfoMovie?.let { movie ->
        VodInfoDialog(
            source = source, prefs = prefs, movie = movie, onPlay = onPlay,
            onClose = { searchInfoMovie = null }
        )
    }
    var recents by remember { mutableStateOf(loadRecents(prefs)) }'''
if old_state not in main:
    raise SystemExit('SearchTab state target not found')
main = main.replace(old_state, new_state, 1)

old_movie_click = '''                        onClick = {
                            saveRecent(q)
                            onPlay(Playable(m.name, m.url, isLive = false, artwork = m.icon))
                        },'''
new_movie_click = '''                        onClick = {
                            saveRecent(q)
                            searchInfoMovie = m
                        },'''
if old_movie_click not in main:
    raise SystemExit('Search movie click target not found')
main = main.replace(old_movie_click, new_movie_click, 1)

# ---------------------------------------------------------------------------
# Stage B: replace one ever-growing timeshift.ts file with the segmented ring.
# ---------------------------------------------------------------------------
ring_runtime = r'''internal object Timeshift {
    // ZAKO_V440_RING_INGEST: one provider connection writes packet-aligned data
    // into small rolling disk segments. USB is preferred automatically; Android
    // phones and Fire TV without USB use a bounded internal-storage ring.
    @Volatile var bytesWritten: Long = 0L
    @Volatile var active: Boolean = false
    // Kept temporarily for source compatibility with the old recording code.
    // The live player/server no longer read one growing file.
    @Volatile var file: File? = null
    @Volatile var startedAtElapsedMs: Long = 0L
        private set
    @Volatile var startedAtWallMs: Long = 0L
        private set
    @Volatile var lastByteAt: Long = 0L
    @Volatile var throughputBps: Double = 0.0
    @Volatile var storageKind: StorageKind? = null
        private set

    @Volatile private var gen = 0L
    @Volatile private var currentCall: okhttp3.Call? = null
    @Volatile private var ring: TimeshiftRing? = null

    private val liveStreamClient = Net.streamClient.newBuilder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun windowMs(): Long = if (startedAtElapsedMs > 0L)
        (android.os.SystemClock.elapsedRealtime() - startedAtElapsedMs).coerceAtLeast(0L)
    else 0L

    fun snapshot(): TimeshiftRing.RingSnapshot? = runCatching { ring?.snapshot() }.getOrNull()

    fun oldestVirtualByte(): Long = snapshot()?.oldestVirtualByte ?: bytesWritten
    fun newestVirtualByte(): Long = snapshot()?.newestVirtualByte ?: bytesWritten

    fun openReader(virtualOffset: Long): TimeshiftRing.RingReader? =
        runCatching { ring?.openReader(virtualOffset) }.getOrNull()

    /** Find the first verified TS packet boundary in a buffer: three sync bytes
     * exactly 188 bytes apart. */
    private fun findTsSync(b: ByteArray, len: Int): Int {
        var i = 0
        while (i + 376 < len) {
            if (b[i] == 0x47.toByte() && b[i + 188] == 0x47.toByte() && b[i + 376] == 0x47.toByte()) return i
            i++
        }
        return -1
    }

    @Synchronized
    fun start(context: Context, url: String, prefs: SharedPreferences? = null): Boolean {
        stopInternal()
        val target = LiveStorageManager.choose(context) ?: run {
            StabilityCore.note("dvr_storage_unavailable")
            return false
        }
        // A process death can leave old private segments behind. They never
        // belong to a new channel/session, so clean only Zako's own ring files.
        runCatching {
            target.root.listFiles()?.filter { it.name.startsWith("segment-") && it.name.endsWith(".ts") }
                ?.forEach { it.delete() }
        }
        val historyMs = if (target.kind == StorageKind.USB) 8L * 60L * 60L * 1000L else 90L * 60L * 1000L
        val localRing = runCatching {
            TimeshiftRing.open(target.root, historyMs, target.maxRingBytes)
        }.getOrElse {
            StabilityCore.note("dvr_ring_open_failed kind=${target.kind} msg=${it.message ?: ""}")
            return false
        }
        ring = localRing
        storageKind = target.kind
        file = null
        bytesWritten = 0L
        startedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        startedAtWallMs = System.currentTimeMillis()
        active = true
        lastByteAt = System.currentTimeMillis()
        throughputBps = 0.0
        StabilityCore.note("dvr_ring_start kind=${target.kind} budget=${target.maxRingBytes}")
        val myGen = ++gen

        Thread {
            var reconnectDelayMs = 250L
            while (active && gen == myGen) {
                val beforeAttempt = bytesWritten
                var storageFailed = false
                try {
                    val req = Request.Builder().url(url).header("User-Agent", Net.UA).build()
                    val c = liveStreamClient.newCall(req)
                    currentCall = c
                    c.execute().use { resp ->
                        if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
                        val inp = resp.body?.byteStream()
                        if (inp != null) {
                            val buf = ByteArray(64 * 1024)
                            val carry = ByteArray(188)
                            var carryLen = 0
                            var aligned = false
                            var pend = java.io.ByteArrayOutputStream()

                            fun appendToRing(data: ByteArray, off: Int, len: Int) {
                                if (len <= 0) return
                                try {
                                    localRing.append(data, off, len)
                                } catch (e: Exception) {
                                    storageFailed = true
                                    throw e
                                }
                                bytesWritten += len
                                lastByteAt = System.currentTimeMillis()
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
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (storageFailed) {
                        StabilityCore.note("dvr_ring_write_failed kind=${storageKind} msg=${e.message ?: ""}")
                        active = false
                        break
                    }
                    StabilityCore.note("dvr_provider_reconnect msg=${e.message ?: ""}")
                }
                if (bytesWritten > beforeAttempt + 188L * 50L) reconnectDelayMs = 250L
                else reconnectDelayMs = (reconnectDelayMs * 2L).coerceAtMost(2_000L)
                if (active && gen == myGen) {
                    try { Thread.sleep(reconnectDelayMs) } catch (_: InterruptedException) { break }
                }
            }
            if (gen == myGen) active = false
        }.apply { isDaemon = true; name = "timeshift-ring-writer" }.start()
        return true
    }

    @Synchronized
    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        active = false
        gen++
        runCatching { currentCall?.cancel() }
        currentCall = null
        val oldRing = ring
        ring = null
        runCatching { oldRing?.close() }
        bytesWritten = 0L
        file = null
        storageKind = null
        startedAtElapsedMs = 0L
        startedAtWallMs = 0L
    }
}

// ZAKO_V440_RING_SERVER: localhost playback follows the virtual byte timeline
// across physical segment boundaries. Reclaimed history is clamped by RingReader
// and reaching the live tail waits for more bytes instead of rebuilding player.
private object TimeshiftServer {
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
                    Thread { handle(sock) }.apply { isDaemon = true }.start()
                }
            } catch (_: Exception) {
                latch.countDown()
            }
        }.apply { isDaemon = true; name = "tshift-ring-server" }.start()
        runCatching { latch.await(2, java.util.concurrent.TimeUnit.SECONDS) }
    }

    private fun handle(sock: java.net.Socket) {
        try {
            sock.tcpNoDelay = true
            val request = java.io.BufferedReader(java.io.InputStreamReader(sock.getInputStream()))
            val requestLine = request.readLine() ?: return
            while (true) {
                val line = request.readLine() ?: break
                if (line.isEmpty()) break
            }
            val target = runCatching {
                val path = requestLine.substringAfter(' ').substringBefore(' ')
                val query = path.substringAfter('?', "")
                query.split('&').firstOrNull { it.startsWith("offset=") }
                    ?.substringAfter('=')?.toLongOrNull() ?: 0L
            }.getOrDefault(0L)

            val ringReader = Timeshift.openReader(target) ?: return
            val out = java.io.BufferedOutputStream(sock.getOutputStream())
            out.write(
                ("HTTP/1.1 200 OK\r\n" +
                    "Content-Type: video/mp2t\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "Connection: close\r\n\r\n").toByteArray()
            )
            out.flush()

            ringReader.use { rr ->
                val buf = ByteArray(64 * 1024)
                var idleTicks = 0
                while (true) {
                    val n = rr.read(buf)
                    if (n > 0) {
                        idleTicks = 0
                        out.write(buf, 0, n)
                        out.flush()
                    } else if (n == 0 && Timeshift.active) {
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
                    } else {
                        break
                    }
                }
            }
        } catch (_: Exception) {
            // Client hung up, channel changed, or storage disappeared.
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

/* ---------------------------------------------------------------------------
 * PLAYBACK STREAM OWNERSHIP.
 * There is still exactly ONE ExoPlayer. The viewer chooses an IPTV-provider
 * connection budget of 1, 2, or 3 in Settings. Ordinary playback consumes one
 * remote slot; same-channel DVR recording tees from the existing timeshift and
 * costs zero extra; only independent recording/download requests consume extra
 * provider connections. Full-screen and corner views share the same player.
 * ------------------------------------------------------------------------- */
@OptIn(UnstableApi::class)
object Playback {'''

pattern = re.compile(
    r'internal object Timeshift \{.*?@OptIn\(UnstableApi::class\)\nobject Playback \{',
    re.S,
)
main, n = pattern.subn(ring_runtime, main, count=1)
if n != 1:
    raise SystemExit(f'Timeshift/Server replacement failed: {n}')

# Remove the obsolete append-only-file cap rescue from the governor. The ring
# now rolls old segments continuously instead of ever reaching a hard cap.
cap_block = re.compile(
    r'''\s*// The append-only DVR must never turn into a hard stop\..*?\n\s*if \(!directLive && Timeshift\.hitCap\) \{.*?\n\s*\}\n''',
    re.S,
)
main, n = cap_block.subn('\n', main, count=1)
if n != 1:
    raise SystemExit(f'old hitCap governor block not found: {n}')

# If neither USB nor safe internal space can host the ring, stay alive by using
# the already-proven direct/Smooth provider path for that channel.
old_start = '''            TimeshiftServer.ensureStarted()
            Timeshift.start(ctx, tsUrl(ch.url), prefsRef)
            // Do not hand Media3 an empty just-created file. Bank a small TS
            // prefix first, then start the dedicated growing-DVR extractor path.
            p.stop()
            p.clearMediaItems()
            val myGen = playbackGen
            startDvrWhenPrimed(p, ch, myGen, keepPlaying = true)'''
new_start = '''            TimeshiftServer.ensureStarted()
            val dvrStarted = Timeshift.start(ctx, tsUrl(ch.url), prefsRef)
            if (!dvrStarted) {
                StabilityCore.note("dvr_storage_unavailable_direct")
                directLive = true
                val item = MediaItem.Builder()
                    .setUri(Uri.parse(tsUrl(ch.url)))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(ch.name).build())
                    .build()
                p.setMediaItem(item)
                p.prepare()
                p.playWhenReady = true
            } else {
                // Bank a small TS prefix before handing the localhost ring tail to Media3.
                p.stop()
                p.clearMediaItems()
                val myGen = playbackGen
                startDvrWhenPrimed(p, ch, myGen, keepPlaying = true)
            }'''
if old_start not in main:
    raise SystemExit('DVR start/fallback target not found')
main = main.replace(old_start, new_start, 1)

MAIN.write_text(main, encoding='utf-8')
print('Applied Zako 4.40 rolling ring + virtual reader + Search movie details')
