package com.easyiptv.player

import androidx.annotation.OptIn
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection

internal const val LIVE_BUFFER_TAG = "ryzod-live-buffer"

/** One allocator; live waits must never become movie/download startup waits. */
@OptIn(UnstableApi::class)
internal class LiveAwareLoadControl(
    private val delegate: DefaultLoadControl,
    private val lowRam: Boolean
) : LoadControl {
    // Kotlin interface delegation does not forward Java default methods.
    // Forward the current per-player API explicitly, including lifecycle.
    override fun onPrepared(playerId: PlayerId) = delegate.onPrepared(playerId)
    override fun onTracksSelected(parameters: LoadControl.Parameters, tracks: TrackGroupArray,
        selections: Array<out ExoTrackSelection?>) = delegate.onTracksSelected(parameters, tracks, selections)
    override fun onStopped(playerId: PlayerId) = delegate.onStopped(playerId)
    override fun onReleased(playerId: PlayerId) = delegate.onReleased(playerId)
    override fun getAllocator(playerId: PlayerId) = delegate.getAllocator(playerId)
    override fun getBackBufferDurationUs(playerId: PlayerId) = delegate.getBackBufferDurationUs(playerId)
    override fun retainBackBufferFromKeyframe(playerId: PlayerId) = delegate.retainBackBufferFromKeyframe(playerId)
    override fun shouldContinueLoading(parameters: LoadControl.Parameters) = delegate.shouldContinueLoading(parameters)
    override fun shouldContinuePreloading(playerId: PlayerId, timeline: Timeline,
        mediaPeriodId: MediaPeriodId, bufferedDurationUs: Long) =
        delegate.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs)

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        val timeline = parameters.timeline
        val live = if (timeline.isEmpty) false else {
            val period = timeline.getPeriodByUid(parameters.mediaPeriodId.periodUid, Timeline.Period())
            timeline.getWindow(period.windowIndex, Timeline.Window())
                .mediaItem.localConfiguration?.tag == LIVE_BUFFER_TAG
        }
        if (live) return delegate.shouldStartPlayback(parameters)
        // Network and saved on-demand items keep a small, fixed startup wait.
        // A full low-RAM allocator still escapes rather than waiting forever.
        val requiredUs = 4_000_000L
        return parameters.bufferedDurationUs / parameters.playbackSpeed >= requiredUs ||
            (lowRam && delegate.getAllocator(parameters.playerId).totalBytesAllocated >= 32 * 1024 * 1024)
    }
}
