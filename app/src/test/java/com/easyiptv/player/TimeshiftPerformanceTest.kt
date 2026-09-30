package com.easyiptv.player

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TimeshiftPerformanceTest {
    private fun packets(count: Int) = ByteArray(188 * count).also {
        for (i in 0 until count) it[i * 188] = 0x47
    }

    @Test fun channelChangeDoesNotWaitForStorageProbe() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val caller = Executors.newSingleThreadExecutor()
        val slowStorage = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getExternalFilesDirs(type: String?): Array<File> {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return emptyArray()
            }
        }
        try {
            val start = caller.submit<Boolean> { Timeshift.start(slowStorage, "http://127.0.0.1:1/live.ts") }
            assertTrue("Storage probe did not run", entered.await(3, TimeUnit.SECONDS))
            try {
                assertTrue(start.get(300, TimeUnit.MILLISECONDS))
            } catch (_: TimeoutException) {
                fail("Changing channels blocked the caller behind the storage probe")
            }
        } finally {
            release.countDown()
            caller.shutdown()
            caller.awaitTermination(5, TimeUnit.SECONDS)
            Timeshift.stop()
        }
    }

    @Test fun bufferStatusDoesNotWaitForSlowUsbWrite() {
        val dir = Files.createTempDirectory("ryzod-slow-usb").toFile()
        val ring = TimeshiftRing.open(dir, 3_600_000, 32L * 1024 * 1024)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        try {
            ring.append(packets(3), 188 * 3)
            // Replace only the physical output with a deliberately stalled disk;
            // all ring locking, metadata publication and reader code remains real.
            val field = TimeshiftRing::class.java.getDeclaredField("currentOut").apply { isAccessible = true }
            (field.get(ring) as FileOutputStream).close()
            val file = dir.listFiles()!!.single()
            field.set(ring, object : FileOutputStream(file, true) {
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    super.write(bytes, offset, length)
                }
            })
            val write = workers.submit { ring.append(packets(3), 188 * 3) }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            val status = workers.submit<TimeshiftRing.RingSnapshot> { ring.snapshot() }
            try {
                assertEquals(564L, status.get(300, TimeUnit.MILLISECONDS).newestVirtualByte)
            } catch (_: TimeoutException) {
                fail("Remote/UI buffer status blocked behind a USB write")
            } finally {
                release.countDown()
                write.get(3, TimeUnit.SECONDS)
            }
            assertEquals(1128L, ring.snapshot().newestVirtualByte)
        } finally {
            release.countDown()
            workers.shutdown()
            workers.awaitTermination(5, TimeUnit.SECONDS)
            ring.close()
            dir.deleteRecursively()
        }
    }

    @Test fun oldChannelReaderEndsWhenRingCloses() {
        val dir = Files.createTempDirectory("ryzod-closed-ring").toFile()
        val ring = TimeshiftRing.open(dir, 3_600_000, 32L * 1024 * 1024)
        try {
            ring.append(packets(3), 564)
            ring.openReader(564).use { reader ->
                ring.close()
                assertEquals("Old channel reader must end instead of polling forever", -1, reader.read(ByteArray(188)))
            }
        } finally {
            ring.close()
            dir.deleteRecursively()
        }
    }
}
