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
 * Experimental HLS segment redundancy. The caller reserves two or three provider slots.
 * Copies race for one complete segment. A truncated prefix may be repaired with one
 * verified byte-range request in its original slot. Oversize copies stream normally.
 *
 * Supply an UNOBSERVED baseFactory and a fresh passive listener per DataSource.
 * Trial transfer callbacks count usable segment payload, not duplicate wire traffic;
 * they are not a measurement of physical ingress bandwidth.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class DualHlsDataSourceFactory(
    private val baseFactory: DataSource.Factory,
    private val userAgent: String,
    val copyCount: Int,
    private val listenerFactory: () -> TransferListener,
) : HlsDataSourceFactory, AutoCloseable {
    private val lifecycle = Any()
    private val sessions = mutableSetOf<Session>()
    init { require(copyCount in 2..3) }

    private val racePermit = Semaphore(1)
    // Includes playlist/key/baseline requests, not just the redundant copies.
    private val networkPermits = Semaphore(copyCount, true)
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
            if (!media || !eligible(spec) || !racePermit.tryAcquire()) return openBaseline()
            if (!networkPermits.tryAcquire(copyCount)) {
                racePermit.release()
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
                    racePermit.release()
                    networkPermits.release(copyCount)
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
            try {
                val length = base.open(spec)
                checkOpen()
                return length
            } catch (error: Exception) {
                // A factory close can run while the delegate is still opening.
                // Clean up any socket the delegate installed after that close.
                if (cancelled.get()) runCatching { base.close() }
                throw error
            }
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

    private inner class Race(private val request: Request) {
        private val lock = Any()
        private val calls = List(copyCount) { client.newCall(request) }
        private val results = LinkedBlockingQueue<Outcome>()
        private val released = AtomicBoolean()
        private val networkReleased = List(copyCount) { AtomicBoolean() }
        private val enqueued = BooleanArray(copyCount)
        private val repairCalls = arrayOfNulls<Call>(copyCount)
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
                            if (response.code != 200) throw IOException("Expected a complete HLS response")
                            val buffered = buffer(call, response, MAX_RACE_BYTES / copyCount) { chunks, total, expected ->
                                repair(index, response, chunks, total, expected)
                            }
                            candidate = buffered
                            synchronized(lock) {
                                if (!stopped && winner == null) {
                                    winner = buffered
                                    retained = true
                                    // Cancel before publishing, including a loser currently reading a body.
                                    calls.indices.filter { it != index }.forEach {
                                        calls[it].cancel()
                                        repairCalls[it]?.cancel()
                                    }
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

        private fun repair(index: Int, original: Response, chunks: MutableList<Chunk>, start: Int, total: Long): Payload {
            val etag = original.headers.values("ETag").singleOrNull()
            if (start <= 0 || start >= total || etag == null || !strongEtag(etag) ||
                !identityEncoding(original) || original.header("Content-Range") != null) {
                throw IOException("Incomplete HLS segment without a verifiable range")
            }
            // Reuse this lane only after closing its original socket. Keep the
            // request in the race's cancellation set while the repair is running.
            original.close()
            val last = total - 1
            val range = request.newBuilder().header("Range", "bytes=$start-$last")
                .header("If-Range", etag).build()
            val repair = synchronized(lock) {
                if (stopped || winner != null || isClosed) throw IOException("HLS repair cancelled")
                client.newCall(range).also {
                    it.timeout().timeout(3, TimeUnit.SECONDS)
                    repairCalls[index] = it
                }
            }
            try {
                repair.execute().use { response ->
                    val body = response.body ?: throw IOException("Missing HLS repair body")
                    if (response.code != 206 || response.request.url != original.request.url ||
                        response.headers.values("ETag").singleOrNull() != etag || !identityEncoding(response) ||
                        response.headers.values("Content-Range").singleOrNull() != "bytes $start-$last/$total" ||
                        (body.contentLength() >= 0 && body.contentLength() != total - start)) {
                        throw IOException("HLS repair does not match the original segment")
                    }
                    val input = body.byteStream()
                    var remaining = (total - start).toInt()
                    while (remaining > 0) {
                        val bytes = ByteArray(minOf(CHUNK_BYTES, remaining))
                        var offset = 0
                        while (offset < bytes.size) {
                            val count = input.read(bytes, offset, bytes.size - offset)
                            if (count == -1) throw IOException("Incomplete HLS repair")
                            offset += count
                        }
                        chunks.add(Chunk(bytes, bytes.size))
                        remaining -= bytes.size
                    }
                    if (input.read() != -1) throw IOException("HLS repair exceeds its verified range")
                }
                return Payload(chunks, null, null, Uri.parse(original.request.url.toString()),
                    original.headers.toMultimap(), total, null)
            } finally {
                synchronized(lock) { repairCalls[index] = null }
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
                        Outcome.Failure -> if (++failures == copyCount) throw IOException("All HLS segment copies failed")
                    }
                }
            } catch (_: InterruptedException) {
                cancel()
                Thread.currentThread().interrupt()
                throw IOException("HLS trial loader interrupted")
            }
        }

        fun releasePermit() { if (released.compareAndSet(false, true)) racePermit.release() }

        fun cancel() {
            val selected = synchronized(lock) {
                if (stopped) return
                stopped = true
                calls.forEachIndexed { index, call ->
                    call.cancel()
                    repairCalls[index]?.cancel()
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
        const val MAX_RACE_BYTES = 16 * 1024 * 1024
        const val CHUNK_BYTES = 64 * 1024

        fun eligible(spec: DataSpec): Boolean =
            spec.httpMethod == DataSpec.HTTP_METHOD_GET && spec.httpBody == null &&
                spec.position == 0L && spec.length == C.LENGTH_UNSET.toLong() &&
                (spec.uri.scheme.equals("http", true) || spec.uri.scheme.equals("https", true)) &&
                spec.httpRequestHeaders.keys.none { it.equals("Range", true) }

        fun identityEncoding(response: Response): Boolean =
            response.header("Content-Encoding")?.equals("identity", ignoreCase = true) ?: true

        fun strongEtag(value: String): Boolean =
            value.length >= 2 && value.first() == '"' && value.last() == '"' &&
                value.substring(1, value.lastIndex).none { it == '"' || it <= ' ' || it == '\u007f' }

        fun buffer(call: Call, response: Response, cap: Int,
            repair: (MutableList<Chunk>, Int, Long) -> Payload): Payload {
            val body = response.body ?: throw IOException("Missing HLS response body")
            val expected = body.contentLength()
            val input = body.byteStream()
            val uri = Uri.parse(response.request.url.toString())
            val headers = response.headers.toMultimap()
            val chunks = ArrayList<Chunk>()
            fun streaming() = Payload(chunks, input, response, uri, headers, expected, call)
            if (expected > cap) return streaming()
            var total = 0
            while (total < cap) {
                if (call.isCanceled()) throw IOException("HLS trial request cancelled")
                val bytes = ByteArray(minOf(CHUNK_BYTES, cap - total))
                var size = 0
                var finished = false
                try {
                    while (size < bytes.size) {
                        val count = input.read(bytes, size, bytes.size - size)
                        if (count == -1) {
                            finished = true
                            break
                        }
                        size += count
                    }
                } catch (error: IOException) {
                    if (call.isCanceled() || expected < 0) throw error
                    finished = true
                }
                if (size > 0) chunks.add(Chunk(if (size < bytes.size) bytes.copyOf(size) else bytes, size))
                total += size
                if (finished) {
                    if (expected >= 0 && expected != total.toLong()) return repair(chunks, total, expected)
                    response.close()
                    return Payload(chunks, null, null, uri, headers, total.toLong(), call)
                }
            }
            return streaming()
        }
    }
}
