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

    enum class TransportMode { PLAY, PAUSE, FAST_FORWARD, REWIND }

    internal data class TransportState(
        val mode: TransportMode = TransportMode.PLAY,
        val rate: Int = 1,
    )

    private val steppedRates = intArrayOf(2, 4, 8, 16)
    @Volatile private var transport = TransportState()

    fun transportState(): TransportState = transport

    fun pause(): TransportState {
        transport = TransportState(TransportMode.PAUSE, 0)
        return transport
    }

    fun play(): TransportState {
        transport = TransportState(TransportMode.PLAY, 1)
        return transport
    }

    fun fastForward(): TransportState = step(TransportMode.FAST_FORWARD)

    fun rewind(): TransportState = step(TransportMode.REWIND)

    private fun step(mode: TransportMode): TransportState {
        val nextRate = if (transport.mode != mode) steppedRates[0] else {
            val i = steppedRates.indexOf(transport.rate)
            if (i < 0 || i == steppedRates.lastIndex) 1 else steppedRates[i + 1]
        }
        transport = if (nextRate == 1) TransportState() else TransportState(mode, nextRate)
        return transport
    }

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

    fun jumpLive(): Long {
        play()
        return Timeshift.snapshot()?.newestVirtualByte ?: Timeshift.newestVirtualByte()
    }
}
''')
print('Applied Zako 4.48 rolling DVR controller')

# Remove stale pre-ring help text; the DVR now uses bounded rolling segments.
main_path = base / 'MainActivity.kt'
main = main_path.read_text()
main = main.replace(
    'DVR Live uses an append-only temporary buffer up to ~1 GB on Fire Stick storage or ~3.5 GB on a verified USB drive, then continues live directly if cap is reached.',
    'DVR Live keeps a rolling local history while you stay on the channel. Zako uses verified USB storage when available and safely falls back to internal storage.'
)
main_path.write_text(main)
