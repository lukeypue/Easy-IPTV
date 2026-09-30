package com.easyiptv.player

/** One cell per provider event, clipped to the visible time window. */
internal object GuideGeometry {
    data class Cell(val startMs: Long, val endMs: Long, val entry: EpgEntry?)

    fun cells(schedule: List<EpgEntry>, start: Long, end: Long): List<Cell> {
        if (end <= start) return emptyList()
        val out = ArrayList<Cell>()
        var cursor = start
        for (entry in schedule.sortedBy { it.startMs }) {
            if (entry.endMs <= cursor || entry.startMs >= end) continue
            val left = maxOf(cursor, entry.startMs)
            if (left > cursor) out.add(Cell(cursor, left, null))
            val right = minOf(end, entry.endMs)
            if (right > left) {
                out.add(Cell(left, right, entry))
                cursor = right
            }
            if (cursor == end) break
        }
        if (cursor < end) out.add(Cell(cursor, end, null))
        return out
    }
}
