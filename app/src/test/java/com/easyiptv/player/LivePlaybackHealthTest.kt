package com.easyiptv.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LivePlaybackHealthTest {
    @Test fun directLiveRetainsTheSuppliedHlsUri() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("live-health", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("simple_mode", true).commit()
        val uri = "http://127.0.0.1:9/provider/live.m3u8"
        try {
            val player = Playback.open(context, prefs, listOf(Playable("HLS", uri, true)), 0, null, false)
            assertEquals("Direct playback must not invent a provider .ts endpoint", uri,
                player.currentMediaItem?.localConfiguration?.uri.toString())
            assertFalse("Ordinary live viewing must not create disk history", Timeshift.active)
        } finally { Playback.releaseAll() }
    }

    @Test fun automaticLiveRetryDoesNotEraseItsFailureBudget() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("live-health", Context.MODE_PRIVATE)
        val retries = Playback.javaClass.getDeclaredField("retriesP").apply { isAccessible = true }
        try {
            Playback.open(context, prefs, listOf(Playable("Live", "http://127.0.0.1:9/live.ts", true)), 0, null, false)
            retries.setInt(Playback, 5)
            Playback.zapTo(0, preserveDirect = true)
            assertEquals("The next failure must reach the retry limit instead of starting over", 5,
                retries.getInt(Playback))
            Playback.zapTo(0)
            assertEquals("An explicit channel selection gets a fresh budget", 0, retries.getInt(Playback))
        } finally { Playback.releaseAll() }
    }

    @Test fun advancingAudioWithFrozenVideoTriggersOnlyAfterAConfirmedStall() {
        val monitor = monitor()
        assertFalse(sample(monitor, 0, 0, 10))
        assertFalse(sample(monitor, 5_000, 5_000, 10))
        assertFalse(sample(monitor, 10_000, 10_000, 10))
        assertTrue(sample(monitor, 15_000, 15_000, 10))
        assertFalse("A recovery must not trigger on every poll", sample(monitor, 20_000, 20_000, 10))
    }

    @Test fun normalFramesAndMotionlessPlaybackDoNotTriggerRecovery() {
        val monitor = monitor()
        assertFalse(sample(monitor, 0, 0, 10))
        assertFalse(sample(monitor, 20_000, 20_000, 20))
        assertFalse(sample(monitor, 40_000, 40_000, 30))
        assertFalse(sample(monitor, 60_000, 40_000, 30))
        assertFalse(sample(monitor, 80_000, 40_000, 30))
    }

    @Test fun ineligiblePlaybackClearsTheStallObservation() {
        for (reason in listOf("paused", "background", "buffering", "detached surface", "audio only")) {
            val monitor = monitor()
            assertFalse(sample(monitor, 0, 0, 10))
            assertFalse(reason, sample(monitor, 20_000, 20_000, 10, eligible = false))
            assertFalse("$reason must receive a fresh grace interval", sample(monitor, 21_000, 21_000, 10))
        }
    }

    @Test fun cooldownAndBudgetBoundRepeatedVideoRecovery() {
        val monitor = monitor()
        assertFalse(sample(monitor, 0, 0, 10))
        assertTrue(sample(monitor, 15_000, 15_000, 10))
        assertFalse(sample(monitor, 30_000, 30_000, 10))
        assertTrue(sample(monitor, 45_000, 45_000, 10))
        assertFalse("Two ineffective recoveries must not loop forever", sample(monitor, 90_000, 90_000, 10))
        assertFalse("A new channel needs its own first observation", sample(monitor, 100_000, 0, 1, generation = 2))
        assertTrue("A new channel has a fresh recovery budget", sample(monitor, 115_000, 15_000, 1, generation = 2))
    }

    @Test fun startupWithoutAnyVideoFrameIsNotMistakenForAStoppedPicture() {
        val monitor = monitor()
        assertFalse(sample(monitor, 0, 0, 0))
        assertFalse(sample(monitor, 30_000, 30_000, 0))
    }

    @Test fun sustainedAudioOnlyPlaybackEarnsAFreshRetryBudget() {
        val retries = Playback.javaClass.getDeclaredField("retriesP").apply { isAccessible = true }
        try {
            retries.setInt(Playback, 5)
            noteAudioProgress(0, 0)
            noteAudioProgress(5_000, 5_000)
            noteAudioProgress(10_000, 10_000)
            noteAudioProgress(15_000, 15_000)
            assertEquals("A brief READY interval must not erase accumulated failures", 5, retries.getInt(Playback))
            noteAudioProgress(20_000, 20_000)
            assertEquals("Sustained advancing audio-only playback must earn a fresh retry budget", 0,
                retries.getInt(Playback))
        } finally { Playback.releaseAll() }
    }

    @Test fun shortAudioStartsAndStalledAudioKeepTheRetryBudgetBounded() {
        val retries = Playback.javaClass.getDeclaredField("retriesP").apply { isAccessible = true }
        try {
            retries.setInt(Playback, 5)
            for (attempt in 0L..5L) {
                val start = attempt * 10_000L
                noteAudioProgress(start, 0)
                noteAudioProgress(start + 5_000, 5_000)
                noteAudioProgress(start + 7_000, 5_000, eligible = false)
                assertEquals("Repeated short starts must still reach the error limit", 5, retries.getInt(Playback))
            }
            noteAudioProgress(70_000, 5_000)
            noteAudioProgress(100_000, 5_000)
            assertEquals("READY without position progress is not healthy audio playback", 5,
                retries.getInt(Playback))
        } finally { Playback.releaseAll() }
    }

    @Test fun detachedVideoCannotResetFailuresUsingOnlyItsAudioClock() {
        val retries = Playback.javaClass.getDeclaredField("retriesP").apply { isAccessible = true }
        try {
            retries.setInt(Playback, 5)
            // The controller marks selected-video playback ineligible for the
            // audio-only health path, including when its surface is invisible.
            noteAudioProgress(0, 0, eligible = false)
            noteAudioProgress(30_000, 30_000, eligible = false)
            assertEquals(5, retries.getInt(Playback))
        } finally { Playback.releaseAll() }
    }

    private fun noteAudioProgress(now: Long, position: Long, eligible: Boolean = true) {
        val method = Playback.javaClass.declaredMethods.firstOrNull { it.name == "noteAudioPlaybackProgress" }
        assertNotNull("Audio-only playback needs a sustained-progress retry reset", method)
        method!!.isAccessible = true
        method.invoke(Playback, now, eligible, position)
    }

    // Reflection allows the initial red run to report a missing behavior as an
    // assertion, while keeping the test source compilable before the policy exists.
    private fun monitor(): Any {
        val type = runCatching { Class.forName("com.easyiptv.player.VideoHealthMonitor") }.getOrNull()
        assertNotNull("Video-only stalls need a bounded health policy", type)
        return type!!.getDeclaredConstructor().newInstance()
    }

    private fun sample(monitor: Any, now: Long, position: Long, frames: Long,
                       eligible: Boolean = true, generation: Long = 1): Boolean =
        monitor.javaClass.getDeclaredMethod("sample", Long::class.javaPrimitiveType,
            Long::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
            Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
            .invoke(monitor, now, generation, eligible, position, frames) as Boolean
}
