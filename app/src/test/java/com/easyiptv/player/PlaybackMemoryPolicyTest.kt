package com.easyiptv.player

import androidx.annotation.OptIn

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.FixedTrackSelection
import androidx.media3.exoplayer.upstream.Allocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Uses Media3 1.9's real per-player allocator and loading decisions, not a policy imitation. */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlaybackMemoryPolicyTest {
    @Test fun longLiveCushionMustNotDelayMoviesOrSavedFiles() {
        val ctx=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs=ctx.getSharedPreferences("vod-cushion",android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().putInt("live_start_ms",30_000).commit()
        try {
            val ensure=Playback.javaClass.getDeclaredMethod("ensurePlayer",android.content.Context::class.java,android.content.SharedPreferences::class.java).apply {isAccessible=true}
            val p=ensure.invoke(Playback,ctx,prefs)
            val internal=p.javaClass.getDeclaredField("internalPlayer").apply {isAccessible=true}.get(p)
            val control=internal.javaClass.getDeclaredField("loadControl").apply {isAccessible=true}.get(internal)
            withPlayer(control as LoadControl,"http://127.0.0.1/movie.mp4") { f ->
                assertTrue("Live 30s choice must not impose a 30s movie wait",f.startPlayback(4_000_000L))
                assertTrue("Live recovery must not delay a movie",f.startPlayback(4_000_000L,rebuffering=true))
            }
        } finally { Playback.releaseAll() }
    }
    @Test fun configuredThirtySecondLiveWaitAndFiniteHlsWindowRemainSafe() {
        configuredControl(false,30_000,60) { control ->
            withPlayer(control) { f ->
                assertFalse(f.startPlayback(29_999_999L))
                assertTrue(f.startPlayback(30_000_000L))
                assertFalse(f.startPlayback(4_999_999L,targetLiveOffsetUs=10_000_000L))
                assertTrue("Short provider window must not deadlock on an unavailable 30s",f.startPlayback(5_000_000L,targetLiveOffsetUs=10_000_000L))
            }
        }
    }
    @Test fun configuredZeroWaitStartsWithoutAnArtificialLiveCushion() {
        configuredControl(true,30_000,0) { control ->
            withPlayer(control) { f ->
                assertTrue(f.startPlayback(0L))
                assertTrue(f.startPlayback(0L,rebuffering=true))
            }
        }
    }
    @Test fun liveLoadingRefillsBeforeTheTenSecondCushionDrains() {
        configuredControl(false) { control ->
            withPlayer(control) { f ->
                assertFalse(f.continueLoading(60_000_000L))
                assertTrue("Resume loading well before the 10s startup reserve is spent", f.continueLoading(20_000_000L))
            }
        }
    }

    @Test fun steadyActuallyRebuildsMoreReserveThanNaturalAtTenSeconds() {
        configuredControl(false) { control ->
            withPlayer(control) { f -> assertTrue(f.startPlayback(20_000_000L, rebuffering=true)) }
        }
        configuredControl(true) { control ->
            withPlayer(control) { f ->
                assertFalse("Steady must not collapse to Natural's 10s rebuffer threshold", f.startPlayback(20_000_000L, rebuffering=true))
                assertTrue(f.startPlayback(30_000_000L, rebuffering=true))
            }
        }
    }

    private fun configuredControl(steady: Boolean, lockMs: Int = 10_000, bufferSec: Int = 10, block: (LoadControl) -> Unit) {
        val ctx=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs=ctx.getSharedPreferences("configured-buffer",android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().putInt("buffer_sec",bufferSec).putInt("live_start_ms",lockMs)
            .putBoolean("live_steady_recovery",steady).commit()
        try {
            val method=Playback.javaClass.getDeclaredMethod("ensurePlayer",android.content.Context::class.java,android.content.SharedPreferences::class.java)
            method.isAccessible=true
            val p=method.invoke(Playback,ctx,prefs)
            val internal=p.javaClass.getDeclaredField("internalPlayer").apply {isAccessible=true}.get(p)
            val control=internal.javaClass.getDeclaredField("loadControl").apply {isAccessible=true}.get(internal)
            block(control as LoadControl)
        } finally {Playback.releaseAll()}
    }

    @Test
    fun lowRamStopsAt32MiBEvenBelowTheTimeMinimumThenResumesAfterRelease() {
        for (uri in listOf("http://127.0.0.1/live.ts", "file:///recording.ts")) {
            withPlayer(PlaybackMemoryPolicy.create(true, 60_000, 60_000, 12_000, 20_000), uri) { f ->
                f.allocateBytes(32 * 1024 * 1024 - C.DEFAULT_BUFFER_SEGMENT_SIZE)
                assertTrue("Below the byte target, continue filling the reserve", f.continueLoading(2_000_000L))
                assertFalse("Below both targets, do not start early", f.startPlayback(2_000_000L))

                f.allocateBytes(C.DEFAULT_BUFFER_SEGMENT_SIZE)
                assertEquals(33_554_432, f.allocator.totalBytesAllocated)
                assertFalse("The 60-second preference must not override the low-RAM byte target", f.continueLoading(2_000_000L))

                f.releaseOne()
                assertTrue("Loading must resume as playback releases sample allocations", f.continueLoading(2_000_000L))
            }
        }
    }

    @Test
    fun reachingTheByteTargetAllowsStartAndRebufferWithoutWaitingForMoreBytes() {
        withPlayer(PlaybackMemoryPolicy.create(true, 60_000, 60_000, 30_000, 50_000)) { f ->
            assertFalse(f.startPlayback(2_000_000L))
            assertFalse(f.startPlayback(2_000_000L, rebuffering = true))
            f.allocateBytes(32 * 1024 * 1024)
            assertTrue("Size-priority must not leave a full allocator waiting for a time target", f.startPlayback(2_000_000L))
            assertTrue("The same escape is required after a rebuffer", f.startPlayback(2_000_000L, rebuffering = true))
            assertFalse(f.continueLoading(2_000_000L))
        }
    }

    @Test
    fun higherRamKeepsTrackSizedBufferAndTimePriority() {
        withPlayer(PlaybackMemoryPolicy.create(false, 30_000, 60_000, 12_000, 20_000)) { f ->
            f.allocateBytes(32 * 1024 * 1024)
            assertTrue(f.continueLoading(2_000_000L))
            assertTrue("Video's track-sized target remains larger than the low-RAM target", f.continueLoading(30_000_000L))
            assertFalse("High-RAM playback still waits for its requested startup cushion", f.startPlayback(2_000_000L))
        }
        // Audio's default target is below 20 MiB. This also distinguishes the
        // preserved automatic target from accidentally applying 32 MiB to all devices.
        withPlayer(
            PlaybackMemoryPolicy.create(false, 30_000, 60_000, 12_000, 20_000),
            mimeType = MimeTypes.AUDIO_AAC
        ) { f ->
            f.allocateBytes(20 * 1024 * 1024)
            assertTrue("High-RAM time priority still loads below the minimum", f.continueLoading(2_000_000L))
            assertFalse("The automatic audio target has been reached", f.continueLoading(30_000_000L))
            assertFalse("Reaching the byte target must not change high-RAM startup", f.startPlayback(2_000_000L))
        }
    }

    @Test
    fun startupRebufferDurationSettingsRemainEffectiveBelowByteTarget() {
        // Hand-checked normal, steady, small-buffer-clamped and maximum UI settings.
        val durations = listOf(
            intArrayOf(30_000, 60_000, 4_000, 4_000),
            intArrayOf(30_000, 60_000, 6_000, 12_000),
            intArrayOf(10_000, 30_000, 10_000, 10_000),
            intArrayOf(60_000, 60_000, 12_000, 20_000),
            intArrayOf(60_000, 60_000, 20_000, 40_000),
            intArrayOf(60_000, 60_000, 30_000, 50_000),
            intArrayOf(5_000, 5_000, 0, 0)
        )
        for (lowRam in listOf(true, false)) {
            for (uri in listOf("http://127.0.0.1/live.ts", "file:///recording.ts")) {
                for ((minMs, maxMs, startMs, rebufferMs) in durations) {
                    withPlayer(PlaybackMemoryPolicy.create(lowRam, minMs, maxMs, startMs, rebufferMs), uri) { f ->
                        if (startMs > 0) assertFalse(f.startPlayback(startMs * 1000L - 1))
                        assertTrue(f.startPlayback(startMs * 1000L))
                        if (rebufferMs > 0) assertFalse(f.startPlayback(rebufferMs * 1000L - 1, rebuffering = true))
                        assertTrue(f.startPlayback(rebufferMs * 1000L, rebuffering = true))
                        assertTrue(f.continueLoading(0L))
                        assertTrue(f.continueLoading(maxMs * 1000L - 1))
                        assertFalse(f.continueLoading(maxMs * 1000L))
                        assertTrue(f.continueLoading(minMs * 1000L - 1))
                    }
                }
            }
        }
    }

    @Test
    fun lowRamLeavesSampleBudgetForForwardBufferWhileHigherRamKeepsFastBackBuffer() {
        withPlayer(PlaybackMemoryPolicy.create(true, 30_000, 60_000, 4_000, 4_000)) { f ->
            assertEquals("Past samples must not crowd out the sample budget", 0L,
                f.control.getBackBufferDurationUs(f.playerId))
            assertFalse(f.control.retainBackBufferFromKeyframe(f.playerId))
        }
        withPlayer(PlaybackMemoryPolicy.create(false, 30_000, 60_000, 4_000, 4_000)) { f ->
            assertEquals(10_000_000L, f.control.getBackBufferDurationUs(f.playerId))
            assertFalse(f.control.retainBackBufferFromKeyframe(f.playerId))
        }
    }

    private fun withPlayer(
        control: LoadControl,
        uri: String = "http://127.0.0.1/live.ts",
        mimeType: String = MimeTypes.VIDEO_H264,
        block: (PlayerFixture) -> Unit
    ) {
        val fixture = PlayerFixture(control, uri, mimeType)
        try {
            block(fixture)
        } finally {
            fixture.close()
        }
    }

    private class PlayerFixture(val control: LoadControl, uri: String, mimeType: String) {
        val playerId = PlayerId("memory-policy-test")
        private val timeline = SinglePeriodTimeline(
            120_000_000L, true, true, false, null, MediaItem.Builder().setUri(uri).setTag(if(uri.endsWith("live.ts")) LIVE_BUFFER_TAG else null).build()
        )
        private val periodId = MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0))
        val allocator = control.getAllocator(playerId)
        private val allocations = ArrayList<Allocation>()

        init {
            control.onPrepared(playerId)
            val tracks = TrackGroup(Format.Builder().setSampleMimeType(mimeType).build())
            control.onTracksSelected(parameters(0L), TrackGroupArray(tracks), arrayOf(FixedTrackSelection(tracks, 0)))
        }

        fun allocateBytes(bytes: Int) {
            require(bytes % allocator.individualAllocationLength == 0)
            repeat(bytes / allocator.individualAllocationLength) { allocations.add(allocator.allocate()) }
        }

        fun releaseOne() {
            allocator.release(allocations.removeAt(allocations.lastIndex))
        }

        fun continueLoading(bufferedUs: Long) = control.shouldContinueLoading(parameters(bufferedUs))

        fun startPlayback(bufferedUs: Long, rebuffering: Boolean = false, targetLiveOffsetUs: Long = C.TIME_UNSET) =
            control.shouldStartPlayback(parameters(bufferedUs, rebuffering, targetLiveOffsetUs))

        private fun parameters(bufferedUs: Long, rebuffering: Boolean = false, targetLiveOffsetUs: Long = C.TIME_UNSET) = LoadControl.Parameters(
            playerId, timeline, periodId, 0L, bufferedUs, 1f, true, rebuffering, targetLiveOffsetUs, C.TIME_UNSET
        )

        fun close() {
            allocations.forEach { allocator.release(it) }
            allocations.clear()
            control.onReleased(playerId)
        }
    }
}
