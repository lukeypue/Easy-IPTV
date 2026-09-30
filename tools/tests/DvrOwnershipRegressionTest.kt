package com.easyiptv.player

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowStatFs

/** These tests exercise the actual playback/service/server ownership boundaries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class DvrOwnershipRegressionTest {
    private val app get() = ApplicationProvider.getApplicationContext<Context>()

    private fun waitFor(message: String, condition: () -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < until) Thread.sleep(10)
        assertTrue(message, condition())
    }

    private fun storageContext(dir: File, probe: (() -> Unit)? = null, available: Int = 1_500_000): Context {
        ShadowStatFs.registerStats(dir.absolutePath, 2_000_000, available, available)
        return object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = dir
            override fun getExternalFilesDirs(type: String?): Array<File> {
                probe?.invoke()
                return emptyArray()
            }
        }
    }

    private fun prefs() = app.getSharedPreferences("easyiptv", Context.MODE_PRIVATE).apply {
        edit().clear().putBoolean("simple_mode", false).putInt("provider_streams", 1).commit()
    }

    @Test fun pendingAndFailedStorageKeepTheirProviderReservationUntilRelease() {
        val dir = Files.createTempDirectory("ryzod-pending-owner").toFile()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val context = storageContext(dir, {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        }, available = 0)
        ReviewProvider().use { provider ->
            try {
                Playback.open(context, prefs(), listOf(Playable("watch", provider.url("watch"), true)), 0, null, false)
                assertTrue(entered.await(3, TimeUnit.SECONDS))
                assertEquals(Player.STATE_IDLE, Playback.playStateC.intValue)
                val preparingSlots = ProviderStreams.playbackSlots()
                release.countDown()
                waitFor("Storage failure did not finish") { !Timeshift.isPreparing }
                // The failure callback is queued on the paused main looper. It can
                // still open direct playback and therefore still owns the slot.
                val failedPendingSlots = ProviderStreams.playbackSlots()
                Playback.releaseAll()
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals("Async setup must reserve its future provider stream", 1, preparingSlots)
                assertEquals("A queued direct-rescue callback must retain ownership", 1, failedPendingSlots)
                assertEquals(0, ProviderStreams.playbackSlots())
                assertNull("A stale setup callback must not resurrect released playback", Playback.player)
                assertEquals(0, provider.requests.get())
            } finally {
                release.countDown()
                Playback.releaseAll()
                dir.deleteRecursively()
            }
        }
    }

    @Test fun directRecordingCannotOpenNetworkUntilMainThreadPlaybackReleaseFinishes() {
        val dir = Files.createTempDirectory("ryzod-record-takeover").toFile()
        val context = storageContext(dir)
        val service = Robolectric.buildService(RecordingService::class.java).create()
        ReviewProvider().use { provider ->
            try {
                Playback.open(context, prefs(), listOf(Playable("watch", provider.url("watch"), true)), 0, null, false)
                waitFor("DVR did not prime") { Timeshift.bytesWritten >= 512 * 1024 }
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals("Playback must own a provider connection before takeover", 1, ProviderStreams.playbackSlots())
                service.get().onStartCommand(Intent(app, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_START
                    putExtra("url", provider.url("record"))
                    putExtra("name", "takeover")
                }, 0, 1)
                // Do not run main-looper tasks: the release acknowledgement is
                // intentionally delayed beyond the old 1.2-second timeout.
                assertFalse("Recording opened a second provider stream before playback release",
                    provider.recordRequest.await(1800, TimeUnit.MILLISECONDS))
                shadowOf(Looper.getMainLooper()).idle()
                assertTrue("Recording never started after acknowledged release", provider.recordRequest.await(3, TimeUnit.SECONDS))
                assertNull(Playback.player)
                assertFalse(Timeshift.active)
                assertTrue(Recorder.usesProviderConnection)
            } finally {
                service.get().onStartCommand(Intent(app, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_STOP
                }, 0, 2)
                service.destroy()
                Playback.releaseAll()
                dir.deleteRecursively()
            }
        }
    }

    @Test fun channelChangeRetiresBothStalledHttpWorkersSoTheNewChannelCanPlay() {
        val dir = Files.createTempDirectory("ryzod-http-retire").toFile()
        val context = storageContext(dir)
        ReviewProvider().use { provider ->
            val stalled = ArrayList<Socket>()
            try {
                Timeshift.start(context, provider.url("watch"))
                waitFor("First DVR did not ingest") { Timeshift.bytesWritten >= 188 * 3 }
                repeat(2) { stalled += partialRequest(serverPort()) }
                waitFor("Both reader workers were not occupied") { serverClientCount() == 2 }
                Timeshift.start(context, provider.url("next"))
                waitFor("New DVR did not ingest") { Timeshift.bytesWritten >= 188 * 3 }
                val generation = Timeshift.generation()
                var response: String? = null
                val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                while (response == null && System.nanoTime() < until) {
                    Socket("127.0.0.1", serverPort()).use { socket ->
                        socket.soTimeout = 500
                        socket.getOutputStream().write(("GET /live?offset=0&generation=$generation HTTP/1.1\r\nHost: localhost\r\n\r\n").toByteArray())
                        response = runCatching { socket.getInputStream().bufferedReader().readLine() }.getOrNull()
                    }
                    if (response == null) Thread.sleep(20)
                }
                assertEquals("Retired clients must not monopolize the bounded worker pool", "HTTP/1.1 200 OK", response)
                stalled.forEach { socket ->
                    socket.soTimeout = 1000
                    assertEquals("A retired incomplete request must be closed", -1, socket.getInputStream().read())
                }
            } finally {
                stalled.forEach { runCatching { it.close() } }
                Timeshift.stop()
                stopServer()
                dir.deleteRecursively()
            }
        }
    }

    @Test fun incompleteHttpHeadersHaveADeadlineWithoutNeedingAChannelChange() {
        val dir = Files.createTempDirectory("ryzod-http-deadline").toFile()
        ReviewProvider().use { provider ->
            try {
                Timeshift.start(storageContext(dir), provider.url("watch"))
                waitFor("DVR did not ingest") { Timeshift.bytesWritten >= 188 * 3 }
                partialRequest(serverPort()).use { socket ->
                    socket.soTimeout = 4000
                    val closed = try { socket.getInputStream().read() == -1 } catch (_: SocketTimeoutException) { false }
                    assertTrue("Incomplete headers held a reader worker indefinitely", closed)
                }
            } finally {
                Timeshift.stop()
                stopServer()
                dir.deleteRecursively()
            }
        }
    }

    private fun partialRequest(port: Int): Socket = Socket("127.0.0.1", port).apply {
        getOutputStream().write("GET /live HTTP/1.1\r\nHost: localhost\r\n".toByteArray())
        getOutputStream().flush()
    }

    private fun serverInstance(): Pair<Class<*>, Any> {
        val type = Class.forName("com.easyiptv.player.TimeshiftServer")
        return type to type.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
    }

    private fun serverPort(): Int {
        val (type, instance) = serverInstance()
        return type.getDeclaredMethod("getPort").apply { isAccessible = true }.invoke(instance) as Int
    }

    private fun serverClientCount(): Int {
        val (type, instance) = serverInstance()
        val clients = type.getDeclaredField("clients").apply { isAccessible = true }.get(instance)
        return when (clients) {
            is Map<*, *> -> clients.size
            is Collection<*> -> clients.size
            else -> error("Unsupported client registry")
        }
    }

    private fun stopServer() {
        val (type, instance) = serverInstance()
        type.getDeclaredMethod("stop").apply { isAccessible = true }.invoke(instance)
    }

    private class ReviewProvider : AutoCloseable {
        private val server = ServerSocket(0)
        private val sockets = ConcurrentHashMap.newKeySet<Socket>()
        private val closed = AtomicBoolean()
        val requests = AtomicInteger()
        val recordRequest = CountDownLatch(1)
        init {
            thread(isDaemon = true, name = "review-provider") {
                while (!closed.get()) {
                    val socket = try { server.accept() } catch (_: Exception) { break }
                    sockets.add(socket)
                    thread(isDaemon = true) {
                        try {
                            val input = socket.getInputStream().bufferedReader()
                            val request = input.readLine() ?: return@thread
                            while (!input.readLine().isNullOrEmpty()) { }
                            requests.incrementAndGet()
                            if (request.contains("/record.ts")) recordRequest.countDown()
                            val packets = ByteArray(188 * 1024) { 0x22 }.also { b -> repeat(1024) { b[it * 188] = 0x47 } }
                            val out = socket.getOutputStream()
                            out.write("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nConnection: close\r\n\r\n".toByteArray())
                            while (!closed.get()) { out.write(packets); out.flush(); Thread.sleep(10) }
                        } catch (_: Exception) { }
                        finally { sockets.remove(socket); runCatching { socket.close() } }
                    }
                }
            }
        }
        fun url(channel: String) = "http://127.0.0.1:${server.localPort}/$channel.ts"
        override fun close() {
            closed.set(true)
            server.close()
            sockets.forEach { runCatching { it.close() } }
        }
    }
}
