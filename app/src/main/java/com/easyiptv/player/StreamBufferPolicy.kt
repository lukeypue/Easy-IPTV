package com.easyiptv.player

import android.content.SharedPreferences

/** One policy for player construction, HLS latency and the on-screen progress target. */
internal data class StreamBufferPolicy(val reserveMs: Int, val startMs: Int, val rebufferMs: Int) {
    companion object {
        fun from(prefs: SharedPreferences): StreamBufferPolicy {
            val seconds = prefs.getInt("buffer_sec", 30).coerceIn(0, 60)
            // Off removes deliberate waits, not the decoder's sample queue.
            if (seconds == 0) return StreamBufferPolicy(5_000, 0, 0)
            val requestedMs = seconds.coerceAtLeast(10) * 1_000
            val lockMs = prefs.getInt("live_start_ms", 4_000).coerceIn(0, 30_000)
            val steady = prefs.getBoolean("live_steady_recovery", false)
            val startMs = lockMs
            val rebufferMs = if (steady) maxOf(lockMs * 3, 15_000).coerceAtMost(50_000)
                else maxOf(lockMs * 2, 8_000).coerceAtMost(40_000)
            // Keep refill headroom beyond the resume threshold. A small forward
            // preference must never clamp Natural and Steady to the same wait.
            val reserveMs = maxOf(requestedMs, rebufferMs + maxOf(startMs, 5_000)).coerceAtMost(60_000)
            return StreamBufferPolicy(reserveMs, startMs, rebufferMs)
        }
    }
}
