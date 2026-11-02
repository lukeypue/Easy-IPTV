package com.easyiptv.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.hls.HlsDataSourceFactory
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * EXPERIMENTAL: the caller must gate this factory on a separate, default-OFF HLS trial
 * and a two-stream account budget. Never use it for progressive/raw TS delivery.
 * Two copies compete for one whole segment; partial copies are never combined.
 * At the memory cap we retain just one ordinary streaming response, including its
 * buffered prefix. This bounds buffering, but offers no capacity/server benefit guarantee.
 *
 * Supply an UNOBSERVED baseFactory and a fresh passive listener per DataSource.
 * Trial transfer callbacks count usable segment payload, not duplicate wire traffic;
 * they are not a measurement of physical ingress bandwidth.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class DualHlsDataSourceFactory(
    private val baseFactory: DataSource.Factory,
    private val userAgent: String,
    private val listenerFactory: () -> TransferListener,
) : HlsDataSourceFactory, AutoCloseable {
    private val lifecycle = Any()
    private val sessions = mutableSetOf<Session>()
    private val pairPermit = Semaphore(1)
    // Includes playlist/key/baseline requests, not just the redundant pair.
    private val networkPermits = Semaphore(2, true)
    val isDeliveryObservationPending: Boolean
        get() = synchronized(lifecycle) { sessions.any { it.observationPending } }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(false)
        .build()

    @Volatile var isClosed: Boolean = false
        private set

    override fun createDataSource(dataType: Int): DataSource = Source(dataType == C.DATA_TYPE_MEDIA)

    override fun close() {
        val pending = synchronized(lifecycle) {
            if (isClosed) return
            isClosed = true
            sessions.toList()
        }
        // Cancel sockets first. Never join callbacks or drain network bodies on the UI thread.
        client.dispatcher.cancelAll()
        pending.forEach { it.cancel(fromFactory = true) }
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    private inner class Source(private val media: Boolean) : DataSource {
        private val listeners = CopyOnWriteArrayList<TransferListener>().apply { add(listenerFactory()) }
        private val lock = Any()
        @Volatile private var current: Session? = null

        override fun addTransferListener(transferListener: TransferListener) {
            if (listeners.addIfAbsent(transferListener)) current?.delegate?.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val session = synchronized(lock) {
                if (current != null) throw IOException("Data source is already open")
                synchronized(lifecycle) {
                    if (isClosed) throw IOException("HLS trial factory is closed")
                    Session(this, dataSpec, listeners).also { current = it; sessions.add(it) }
                }
            }
            try {
                return session.open(media)
            } catch (error: Exception) {
                close()
                throw error
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
            return (current ?: throw IOException("Data source is closed")).read(buffer, offset, length)
        }

        override fun getUri(): Uri? = current?.uri()
        override fun getResponseHeaders(): Map<String, List<String>> = current?.headers() ?: emptyMap()
        override fun close() {
            val previous = synchronized(lock) { current.also { current = null } }
            previous?.cancel(fromFactory = false)
        }
    }

    private inner class Session(
        private val source: Source,
        private val spec: DataSpec,
        private val listeners: List<TransferListener>,
    ) {
        private val lock = Any()
        private val cancelled = AtomicBoolean()
        private val baselineSlot = AtomicBoolean()
        @Volatile var observationPending = false
            private set
        @Volatile var delegate: DataSource? = null
            private set
        @Volatile private var race: Race? = null
        @Volatile private var payload: Payload? = null
        private var started = false
        private var chunkIndex = 0
        private var chunkOffset = 0
        private var readCount = 0L
        private var pendingRead: LinkedBlockingQueue<StreamRead>? = null

        private fun checkOpen() {
            if (cancelled.get() || isClosed) throw IOException("HLS trial request cancelled")
            if (Thread.currentThread().isInterrupted) {
                cancel(false)
                throw IOException("HLS trial loader interrupted")
            }
        }

        fun open(media: Boolean): Long {
            checkOpen()
            observationPending = true
            if (!media || !eligible(spec) || !pairPermit.tryAcquire()) return openBaseline()
            if (!networkPermits.tryAcquire(2)) {
                pairPermit.release()
                return openBaseline()
            }
            var ownedRace: Race? = null
            try {
                val request = Request.Builder().url(spec.uri.toString())
                    .header("User-Agent", userAgent)
                    .header("Accept-Encoding", "identity")
                    .apply { spec.httpRequestHeaders.forEach { (key, value) -> header(key, value) } }
                    .build()
                ownedRace = Race(request)
                synchronized(lock) {
                    checkOpen()
                    race = ownedRace
                    listeners.forEach { it.onTransferInitializing(source, spec, true) }
                    // BandwidthMeter must include prefetch time, not just subsequent RAM reads.
                    started = true
                    listeners.forEach { it.onTransferStart(source, spec, true) }
                }
                ownedRace.start()
                val winner = ownedRace.await()
                synchronized(lock) {
                    checkOpen()
                    payload = winner
                    observationPending = false
                }
                // Retain the memory permit until this source is closed, even
                // after a fully buffered winner has released its network slot.
                return winner.length
            } catch (error: Exception) {
                if (ownedRace == null) {
                    pairPermit.release()
                    networkPermits.release(2)
                } else ownedRace.cancel()
                if (error is IOException) throw error
                throw IOException("Unable to open HLS trial segment")
            }
        }

        private fun openBaseline(): Long {
            try {
                while (true) {
                    checkOpen()
                    if (networkPermits.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                        baselineSlot.set(true)
                        break
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("HLS trial loader interrupted")
            }
            observationPending = false
            val base = baseFactory.createDataSource()
            listeners.forEach { base.addTransferListener(it) }
            synchronized(lock) { checkOpen(); delegate = base }
            // No lock is held across a network operation, so factory close can cancel a race.
            val length = base.open(spec)
            checkOpen()
            return length
        }

        fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            checkOpen()
            delegate?.let { return it.read(buffer, offset, length) }
            val winner = payload ?: throw IOException("Segment is not open")
            val count = if (chunkIndex < winner.chunks.size) {
                val chunk = winner.chunks[chunkIndex]
                val size = minOf(length, chunk.size - chunkOffset)
                chunk.bytes.copyInto(buffer, offset, chunkOffset, chunkOffset + size)
                chunkOffset += size
                if (chunkOffset == chunk.size) { chunkIndex++; chunkOffset = 0 }
                size
            } else if (winner.stream != null) readStream(winner, buffer, offset, length)
            else C.RESULT_END_OF_INPUT
            synchronized(lock) {
                checkOpen()
                if (count > 0) {
                    readCount += count
                    listeners.forEach { it.onBytesTransferred(source, spec, true, count) }
                } else if (winner.length != C.LENGTH_UNSET.toLong() && readCount < winner.length) {
                    throw IOException("Incomplete HLS segment")
                }
            }
            return count
        }

        private fun readStream(winner: Payload, buffer: ByteArray, offset: Int, length: Int): Int {
            val queue = LinkedBlockingQueue<StreamRead>()
            synchronized(lock) { checkOpen(); pendingRead = queue }
            try {
                // SocketInputStream does not respond to Thread.interrupt. Keep blocking socket
                // reads on the owned executor and interruptibly await them on the loader.
                client.dispatcher.executorService.execute {
                    val bytes = ByteArray(minOf(CHUNK_BYTES, length))
                    try {
                        val count = synchronized(winner.streamLock) {
                            checkOpen()
                            winner.stream!!.read(bytes)
                        }
                        queue.offer(StreamRead(bytes, count, false))
                    } catch (_: Exception) { queue.offer(StreamRead(null, 0, true)) }
                }
                val result = queue.take()
                checkOpen()
                if (result.failed) throw IOException("HLS segment read failed")
                if (result.count > 0) result.bytes!!.copyInto(buffer, offset, 0, result.count)
                return result.count
            } catch (_: InterruptedException) {
                cancel(false)
                Thread.currentThread().interrupt()
                throw IOException("HLS trial loader interrupted")
            } catch (_: RejectedExecutionException) {
                throw IOException("HLS trial factory is closed")
            } finally {
                synchronized(lock) { if (pendingRead === queue) pendingRead = null }
            }
        }

        fun uri(): Uri? = if (cancelled.get()) null else delegate?.uri ?: payload?.uri ?: if (race != null) spec.uri else null
        fun headers(): Map<String, List<String>> =
            if (cancelled.get()) emptyMap() else delegate?.responseHeaders ?: payload?.headers ?: emptyMap()

        fun cancel(fromFactory: Boolean) {
            if (!cancelled.compareAndSet(false, true)) return
            observationPending = false
            race?.cancel()
            fun releaseBaselineSlot() {
                if (baselineSlot.compareAndSet(true, false)) networkPermits.release()
            }
            val base = delegate
            if (base != null) {
                if (fromFactory) {
                    // Keep draining/closing off UI and retain admission until closed.
                    try { client.dispatcher.executorService.execute {
                        try { runCatching { base.close() } } finally { releaseBaselineSlot() }
                    } } catch (_: RejectedExecutionException) { releaseBaselineSlot() }
                } else try { runCatching { base.close() } } finally { releaseBaselineSlot() }
            } else releaseBaselineSlot()
            synchronized(lock) {
                pendingRead?.offer(StreamRead(null, 0, true))
                payload = null
                if (started) {
                    started = false
                    listeners.forEach { it.onTransferEnd(source, spec, true) }
                }
            }
            synchronized(lifecycle) { sessions.remove(this) }
        }
    }

    private inner class Race(request: Request) {
        private val lock = Any()
        private val calls = List(2) { client.newCall(request) }
        private val results = LinkedBlockingQueue<Outcome>()
        private val released = AtomicBoolean()
        private val networkReleased = List(2) { AtomicBoolean() }
        private val enqueued = BooleanArray(2)
        private fun releaseNetwork(call: Call) {
            val index = calls.indexOfFirst { it === call }
            if (index >= 0 && networkReleased[index].compareAndSet(false, true)) networkPermits.release()
        }
        private var stopped = false
        private var winner: Payload? = null

        fun start() = synchronized(lock) {
            if (stopped || isClosed) { cancel(); return@synchronized }
            calls.forEachIndexed { index, call ->
                enqueued[index] = true
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        try { failed() } finally { releaseNetwork(call) }
                    }
                    override fun onResponse(call: Call, response: Response) {
                        var retained = false
                        var candidate: Payload? = null
                        try {
                            if (!response.isSuccessful) throw IOException("Unsuccessful HLS response")
                            val buffered = buffer(call, response)
                            candidate = buffered
                            synchronized(lock) {
                                if (!stopped && winner == null) {
                                    winner = buffered
                                    retained = true
                                    // Cancel before publishing, including a loser currently reading a body.
                                    calls.filter { it !== call }.forEach { it.cancel() }
                                    results.offer(Outcome.Success(buffered))
                                }
                            }
                        } catch (_: IOException) {
                            failed()
                        } finally {
                            if (!retained) {
                                call.cancel()
                                runCatching { response.close() }
                            }
                            if (!retained || candidate?.response == null) releaseNetwork(call)
                        }
                    }
                })
            }
        }

        private fun failed() = synchronized(lock) {
            if (!stopped && winner == null) results.offer(Outcome.Failure)
            Unit
        }

        fun await(): Payload {
            var failures = 0
            try {
                while (true) {
                    when (val next = results.take()) {
                        is Outcome.Success -> return next.payload
                        Outcome.Cancelled -> throw IOException("HLS trial request cancelled")
                        Outcome.Failure -> if (++failures == 2) throw IOException("Both HLS segment requests failed")
                    }
                }
            } catch (_: InterruptedException) {
                cancel()
                Thread.currentThread().interrupt()
                throw IOException("HLS trial loader interrupted")
            }
        }

        fun releasePermit() { if (released.compareAndSet(false, true)) pairPermit.release() }

        fun cancel() {
            val selected = synchronized(lock) {
                if (stopped) return
                stopped = true
                calls.forEachIndexed { index, call ->
                    call.cancel()
                    if (!enqueued[index]) releaseNetwork(call)
                }
                results.clear()
                results.offer(Outcome.Cancelled)
                winner.also { winner = null }
            }
            // Closing an Okio body concurrently with read can corrupt timeout
            // bookkeeping. Cancel the socket now, then serialize body cleanup off UI.
            if (selected?.response != null) {
                try { client.dispatcher.executorService.execute {
                    try { synchronized(selected.streamLock) { runCatching { selected.response.close() } } }
                    finally { selected.call?.let { releaseNetwork(it) } }
                } } catch (_: RejectedExecutionException) { selected.call?.let { releaseNetwork(it) } }
            }
            releasePermit()
        }
    }

    private class Chunk(val bytes: ByteArray, val size: Int)
    private class StreamRead(val bytes: ByteArray?, val count: Int, val failed: Boolean)
    private class Payload(
        val chunks: List<Chunk>,
        val stream: InputStream?,
        val response: Response?,
        val uri: Uri,
        val headers: Map<String, List<String>>,
        val length: Long,
        val call: Call?,
    ) { val streamLock = Any() }
    private sealed class Outcome {
        class Success(val payload: Payload) : Outcome()
        data object Failure : Outcome()
        data object Cancelled : Outcome()
    }

    private companion object {
        const val MAX_COPY_BYTES = 8 * 1024 * 1024
        const val CHUNK_BYTES = 64 * 1024

        fun eligible(spec: DataSpec): Boolean =
            spec.httpMethod == DataSpec.HTTP_METHOD_GET && spec.httpBody == null &&
                spec.position == 0L && spec.length == C.LENGTH_UNSET.toLong() &&
                (spec.uri.scheme.equals("http", true) || spec.uri.scheme.equals("https", true)) &&
                spec.httpRequestHeaders.keys.none { it.equals("Range", true) }

        fun buffer(call: Call, response: Response): Payload {
            val body = response.body ?: throw IOException("Missing HLS response body")
            val expected = body.contentLength()
            val input = body.byteStream()
            val uri = Uri.parse(response.request.url.toString())
            val headers = response.headers.toMultimap()
            val chunks = ArrayList<Chunk>()
            fun streaming() = Payload(chunks, input, response, uri, headers, expected, call)
            if (expected > MAX_COPY_BYTES) return streaming()
            var total = 0
            while (total < MAX_COPY_BYTES) {
                if (call.isCanceled()) throw IOException("HLS trial request cancelled")
                val bytes = ByteArray(minOf(CHUNK_BYTES, MAX_COPY_BYTES - total))
                var size = 0
                while (size < bytes.size) {
                    val count = input.read(bytes, size, bytes.size - size)
                    if (count == -1) {
                        if (size > 0) chunks.add(Chunk(bytes, size))
                        total += size
                        if (expected >= 0 && expected != total.toLong()) throw IOException("Incomplete HLS segment")
                        response.close()
                        return Payload(chunks, null, null, uri, headers, total.toLong(), call)
                    }
                    size += count
                }
                chunks.add(Chunk(bytes, size))
                total += size
            }
            // No extra allocation/probe, concatenation with the other copy, or reopening.
            return streaming()
        }
    }
}
