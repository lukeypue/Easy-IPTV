package com.easyiptv.player

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class RecordingOwnershipTest {
    @Test fun startWaitsForACancelledWriterToFinishCleanup() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("easyiptv",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val service=Robolectric.buildService(RecordingService::class.java).create()
        val entered=java.util.concurrent.CountDownLatch(1)
        val finish=java.util.concurrent.CountDownLatch(1)
        val scope=CoroutineScope(Dispatchers.IO)
        val old=scope.launch(start=CoroutineStart.UNDISPATCHED) {
            try {awaitCancellation()} finally {withContext(NonCancellable) {entered.countDown();finish.await(5,java.util.concurrent.TimeUnit.SECONDS)}}
        }
        try {
            old.cancel()
            // Ensure coroutine entered before cancellation when using lazy worker scheduling.
            if(!entered.await(500,java.util.concurrent.TimeUnit.MILLISECONDS)) return run { fail("Cleanup barrier not entered") }
            RecordingService::class.java.getDeclaredField("job").apply {isAccessible=true}.set(service.get(),old)
            service.get().onStartCommand(Intent().setAction(RecordingService.ACTION_START)
                .putExtra("url","https://example.invalid/new.ts").putExtra("name","new").putExtra("stopAt",System.currentTimeMillis()+60_000),0,2)
            assertNull("A new writer must not replace one still cleaning up",RecordingRecovery.load(prefs))
        } finally {finish.countDown();scope.cancel();service.get().onStartCommand(Intent().setAction(RecordingService.ACTION_STOP),0,3);service.destroy()}
    }
}
