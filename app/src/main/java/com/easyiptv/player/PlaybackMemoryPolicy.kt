package com.easyiptv.player

import androidx.annotation.OptIn

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl

/** Configures sample buffering; this is not a limit on total process memory. */
@OptIn(UnstableApi::class)
internal object PlaybackMemoryPolicy {
    fun create(
        lowRam: Boolean,
        minBufferMs: Int,
        maxBufferMs: Int,
        startBufferMs: Int,
        rebufferMs: Int
    ): DefaultLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(minBufferMs, maxBufferMs, startBufferMs, rebufferMs)
        // Past samples share the same byte target as the forward cushion.
        // Low-RAM devices reclaim them promptly; seeks still use the media
        // source, and live DVR rewind reopens the existing rolling disk buffer.
        .setBackBuffer(if (lowRam) 0 else 10_000, false)
        .setTargetBufferBytes(if (lowRam) 32 * 1024 * 1024 else C.LENGTH_UNSET)
        // Keep the chosen time cushions when memory permits. At the low-RAM
        // sample target, Media3 must stop loading and allow playback to proceed
        // instead of waiting for a longer duration and allocating beyond it.
        .setPrioritizeTimeOverSizeThresholds(!lowRam)
        .build()
}
