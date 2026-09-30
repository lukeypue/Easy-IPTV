package com.easyiptv.player

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket
import java.net.Socket
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
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TimeshiftSessionTest {
    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue(message, condition())
    }

    private fun storageContext(dir: File): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        ShadowStatFs.registerStats(dir.absolutePath, 2_000_000, 1_500_000, 1_500_000)
        return object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = dir
            override fun getExternalFilesDirs(type: String?): Array<File> = emptyArray()
        }
    }

    @Test fun rapidChannelChangesKeepOnlyTheNewestSessionAndBoundWorkers() {
        val dir = Files.createTempDirectory("ryzod-zap-stress").toFile()
        val context = storageContext(dir)
        TsProvider().use { provider ->
            try {
                Timeshift.start(context, provider.url("first"))
                waitFor("First stream did not ingest") { Timeshift.bytesWritten >= 188 * 3 }
                val firstGeneration = Timeshift.generation()
                val oldReader = Timeshift.openReader(Timeshift.newestVirtualByte(), firstGeneration)!!
                repeat(100) { Timeshift.start(context, provider.url("skip$it")) }
                Timeshift.start(context, provider.url("last"))
                val expectedGeneration = Timeshift.generation()
                waitFor("Final stream did not ingest") { Timeshift.bytesWritten >= 188 * 3 }
                assertEquals(expectedGeneration, Timeshift.generation())
                assertTrue(Timeshift.active)
                assertNull("A stale request must not attach to another channel", Timeshift.openReader(0, firstGeneration))
                Timeshift.openReader(0, expectedGeneration)!!.use { reader ->
                    val bytes = ByteArray(188 * 3)
                    assertEquals(bytes.size, reader.read(bytes))
                    assertEquals(0x47.toByte(), bytes[0])
                    assertEquals("The final ring must contain only the final channel", 0x33.toByte(), bytes[1])
                }
                val ingestThreads = Thread.getAllStackTraces().keys.count { it.name == "ryzod-dvr-ingest" && it.isAlive }
                val cleanupThreads = Thread.getAllStackTraces().keys.count { it.name == "ryzod-dvr-cleanup" && it.isAlive }
                assertEquals("Rapid zapping must reuse one ingest worker", 1, ingestThreads)
                assertTrue("Cleanup threads must not grow with channel count", cleanupThreads <= 1)
                waitFor("Retired ring reader did not terminate") { oldReader.read(ByteArray(188)) == -1 }
                oldReader.close()
                val ownedRoot = File(dir, "zako-live-ring")
                waitFor("Retired channel folders were not reclaimed") {
                    ownedRoot.listFiles()?.count { it.isDirectory && it.name.startsWith("session-") } == 1
                }
                assertTrue(Timeshift.bytesWritten > 0)
            } finally {
                Timeshift.stop()
            }
        }
        dir.deleteRecursively()
    }

    @Test fun recordingTeeKeepsUsingTheSingleExistingProviderConnection() {
        val dir = Files.createTempDirectory("ryzod-record-tee").toFile()
        val context = storageContext(dir)
        TsProvider().use { provider ->
            val recording = File(dir, "recording.ts")
            val keepRecording = AtomicBoolean(true)
            val finished = CountDownLatch(1)
            var recordingError: Throwable? = null
            try {
                Timeshift.start(context, provider.url("last"))
                waitFor("DVR did not begin") { Timeshift.bytesWritten >= 188 * 3 }
                val service = Robolectric.buildService(RecordingService::class.java).get()
                val tee = RecordingService::class.java.getDeclaredMethod(
                    "teeFromTimeshift", FileOutputStream::class.java, java.lang.Long::class.java,
                    kotlin.jvm.functions.Function0::class.java
                ).apply { isAccessible = true }
                thread(isDaemon = true, name = "record-tee-test") {
                    try {
                        FileOutputStream(recording).use { out ->
                            val active: () -> Boolean = { keepRecording.get() }
                            tee.invoke(service, out, null, active)
                        }
                    } catch (error: Throwable) { recordingError = error }
                    finally { finished.countDown() }
                }
                waitFor("Tee did not save bytes") { recording.length() >= 188 * 6 }
                keepRecording.set(false)
                assertTrue(finished.await(3, TimeUnit.SECONDS))
                assertNull(recordingError)
                assertEquals("Recording the watched channel must not open another provider stream", 1, provider.requests.get())
                assertEquals(0x47.toByte(), recording.inputStream().use { it.read().toByte() })
            } finally {
                keepRecording.set(false)
                Timeshift.stop()
            }
        }
        dir.deleteRecursively()
    }

    private class TsProvider : AutoCloseable {
        private val server = ServerSocket(0)
        private val sockets = ConcurrentHashMap.newKeySet<Socket>()
        private val closed = AtomicBoolean(false)
        val requests = AtomicInteger()
        init {
            thread(isDaemon = true, name = "test-ts-provider") {
                while (!closed.get()) {
                    val socket = try { server.accept() } catch (_: Exception) { break }
                    sockets.add(socket)
                    thread(isDaemon = true) {
                        try {
                            val input = socket.getInputStream().bufferedReader()
                            val request = input.readLine() ?: return@thread
                            while (!input.readLine().isNullOrEmpty()) { }
                            requests.incrementAndGet()
                            val marker = if (request.contains("/last")) 0x33 else 0x11
                            val packets = ByteArray(188 * 32) { marker.toByte() }
                            repeat(32) { packets[it * 188] = 0x47 }
                            val out = socket.getOutputStream()
                            out.write("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nConnection: close\r\n\r\n".toByteArray())
                            while (!closed.get()) {
                                out.write(packets)
                                out.flush()
                                Thread.sleep(10)
                            }
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
