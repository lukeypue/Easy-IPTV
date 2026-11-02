package com.easyiptv.player

import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.source.chunk.MediaChunkIterator
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.trackselection.FixedTrackSelection
import androidx.media3.exoplayer.upstream.BandwidthMeter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SmoothAdaptiveSelectionTest {
    @Test fun smoothLiveClimbsFrom480Through720To1080WithFourSecondsBuffered() {
        val meter = ControlledBandwidthMeter(600_000L)
        val selection = selection(factory { true }, meter, timeline(LIVE_BUFFER_TAG))
        assertTrue(selection is AdaptiveTrackSelection)

        update(selection, bufferedUs = 4_000_000L)
        assertEquals(480, selection.selectedFormat.height)
        meter.estimate = 3_000_000L
        update(selection, bufferedUs = 4_000_000L)
        assertEquals(720, selection.selectedFormat.height)
        meter.estimate = 8_000_000L
        update(selection, bufferedUs = 4_000_000L)
        assertEquals(1080, selection.selectedFormat.height)
    }

    @Test fun smoothLiveDropsTo480WhenBandwidthFallsAndBufferIsLow() {
        val meter = ControlledBandwidthMeter(8_000_000L)
        val selection = selection(factory { true }, meter, timeline(LIVE_BUFFER_TAG))
        update(selection, bufferedUs = 4_000_000L)
        assertEquals(1080, selection.selectedFormat.height)

        meter.estimate = 600_000L
        update(selection, bufferedUs = 1_000_000L)
        assertEquals(480, selection.selectedFormat.height)
    }

    @Test fun smoothLiveStillRequiresThreeSecondsBeforeIncreasingQuality() {
        val meter = ControlledBandwidthMeter(600_000L)
        val selection = selection(factory { true }, meter, timeline(LIVE_BUFFER_TAG))
        update(selection, bufferedUs = 0L)
        assertEquals(480, selection.selectedFormat.height)

        meter.estimate = 8_000_000L
        update(selection, bufferedUs = 2_999_999L)
        assertEquals(480, selection.selectedFormat.height)
        update(selection, bufferedUs = 3_000_000L)
        assertEquals(1080, selection.selectedFormat.height)
    }

    @Test fun smoothLiveKeepsHighQualityAboveFiveSecondsButCanDropAtFourSeconds() {
        val meter = ControlledBandwidthMeter(8_000_000L)
        val selection = selection(factory { true }, meter, timeline(LIVE_BUFFER_TAG))
        update(selection, bufferedUs = 6_000_000L)
        assertEquals(1080, selection.selectedFormat.height)

        meter.estimate = 600_000L
        update(selection, bufferedUs = 6_000_000L)
        assertEquals(1080, selection.selectedFormat.height)
        update(selection, bufferedUs = 4_000_000L)
        assertEquals(480, selection.selectedFormat.height)
    }

    @Test fun vodWithSmoothEnabledKeepsDefaultTenSecondUpshiftGate() {
        assertDefaultUpshiftGate(factory { true }, timeline(tag = null, live = false))
    }

    @Test fun taggedLiveWithSmoothDisabledKeepsDefaultTenSecondUpshiftGate() {
        assertDefaultUpshiftGate(factory { false }, timeline(LIVE_BUFFER_TAG))
    }

    @Test fun liveWithDifferentTagKeepsDefaultTenSecondUpshiftGate() {
        assertDefaultUpshiftGate(factory { true }, timeline("another-feature"))
    }

    @Test fun emptyTimelineKeepsDefaultTenSecondUpshiftGate() {
        assertDefaultUpshiftGate(factory { true }, Timeline.EMPTY)
    }

    @Test fun smoothPreferenceIsReadAgainForNewSelections() {
        var smooth = false
        val factory = factory { smooth }
        val live = timeline(LIVE_BUFFER_TAG)
        assertDefaultUpshiftGate(factory, live)

        smooth = true
        val meter = ControlledBandwidthMeter(600_000L)
        val selection = selection(factory, meter, live)
        update(selection, bufferedUs = 4_000_000L)
        assertEquals(480, selection.selectedFormat.height)
        meter.estimate = 3_000_000L
        update(selection, bufferedUs = 4_000_000L)
        assertEquals(720, selection.selectedFormat.height)

        smooth = false
        assertDefaultUpshiftGate(factory, live)
    }

    @Test fun singleRenditionRemainsFixedRegardlessOfBandwidth() {
        val meter = ControlledBandwidthMeter(600_000L)
        val fixedGroup = TrackGroup("fixed", format(480, 854, 500_000))
        val selection = selection(factory { true }, meter, timeline(LIVE_BUFFER_TAG), fixedGroup)
        assertTrue(selection is FixedTrackSelection)
        assertEquals(1, selection.length())
        update(selection, bufferedUs = 4_000_000L)
        assertEquals(480, selection.selectedFormat.height)
        meter.estimate = 20_000_000L
        update(selection, bufferedUs = 4_000_000L)
        assertEquals(1, selection.length())
        assertEquals(480, selection.selectedFormat.height)
    }

    private fun assertDefaultUpshiftGate(factory: ExoTrackSelection.Factory, timeline: Timeline) {
        val meter = ControlledBandwidthMeter(600_000L)
        val selection = selection(factory, meter, timeline)
        update(selection, bufferedUs = 4_000_000L)
        assertEquals(480, selection.selectedFormat.height)
        meter.estimate = 8_000_000L
        update(selection, bufferedUs = 4_000_000L)
        assertEquals("Default selection must not upshift with only four seconds buffered",
            480, selection.selectedFormat.height)
        update(selection, bufferedUs = 10_000_000L)
        assertEquals("Default Auto must still use the provider's 1080 rendition when ready",
            1080, selection.selectedFormat.height)
    }

    // Reflection keeps the RED phase compilable before the production class exists.
    // The returned factory and every selection/update below are real production Media3 objects.
    private fun factory(smoothEnabled: () -> Boolean): ExoTrackSelection.Factory {
        val type = try {
            Class.forName("com.easyiptv.player.SmoothAdaptiveSelectionFactory")
        } catch (missing: ClassNotFoundException) {
            throw AssertionError("SmoothAdaptiveSelectionFactory must implement the Smooth live policy", missing)
        }
        val constructor = type.getDeclaredConstructor(kotlin.jvm.functions.Function0::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(smoothEnabled) as ExoTrackSelection.Factory
    }

    private fun selection(
        factory: ExoTrackSelection.Factory,
        meter: ControlledBandwidthMeter,
        timeline: Timeline,
        group: TrackGroup = TrackGroup("video",
            format(480, 854, 500_000),
            format(720, 1280, 2_000_000),
            format(1080, 1920, 4_000_000))
    ): ExoTrackSelection {
        val periodUid = if (timeline.isEmpty) Any() else timeline.getUidOfPeriod(0)
        val definition = ExoTrackSelection.Definition(group, *IntArray(group.length) { it })
        return requireNotNull(factory.createTrackSelections(
            arrayOf(definition), meter, MediaPeriodId(periodUid), timeline)[0]).also { it.enable() }
    }

    private fun update(selection: ExoTrackSelection, bufferedUs: Long) {
        selection.updateSelectedTrack(
            /* playbackPositionUs = */ 0L,
            bufferedUs,
            // No live-edge relaxation: this isolates the configured buffer gate.
            /* availableDurationUs = */ C.TIME_UNSET,
            emptyList(),
            Array(selection.length()) { MediaChunkIterator.EMPTY })
    }

    private fun timeline(tag: Any?, live: Boolean = true): Timeline {
        val item = MediaItem.Builder()
            .setUri("https://example.invalid/manifest.m3u8")
            .setTag(tag)
            .build()
        return SinglePeriodTimeline(60_000_000L, true, live, live, null, item)
    }

    private fun format(height: Int, width: Int, bitrate: Int): Format = Format.Builder()
        .setId("${height}p")
        .setSampleMimeType(MimeTypes.VIDEO_H264)
        .setWidth(width)
        .setHeight(height)
        .setAverageBitrate(bitrate)
        .build()

    /** Only the external network estimate is controlled; Media3 owns all adaptation decisions. */
    private class ControlledBandwidthMeter(var estimate: Long) : BandwidthMeter {
        override fun getBitrateEstimate(): Long = estimate
        override fun getTransferListener(): TransferListener? = null
        override fun addEventListener(handler: Handler, eventListener: BandwidthMeter.EventListener) = Unit
        override fun removeEventListener(eventListener: BandwidthMeter.EventListener) = Unit
    }
}
