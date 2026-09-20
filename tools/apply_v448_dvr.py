from pathlib import Path
base=Path('app/src/main/java/com/easyiptv/player')
p=base/'LiveDvrController.kt'
p.write_text(r'''package com.easyiptv.player

/**
 * DVR timeline derived only from the retained timeshift ring.
 * EPG/program boundaries never reset this session timeline.
 */
internal object LiveDvrController {
    internal data class Timeline(
        val oldestVirtualByte: Long,
        val newestVirtualByte: Long,
        val currentVirtualByte: Long,
        val retainedBytes: Long,
        val atLiveEdge: Boolean,
    )

    fun timeline(currentVirtualByte: Long? = null): Timeline? {
        val snap = Timeshift.snapshot() ?: return null
        val oldest = snap.oldestVirtualByte
        val newest = snap.newestVirtualByte
        val current = (currentVirtualByte ?: newest).coerceIn(oldest, newest)
        val tolerance = (188L * 64L).coerceAtMost((newest - oldest).coerceAtLeast(0L))
        return Timeline(
            oldestVirtualByte = oldest,
            newestVirtualByte = newest,
            currentVirtualByte = current,
            retainedBytes = (newest - oldest).coerceAtLeast(0L),
            atLiveEdge = newest - current <= tolerance,
        )
    }

    fun clampToRetainedHistory(requestedVirtualByte: Long): Long {
        val snap = Timeshift.snapshot() ?: return requestedVirtualByte
        return requestedVirtualByte.coerceIn(snap.oldestVirtualByte, snap.newestVirtualByte)
    }

    fun jumpLive(): Long = Timeshift.snapshot()?.newestVirtualByte ?: Timeshift.newestVirtualByte()
}
''')
print('Applied Zako 4.48 rolling DVR controller')
