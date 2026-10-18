package com.easyiptv.player

import android.content.SharedPreferences

/** One policy for player construction, HLS latency and the on-screen progress target. */
internal data class StreamBufferPolicy(val reserveMs: Int, val startMs: Int, val rebufferMs: Int) {
    companion object {
        fun from(prefs: SharedPreferences): StreamBufferPolicy {
            val requestedMs = prefs.getInt("buffer_sec", 30).coerceIn(10, 60) * 1_000
            val lockMs = prefs.getInt("live_start_ms", 4_000).coerceIn(2_000, 12_000)
            val steady = prefs.getBoolean("live_steady_recovery", false)
            val startMs = if (steady) maxOf(lockMs, 6_000) else lockMs
            val rebufferMs = if (steady) maxOf(lockMs * 3, 15_000).coerceAtMost(30_000)
                else maxOf(lockMs * 2, 8_000).coerceAtMost(24_000)
            // Keep refill headroom beyond the resume threshold. A small forward
            // preference must never clamp Natural and Steady to the same wait.
            val reserveMs = maxOf(requestedMs, rebufferMs + maxOf(startMs, 5_000)).coerceAtMost(60_000)
            return StreamBufferPolicy(reserveMs, startMs, rebufferMs)
        }
    }
}
