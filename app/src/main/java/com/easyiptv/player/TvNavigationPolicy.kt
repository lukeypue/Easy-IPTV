package com.easyiptv.player

// ZAKO_V447_DETERMINISTIC_REMOTE_FOCUS
object TvNavigationPolicy {
    enum class HorizontalAction { OUTWARD, INWARD, STAY }

    fun horizontal(isAtLeftEdge: Boolean, direction: Int): HorizontalAction = when {
        direction < 0 && isAtLeftEdge -> HorizontalAction.OUTWARD
        direction > 0 && isAtLeftEdge -> HorizontalAction.INWARD
        else -> HorizontalAction.STAY
    }

    fun initialIndex(itemCount: Int): Int = if (itemCount > 0) 0 else -1
    fun restoredIndex(previous: Int, itemCount: Int): Int =
        if (itemCount <= 0) -1 else previous.coerceIn(0, itemCount - 1)
}
