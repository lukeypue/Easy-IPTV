package com.easyiptv.player

import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Private, bounded snapshots. Neither filenames nor payloads contain the provider URL. */
internal class EpgSnapshotCache(val directory: File) {
    data class Snapshot(
        val savedAt: Long,
        val channels: Map<String, List<EpgEntry>>,
        val names: Map<String, String>,
        val displayNames: Map<String, String>
    )

    fun read(url: String): Snapshot? = try {
        pruneOrphanTemps()
        val file = File(directory, key(url) + ".epg")
        require(file.length() in 1..MAX_BYTES)
        DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            require(input.readInt() == 0x45504731)
            require(input.readUTF() == key(url))
            val savedAt = input.readLong()
            require(savedAt > 0 && savedAt <= System.currentTimeMillis() + 60_000)
            var total = 0
            val channels = HashMap<String, List<EpgEntry>>()
            repeat(count(input, MAX_CHANNELS)) {
                val id = input.readUTF()
                val entries = ArrayList<EpgEntry>()
                repeat(count(input, MAX_ENTRIES - total)) {
                    total++
                    val title = input.readUTF()
                    val start = input.readLong()
                    val end = input.readLong()
                    require(start > 0 && end > start)
                    entries.add(EpgEntry(title, "", start, end))
                }
                channels[id] = entries.sortedBy { it.startMs }
            }
            fun readMap(): Map<String, String> {
                val result = HashMap<String, String>()
                repeat(count(input, MAX_CHANNELS)) {
                    val key = input.readUTF()
                    result[key] = input.readUTF()
                }
                return result
            }
            val snapshot = Snapshot(savedAt, channels, readMap(), readMap())
            require(input.read() == -1)
            snapshot
        }
    } catch (_: Exception) { null }

    /** Write outside the store lock; only the final rename requires generation ownership. */
    fun prepare(url: String, snapshot: Snapshot): File {
        check(directory.isDirectory || directory.mkdirs())
        pruneOrphanTemps()
        val temp = File.createTempFile("snapshot-", ".tmp", directory)
        try {
            FileOutputStream(temp).use { file ->
                val bounded = object : FilterOutputStream(file) {
                    var size = 0L
                    override fun write(b: Int) { check(++size <= MAX_BYTES); out.write(b) }
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        size += len; check(size <= MAX_BYTES); out.write(b, off, len)
                    }
                }
                val output = DataOutputStream(BufferedOutputStream(bounded))
                output.writeInt(0x45504731)
                output.writeUTF(key(url))
                output.writeLong(snapshot.savedAt)
                require(snapshot.channels.size <= MAX_CHANNELS)
                require(snapshot.channels.values.sumOf { it.size } <= MAX_ENTRIES)
                output.writeInt(snapshot.channels.size)
                for ((id, entries) in snapshot.channels) {
                    output.writeUTF(id.take(4096)); output.writeInt(entries.size)
                    entries.forEach { entry ->
                        output.writeUTF(entry.title.take(8192))
                        output.writeLong(entry.startMs); output.writeLong(entry.endMs)
                    }
                }
                for (map in listOf(snapshot.names, snapshot.displayNames)) {
                    require(map.size <= MAX_CHANNELS)
                    output.writeInt(map.size)
                    for ((key, value) in map) {
                        output.writeUTF(key.take(4096)); output.writeUTF(value.take(4096))
                    }
                }
                output.flush()
                file.fd.sync()
            }
            return temp
        } catch (error: Exception) { temp.delete(); throw error }
    }

    fun commit(url: String, prepared: File) {
        Files.move(prepared.toPath(), File(directory, key(url) + ".epg").toPath(),
            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        // Three providers at most, each bounded independently. The committed file is kept.
        directory.listFiles { file -> file.extension == "epg" }
            ?.filter { it.name != key(url) + ".epg" }
            ?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach { it.delete() }
    }

    /** A writer may still own a recent temp; only remove abandoned, day-old staging files. */
    private fun pruneOrphanTemps() {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        directory.listFiles { file ->
            file.isFile && file.name.startsWith("snapshot-") && file.extension == "tmp" && file.lastModified() < cutoff
        }?.forEach { it.delete() }
    }

    private fun count(input: DataInputStream, max: Int): Int = input.readInt().also { require(it in 0..max) }

    companion object {
        const val MAX_CHANNELS = 20_000
        const val MAX_ENTRIES = 200_000
        private const val MAX_BYTES = 24L * 1024 * 1024
        const val FRESH_MS = 6L * 60 * 60 * 1000
        fun key(url: String): String = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
