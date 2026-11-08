package com.easyiptv.player

import android.content.SharedPreferences
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.Util

internal data class DualStreamPlan(val active: Boolean, val slots: Int, val status: String)

/** An opt-in allowance, never permission to exceed the user's provider limit. */
internal fun planDualStreamTrial(prefs: SharedPreferences, url: String, backgroundSlots: Int): DualStreamPlan {
    if (!prefs.getBoolean("dual_stream_trial", false)) return DualStreamPlan(false, 1, "Off — single stream")
    if (prefs.getBoolean("live_smooth", false)) return DualStreamPlan(false, 1, "Trial inactive in Smooth — single stream")
    val hls = runCatching {
        val uri = Uri.parse(url)
        uri.scheme?.lowercase() in setOf("http", "https") && Util.inferContentType(uri) == C.CONTENT_TYPE_HLS
    }.getOrDefault(false)
    if (!hls) return DualStreamPlan(false, 1, "Unavailable for this feed — HLS segments required; single stream active")
    val available = ProviderStreams.max(prefs) - backgroundSlots.coerceAtLeast(0)
    if (backgroundSlots < 0 || available < 2) {
        return DualStreamPlan(false, 1, "Fewer than two free provider streams — single stream active")
    }
    val requested = prefs.getInt("dual_stream_count", 2).coerceIn(2, 3)
    val slots = minOf(requested, available)
    return DualStreamPlan(true, slots, "HLS trial active — $slots provider streams reserved")
}
