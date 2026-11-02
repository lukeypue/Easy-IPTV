package com.easyiptv.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reflection keeps the pre-implementation RED run compilable: absence is an assertion failure. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StreamHealthTest {
    private var now = 0L
    private val remote = DataSpec(Uri.parse("https://example.test/live"))

    private fun type(name: String): Class<*> {
        val found = runCatching { Class.forName("com.easyiptv.player.$name") }.getOrNull()
        assertNotNull("Missing event-driven stream health API: $name", found)
        return found!!
    }
    private fun meter(): Any = type("StreamHealthMeter")
        .getDeclaredConstructor(kotlin.jvm.functions.Function0::class.java)
        .apply { isAccessible = true }.newInstance({ now })
    private fun call(target: Any, name: String): Any? = target.javaClass
        .getDeclaredMethod(name).apply { isAccessible = true }.invoke(target)
    private fun listener(m: Any) = call(m, "newTransferListener") as TransferListener
    private fun snapshot(m: Any) = call(m, "snapshot")!!
    private fun field(target: Any, name: String): Any? = target.javaClass
        .getDeclaredField(name).apply { isAccessible = true }.get(target)
    private fun number(target: Any, name: String) = (field(target, name) as Number).toLong()
    private fun start(l: TransferListener, s: DataSource, spec: DataSpec = remote, network: Boolean = true) =
        l.onTransferStart(s, spec, network)
    private fun bytes(l: TransferListener, s: DataSource, count: Int, spec: DataSpec = remote, network: Boolean = true) =
        l.onBytesTransferred(s, spec, network, count)

    private class Source(private val uri: Uri? = Uri.parse("https://example.test/live")) : DataSource {
        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long = 0
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = -1
        override fun getUri(): Uri? = uri
        override fun close() = Unit
    }

    @Test fun rateNeedsThreeSecondsAndIncludesIdleWallTime() {
        val m = meter(); val l = listener(m); val s = Source()
        start(l, s); bytes(l, s, 750_000)
        now = 2_999
        assertNull(field(snapshot(m), "receivedBitsPerSecond"))
        now = 3_000
        val snap = snapshot(m)
        assertEquals(2_000_000L, field(snap, "receivedBitsPerSecond"))
        assertEquals(750_000L, number(snap, "bytesReceived"))
        assertEquals(3_000L, field(snap, "sinceLastByteMs"))
        assertEquals(3_000L, number(snap, "sampledForMs"))
        now = 6_000
        assertEquals(1_000_000L, field(snapshot(m), "receivedBitsPerSecond"))
    }

    @Test fun rollingWindowExpiresOldBucketsButPreservesSessionByteTotal() {
        val m = meter(); val l = listener(m); val s = Source()
        start(l, s); bytes(l, s, 1_000)
        now = 1_000; bytes(l, s, 2_000)
        now = 11_000
        assertEquals(1_600L, field(snapshot(m), "receivedBitsPerSecond"))
        assertEquals(3_000L, number(snapshot(m), "bytesReceived"))
        now = 12_000
        assertEquals(0L, field(snapshot(m), "receivedBitsPerSecond"))
        assertEquals(12_000L, number(snapshot(m), "sampledForMs"))
    }

    @Test fun activeTransfersAreDistinctAndEndedSourcesCannotDeliverMoreBytes() {
        val m = meter(); val l = listener(m); val a = Source(); val b = Source()
        start(l, a); start(l, a); start(l, b)
        assertEquals(2L, number(snapshot(m), "activeTransfers"))
        bytes(l, a, -10); bytes(l, a, 0)
        assertNull(field(snapshot(m), "sinceLastByteMs"))
        bytes(l, a, 100); bytes(l, b, 200)
        l.onTransferEnd(a, remote, true); l.onTransferEnd(a, remote, true)
        bytes(l, a, 900)
        assertEquals(1L, number(snapshot(m), "activeTransfers"))
        assertEquals(300L, number(snapshot(m), "bytesReceived"))
        l.onTransferEnd(b, remote, true)
        assertEquals(0L, number(snapshot(m), "activeTransfers"))
    }

    @Test fun resetRejectsEveryOldListenerCallbackIncludingLateStart() {
        val m = meter(); val old = listener(m); val s = Source()
        start(old, s); bytes(old, s, 100)
        now = 8_000; call(m, "reset")
        start(old, s); bytes(old, s, 1_000); old.onTransferEnd(s, remote, true)
        val snap = snapshot(m)
        assertEquals(0L, number(snap, "bytesReceived"))
        assertEquals(0L, number(snap, "activeTransfers"))
        assertEquals(0L, number(snap, "sampledForMs"))
        assertNull(field(snap, "sinceLastByteMs"))
        assertNull(field(snap, "receivedBitsPerSecond"))
        val fresh = listener(m); start(fresh, s); bytes(fresh, s, 50)
        old.onTransferEnd(s, remote, true)
        assertEquals(1L, number(snapshot(m), "activeTransfers"))
        assertEquals(50L, number(snapshot(m), "bytesReceived"))
    }

    @Test fun excludesLocalNonHttpAndNonNetworkTransfers() {
        val m = meter(); val l = listener(m)
        for (url in listOf("http://127.0.0.1/live", "https://127.8.9.10/live", "http://localhost/live",
            "http://LOCALHOST/live", "http://[::1]/live", "file:///saved.ts", "rtsp://example.test/live")) {
            val spec = DataSpec(Uri.parse(url)); val s = Source(spec.uri)
            start(l, s, spec); bytes(l, s, 100, spec)
        }
        val s = Source(); start(l, s, network = false); bytes(l, s, 100, network = false)
        assertEquals(0L, number(snapshot(m), "bytesReceived"))
        assertEquals(0L, number(snapshot(m), "activeTransfers"))
    }

    @Test fun nullSourceUriSafelyUsesHttpDataSpecAndInitializationDoesNotActivate() {
        val m = meter(); val l = listener(m); val s = Source(null)
        l.onTransferInitializing(s, remote, true)
        bytes(l, s, 100)
        assertEquals(0L, number(snapshot(m), "activeTransfers"))
        assertEquals(0L, number(snapshot(m), "bytesReceived"))
        start(l, s); bytes(l, s, 200)
        assertEquals(200L, number(snapshot(m), "bytesReceived"))
    }

    @Test fun localhostRedirectIsExcludedEvenWhenRequestedUriWasRemote() {
        val m = meter(); val l = listener(m); val s = Source(Uri.parse("http://127.0.0.1/saved"))
        start(l, s); bytes(l, s, 100)
        assertEquals(0L, number(snapshot(m), "bytesReceived"))
    }

    @Test fun manyCallbacksInOneSecondAndLongSessionsKeepRollingTotalsCorrect() {
        val m = meter(); val l = listener(m); val s = Source(); start(l, s)
        repeat(120) { second ->
            now = second * 1_000L
            repeat(1_000) { bytes(l, s, 1) }
        }
        now = 120_000
        val snap = snapshot(m)
        assertEquals(120_000L, number(snap, "bytesReceived"))
        assertEquals(8_000L, field(snap, "receivedBitsPerSecond"))
    }

    private fun assess(
        play: Boolean = true, buffering: Boolean = false, loading: Boolean = false,
        buffer: Long = 5_000, rate: Long? = 6_000_000, total: Long = 1_000,
        since: Long? = 0, active: Int = 1, sampled: Long = 10_000,
        bitrate: Long? = 6_000_000, dropped: Int = 0, rendered: Int = 100,
        decoder: Boolean = false, local: Boolean = false,
    ): Any {
        val snap = type("StreamTransferSnapshot").declaredConstructors.single { it.parameterCount == 5 }
            .apply { isAccessible = true }.newInstance(rate, total, since, active, sampled)
        val input = type("StreamHealthInput").declaredConstructors.single { it.parameterCount == 10 }
            .apply { isAccessible = true }
            .newInstance(play, buffering, loading, buffer, snap, bitrate, dropped, rendered, decoder, local)
        return type("StreamHealthKt").declaredMethods.single { it.name == "assessStreamHealth" && it.parameterCount == 1 }
            .apply { isAccessible = true }.invoke(null, input)
    }
    private fun title(a: Any) = field(a, "title") as String
    private fun detail(a: Any) = (field(a, "detail") as String).lowercase()

    @Test fun pauseAndSavedPlaybackTakePriorityOverNetworkAlarms() {
        assertEquals("Paused", title(assess(play = false, buffering = true, decoder = true)))
        val saved = assess(local = true, buffering = true, rate = 0, since = 60_000)
        assertEquals("Playing saved pause video", title(saved))
        assertTrue(detail(saved).contains("network") && detail(saved).contains("unavailable"))
    }

    @Test fun decoderProblemDoesNotDefinitivelyBlameHardware() {
        val result = assess(decoder = true, dropped = 10, rendered = 10)
        assertEquals("Playback/decoder problem detected", title(result))
        assertFalse(detail(result).contains("hardware failure"))
    }

    @Test fun frameDropsNeedFiveFramesAndStrictlyMoreThanFivePercentOfTotalFrames() {
        assertEquals("Frame drops detected", title(assess(dropped = 5, rendered = 94)))
        assertEquals("No delivery problem detected", title(assess(dropped = 5, rendered = 95)))
        assertEquals("No delivery problem detected", title(assess(dropped = 4, rendered = 0)))
        assertEquals("Frame drops detected", title(assess(dropped = 10, rendered = 0)))
        assertEquals("No delivery problem detected", title(assess(dropped = -1, rendered = -1)))
    }

    @Test fun startupWaitsForThreeSecondsBeforeJudgingDelivery() {
        assertEquals("Measuring", title(assess(sampled = 2_999, buffering = true, buffer = 0,
            rate = null, total = 0, since = null)))
        assertEquals("Delivery interruption detected", title(assess(sampled = 3_000,
            buffering = true, buffer = 0, total = 0, since = null)))
    }

    @Test fun interruptionRequiresStarvingBufferBufferingAndDeliveryEvidence() {
        val stall = assess(buffering = true, buffer = 999, since = 3_000)
        assertEquals("Delivery interruption detected", title(stall))
        assertTrue(detail(stall).contains("source") && detail(stall).contains("path"))
        assertEquals("Collecting video", title(assess(buffering = true, buffer = 1_000, since = 3_000)))
        assertEquals("Collecting video", title(assess(buffering = true, buffer = 999, since = 2_999)))
        assertEquals("Collecting video", title(assess(buffering = true, buffer = 0,
            total = 0, since = null, active = 0, loading = true)))
        assertEquals("Delivery interruption detected", title(assess(buffering = true, buffer = 0,
            total = 0, since = null, active = 1)))
        assertEquals("No delivery problem detected", title(assess(buffering = false, buffer = 0, since = 60_000)))
    }

    @Test fun pacedLiveEqualOrBelowAdvertisedBitrateDoesNotImplyBadHealth() {
        for (rate in listOf(6_000_000L, 1_000_000L, 0L)) {
            val result = assess(rate = rate, active = 0, since = 20_000, buffer = 30_000)
            assertEquals("No delivery problem detected", title(result))
            assertTrue(detail(result).contains("short") && detail(result).contains("proof"))
        }
    }

    @Test fun unknownBitrateIsExplicitAndNoAssessmentInventsServerOverload() {
        val unknown = assess(bitrate = null)
        assertTrue(detail(unknown).contains("bitrate") && detail(unknown).contains("unknown"))
        val stall = assess(buffering = true, buffer = 0, since = 10_000)
        assertFalse(detail(stall).contains("overload"))
    }
}
