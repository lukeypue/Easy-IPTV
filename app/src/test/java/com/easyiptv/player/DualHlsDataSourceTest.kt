package com.easyiptv.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.hls.HlsDataSourceFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real HTTP transport tests. No MockWebServer dependency, player, or decoder is needed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DualHlsDataSourceTest {
    @Test fun fastSecondCompleteSegmentWinsAndCancelsThePartialFirstCopy() {
        val abandoned = CountDownLatch(1)
        val prefixSent = CountDownLatch(1)
        Fixture { request, socket ->
            if (request.number == 1) {
                request.headers(socket, length = 100_000)
                socket.getOutputStream().apply { write("WRONG-PREFIX".toByteArray()); flush() }
                prefixSent.countDown()
                request.fixture.second.awaitOrFail()
                if (socket.getInputStream().read() == -1) abandoned.countDown()
            } else {
                prefixSent.awaitOrFail()
                request.reply(socket, "complete-second".toByteArray())
            }
        }.use { server ->
            factory().use { factory ->
                val bytes = load(factory.media(), spec(server.url))
                assertArrayEquals("complete-second".toByteArray(), bytes)
                abandoned.awaitOrFail()
                assertEquals(2, server.requests.size)
                assertEquals(0, factory.base.opens.get())
            }
        }
    }

    @Test fun failedFirstCopyDoesNotHideSuccessfulSecondCopy() {
        Fixture { request, socket ->
            if (request.number == 1) request.reply(socket, "denied".toByteArray(), 503)
            else request.reply(socket, "winner".toByteArray())
        }.use { server ->
            factory().use { assertArrayEquals("winner".toByteArray(), load(it.media(), spec(server.url))) }
        }
    }

    @Test fun twoHttpFailuresThrowIOExceptionWithoutLeakingTheUrl() {
        Fixture { request, socket -> request.reply(socket, byteArrayOf(), 403) }.use { server ->
            factory().use { factory ->
                val source = factory.media()
                try {
                    val error = expectIo { source.open(spec(server.url + "?password=secret")) }
                    assertFalse(error.message.orEmpty().contains(server.url))
                    assertFalse(error.message.orEmpty().contains("secret"))
                    assertEquals(2, server.requests.size)
                } finally { source.close() }
                assertEquals(1, factory.meters.single().starts.get())
                assertEquals(1, factory.meters.single().ends.get())
            }
        }
    }

    @Test fun prematureEofIsNotACompleteWinningSegment() {
        Fixture { request, socket ->
            if (request.number == 1) {
                request.headers(socket, length = 100)
                socket.getOutputStream().write("truncated".toByteArray())
            } else {
                request.fixture.second.awaitOrFail()
                request.reply(socket, "intact".toByteArray())
            }
        }.use { server ->
            factory().use { assertArrayEquals("intact".toByteArray(), load(it.media(), spec(server.url))) }
        }
    }

    @Test fun copiesUseIdenticalPathHeadersAndProviderUserAgent() {
        Fixture { request, socket ->
            request.fixture.second.awaitOrFail()
            request.reply(socket, "segment".toByteArray())
        }.use { server ->
            factory().use { factory ->
                load(factory.media(), spec(server.url + "?token=a%2Fb", mapOf("Authorization" to "Bearer private", "X-Feed" to "one")))
                assertEquals(2, server.requests.size)
                server.requests.forEach {
                    assertEquals("GET /segment.ts?token=a%2Fb HTTP/1.1", it.line)
                    assertEquals("Bearer private", it.values["authorization"])
                    assertEquals("one", it.values["x-feed"])
                    assertEquals(USER_AGENT, it.values["user-agent"])
                    assertEquals("identity", it.values["accept-encoding"])
                }
            }
        }
    }

    @Test fun explicitUserAgentAndAcceptEncodingHeadersPassThrough() {
        Fixture { request, socket ->
            request.fixture.second.awaitOrFail()
            request.reply(socket, byteArrayOf(1))
        }.use { server ->
            factory().use {
                load(it.media(), spec(server.url, mapOf("User-Agent" to "override", "Accept-Encoding" to "identity")))
                server.requests.forEach { request -> assertEquals("override", request.values["user-agent"]) }
            }
        }
    }

    @Test fun mediaRangePositionLengthMethodBodyAndExplicitRangeHeaderUseOneBaselineConnection() {
        Fixture { request, socket -> request.reply(socket, "0123456789".toByteArray()) }.use { server ->
            factory().use { factory ->
                val original = spec(server.url)
                val requests = listOf(
                    original.buildUpon().setPosition(2).build(),
                    original.buildUpon().setLength(3).build(),
                    original.buildUpon().setHttpMethod(DataSpec.HTTP_METHOD_POST).setHttpBody(byteArrayOf(1)).build(),
                    original.buildUpon().setHttpMethod(DataSpec.HTTP_METHOD_GET).setHttpBody(byteArrayOf(2)).build(),
                    spec(server.url, mapOf("rAnGe" to "bytes=0-3")),
                )
                requests.forEach { request ->
                    val before = server.requests.size
                    load(factory.media(), request)
                    assertEquals(before + 1, server.requests.size)
                    assertSame(request, factory.base.specs.last())
                }
                assertEquals(requests.size, factory.base.opens.get())
                assertEquals(requests.size, factory.meters.size)
                factory.meters.forEach { assertEquals(1, it.starts.get()); assertEquals(1, it.ends.get()) }
            }
        }
    }

    @Test fun manifestKeyInitializationAndProgressiveTypesUseBaseline() {
        Fixture { request, socket -> request.reply(socket, "baseline".toByteArray()) }.use { server ->
            factory().use { factory ->
                listOf(C.DATA_TYPE_MANIFEST, C.DATA_TYPE_DRM, C.DATA_TYPE_MEDIA_INITIALIZATION,
                    C.DATA_TYPE_UNKNOWN, C.DATA_TYPE_MEDIA_PROGRESSIVE_LIVE).forEach { type ->
                    assertArrayEquals("baseline".toByteArray(), load(factory.value.createDataSource(type), spec(server.url)))
                }
                assertEquals(5, server.requests.size)
                assertEquals(5, factory.base.opens.get())
            }
        }
    }

    @Test fun nonHttpMediaDelegatesUntouchedAndReportsNonNetworkTransfers() {
        val base = TrackingFactory(DataSource.Factory { LocalSource() })
        factory(base).use { factory ->
            assertArrayEquals(byteArrayOf(9), load(factory.media(), spec("asset:///segment.ts")))
            assertEquals(1, base.opens.get())
            val meter = factory.meters.single()
            assertEquals(1, meter.starts.get())
            assertEquals(1, meter.ends.get())
            assertEquals(listOf(false, false, false, false), meter.network.toList())
        }
    }

    @Test fun winnerMetadataReadOffsetsZeroLengthEofAndUsefulPayloadCallbacks() {
        val payload = ByteArray(4097) { (it % 251).toByte() }
        Fixture { request, socket ->
            request.fixture.second.awaitOrFail()
            request.reply(socket, payload)
        }.use { server ->
            factory().use { factory ->
                val source = factory.media()
                val request = spec(server.url)
                try {
                    assertEquals(payload.size.toLong(), source.open(request))
                    assertEquals(request.uri, source.uri)
                    assertEquals("fixture", header(source, "X-Segment"))
                    assertEquals(0, source.read(ByteArray(1), 0, 0))
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(107) { 77 }
                    while (true) {
                        val count = source.read(buffer, 3, 101)
                        if (count == C.RESULT_END_OF_INPUT) break
                        assertTrue(count in 1..101)
                        assertEquals(77.toByte(), buffer[0])
                        assertEquals(77.toByte(), buffer[106])
                        output.write(buffer, 3, count)
                    }
                    assertArrayEquals(payload, output.toByteArray())
                    assertEquals(C.RESULT_END_OF_INPUT, source.read(buffer, 0, 1))
                } finally { source.close(); source.close() }
                assertNull(source.uri)
                val meter = factory.meters.single()
                assertEquals(1, meter.initializes.get())
                assertEquals(1, meter.starts.get())
                assertEquals(1, meter.ends.get())
                assertEquals(payload.size, meter.bytes.get())
                assertTrue(meter.network.all { it })
            }
        }
    }

    @Test fun transferStartsBeforeDelayedNetworkRaceSoBandwidthMeterIncludesDownloadTime() {
        val release = CountDownLatch(1)
        Fixture { request, socket ->
            release.awaitOrFail()
            request.reply(socket, "usable".toByteArray())
        }.use { server ->
            factory().use { factory ->
                val source = factory.media()
                worker { load(source, spec(server.url)) }.use { loading ->
                    try {
                        server.second.awaitOrFail()
                        val meter = factory.meters.single()
                        assertEquals("Transfer must start before the segment is downloaded", 1, meter.starts.get())
                        assertEquals(0, meter.bytes.get())
                        assertEquals(0, meter.ends.get())
                        assertEquals(Uri.parse(server.url), source.uri)
                    } finally { release.countDown() }
                    assertArrayEquals("usable".toByteArray(), loading.get())
                }
                val meter = factory.meters.single()
                assertEquals(6, meter.bytes.get())
                assertEquals(1, meter.ends.get())
            }
        }
    }

    @Test fun baselineFallbackPreservesMetadataLengthAndDoesNotDoubleMeter() {
        Fixture { request, socket -> request.reply(socket, "baseline".toByteArray()) }.use { server ->
            factory().use { factory ->
                val source = factory.media()
                try {
                    assertEquals(4L, source.open(spec(server.url).buildUpon().setLength(4).build()))
                    assertEquals(Uri.parse(server.url), source.uri)
                    assertEquals("fixture", header(source, "X-Segment"))
                    assertArrayEquals("base".toByteArray(), readAll(source))
                } finally { source.close() }
                val meter = factory.meters.single()
                assertEquals(1, meter.initializes.get())
                assertEquals(1, meter.starts.get())
                assertEquals(1, meter.ends.get())
                assertEquals(4, meter.bytes.get())
            }
        }
    }

    @Test fun oversizedDeclaredContentLengthStreamsWithoutReopenOrTruncation() = oversized(false)

    @Test fun unknownLengthCrossingMemoryCapKeepsPrefixAndStreamsRemainder() = oversized(true)

    private fun oversized(unknownLength: Boolean) {
        val payload = ByteArray(8 * 1024 * 1024 + 71_231) { ((it * 31 + 19) % 251).toByte() }
        Fixture { request, socket ->
            request.fixture.second.awaitOrFail()
            if (request.number == 1) request.reply(socket, payload, unknownLength = unknownLength)
            else socket.getInputStream().read() // Losing copy stays pending until cancelled.
        }.use { server ->
            factory().use { factory ->
                val source = factory.media()
                try {
                    val length = source.open(spec(server.url))
                    if (!unknownLength) assertEquals(payload.size.toLong(), length)
                    else assertTrue(length == C.LENGTH_UNSET.toLong() || length == payload.size.toLong())
                    assertEquals(Uri.parse(server.url), source.uri)
                    assertEquals("fixture", header(source, "X-Segment"))
                    assertArrayEquals(payload, readAll(source))
                } finally { source.close() }
                assertEquals(2, server.requests.size)
                assertEquals(0, factory.base.opens.get())
                assertEquals(payload.size, factory.meters.single().bytes.get())
                assertEquals(1, factory.meters.single().ends.get())
            }
        }
    }

    @Test fun manifestRefreshCannotOpenAThirdRequestWhileThePairIsPending() {
        val release = CountDownLatch(1)
        Fixture { request, socket ->
            if (request.line.contains("/segment.ts")) release.awaitOrFail()
            request.reply(socket, "ok".toByteArray())
        }.use { server ->
            factory().use { factory ->
                worker { load(factory.media(), spec(server.url)) }.use { first ->
                    server.second.awaitOrFail()
                    worker { load(factory.value.createDataSource(C.DATA_TYPE_MANIFEST), spec(server.url.replace("/segment.ts", "/manifest.m3u8"))) }.use { manifest ->
                        try {
                            Thread.sleep(150)
                            assertEquals("Playlist refresh must wait rather than opening request three", 2, server.requests.size)
                        } finally { release.countDown() }
                        assertArrayEquals("ok".toByteArray(), first.get())
                        assertArrayEquals("ok".toByteArray(), manifest.get())
                    }
                }
            }
        }
    }

    @Test fun completedPayloadKeepsPairPermitUntilSourceIsClosed() {
        Fixture { request, socket ->
            if (request.line.contains("/segment.ts")) request.fixture.second.awaitOrFail()
            request.reply(socket, "bounded".toByteArray())
        }.use { server ->
            factory().use { factory ->
                val first = factory.media()
                val second = factory.media()
                try {
                    first.open(spec(server.url))
                    second.open(spec(server.url.replace("/segment.ts", "/next.ts")))
                    assertEquals("An unread completed payload must retain the pair's memory permit", 1, factory.base.opens.get())
                    assertArrayEquals("bounded".toByteArray(), readAll(second))
                    assertArrayEquals("bounded".toByteArray(), readAll(first))
                } finally { first.close(); second.close() }
            }
        }
    }

    @Test fun concurrentMediaLoadsUseOnlyOneRacingPairAndPermitIsReusable() {
        val release = CountDownLatch(1)
        Fixture { request, socket ->
            if (request.line.contains("/segment.ts")) release.awaitOrFail()
            request.reply(socket, "ok".toByteArray())
        }.use { server ->
            factory().use { factory ->
                worker { load(factory.media(), spec(server.url)) }.use { first ->
                    server.second.awaitOrFail()
                    worker { load(factory.media(), spec(server.url.replace("segment.ts", "audio.aac"))) }.use { audio ->
                        try {
                            Thread.sleep(100)
                            assertEquals(2, server.requests.size)
                        } finally { release.countDown() }
                        assertArrayEquals("ok".toByteArray(), first.get())
                        assertArrayEquals("ok".toByteArray(), audio.get())
                        assertEquals(1, factory.base.opens.get())
                    }
                }
                // Hold both new responses until observed, so cancellation cannot mask admission.
                Fixture { request, socket ->
                    request.fixture.second.awaitOrFail()
                    request.reply(socket, "again".toByteArray())
                }.use { next ->
                    assertArrayEquals("again".toByteArray(), load(factory.media(), spec(next.url)))
                    assertEquals(2, next.requests.size)
                }
            }
        }
    }

    @Test fun sourceCloseCancelsBothStalledCallsAndUnblocksOpenPromptly() = cancelStalled(false, false)

    @Test fun factoryCloseCancelsBothStalledCallsAndUnblocksOpenPromptly() = cancelStalled(true, false)

    @Test fun loaderInterruptCancelsBothStalledCallsPromptly() = cancelStalled(false, true)

    private fun cancelStalled(closeFactory: Boolean, interrupt: Boolean) {
        val disconnected = CountDownLatch(2)
        val headersSent = CountDownLatch(2)
        Fixture { request, socket ->
            request.headers(socket, 100_000)
            headersSent.countDown()
            request.fixture.second.awaitOrFail()
            if (socket.getInputStream().read() == -1) disconnected.countDown()
        }.use { server ->
            factory().use { factory ->
                val source = factory.media()
                val done = CountDownLatch(1)
                val failures = CopyOnWriteArrayList<Throwable>()
                val loader = thread(isDaemon = true, name = "dual-hls-test-loader") {
                    try { source.open(spec(server.url)); failures += AssertionError("Cancelled open returned successfully") }
                    catch (error: Throwable) { failures += error }
                    finally { source.close(); done.countDown() }
                }
                server.second.awaitOrFail()
                headersSent.awaitOrFail()
                val start = System.nanoTime()
                when {
                    interrupt -> loader.interrupt()
                    closeFactory -> factory.close()
                    else -> source.close()
                }
                done.awaitOrFail(2)
                disconnected.awaitOrFail(2)
                assertTrue("Cancellation took too long", System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2))
                assertEquals(1, failures.size)
                assertTrue(failures.single().toString(), failures.single() is IOException)
                assertEquals(1, factory.meters.single().starts.get())
                assertEquals(1, factory.meters.single().ends.get())
            }
        }
    }

    @Test fun factoryCloseUnblocksReadOfAnOversizedStreamingResponse() {
        val disconnected = CountDownLatch(2)
        Fixture { request, socket ->
            request.fixture.second.awaitOrFail()
            try {
                request.headers(socket, 9 * 1024 * 1024)
                socket.getInputStream().read()
            } finally { disconnected.countDown() }
        }.use { server ->
            factory().use { factory ->
                val source = factory.media()
                source.open(spec(server.url))
                worker { expectIo { source.read(ByteArray(1), 0, 1) } }.use { read ->
                    factory.close()
                    read.get(2)
                    disconnected.awaitOrFail(2)
                }
                source.close()
            }
        }
    }

    @Test fun closedFactoryRejectsEveryNewOpenIncludingPreviouslyCreatedBaselineSources() {
        Fixture { request, socket -> request.reply(socket, "baseline".toByteArray()) }.use { server ->
            factory().use { factory ->
                val oldSource = factory.media()
                val oldManifest = factory.value.createDataSource(C.DATA_TYPE_MANIFEST)
                factory.close()
                assertEquals(true, factory.value.javaClass.getMethod("isClosed").invoke(factory.value))
                val sources = listOf(oldSource, oldManifest, factory.media(),
                    factory.value.createDataSource(C.DATA_TYPE_MANIFEST),
                    factory.value.createDataSource(C.DATA_TYPE_MEDIA_INITIALIZATION),
                    factory.value.createDataSource(C.DATA_TYPE_DRM))
                sources.forEach { source ->
                    expectIo { load(source, spec(server.url)) }
                }
                expectIo { load(factory.media(), spec(server.url).buildUpon().setLength(1).build()) }
                assertEquals("Closed factory must never start a request", 0, server.requests.size)
                assertEquals("Closed factory must never open the baseline", 0, factory.base.opens.get())
            }
        }
    }

    @Test fun loaderInterruptAlsoCancelsAStalledReadAfterStreamingFallback() {
        val disconnected = CountDownLatch(2)
        Fixture { request, socket ->
            request.fixture.second.awaitOrFail()
            try {
                request.headers(socket, 9 * 1024 * 1024)
                socket.getInputStream().read()
            } finally { disconnected.countDown() }
        }.use { server ->
            factory().use { factory ->
                val source = factory.media()
                source.open(spec(server.url))
                val entered = CountDownLatch(1)
                val done = CountDownLatch(1)
                val failures = CopyOnWriteArrayList<Throwable>()
                val loader = thread(isDaemon = true, name = "dual-hls-interrupted-stream-read") {
                    entered.countDown()
                    try { source.read(ByteArray(1), 0, 1); failures += AssertionError("Interrupted read returned") }
                    catch (error: Throwable) { failures += error }
                    finally { done.countDown() }
                }
                entered.awaitOrFail()
                loader.interrupt()
                done.awaitOrFail(2)
                disconnected.awaitOrFail(2)
                assertEquals(1, failures.size)
                assertTrue(failures.single().toString(), failures.single() is IOException)
                source.close()
            }
        }
    }

    private fun spec(url: String, headers: Map<String, String> = emptyMap()) =
        DataSpec.Builder().setUri(url).setHttpRequestHeaders(headers).build()

    private fun load(source: DataSource, request: DataSpec): ByteArray =
        try { source.open(request); readAll(source) } finally { source.close() }

    private fun readAll(source: DataSource): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = source.read(buffer, 0, buffer.size)
            if (count == C.RESULT_END_OF_INPUT) return result.toByteArray()
            assertTrue("A nonempty read must make progress", count > 0)
            result.write(buffer, 0, count)
        }
    }

    private fun header(source: DataSource, name: String): String? =
        source.responseHeaders.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()

    private fun expectIo(action: () -> Unit): IOException {
        try { action() } catch (error: IOException) { return error }
        throw AssertionError("Expected IOException")
    }

    private fun CountDownLatch.awaitOrFail(seconds: Long = 5) {
        assertTrue("Timed out waiting for fixture/transport", await(seconds, TimeUnit.SECONDS))
    }

    /** Reflection deliberately keeps the parent RED build compilable with no production class. */
    private fun factory(base: TrackingFactory = TrackingFactory()): FactoryHandle {
        val meters = CopyOnWriteArrayList<Meter>()
        val listenerFactory: () -> TransferListener = { Meter().also { meters += it } }
        val type = try { Class.forName("com.easyiptv.player.DualHlsDataSourceFactory") }
        catch (missing: ClassNotFoundException) { throw AssertionError("DualHlsDataSourceFactory is not implemented yet", missing) }
        val constructor = type.getDeclaredConstructor(DataSource.Factory::class.java, String::class.java, kotlin.jvm.functions.Function0::class.java)
        constructor.isAccessible = true
        return FactoryHandle(constructor.newInstance(base, USER_AGENT, listenerFactory) as HlsDataSourceFactory, base, meters)
    }

    private class FactoryHandle(val value: HlsDataSourceFactory, val base: TrackingFactory, val meters: List<Meter>) : AutoCloseable {
        fun media() = value.createDataSource(C.DATA_TYPE_MEDIA)
        override fun close() = (value as AutoCloseable).close()
    }

    private class TrackingFactory(private val delegate: DataSource.Factory = DefaultHttpDataSource.Factory()
        .setUserAgent(USER_AGENT).setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
        .setAllowCrossProtocolRedirects(true)) : DataSource.Factory {
        val opens = AtomicInteger()
        val specs = CopyOnWriteArrayList<DataSpec>()
        override fun createDataSource(): DataSource {
            val source = delegate.createDataSource()
            return object : DataSource by source {
                override fun open(dataSpec: DataSpec): Long {
                    opens.incrementAndGet(); specs += dataSpec
                    return source.open(dataSpec)
                }
                // Kotlin delegation doesn't forward Java interface default methods.
                override fun getResponseHeaders(): Map<String, List<String>> = source.responseHeaders
            }
        }
    }

    private class LocalSource : BaseDataSource(false) {
        private var request: DataSpec? = null
        private var remaining = false
        override fun open(dataSpec: DataSpec): Long {
            transferInitializing(dataSpec); request = dataSpec; remaining = true; transferStarted(dataSpec)
            return 1
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (!remaining) return C.RESULT_END_OF_INPUT
            buffer[offset] = 9; remaining = false; bytesTransferred(1); return 1
        }
        override fun getUri(): Uri? = request?.uri
        override fun close() { if (request != null) { request = null; transferEnded() } }
    }

    private class Meter : TransferListener {
        val initializes = AtomicInteger()
        val starts = AtomicInteger()
        val ends = AtomicInteger()
        val bytes = AtomicInteger()
        val network = CopyOnWriteArrayList<Boolean>()
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) { initializes.incrementAndGet(); network += isNetwork }
        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) { starts.incrementAndGet(); network += isNetwork }
        override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) { bytes.addAndGet(bytesTransferred); network += isNetwork }
        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) { ends.incrementAndGet(); network += isNetwork }
    }

    private class Worker<T>(action: () -> T) : AutoCloseable {
        private val executor = Executors.newSingleThreadExecutor { Thread(it, "dual-hls-test-worker").apply { isDaemon = true } }
        private val future: Future<T> = executor.submit<T> { action() }
        fun get(seconds: Long = 5): T = future.get(seconds, TimeUnit.SECONDS)
        override fun close() { future.cancel(true); executor.shutdownNow() }
    }

    private fun <T> worker(action: () -> T) = Worker(action)

    private class Fixture(val handler: (Request, Socket) -> Unit) : AutoCloseable {
        private val server = ServerSocket(0)
        private val sockets = CopyOnWriteArrayList<Socket>()
        private val sequence = AtomicInteger()
        val requests = CopyOnWriteArrayList<Request>()
        val second = CountDownLatch(1)
        val url = "http://127.0.0.1:${server.localPort}/segment.ts"
        init {
            thread(isDaemon = true, name = "dual-hls-test-accept") {
                while (!server.isClosed) {
                    val socket = try { server.accept() } catch (_: IOException) { break }
                    sockets += socket
                    thread(isDaemon = true, name = "dual-hls-test-response") {
                        try {
                            socket.use {
                                // Read precisely through CRLFCRLF, without consuming POST bytes.
                                val input = socket.getInputStream()
                                val bytes = ByteArrayOutputStream()
                                var tail = 0
                                while (true) {
                                    val next = input.read()
                                    if (next == -1) return@use
                                    bytes.write(next); tail = (tail shl 8) or next
                                    if (tail == 0x0d0a0d0a) break
                                    check(bytes.size() < 64 * 1024)
                                }
                                val lines = bytes.toString("ISO-8859-1").split("\r\n")
                                val values = lines.drop(1).filter { it.contains(':') }.associate {
                                    it.substringBefore(':').lowercase() to it.substringAfter(':').trim()
                                }
                                val request = Request(this, sequence.incrementAndGet(), lines.first(), values)
                                requests += request
                                if (request.number == 2) second.countDown()
                                handler(request, socket)
                            }
                        } catch (_: IOException) { /* Expected when a losing socket is cancelled. */ }
                        finally { sockets.remove(socket) }
                    }
                }
            }
        }
        override fun close() {
            server.close()
            sockets.forEach { runCatching { it.close() } }
        }
    }

    private class Request(val fixture: Fixture, val number: Int, val line: String, val values: Map<String, String>) {
        fun headers(socket: Socket, length: Int?, code: Int = 200) {
            val header = buildString {
                append("HTTP/1.1 $code Fixture\r\n")
                if (length != null) append("Content-Length: $length\r\n")
                append("Content-Type: video/mp2t\r\nX-Segment: fixture\r\nConnection: close\r\n\r\n")
            }
            socket.getOutputStream().apply { write(header.toByteArray(Charsets.ISO_8859_1)); flush() }
        }
        fun reply(socket: Socket, payload: ByteArray, code: Int = 200, unknownLength: Boolean = false) {
            headers(socket, if (unknownLength) null else payload.size, code)
            socket.getOutputStream().apply { write(payload); flush() }
        }
    }

    private companion object { const val USER_AGENT = "dual-hls-provider-test/1" }
}
