package com.easyiptv.player

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

enum class StorageKind { USB, INTERNAL }

data class LiveStorageTarget(
    val root: File,
    val kind: StorageKind,
    val freeBytes: Long,
    val maxRingBytes: Long,
)

/** Picks durable app-owned disk for the rolling live-TV ring. USB/removable
 * storage wins when present and writable; phones and Fire TV without USB fall
 * back to internal app storage automatically. Each mode leaves a safety reserve
 * and receives a bounded ring budget so DVR can never consume the whole device. */
internal object LiveStorageManager {
    const val RESERVE_FREE_BYTES = 512L * 1024L * 1024L
    const val MIN_RING_BYTES = 256L * 1024L * 1024L
    const val INTERNAL_MAX_RING_BYTES = 1L * 1024L * 1024L * 1024L
    const val USB_MAX_RING_BYTES = 64L * 1024L * 1024L * 1024L

    fun choose(context: Context): LiveStorageTarget? {
        val removable = context.getExternalFilesDirs(null)
            .filterNotNull()
            .filter { dir -> runCatching { Environment.isExternalStorageRemovable(dir) }.getOrDefault(false) }

        for (base in removable) {
            probe(base, StorageKind.USB)?.let { return it }
        }
        return probe(context.filesDir, StorageKind.INTERNAL)
    }

    private fun probe(base: File, kind: StorageKind): LiveStorageTarget? {
        return runCatching {
            val root = File(base, "zako-live-ring")
            if (!root.exists() && !root.mkdirs()) return@runCatching null
            val free = StatFs(root.absolutePath).availableBytes
            val usable = (free - RESERVE_FREE_BYTES).coerceAtLeast(0L)
            val ceiling = if (kind == StorageKind.USB) USB_MAX_RING_BYTES else INTERNAL_MAX_RING_BYTES
            val budget = minOf(usable, ceiling)
            if (budget < MIN_RING_BYTES) return@runCatching null

            val probe = File(root, ".write-probe-${System.nanoTime()}")
            probe.writeBytes(byteArrayOf(0x5A))
            if (probe.length() != 1L) return@runCatching null
            if (!probe.delete()) return@runCatching null
            LiveStorageTarget(root, kind, free, budget)
        }.getOrNull()
    }
}
