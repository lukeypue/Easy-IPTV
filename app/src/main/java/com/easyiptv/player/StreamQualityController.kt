package com.easyiptv.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks

/** Manual video-size preference; call on the player's application thread. */
internal class StreamQualityController {
    private data class OriginalVideoSettings(
        val maxWidth: Int,
        val maxHeight: Int,
        val overrides: List<TrackSelectionOverride>,
    )

    private var originalVideoSettings: OriginalVideoSettings? = null

    val isLimited: Boolean
        get() = originalVideoSettings != null

    fun canLimit(tracks: Tracks): Boolean = tracks.groups.any { group ->
        if (group.type != C.TRACK_TYPE_VIDEO || !group.isSelected) {
            return@any false
        }
        var hasSmaller = false
        var hasLarger = false
        for (index in 0 until group.length) {
            if (!group.isTrackSupported(index)) continue
            val format = group.getTrackFormat(index)
            if (format.width <= 0 || format.height <= 0) continue
            if (format.width <= 1280 && format.height <= 720) {
                hasSmaller = true
            } else {
                hasLarger = true
            }
        }
        hasSmaller && hasLarger
    }

    /** Returns whether the request succeeded. Disabling requires no current tracks. */
    fun setLimited(player: Player, limited: Boolean): Boolean {
        if (!limited) {
            reset(player)
            return true
        }
        // Repeated enables must not replace the original settings with our own cap.
        if (isLimited) return true
        if (!canLimit(player.currentTracks)) return false

        val parameters = player.trackSelectionParameters
        val original = OriginalVideoSettings(
            parameters.maxVideoWidth,
            parameters.maxVideoHeight,
            parameters.overrides.values.filter { it.type == C.TRACK_TYPE_VIDEO },
        )
        player.trackSelectionParameters = parameters.buildUpon()
            .setMaxVideoSize(minOf(parameters.maxVideoWidth, 1280), minOf(parameters.maxVideoHeight, 720))
            // A forced 1080p selection would otherwise take precedence over the size preference.
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()
        originalVideoSettings = original
        return true
    }

    /** Restore before a channel/VOD change; null discards state after the player is gone. */
    fun reset(player: Player?) {
        val original = originalVideoSettings ?: return
        if (player != null) {
            // Start with today's parameters so audio/caption changes made while capped survive.
            val builder = player.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(original.maxWidth, original.maxHeight)
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            original.overrides.forEach { builder.addOverride(it) }
            player.trackSelectionParameters = builder.build()
        }
        originalVideoSettings = null
    }
}
