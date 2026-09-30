package com.easyiptv.player

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class TimeshiftRingRegressionTest {
    private val packets = ByteArray(188 * 1024) { 0x22 }.also {
        repeat(1024) { index -> it[index * 188] = 0x47 }
    }

    @Test fun readerCrossesSegmentBoundaryWithoutLosingRecordedBytes() {
        val dir = Files.createTempDirectory("ryzod-ring-crossing").toFile()
        val ring = TimeshiftRing.open(dir, 3_600_000, 32L * 1024 * 1024)
        try {
            repeat(48) { ring.append(packets, packets.size) }
            assertEquals(2, ring.snapshot().segmentCount)
            ring.openReader(0).use { reader ->
                val buffer = ByteArray(188 * 512)
                var total = 0L
                while (true) {
                    val n = reader.read(buffer)
                    if (n == 0) break
                    assertTrue(n > 0)
                    for (i in 0 until n) {
                        val expected = if ((total + i) % 188 == 0L) 0x47.toByte() else 0x22.toByte()
                        assertEquals(expected, buffer[i])
                    }
                    total += n
                }
                assertEquals(9_240_576L, total)
            }
        } finally { ring.close(); dir.deleteRecursively() }
    }

    @Test fun longHistoryKeepsBoundedDiskAndClampsRewindToRetainedData() {
        val dir = Files.createTempDirectory("ryzod-ring-retention").toFile()
        val ring = TimeshiftRing.open(dir, 3_600_000, 16L * 1024 * 1024)
        try {
            var previousNewest = 0L
            repeat(600) {
                ring.append(packets, packets.size)
                val snapshot = ring.snapshot()
                assertTrue(snapshot.newestVirtualByte > previousNewest)
                assertTrue(snapshot.totalBytesOnDisk <= 16L * 1024 * 1024)
                previousNewest = snapshot.newestVirtualByte
            }
            val snapshot = ring.snapshot()
            assertTrue(snapshot.oldestVirtualByte > 0)
            ring.openReader(0).use { reader ->
                assertEquals(snapshot.oldestVirtualByte, reader.virtualPosition())
                val bytes = ByteArray(188)
                assertEquals(188, reader.read(bytes))
                assertEquals(0x47.toByte(), bytes[0])
            }
            assertTrue(dir.listFiles()!!.size <= 3)
        } finally { ring.close(); dir.deleteRecursively() }
    }

    @Test fun pinnedReaderProtectsItsSegmentUntilReleaseThenReclaimsHistory() {
        val dir = Files.createTempDirectory("ryzod-ring-lease").toFile()
        val ring = TimeshiftRing.open(dir, 3_600_000, 8L * 1024 * 1024)
        try {
            ring.append(packets, packets.size)
            val reader = ring.openReader(0)
            repeat(50) { ring.append(packets, packets.size) }
            assertEquals("An active recording reader keeps its segment", 0L, ring.snapshot().oldestVirtualByte)
            val bytes = ByteArray(188)
            assertEquals(188, reader.read(bytes))
            assertEquals(0x47.toByte(), bytes[0])
            reader.close()
            val snapshot = ring.snapshot()
            assertTrue(snapshot.oldestVirtualByte > 0L)
            assertTrue("Lease release must immediately reclaim excess history", snapshot.totalBytesOnDisk <= 8L * 1024 * 1024)
        } finally { ring.close(); dir.deleteRecursively() }
    }

}
