package com.easyiptv.player

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import kotlin.coroutines.CoroutineContext

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class RecordingEarlyStopTest {
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = java.util.ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    private fun field(service: RecordingService, name: String): Any? =
        RecordingService::class.java.getDeclaredField(name).apply { isAccessible = true }.get(service)

    private fun start(service: RecordingService, name: String, startId: Int) {
        service.onStartCommand(Intent().setAction(RecordingService.ACTION_START)
            .putExtra("url", "https://example.invalid/$name.ts")
            .putExtra("name", name)
            .putExtra("stopAt", System.currentTimeMillis() + 60_000), 0, startId)
    }

    private fun withQueuedService(test: (RecordingService, QueuedDispatcher) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("easyiptv", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val controller = Robolectric.buildService(RecordingService::class.java).create()
        val service = controller.get()
        val dispatcher = QueuedDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        RecordingService::class.java.getDeclaredField("scope").apply { isAccessible = true }.set(service, scope)
        try { test(service, dispatcher) } finally {
            service.onStartCommand(Intent().setAction(RecordingService.ACTION_STOP), 0, 99)
            scope.cancel()
            dispatcher.drain()
            shadowOf(Looper.getMainLooper()).idle()
            controller.destroy()
        }
    }

    @Test fun stopBeforeWriterEntersReleasesWakeLockAndStopsService() = withQueuedService { service, dispatcher ->
        val prefs = service.getSharedPreferences("easyiptv", Context.MODE_PRIVATE)
        start(service, "first", 1)
        val saved = requireNotNull(RecordingRecovery.load(prefs))
        val wakeLock = field(service, "wakeLock") as PowerManager.WakeLock
        assertTrue(wakeLock.isHeld)
        assertEquals("first", Recorder.activeName.value)
        assertFalse("The writer must still be queued", File(saved.path).exists())

        service.onStartCommand(Intent().setAction(RecordingService.ACTION_STOP), 0, 2)
        dispatcher.drain()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(RecordingRecovery.load(prefs))
        assertNull("Cancelled startup must clear recorder state", Recorder.activeName.value)
        assertNull(Recorder.activeUrl)
        assertNull(Recorder.activeSessionId)
        assertEquals(-1L, Recorder.activeScheduleId)
        assertFalse(Recorder.usesProviderConnection)
        assertFalse("Completion must release the startup wakelock", wakeLock.isHeld)
        assertTrue("Completion must stop the foreground service", shadowOf(service).isStoppedBySelf)
        assertEquals(2, shadowOf(service).stopSelfId)
        assertFalse("A cancelled queued writer must not create a recording", File(saved.path).exists())
    }

    @Test fun delayedOldCompletionDoesNotCleanUpResumedRecordingOwner() = withQueuedService { service, dispatcher ->
        val prefs = service.getSharedPreferences("easyiptv", Context.MODE_PRIVATE)
        start(service, "first", 1)
        val original = requireNotNull(RecordingRecovery.load(prefs))
        val oldWakeLock = field(service, "wakeLock") as PowerManager.WakeLock
        // Cancellation without ACTION_STOP keeps recovery eligible. The next
        // writer deliberately reuses its session ID, but has a distinct owner.
        (field(service, "job") as Job).cancel()
        dispatcher.drain() // Complete the old job, leaving its main-thread cleanup queued.
        start(service, "first", 3)
        val next = requireNotNull(RecordingRecovery.load(prefs))
        assertEquals("Recovery must reuse the persisted session", original.id, next.id)
        val newWakeLock = field(service, "wakeLock") as PowerManager.WakeLock
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse("The completed owner's own wakelock must be released", oldWakeLock.isHeld)
        assertTrue("Old completion must not release the new owner's wakelock", newWakeLock.isHeld)
        assertEquals("first", Recorder.activeName.value)
        assertEquals("https://example.invalid/first.ts", Recorder.activeUrl)
        assertEquals(next.id, Recorder.activeSessionId)
        assertEquals(next, RecordingRecovery.load(prefs))
        assertFalse("Old completion must not stop the replacement service", shadowOf(service).isStoppedBySelf)
    }
}
