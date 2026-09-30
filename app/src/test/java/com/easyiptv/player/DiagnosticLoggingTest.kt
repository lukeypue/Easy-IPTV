package com.easyiptv.player

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
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
class DiagnosticLoggingTest {
    @Test fun ordinaryDiagnosticsDoNotBlockNavigationOnStorage() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val caller = Executors.newSingleThreadExecutor()
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        val slowStorage = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return app.filesDir
            }
        }
        try {
            val navigation = caller.submit { StabilityCore.install(slowStorage); StabilityCore.noteScreen("live") }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            try { navigation.get(300, TimeUnit.MILLISECONDS) }
            catch (_: TimeoutException) { fail("Normal diagnostics blocked navigation behind a disk operation") }
        } finally {
            release.countDown()
            caller.shutdown()
            caller.awaitTermination(5, TimeUnit.SECONDS)
            Thread.setDefaultUncaughtExceptionHandler(oldHandler)
        }
    }
}
