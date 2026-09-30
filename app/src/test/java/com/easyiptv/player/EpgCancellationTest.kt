package com.easyiptv.player

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EpgCancellationTest {
    @Test
    fun leavingChannelCancelsARequestWaitingForHeadersWithoutFallback() =
        cancellationClosesRequest(sendPartialBody = false)

    @Test
    fun leavingChannelCancelsResponseBodyReadWithoutFallback() =
        cancellationClosesRequest(sendPartialBody = true)

    private fun cancellationClosesRequest(sendPartialBody: Boolean) = runBlocking {
        val waiting = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        EpgServer { request, socket ->
            if (request.contains("action=get_short_epg")) {
                if (sendPartialBody) {
                    val header = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 100000\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().write((header + "{\"epg_listings\":[").toByteArray())
                    socket.getOutputStream().flush()
                }
                waiting.countDown()
                try {
                    if (socket.getInputStream().read() == -1) disconnected.countDown()
                } catch (_: SocketTimeoutException) {
                    // A provider timeout must not masquerade as prompt cancellation.
                } catch (_: IOException) {
                    disconnected.countDown()
                }
            } else {
                respond(socket, "{\"epg_listings\":[]}")
            }
        }.use { server ->
            val source = XtreamSource(server.url, "test", "test")
            val request = launch(Dispatchers.IO) { source.epg("101", 2) }
            try {
                assertTrue("The provider did not receive the short-guide request", waiting.await(3, TimeUnit.SECONDS))
                // Let the async callback enter its incomplete-body read. The server
                // never completes the body; only client cancellation can finish it.
                if (sendPartialBody) kotlinx.coroutines.delay(150)
                withTimeout(1_500) { request.cancelAndJoin() }
                assertTrue("Cancellation must close the active HTTP read", disconnected.await(1, TimeUnit.SECONDS))
                assertEquals("A cancelled channel must not request the full-day fallback", 1, server.requests.size)
                assertTrue(server.requests.single().contains("action=get_short_epg"))
                server.assertHealthy()
            } finally {
                request.cancel()
                // Close a stalled baseline request before joining it, so a failing
                // regression test never waits for the production 30-second timeout.
                server.close()
                withTimeout(3_000) { request.join() }
            }
        }
    }

    @Test
    fun successfulShortGuideKeepsDecodedTitlesAndDoesNotFetchFallback() = runBlocking {
        val now = System.currentTimeMillis() / 1000
        EpgServer { _, socket ->
            respond(socket, listings(entry("Current show", now - 60, now + 600)))
        }.use { server ->
            val entries = withTimeout(3_000) { XtreamSource(server.url, "test", "test").epg("101", 2) }
            assertEquals(listOf("Current show"), entries.map { it.title })
            assertEquals(1, server.requests.size)
            assertTrue(server.requests.single().contains("action=get_short_epg"))
            server.assertHealthy()
        }
    }

    @Test
    fun emptyShortGuideStillFallsBackAndSortsFiltersAndLimitsResults() = runBlocking {
        assertFallbackPreserved(shortStatus = 200)
    }

    @Test
    fun providerFailureStillFallsBackWhenTheChannelHasNotBeenCancelled() = runBlocking {
        assertFallbackPreserved(shortStatus = 503)
    }

    private suspend fun assertFallbackPreserved(shortStatus: Int) {
        val now = System.currentTimeMillis() / 1000
        EpgServer { request, socket ->
            if (request.contains("action=get_short_epg")) {
                respond(socket, "{\"epg_listings\":[]}", shortStatus)
            } else {
                respond(socket, listings(
                    entry("Later show", now + 1_200, now + 1_800),
                    entry("Ended show", now - 1_200, now - 600),
                    entry("Next show", now + 600, now + 1_200),
                    entry("Current show", now - 60, now + 600)
                ))
            }
        }.use { server ->
            val entries = withTimeout(3_000) { XtreamSource(server.url, "test", "test").epg("101", 2) }
            assertEquals(listOf("Current show", "Next show"), entries.map { it.title })
            assertEquals(2, server.requests.size)
            assertTrue(server.requests[0].contains("action=get_short_epg"))
            assertTrue(server.requests[1].contains("action=get_simple_data_table"))
            server.assertHealthy()
        }
    }

    private fun entry(title: String, start: Long, end: Long): String {
        val encoded = Base64.getEncoder().encodeToString(title.toByteArray())
        return "{\"title\":\"$encoded\",\"start_timestamp\":$start,\"stop_timestamp\":$end}"
    }

    private fun listings(vararg entries: String) = "{\"epg_listings\":[${entries.joinToString(",")}]}"

    private fun respond(socket: Socket, body: String, status: Int = 200) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $status Test\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().write(header.toByteArray(Charsets.US_ASCII))
        socket.getOutputStream().write(bytes)
        socket.getOutputStream().flush()
    }

    /** Real loopback HTTP with deliberately controlled response completion. */
    private class EpgServer(private val reply: (String, Socket) -> Unit) : Closeable {
        private val listener = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val failure = AtomicReference<Throwable?>()
        @Volatile private var closed = false
        @Volatile private var active: Socket? = null
        val requests = CopyOnWriteArrayList<String>()
        val url = "http://127.0.0.1:${listener.localPort}"
        private val worker = Thread({
            try {
                while (!closed) {
                    listener.accept().use { socket ->
                        active = socket
                        socket.soTimeout = 5_000
                        val input = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                        val request = input.readLine() ?: return@use
                        while (!input.readLine().isNullOrEmpty()) { /* consume headers */ }
                        requests.add(request)
                        reply(request, socket)
                    }
                    active = null
                }
            } catch (error: Throwable) {
                if (!closed) failure.set(error)
            }
        }, "epg-test-provider").apply { isDaemon = true; start() }

        fun assertHealthy() { failure.get()?.let { throw AssertionError("HTTP fixture failed", it) } }

        override fun close() {
            closed = true
            try { listener.close() } catch (_: SocketException) { }
            try { active?.close() } catch (_: IOException) { }
            worker.join(2_000)
        }
    }
}
