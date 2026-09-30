#!/usr/bin/env python3
"""Apply the narrow DVR ownership review fixes after apply_v467_memory.py."""
from pathlib import Path


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise ValueError(f'Expected one 4.67 {label} anchor, found {count}')
    return text.replace(old, new, 1)


def apply(root: Path = Path('.')) -> None:
    source = root / 'app/src/main/java/com/easyiptv/player'
    activity = source / 'MainActivity.kt'
    recording = source / 'Recording.kt'
    main = activity.read_text()
    recorder = recording.read_text()

    # Preparing playback owns a provider slot even though ExoPlayer is IDLE.
    # Keep it through a failed-setup callback, which may open direct rescue.
    main = once(main, '''    @Volatile private var backgroundSuspended = false''', '''    @Volatile private var backgroundSuspended = false
    @Volatile private var liveProviderReserved = false''', 'live provider reservation')
    main = once(main, '''        val ch = q[i]
        currentIdxC.intValue = i''', '''        val ch = q[i]
        liveProviderReserved = true
        currentIdxC.intValue = i''', 'reserve before async tune')
    main = once(main, '''            liveMode = false
            vodUaFallbackUsed = false''', '''            liveMode = false
            liveProviderReserved = false
            vodUaFallbackUsed = false''', 'release live ownership for VOD')
    main = once(main, '''            backgroundSuspended = true
            playbackGen++''', '''            backgroundSuspended = true
            liveProviderReserved = false
            playbackGen++''', 'release suspended live ownership')
    main = once(main, '''        if (player == null || playStateC.intValue == Player.STATE_IDLE) return 0
        return if (liveMode && !simpleRaw && !directLive) {''', '''        if (player == null) return 0
        if (liveProviderReserved) return 1
        if (playStateC.intValue == Player.STATE_IDLE) return 0
        return if (liveMode && !simpleRaw && !directLive) {''', 'count pending provider ownership')
    main = once(main, '''    fun releaseAll() {
        playbackGen++''', '''    fun releaseAll() {
        liveProviderReserved = false
        playbackGen++''', 'release full playback ownership')

    # Generation-tag all accepted clients. Socket.close interrupts blocked
    # header reads and writes; no ring lock or server-start monitor is acquired.
    old = '        old?.call?.cancel()'
    if main.count(old) != 2:
        raise ValueError('Expected two Timeshift cancellation anchors')
    main = main.replace(old, old + '\n        TimeshiftServer.retireClients(generation())')
    main = once(main, '''    private val clients = java.util.concurrent.ConcurrentHashMap.newKeySet<java.net.Socket>()''', '''    private val clients = java.util.concurrent.ConcurrentHashMap<java.net.Socket, Long>()''', 'generation-owned HTTP clients')
    main = once(main, '''                    clients.add(sock)
                    try { handlers.execute { handle(sock) } }''', '''                    val acceptedGeneration = Timeshift.generation()
                    clients[sock] = acceptedGeneration
                    // A tune/stop can race accept and registration. Recheck
                    // after registration so retirement cannot miss this socket.
                    if (acceptedGeneration != Timeshift.generation() || server !== ss) {
                        clients.remove(sock)
                        runCatching { sock.close() }
                        continue
                    }
                    try { handlers.execute { handle(sock, acceptedGeneration) } }''', 'register accepted session')
    main = once(main, '''    private fun handle(sock: java.net.Socket) {
        try {
            sock.tcpNoDelay = true''', '''    fun retireClients(currentGeneration: Long) {
        clients.entries.forEach { (socket, generation) ->
            if (generation != currentGeneration && clients.remove(socket, generation)) {
                runCatching { socket.close() }
            }
        }
    }

    private fun handle(sock: java.net.Socket, acceptedGeneration: Long) {
        try {
            sock.tcpNoDelay = true
            // Incomplete localhost requests must not occupy the bounded reader
            // workers forever. Generation retirement also interrupts writes.
            sock.soTimeout = 2_000''', 'bounded HTTP header read')
    main = once(main, '''            val ringReader = Timeshift.openReader(target, expectedGeneration) ?: return''', '''            if (expectedGeneration != acceptedGeneration) return
            val ringReader = Timeshift.openReader(target, expectedGeneration) ?: return''', 'reject stale accepted request')
    main = once(main, '''            val out = java.io.BufferedOutputStream(sock.getOutputStream())
            out.write(
                ("HTTP/1.1 200 OK\\r\\n" +
                    "Content-Type: video/mp2t\\r\\n" +
                    "Cache-Control: no-store\\r\\n" +
                    "Connection: close\\r\\n\\r\\n").toByteArray()
            )
            out.flush()

            ringReader.use { rr ->''', '''            ringReader.use { rr ->
                // Retirement can close the socket during the response header.
                // Acquire and release the reader around that write too.
                val out = java.io.BufferedOutputStream(sock.getOutputStream())
                out.write(
                    ("HTTP/1.1 200 OK\\r\\n" +
                        "Content-Type: video/mp2t\\r\\n" +
                        "Cache-Control: no-store\\r\\n" +
                        "Connection: close\\r\\n\\r\\n").toByteArray()
                )
                out.flush()
''', 'release reader when response header fails')
    main = once(main, '''        clients.forEach { runCatching { it.close() } }
        clients.clear()''', '''        clients.keys.forEach { runCatching { it.close() } }
        clients.clear()''', 'close mapped clients on full release')

    # Reserve direct recording before posting a takeover, so another UI action
    # cannot claim the released slot before this IO coroutine resumes.
    recorder = once(recorder, '''                    if (needNetwork) {
                        val prefs = getSharedPreferences("easyiptv", Context.MODE_PRIVATE)''', '''                    if (needNetwork) {
                        if (!isActive || (stopAt != null && System.currentTimeMillis() >= stopAt)) return@launch
                        Recorder.usesProviderConnection = true
                        val prefs = getSharedPreferences("easyiptv", Context.MODE_PRIVATE)''', 'reserve direct recording')
    recorder = once(recorder, '''                        if (ProviderStreams.playbackSlots() + 1 > ProviderStreams.max(prefs)) {
                            val latch = java.util.concurrent.CountDownLatch(1)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                Playback.releaseAll()
                                latch.countDown()
                            }
                            runCatching { latch.await(1200, java.util.concurrent.TimeUnit.MILLISECONDS) }
                        }
                        Recorder.usesProviderConnection = true''', '''                        if (ProviderStreams.playbackSlots() + 1 > ProviderStreams.max(prefs)) {
                            val latch = java.util.concurrent.CountDownLatch(1)
                            val released = java.util.concurrent.atomic.AtomicBoolean(false)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                try {
                                    // A cancelled or expired recording must not
                                    // execute a stale takeover when main resumes.
                                    if (isActive && (stopAt == null || System.currentTimeMillis() < stopAt)) {
                                        released.set(runCatching { Playback.releaseAll() }.isSuccess)
                                    }
                                } finally {
                                    latch.countDown()
                                }
                            }
                            // Waiting is on recording IO, never the UI. A slow
                            // main looper is not permission to open a second stream.
                            while (!latch.await(50, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                                if (!isActive || (stopAt != null && System.currentTimeMillis() >= stopAt)) return@launch
                            }
                            if (!released.get()) return@launch
                        }
                        if (!isActive || (stopAt != null && System.currentTimeMillis() >= stopAt)) return@launch''', 'acknowledged cancellable recording takeover')

    # Validate both generated files completely before changing either one.
    activity.write_text(main)
    recording.write_text(recorder)
    print('Applied 4.67 provider ownership and cancellable HTTP reader review fixes')


if __name__ == '__main__':
    apply()
