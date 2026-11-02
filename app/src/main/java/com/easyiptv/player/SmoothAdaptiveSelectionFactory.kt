package com.easyiptv.player

import androidx.annotation.OptIn
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.BandwidthMeter

/** Lets Smooth's short live reserve satisfy Media3's quality-increase buffer gate. */
@OptIn(UnstableApi::class)
internal class SmoothAdaptiveSelectionFactory(
    private val smoothEnabled: () -> Boolean
) : ExoTrackSelection.Factory {
    private val defaultFactory = AdaptiveTrackSelection.Factory()
    private val smoothFactory = AdaptiveTrackSelection.Factory(3000, 5000, 5000, 0.7f)

    override fun createTrackSelections(
        definitions: Array<out ExoTrackSelection.Definition?>,
        bandwidthMeter: BandwidthMeter,
        mediaPeriodId: MediaPeriodId,
        timeline: Timeline
    ): Array<ExoTrackSelection?> {
        val smoothLive = smoothEnabled() && !timeline.isEmpty && run {
            val period = timeline.getPeriodByUid(mediaPeriodId.periodUid, Timeline.Period())
            timeline.getWindow(period.windowIndex, Timeline.Window())
                .mediaItem.localConfiguration?.tag == LIVE_BUFFER_TAG
        }
        val factory = if (smoothLive) smoothFactory else defaultFactory
        return factory.createTrackSelections(definitions, bandwidthMeter, mediaPeriodId, timeline)
    }
}
