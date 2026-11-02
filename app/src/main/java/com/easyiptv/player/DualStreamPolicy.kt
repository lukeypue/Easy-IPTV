package com.easyiptv.player

import android.content.SharedPreferences
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.Util

internal data class DualStreamPlan(val active: Boolean, val slots: Int, val status: String)

/** An opt-in allowance, never permission to exceed the user's provider limit. */
internal fun planDualStreamTrial(prefs: SharedPreferences, url: String, backgroundSlots: Int): DualStreamPlan {
    if (!prefs.getBoolean("dual_stream_trial", false)) return DualStreamPlan(false, 1, "Off — single stream")
    val hls = runCatching {
        val uri = Uri.parse(url)
        uri.scheme?.lowercase() in setOf("http", "https") && Util.inferContentType(uri) == C.CONTENT_TYPE_HLS
    }.getOrDefault(false)
    if (!hls) return DualStreamPlan(false, 1, "Unavailable for this feed — HLS segments required; single stream active")
    if (backgroundSlots < 0 || backgroundSlots > ProviderStreams.max(prefs) - 2) {
        return DualStreamPlan(false, 1, "No room for two provider streams — single stream active")
    }
    return DualStreamPlan(true, 2, "Trial enabled for HLS segments — two provider streams reserved")
}
