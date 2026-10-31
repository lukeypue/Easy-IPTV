package com.easyiptv.player

import android.content.Context
import android.os.Looper
import androidx.lifecycle.repeatOnLifecycle
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EpgPersistenceTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Before fun setup() { EpgStore.clear(); java.io.File(context.filesDir, "epg_snapshots").deleteRecursively() }
    @After fun cleanup() { EpgStore.clear(); java.io.File(context.filesDir, "epg_snapshots").deleteRecursively() }

    @Test fun successfulLoadSurvivesMemoryLossAndFreshLoadAvoidsNetwork() = onStore {
        Provider().use { provider ->
            EpgStore.restore(context, provider.url)
            EpgStore.load(provider.url)
            assertEquals(listOf("Saved show"), titles())
            EpgStore.clear()
            EpgStore.restore(context, provider.url)
            assertEquals(listOf("Saved show"), titles())
            EpgStore.load(provider.url)
            assertEquals(1, provider.requests.get())
            val files = java.io.File(context.filesDir, "epg_snapshots").listFiles()!!.toList()
            assertTrue(files.isNotEmpty())
            assertTrue(files.none { it.name.contains("secret") || it.readBytes().toString(Charsets.ISO_8859_1).contains(provider.url) })
        }
    }

    @Test fun failedForcedRefreshKeepsRestoredRows() = onStore {
        Provider().use { provider ->
            EpgStore.restore(context, provider.url); EpgStore.load(provider.url)
            EpgStore.clear(); EpgStore.restore(context, provider.url)
            provider.fail = true
            EpgStore.load(provider.url, force = true)
            assertEquals(listOf("Saved show"), titles())
            assertTrue(EpgStore.loaded.value)
            assertFalse(EpgStore.loading.value)
            assertTrue(EpgStore.status.value.startsWith("Guide update failed:"))
        }
    }

    @Test fun sourceSwitchIsolatesRowsAndCanRestoreTheOriginalProvider() = onStore {
        Provider().use { provider ->
            EpgStore.restore(context, provider.url); EpgStore.load(provider.url)
            EpgStore.restore(context, provider.url)
            assertEquals(listOf("Saved show"), titles())
            EpgStore.restore(context, provider.url + "&other=1")
            assertTrue(titles().isEmpty()); assertFalse(EpgStore.loaded.value)
            EpgStore.restore(context, provider.url)
            assertEquals(listOf("Saved show"), titles())
            EpgStore.restore(context, null)
            assertTrue(titles().isEmpty())
        }
    }

    @Test fun corruptSnapshotIsIgnored() = onStore {
        val url = "https://provider.invalid/xmltv?password=secret"
        val cache = EpgSnapshotCache(java.io.File(context.filesDir, "epg_snapshots"))
        cache.directory.mkdirs()
        java.io.File(cache.directory, EpgSnapshotCache.key(url) + ".epg").writeText("broken snapshot")
        EpgStore.restore(context, url)
        assertFalse(EpgStore.loaded.value); assertTrue(titles().isEmpty())
    }

    @Test fun expiredProgramsAreNeverRestoredAsCurrentGuide() = onStore {
        val url = "https://provider.invalid/xmltv"
        val cache = EpgSnapshotCache(java.io.File(context.filesDir, "epg_snapshots"))
        val now = System.currentTimeMillis()
        val snapshot = EpgSnapshotCache.Snapshot(now - 7 * 60 * 60_000L,
            mapOf("channel" to listOf(EpgEntry("Expired", "", now - 120_000, now - 60_000))),
            mapOf("channel" to "channel"), mapOf("channel" to "Channel"))
        cache.prepare(url, snapshot).let { cache.commit(url, it) }
        EpgStore.restore(context, url)
        assertTrue(titles().isEmpty()); assertFalse(EpgStore.loaded.value)
        assertTrue(EpgStore.search("Expired").isEmpty())
    }

    @Test fun staleButUsefulSnapshotRemainsReadableDuringRefreshAndFailure() = onStore {
        Provider().use { provider ->
            val now = System.currentTimeMillis()
            val cache = EpgSnapshotCache(java.io.File(context.filesDir, "epg_snapshots"))
            val snapshot = EpgSnapshotCache.Snapshot(now - 7 * 60 * 60_000L,
                mapOf("channel" to listOf(EpgEntry("Cached", "", now - 60_000, now + 600_000))),
                mapOf("channel" to "channel"), mapOf("channel" to "Channel"))
            cache.prepare(provider.url, snapshot).let { cache.commit(provider.url, it) }
            EpgStore.restore(context, provider.url)
            provider.fail = true
            EpgStore.load(provider.url)
            assertEquals(1, provider.requests.get())
            assertEquals(listOf("Cached"), titles())
        }
    }

    @Test fun rowsRemainVisibleWhileRefreshIsWaitingForTheProvider() = onStore {
        Provider().use { provider ->
            EpgStore.restore(context, provider.url); EpgStore.load(provider.url)
            provider.hold = true
            val refresh = async { EpgStore.load(provider.url, force = true) }
            try {
                withTimeout(3000) { while (provider.requests.get() < 2) delay(5) }
                assertTrue(EpgStore.loading.value)
                assertEquals(listOf("Saved show"), titles())
            } finally { provider.release.countDown(); refresh.await() }
        }
    }

    @Test fun switchedSourceCannotPublishOrPersistAnOlderInflightResponse() = onStore {
        Provider().use { provider ->
            EpgStore.restore(context, provider.url)
            provider.hold = true
            val old = async { EpgStore.load(provider.url) }
            try {
                withTimeout(3000) { while (provider.requests.get() == 0) delay(5) }
                EpgStore.restore(context, provider.url + "&provider=other")
                provider.release.countDown()
                old.await()
                assertTrue(titles().isEmpty())
                assertFalse(EpgStore.loaded.value)
                EpgStore.clear()
                EpgStore.restore(context, provider.url)
                assertTrue("An obsolete response must not reach disk", titles().isEmpty())
            } finally { provider.release.countDown(); old.cancelAndJoin() }
        }
    }

    @Test fun cancellingRefreshKeepsRowsAndResetsLoading() = onStore {
        Provider().use { provider ->
            EpgStore.restore(context, provider.url); EpgStore.load(provider.url)
            provider.hold = true
            val refresh = async { EpgStore.load(provider.url, force = true) }
            try {
                withTimeout(3000) { while (provider.requests.get() < 2) delay(5) }
                withTimeout(2000) { refresh.cancelAndJoin() }
                assertEquals(listOf("Saved show"), titles())
                assertFalse(EpgStore.loading.value)
            } finally { provider.release.countDown(); refresh.cancelAndJoin() }
        }
    }

    @Test fun browsingRefreshesWhenFreshSnapshotExpiresWithoutNavigation() = onStore {
        Provider().use { provider ->
            EpgStore.restore(context, provider.url); EpgStore.load(provider.url)
            setSavedAt(System.currentTimeMillis() - EpgSnapshotCache.FRESH_MS + 250)
            val browsing = launch { EpgStore.refreshWhileBrowsing(provider.url) }
            try {
                withTimeout(3000) { while (provider.requests.get() < 2 || EpgStore.loading.value) delay(5) }
                assertEquals(listOf("Saved show"), titles())
                assertEquals(2, provider.requests.get())
            } finally { browsing.cancelAndJoin() }
        }
    }

    @Test fun stoppingBrowsingCancelsTimerAndResumeImmediatelyRechecksStaleSnapshot() = onStore {
        Provider().use { provider ->
            EpgStore.restore(context, provider.url); EpgStore.load(provider.url)
            val browsing = launch { EpgStore.refreshWhileBrowsing(provider.url) }
            delay(50)
            browsing.cancelAndJoin()
            setSavedAt(System.currentTimeMillis() - EpgSnapshotCache.FRESH_MS - 1)
            delay(100)
            assertEquals("Stopped browsing must not refresh", 1, provider.requests.get())
            assertEquals(listOf("Saved show"), titles())
            val resumed = launch { EpgStore.refreshWhileBrowsing(provider.url) }
            try {
                withTimeout(3000) { while (provider.requests.get() < 2 || EpgStore.loading.value) delay(5) }
                assertEquals(2, provider.requests.get())
            } finally { resumed.cancelAndJoin() }
        }
    }

    @Test fun lifecycleResumeRechecksFreshnessAndPauseStopsTheBrowsingLoop() = onStore {
        Provider().use { provider ->
            EpgStore.restore(context, provider.url); EpgStore.load(provider.url)
            val owner = withContext(Dispatchers.Main) {
                object : androidx.lifecycle.LifecycleOwner {
                    override val lifecycle = androidx.lifecycle.LifecycleRegistry(this)
                }.also { it.lifecycle.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
            }
            val lifecycleJob = launch(Dispatchers.Main) {
                owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                    EpgStore.refreshWhileBrowsing(provider.url)
                }
            }
            try {
                delay(50)
                withContext(Dispatchers.Main) { owner.lifecycle.currentState = androidx.lifecycle.Lifecycle.State.STARTED }
                setSavedAt(System.currentTimeMillis() - EpgSnapshotCache.FRESH_MS - 1)
                delay(100)
                assertEquals(1, provider.requests.get())
                assertEquals(listOf("Saved show"), titles())
                withContext(Dispatchers.Main) { owner.lifecycle.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
                withTimeout(3000) { while (provider.requests.get() < 2 || EpgStore.loading.value) delay(5) }
                assertEquals(2, provider.requests.get())
            } finally {
                withContext(Dispatchers.Main) { owner.lifecycle.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED }
                lifecycleJob.cancelAndJoin()
            }
        }
    }

    @Test fun failedBrowsingRefreshRetainsRowsAndDoesNotSpin() = onStore {
        Provider().use { provider ->
            EpgStore.restore(context, provider.url); EpgStore.load(provider.url)
            setSavedAt(System.currentTimeMillis() - EpgSnapshotCache.FRESH_MS - 1)
            provider.fail = true
            val browsing = launch { EpgStore.refreshWhileBrowsing(provider.url) }
            try {
                withTimeout(3000) { while (provider.requests.get() < 2 || EpgStore.loading.value) delay(5) }
                delay(100)
                assertEquals(2, provider.requests.get())
                assertEquals(listOf("Saved show"), titles())
            } finally { browsing.cancelAndJoin() }
        }
    }

    @Test fun cachePrunesAgedTemporaryFilesButPreservesRecentWrites() = onStore {
        val cache = EpgSnapshotCache(java.io.File(context.filesDir, "epg_snapshots"))
        cache.directory.mkdirs()
        val orphan = java.io.File(cache.directory, "snapshot-orphan.tmp").apply {
            writeText("abandoned")
            setLastModified(System.currentTimeMillis() - 2 * 24 * 60 * 60_000L)
        }
        val recent = java.io.File(cache.directory, "snapshot-recent.tmp").apply { writeText("active") }
        withContext(Dispatchers.IO) { cache.read("https://provider.invalid/xmltv") }
        assertFalse(orphan.exists())
        assertTrue(recent.exists())
    }

    private fun setSavedAt(value: Long) {
        EpgStore::class.java.getDeclaredField("savedAt").apply { isAccessible = true }.setLong(EpgStore, value)
    }

    private fun titles() = EpgStore.guide("channel", "Channel").map { it.title }

    // Drive Android Main publications while IO performs real HTTP and disk work.
    private fun onStore(block: suspend CoroutineScope.() -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val result = scope.async(block = block)
        val deadline = System.nanoTime() + 10_000_000_000L
        try {
            while (!result.isCompleted && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(5)
            }
            assertTrue("EPG operation timed out", result.isCompleted)
            runBlocking { result.await() }
        } finally { scope.cancel() }
    }

    private class Provider : java.io.Closeable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${server.localPort}/xmltv?password=secret"
        val requests = AtomicInteger()
        @Volatile var fail = false
        @Volatile var hold = false
        val release = java.util.concurrent.CountDownLatch(1)
        private val worker = Thread {
            try {
                while (!server.isClosed) server.accept().use { socket ->
                    socket.soTimeout = 2000
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { }
                    requests.incrementAndGet()
                    if (hold) release.await(5, java.util.concurrent.TimeUnit.SECONDS)
                    val fmt = java.text.SimpleDateFormat("yyyyMMddHHmmss Z", java.util.Locale.US)
                    val now = System.currentTimeMillis()
                    val body = "<tv><channel id=\"channel\"><display-name>Channel</display-name></channel>" +
                        "<programme channel=\"channel\" start=\"${fmt.format(java.util.Date(now - 60000))}\" stop=\"${fmt.format(java.util.Date(now + 600000))}\"><title>Saved show</title></programme></tv>"
                    val bytes = body.toByteArray()
                    socket.getOutputStream().write(("HTTP/1.1 ${if (fail) 503 else 200} Test\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
                    socket.getOutputStream().write(bytes)
                }
            } catch (_: java.io.IOException) { }
        }.apply { isDaemon = true; start() }
        override fun close() { release.countDown(); server.close(); worker.join(2000) }
    }
}
