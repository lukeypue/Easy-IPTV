package com.easyiptv.player

internal object GuideNavigation {
    const val WINDOW_MS = 2L * 60 * 60 * 1000
    const val LAST_PAGE = 35
    fun page(current: Int, direction: Int): Int = (current + direction).coerceIn(0, LAST_PAGE)
    fun row(current: Int, direction: Int, count: Int): Int = (current + direction).coerceIn(0, (count - 1).coerceAtLeast(0))
}
internal object ManualRecordingDuration {
    val minutes = (30..300 step 30).toList()
    fun label(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} hr"
        else -> "${minutes / 60}.5 hr"
    }
    fun end(startMs: Long, minutes: Int): Long {
        require(minutes in this.minutes)
        return startMs + minutes * 60_000L
    }
}
