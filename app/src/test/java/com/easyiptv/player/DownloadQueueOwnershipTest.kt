package com.easyiptv.player

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DownloadQueueOwnershipTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = app.getSharedPreferences("easyiptv", Context.MODE_PRIVATE)

    @Before fun reset() {
        Playback.releaseAll()
        Recorder.usesProviderConnection = false
        prefs.edit().clear().putInt("provider_streams", 1).commit()
        DownloadService::class.java.getDeclaredField("activeId").apply { isAccessible = true }.setLong(null, 0L)
        while (shadowOf(app).nextStartedService != null) {}
    }

    @After fun cleanUp() {
        DownloadService::class.java.getDeclaredField("activeId").apply { isAccessible = true }.setLong(null, 0L)
        while (shadowOf(app).nextStartedService != null) {}
    }

    private fun queued(id: Long, url: String = "https://example.invalid/movie.mp4"): DownloadStore.Item {
        val file = File(app.cacheDir, "queue-$id.mp4")
        file.delete()
        File(file.path + ".part").delete()
        val item = DownloadStore.Item(id, "Movie $id", file.path, Long.MAX_VALUE, url)
        DownloadStore.save(prefs, DownloadStore.load(prefs) + item)
        DownloadStore.mark(app, id, DownloadStore.STATE_PENDING)
        return item
    }

    @Test fun queueReservesOnlyOneStartBeforeServiceDispatch() {
        val first = queued(11)
        queued(12)
        try {
            assertTrue(DownloadStore.kickQueue(app, prefs))
            assertFalse("Dispatching a second queue head must wait for the first service start", DownloadStore.kickQueue(app, prefs))
            assertEquals(DownloadStore.STATE_RUNNING, DownloadStore.state(app, 11))
            assertEquals("The next movie must remain queued", DownloadStore.STATE_PENDING, DownloadStore.state(app, 12))
            assertEquals(11L, shadowOf(app).nextStartedService!!.getLongExtra("id", 0))
            assertNull("Only one foreground service start may be dispatched", shadowOf(app).nextStartedService)
        } finally { DownloadStore.stopAndRemove(app, prefs, first) }
    }

    @Test fun pausedDispatchCannotStartLaterAndNextMovieCanRun() {
        ServerSocket(0).use { stalled ->
            val first = queued(21, "http://127.0.0.1:${stalled.localPort}/movie.mp4")
            val second = queued(22)
            assertTrue(DownloadStore.kickQueue(app, prefs))
            val dispatched = shadowOf(app).nextStartedService!!
            File(first.path + ".part").writeText("PART")
            DownloadStore.pause(app, prefs, first)
            val nextDispatch = shadowOf(app).nextStartedService
            assertNotNull("Pausing a dispatched head must automatically advance the queue", nextDispatch)
            assertEquals(22L, nextDispatch!!.getLongExtra("id", 0))
            val service = Robolectric.buildService(DownloadService::class.java).create()
            try {
                service.get().onStartCommand(dispatched, 0, 1)
                assertFalse("An already paused queued start must not acquire a provider socket", DownloadService.isActive(21))
                assertEquals(DownloadStore.STATE_FAILED, DownloadStore.state(app, 21))
                assertEquals("PART", File(first.path + ".part").readText())
                assertEquals("A stale head must not replace the next movie's reservation", DownloadStore.STATE_RUNNING, DownloadStore.state(app, 22))
                assertNull("The next movie must be dispatched only once", shadowOf(app).nextStartedService)
            } finally {
                service.destroy()
                DownloadStore.stopAndRemove(app, prefs, second)
            }
        }
    }

    @Test fun removedDispatchAutomaticallyAdvancesWithoutRestartingDeletedMovie() {
        val first = queued(71)
        val second = queued(72)
        assertTrue(DownloadStore.kickQueue(app, prefs))
        val removedDispatch = shadowOf(app).nextStartedService!!
        DownloadStore.stopAndRemove(app, prefs, first)
        val nextDispatch = shadowOf(app).nextStartedService
        assertNotNull("Removing a dispatched head must automatically advance the queue", nextDispatch)
        assertEquals(72L, nextDispatch!!.getLongExtra("id", 0))
        val service = Robolectric.buildService(DownloadService::class.java).create()
        try {
            service.get().onStartCommand(removedDispatch, 0, 1)
            assertEquals(listOf(72L), DownloadStore.load(prefs).map { it.id })
            assertFalse(DownloadService.isActive(71))
            assertEquals(DownloadStore.STATE_RUNNING, DownloadStore.state(app, 72))
            assertNull(shadowOf(app).nextStartedService)
        } finally {
            service.destroy()
            DownloadStore.stopAndRemove(app, prefs, second)
        }
    }

    @Test fun startWaitsForCancelledWriterCleanup() {
        val service = Robolectric.buildService(DownloadService::class.java).create()
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val scope = CoroutineScope(Dispatchers.IO)
        val old = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() }
            finally { withContext(NonCancellable) { entered.countDown(); finish.await(5, TimeUnit.SECONDS) } }
        }
        val field = DownloadService::class.java.getDeclaredField("job").apply { isAccessible = true }
        try {
            old.cancel()
            assertTrue("Cancelled writer must enter the cleanup barrier", entered.await(1, TimeUnit.SECONDS))
            field.set(service.get(), old)
            val item = queued(31)
            service.get().onStartCommand(Intent(app, DownloadService::class.java)
                .setAction("com.easyiptv.player.DOWNLOAD_START")
                .putExtra("id", item.id).putExtra("title", item.title)
                .putExtra("url", item.url).putExtra("path", item.path), 0, 2)
            assertSame("A cancelled writer still owns the service until cleanup completes", old, field.get(service.get()))
            assertEquals("Rejected start must remain queued", DownloadStore.STATE_PENDING, DownloadStore.state(app, 31))
        } finally {
            finish.countDown()
            runBlocking { old.join() }
            scope.cancel()
            service.destroy()
        }
    }

    @Test fun destroyingOldServiceCannotClearReplacementOwnership() {
        val old = Robolectric.buildService(DownloadService::class.java).create()
        // Simulate replacement ownership while the old service receives delayed destruction.
        DownloadService::class.java.getDeclaredField("activeId").apply { isAccessible = true }.setLong(null, 41L)
        old.destroy()
        assertTrue("Old destruction must leave the replacement writer's provider slot intact", DownloadService.isActive(41))
    }

    @Test fun mismatchedPartialResponsePreservesResumeFileInsteadOfCorruptingIt() {
        ServerSocket(0).use { provider ->
            val response = thread(isDaemon = true) {
                provider.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) {}
                    socket.getOutputStream().write(("HTTP/1.1 206 Partial Content\r\n" +
                        "Content-Length: 3\r\nContent-Range: bytes 0-2/7\r\nConnection: close\r\n\r\nBAD").toByteArray())
                }
            }
            val item = queued(51, "http://127.0.0.1:${provider.localPort}/movie.mp4")
            val part = File(item.path + ".part").apply { writeText("PART") }
            assertTrue(DownloadStore.kickQueue(app, prefs))
            val intent = shadowOf(app).nextStartedService!!
            val service = Robolectric.buildService(DownloadService::class.java).create()
            try {
                service.get().onStartCommand(intent, 0, 1)
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (DownloadStore.state(app, 51) == DownloadStore.STATE_RUNNING && System.nanoTime() < deadline) Thread.sleep(10)
                assertEquals("A mismatched range must fail safely instead of joining different byte positions", DownloadStore.STATE_FAILED, DownloadStore.state(app, 51))
                assertEquals("Existing resume bytes must survive the rejected provider response", "PART", part.readText())
                assertFalse("Corrupt output must never be published as downloaded", File(item.path).exists())
            } finally {
                service.destroy()
                response.join(1000)
            }
        }
    }

    @Test fun matchingPartialResponseAppendsAndPublishesCompleteDownload() {
        ServerSocket(0).use { provider ->
            val response = thread(isDaemon = true) {
                provider.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) {}
                    socket.getOutputStream().write(("HTTP/1.1 206 Partial Content\r\n" +
                        "Content-Length: 3\r\nContent-Range: bytes 4-6/7\r\nConnection: close\r\n\r\nNEW").toByteArray())
                }
            }
            val item = queued(61, "http://127.0.0.1:${provider.localPort}/movie.mp4")
            File(item.path + ".part").writeText("PART")
            assertTrue(DownloadStore.kickQueue(app, prefs))
            val service = Robolectric.buildService(DownloadService::class.java).create()
            try {
                service.get().onStartCommand(shadowOf(app).nextStartedService!!, 0, 1)
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (DownloadStore.state(app, 61) == DownloadStore.STATE_RUNNING && System.nanoTime() < deadline) Thread.sleep(10)
                assertEquals(DownloadStore.STATE_SUCCESS, DownloadStore.state(app, 61))
                assertEquals("PARTNEW", File(item.path).readText())
                assertFalse(File(item.path + ".part").exists())
            } finally {
                service.destroy()
                response.join(1000)
            }
        }
    }
}
