package com.easyiptv.player

// ZAKO_V447_LIVE_OVERLAY
object LiveOverlay {
    const val visibleRowCount = 3

    enum class Action {
        PREVIOUS_CHANNEL,
        RECORD,
        STOP,
        PAUSE_PLAY,
        REWIND,
        FAST_FORWARD,
        CAPTIONS,
        GUIDE
    }

    /** Repeated transport presses: 2x, 4x, 8x, 16x, then cycle to normal. */
    fun seekMultiplier(pressCount: Int): Int = when (((pressCount - 1).mod(5)) + 1) {
        1 -> 2
        2 -> 4
        3 -> 8
        4 -> 16
        5 -> 1
        else -> 1
    }

    fun centeredWindow(currentIndex: Int, itemCount: Int): IntRange {
        if (itemCount <= 0) return IntRange.EMPTY
        val current = currentIndex.coerceIn(0, itemCount - 1)
        val maxStart = (itemCount - visibleRowCount).coerceAtLeast(0)
        val start = (current - 1).coerceAtLeast(0).coerceAtMost(maxStart)
        val end = (start + visibleRowCount - 1).coerceAtMost(itemCount - 1)
        return start..end
    }
}
