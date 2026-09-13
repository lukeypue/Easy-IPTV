from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
GRADLE = Path('app/build.gradle.kts')
main = MAIN.read_text()
gradle = GRADLE.read_text()

def once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1, found {n}')
    return text.replace(old, new, 1)

gradle, n = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 62', gradle, count=1)
if n != 1: raise SystemExit('version code')
gradle, n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.37"', gradle, count=1)
if n != 1: raise SystemExit('version name')

main = once(main,
'''private fun tsUrl(url: String): String =
    if (url.endsWith(".m3u8")) url.removeSuffix(".m3u8") + ".ts" else url

private fun liveAutoUrl(prefs: SharedPreferences, url: String): String = url
''',
'''private fun tsUrl(url: String): String =
    if (url.endsWith(".m3u8")) url.removeSuffix(".m3u8") + ".ts" else url

// ZAKO_V437_WEAK_CHANNEL_CORE: Steady mode uses the provider's HLS form when
// available. HLS segment retries tolerate bursty/overloaded sports feeds much
// better than a single long MPEG-TS socket. If HLS is rejected, Playback falls
// back to classic TS automatically for that channel.
private fun hlsUrl(url: String): String = when {
    url.endsWith(".ts", ignoreCase = true) -> url.dropLast(3) + ".m3u8"
    else -> url
}

private fun liveAutoUrl(prefs: SharedPreferences, url: String): String = url
''', 'hls helper')

main = once(main,
'''    @Volatile private var currentCall: okhttp3.Call? = null
    /** Append-only DVR safety cap. Internal eMMC stays deliberately small; a
''',
'''    @Volatile private var currentCall: okhttp3.Call? = null
    // ZAKO_V437_STREAM_RECONNECT: a provider socket that stops sending data must
    // not live forever. Twelve seconds is long enough for the player cushion to
    // bridge a congested server pause, then the writer reconnects cleanly.
    private val liveStreamClient = Net.streamClient.newBuilder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    /** Append-only DVR safety cap. Internal eMMC stays deliberately small; a
''', 'timeshift client')

main = once(main,
'''        val myGen = ++gen
        Thread {
            while (active && gen == myGen && bytesWritten < capBytes) {
                try {
                    val req = Request.Builder().url(url).header("User-Agent", Net.UA).build()
                    val c = Net.streamClient.newCall(req)
                    currentCall = c
                    c.execute().use { resp ->
                        val inp = resp.body?.byteStream()
''',
'''        val myGen = ++gen
        Thread {
            var reconnectDelayMs = 250L
            while (active && gen == myGen && bytesWritten < capBytes) {
                val beforeAttempt = bytesWritten
                try {
                    val req = Request.Builder().url(url).header("User-Agent", Net.UA).build()
                    val c = liveStreamClient.newCall(req)
                    currentCall = c
                    c.execute().use { resp ->
                        if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
                        val inp = resp.body?.byteStream()
''', 'timeshift connect')

main = once(main,
'''                } catch (e: Exception) {
                    // Connection dropped or cancelled — fall through.
                }
                if (active && gen == myGen) {
                    try { Thread.sleep(1_000) } catch (e: InterruptedException) { break }
                }
            }
''',
'''                } catch (e: Exception) {
                    // Connection dropped, timed out, or was cancelled — reconnect below.
                }
                if (bytesWritten > beforeAttempt + 188L * 50L) reconnectDelayMs = 250L
                else reconnectDelayMs = (reconnectDelayMs * 2L).coerceAtMost(2_000L)
                if (active && gen == myGen) {
                    try { Thread.sleep(reconnectDelayMs) } catch (e: InterruptedException) { break }
                }
            }
''', 'timeshift retry')

main = once(main,
'''        val steadyRecovery = prefs.getBoolean("live_steady_recovery", false)
        // ZAKO_V433_BUFFER_CAP: keep the proven startup/rebuffer cushion, but stop
''',
'''        val steadyRecovery = prefs.getBoolean("live_steady_recovery", false)
        val steadyStartMs = if (steadyRecovery) maxOf(lockMs, 6_000) else lockMs
        val steadyRebufferMs = if (steadyRecovery) maxOf(lockMs * 3, 12_000).coerceAtMost(20_000) else lockMs
        // ZAKO_V433_BUFFER_CAP: keep the proven startup/rebuffer cushion, but stop
''', 'steady thresholds')

main = once(main,
'''                lockMs,                                    // collect the chosen cushion before starting
                if (steadyRecovery) (lockMs * 2).coerceAtLeast(6_000) else lockMs
''',
'''                steadyStartMs,                             // steady mode banks a real reserve before picture
                steadyRebufferMs                              // and rebuilds more deeply after a weak-feed stall
''', 'load control steady')

main = once(main,
'''    private var liveFails = 0
    private var retriesP = 0
''',
'''    private var liveFails = 0
    private var retriesP = 0
    @Volatile private var steadyHlsFailed = false
    @Volatile private var directUsingHls = false
''', 'steady state')

main = once(main,
'''            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // VOD-only compatibility retry: if the provider explicitly
''',
'''            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // ZAKO_V437_LIVE_EDGE_RECOVERY: Media3 documents this as the
                // correct recovery when an HLS/live player falls behind the live window.
                if (liveMode && error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    StabilityCore.note("live_edge_recover")
                    p.seekToDefaultPosition()
                    p.prepare()
                    p.playWhenReady = true
                    return
                }
                // Steady weak-channel tries HLS first when the provider exposes
                // an Xtream-style .ts endpoint. If that exact provider rejects or
                // fails HLS, retry the same channel once as classic TS.
                if (liveMode && directUsingHls && !steadyHlsFailed) {
                    StabilityCore.note("steady_hls_fallback")
                    steadyHlsFailed = true
                    directUsingHls = false
                    zapTo(currentIdxC.intValue, preserveDirect = true)
                    return
                }
                // VOD-only compatibility retry: if the provider explicitly
''', 'player error recovery')

main = once(main,
'''                    // Optional cushion assist. Default is NORMAL 1.0x playback:
                    // collect the lock-in buffer once, then leave speed alone.
                    // Customers with especially bursty providers can opt into a
                    // gentle 0.95x refill when the cushion gets dangerously thin.
                    val steadyRecovery = prefsRef?.getBoolean("live_steady_recovery", false) == true
                    if (!steadyRecovery) {
                        if (p.playbackParameters.speed != 1.0f) p.setPlaybackSpeed(1.0f)
                    } else if (p.isPlaying) {
                        val cur = p.playbackParameters.speed
                        if (cushionMs < 4_000 && cur > 0.96f) p.setPlaybackSpeed(0.95f)
                        else if (cushionMs > 6_000 && cur < 1.0f) p.setPlaybackSpeed(1.0f)
                    }
''',
'''                    // ZAKO_V437_NO_SPEED_GOVERNOR: do not manipulate live playback
                    // speed to manufacture a cushion. Keep A/V clocking at 1.0x;
                    // reserve comes from startup/rebuffer thresholds and HLS/direct recovery.
                    val steadyRecovery = prefsRef?.getBoolean("live_steady_recovery", false) == true
                    if (p.playbackParameters.speed != 1.0f) p.setPlaybackSpeed(1.0f)
''', 'remove speed governor')

main = once(main,
'''        if (!preserveDirect) {
            directLive = false
            // A new viewer-selected channel gets a clean DVR attempt. Failure
            // strikes from the previous channel must never carry over.
            liveFails = 0
        }
''',
'''        if (!preserveDirect) {
            directLive = false
            steadyHlsFailed = false
            directUsingHls = false
            // A new viewer-selected channel gets a clean DVR attempt. Failure
            // strikes from the previous channel must never carry over.
            liveFails = 0
        }
''', 'reset hls state')

main = once(main,
'''        dvrSourceOffsetBytes = 0L
        dvrSourceBaseMs = 0L
        if (directLive || simpleRaw) {
            // Direct from the provider: the proven ultra-light path.
            Timeshift.stop()
            val item = MediaItem.Builder()
                .setUri(Uri.parse(tsUrl(ch.url)))
''',
'''        dvrSourceOffsetBytes = 0L
        dvrSourceBaseMs = 0L
        val steadyRecovery = prefsRef?.getBoolean("live_steady_recovery", false) == true
        if (steadyRecovery) directLive = true
        if (directLive || simpleRaw) {
            // Direct from the provider. Steady mode prefers HLS because segmented
            // delivery recovers from overloaded sports servers without one giant
            // socket stalling forever; unsupported HLS falls back to TS on error.
            Timeshift.stop()
            val steadyCandidate = steadyRecovery && !steadyHlsFailed && ch.url.endsWith(".ts", ignoreCase = true)
            directUsingHls = steadyCandidate
            val directUrl = if (steadyCandidate) hlsUrl(ch.url) else tsUrl(ch.url)
            StabilityCore.note(if (steadyCandidate) "steady_live_hls" else "steady_live_ts")
            val item = MediaItem.Builder()
                .setUri(Uri.parse(directUrl))
''', 'steady direct path')

MAIN.write_text(main)
GRADLE.write_text(gradle)
print('Applied Zako 4.37 weak-channel buffering core')
