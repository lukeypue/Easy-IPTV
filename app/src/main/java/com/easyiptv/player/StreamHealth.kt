package com.easyiptv.player

import android.net.Uri
import android.os.SystemClock
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.IdentityHashMap

internal data class StreamTransferSnapshot(
    val receivedBitsPerSecond: Long?,
    val bytesReceived: Long,
    val sinceLastByteMs: Long?,
    val activeTransfers: Int,
    val sampledForMs: Long,
)

/** Passive HTTP accounting. Call snapshot from the open panel; no polling or network work here. */
internal class StreamHealthMeter(private val clock: () -> Long = { SystemClock.elapsedRealtime() }) {
    private val lock = Any()
    private var generation = 0L
    // Identity also prevents an end from one listener retiring another listener's transfer.
    private val active = IdentityHashMap<DataSource, Any>()
    private var observedAt: Long? = null
    private var lastByteAt: Long? = null
    private var totalBytes = 0L
    // Ten seconds plus the partially overlapping boundary second. Never retain byte events.
    private val bucketSeconds = LongArray(11) { Long.MIN_VALUE }
    private val bucketBytes = LongArray(11)

    fun reset(): Unit = synchronized(lock) {
        generation++
        active.clear()
        observedAt = null
        lastByteAt = null
        totalBytes = 0L
        bucketSeconds.fill(Long.MIN_VALUE)
        bucketBytes.fill(0L)
    }

    fun newTransferListener(): TransferListener = synchronized(lock) {
        val capturedGeneration = generation
        val owner = Any()
        object : TransferListener {
            override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

            override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                synchronized(lock) {
                    if (capturedGeneration != generation || !eligible(source, dataSpec, isNetwork)) return
                    if (!active.containsKey(source)) active[source] = owner
                    if (observedAt == null) observedAt = clock()
                }
            }

            override fun onBytesTransferred(
                source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int,
            ) {
                synchronized(lock) {
                    if (capturedGeneration != generation || active[source] !== owner ||
                        bytesTransferred <= 0 || !eligible(source, dataSpec, isNetwork)) return
                    val now = clock()
                    val second = now / 1_000L
                    val index = (second % bucketSeconds.size).toInt()
                    if (bucketSeconds[index] != second) {
                        bucketSeconds[index] = second
                        bucketBytes[index] = 0L
                    }
                    bucketBytes[index] = addBytes(bucketBytes[index], bytesTransferred)
                    totalBytes = addBytes(totalBytes, bytesTransferred)
                    lastByteAt = now
                }
            }

            override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                synchronized(lock) {
                    if (capturedGeneration == generation && active[source] === owner) active.remove(source)
                }
            }
        }
    }

    fun snapshot(): StreamTransferSnapshot = synchronized(lock) {
        val now = clock()
        val sampledFor = observedAt?.let { (now - it).coerceAtLeast(0L) } ?: 0L
        val currentSecond = now / 1_000L
        var recentBytes = 0.0
        for (index in bucketSeconds.indices) {
            if (bucketSeconds[index] in (currentSecond - 10L)..currentSecond) {
                recentBytes += bucketBytes[index].toDouble()
            }
        }
        StreamTransferSnapshot(
            receivedBitsPerSecond = if (sampledFor < 3_000L) null else
                (recentBytes * 8_000.0 / sampledFor.coerceAtMost(10_000L)).toLong(),
            bytesReceived = totalBytes,
            sinceLastByteMs = lastByteAt?.let { (now - it).coerceAtLeast(0L) },
            activeTransfers = active.size,
            sampledForMs = sampledFor,
        )
    }

    private fun addBytes(total: Long, count: Int): Long =
        if (total > Long.MAX_VALUE - count) Long.MAX_VALUE else total + count

    private fun eligible(source: DataSource, spec: DataSpec, network: Boolean): Boolean {
        if (!network || !remoteHttp(spec.uri)) return false
        // A callback may precede URI availability; a known local redirect must still be excluded.
        return source.uri?.let(::remoteHttp) ?: true
    }

    private fun remoteHttp(uri: Uri): Boolean {
        val scheme = uri.scheme
        if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase()?.removePrefix("[")?.removeSuffix("]")?.trimEnd('.') ?: return false
        return host.isNotEmpty() && host != "localhost" && !host.endsWith(".localhost") &&
            !host.startsWith("127.") && host != "::1" && host != "0:0:0:0:0:0:0:1"
    }
}

internal data class StreamHealthInput(
    val playRequested: Boolean,
    val buffering: Boolean,
    val loading: Boolean,
    val bufferedMs: Long,
    val received: StreamTransferSnapshot,
    val formatBitrateBps: Long?,
    // Panel interval deltas, not lifetime decoder counters.
    val droppedFrames: Int,
    val renderedFrames: Int,
    val decoderError: Boolean,
    val localPlayback: Boolean,
)

internal data class StreamHealthAssessment(val title: String, val detail: String, val deliveryInterrupted: Boolean = false)

internal fun assessStreamHealth(input: StreamHealthInput): StreamHealthAssessment {
    if (!input.playRequested) return StreamHealthAssessment("Paused", "Playback is intentionally paused.")
    if (input.localPlayback) return StreamHealthAssessment(
        "Playing saved pause video", "Network delivery assessment is unavailable during saved pause playback.",
    )
    if (input.decoderError) return StreamHealthAssessment(
        "Playback/decoder problem detected",
        "A playback or decoder error was reported. This does not identify a hardware fault or delivery cause.",
    )
    val dropped = input.droppedFrames.coerceAtLeast(0).toLong()
    val rendered = input.renderedFrames.coerceAtLeast(0).toLong()
    if (dropped >= 5L && dropped * 20L > dropped + rendered) return StreamHealthAssessment(
        "Frame drops detected", "More than 5% of frames were dropped in the latest sample; the cause is uncertain.",
    )
    if (input.received.sampledForMs < 3_000L) return StreamHealthAssessment(
        "Measuring", "Collecting at least three seconds of delivery observations.",
    )
    val noRecentBytes = (input.received.sinceLastByteMs ?: -1L) >= 3_000L
    val activeWithoutBytes = input.received.activeTransfers > 0 && input.received.bytesReceived == 0L
    if (input.buffering && input.bufferedMs < 1_000L && (noRecentBytes || activeWithoutBytes)) {
        return StreamHealthAssessment(
            "Delivery interruption detected",
            "The playback buffer is nearly empty and delivery has stalled. The source or network path may be involved; the cause is uncertain.",
            deliveryInterrupted = true,
        )
    }
    if (input.buffering) return StreamHealthAssessment(
        "Collecting video", "Playback is buffering; current observations do not establish a delivery interruption.",
    )
    val bitrateNote = if (input.formatBitrateBps == null || input.formatBitrateBps <= 0L)
        " Stream bitrate is unknown." else " Incoming rate alone does not establish delivery health."
    return StreamHealthAssessment(
        "No delivery problem detected", "This short observation is not proof of uninterrupted delivery.$bitrateNote",
    )
}

/** A complete-segment prefetch/admission wait has no committed delivery progress yet. */
internal fun assessStreamHealth(input: StreamHealthInput, deliveryProgressKnown: Boolean): StreamHealthAssessment {
    val measured = assessStreamHealth(input)
    return if (!deliveryProgressKnown && measured.deliveryInterrupted) StreamHealthAssessment(
        "Collecting complete segment",
        "The dual trial is waiting for a complete segment or a connection slot. These observations do not establish a stalled provider delivery.") else measured
}
