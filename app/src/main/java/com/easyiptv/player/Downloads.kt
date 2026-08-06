package com.easyiptv.player

import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/* ----------------------------- offline downloads (expire after 14 days) ----------------------------- */

object DownloadStore {
    const val DAYS = 14L
    private const val KEY = "downloads_v2"

    data class Item(val id: Long, val title: String, val path: String, val expires: Long)

    fun load(prefs: SharedPreferences): List<Item> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val all = try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Item(
                    id = o.optLong("id"),
                    title = o.optString("title"),
                    path = o.optString("path"),
                    expires = o.optLong("expires")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        // A title should only ever appear ONCE in the list. If a duplicate snuck in
        // (e.g. the download button was pressed twice), keep the copy whose file
        // actually exists and works, and hide the dead one.
        val byTitle = LinkedHashMap<String, Item>()
        all.forEach { item ->
            val key = item.title.trim().lowercase()
            val existing = byTitle[key]
            if (existing == null) {
                byTitle[key] = item
            } else {
                val existingReady = File(existing.path).let { it.exists() && it.length() > 0 }
                val itemReady = File(item.path).let { it.exists() && it.length() > 0 }
                if (itemReady && !existingReady) byTitle[key] = item
            }
        }
        return byTitle.values.toList()
    }

    fun save(prefs: SharedPreferences, items: List<Item>) {
        val arr = JSONArray()
        items.forEach { d ->
            val o = JSONObject()
            o.put("id", d.id)
            o.put("title", d.title)
            o.put("path", d.path)
            o.put("expires", d.expires)
            arr.put(o)
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    /** Is this download still actively working in the Android download system? */
    private fun isStillDownloading(context: Context, id: Long): Boolean {
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (!c.moveToFirst()) return false
                when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED -> true
                    else -> false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Housekeeping at app start:
     *  - delete anything older than 14 days
     *  - drop dead entries (downloads that failed and left no file behind),
     *    which is what caused the "shows twice but only one works" problem
     */
    fun cleanup(context: Context, prefs: SharedPreferences) {
        val now = System.currentTimeMillis()
        val keep = ArrayList<Item>()
        load(prefs).forEach { item ->
            val f = File(item.path)
            val hasFile = f.exists() && f.length() > 0
            when {
                item.expires < now -> runCatching { f.delete() }          // too old — remove
                hasFile -> keep.add(item)                                  // downloaded and working
                isStillDownloading(context, item.id) -> keep.add(item)     // still coming in
                else -> runCatching { f.delete() }                         // failed/dead — drop it
            }
        }
        save(prefs, keep)
    }

    fun remove(prefs: SharedPreferences, item: Item) {
        runCatching { File(item.path).delete() }
        save(prefs, load(prefs).filterNot { it.path == item.path })
    }

    /** Stop an in-progress download AND remove it (also works on finished ones). */
    fun stopAndRemove(context: Context, prefs: SharedPreferences, item: Item) {
        runCatching {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.remove(item.id)   // cancels if running, deletes the partial file
        }
        remove(prefs, item)
    }

    /** (bytes so far, total bytes or -1 if unknown), or null if the system has no record. */
    fun progress(context: Context, id: Long): Pair<Long, Long>? {
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (!c.moveToFirst()) return null
                val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                done to total
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun safeName(title: String): String =
        title.replace(Regex("[^A-Za-z0-9 _.-]"), "").trim().replace(' ', '_').take(48)
            .ifBlank { "video" }

    /** Free bytes on the storage that holds downloads, or -1 if unknown. */
    fun freeBytes(context: Context): Long = try {
        val dir = context.getExternalFilesDir("downloads") ?: context.filesDir
        android.os.StatFs(dir.absolutePath).availableBytes
    } catch (e: Exception) {
        -1L
    }

    /** Starts a system download. Returns a message to show the user. */
    fun start(context: Context, prefs: SharedPreferences, title: String, url: String): String {
        return try {
            // Storage guard: Fire Sticks (16 GB) corrupt themselves when storage
            // fills up. Require 3 GB free before starting — a typical movie is
            // 1–2 GB, which always leaves breathing room.
            val free = freeBytes(context)
            if (free in 0 until 3_000_000_000L) {
                val gb = String.format(java.util.Locale.US, "%.1f", free / 1_073_741_824.0)
                return "Not enough storage to download safely — only $gb GB free. EZTV needs 3 GB free to start a download so the device stays healthy. Delete a download or recording first."
            }
            // Never create a second copy of the same title. If it's already saved
            // or already on its way down, just say so.
            val existing = load(prefs).firstOrNull { it.title.trim().equals(title.trim(), ignoreCase = true) }
            if (existing != null) {
                val f = File(existing.path)
                when {
                    f.exists() && f.length() > 0 ->
                        return "\"$title\" is already downloaded — find it in Downloads."
                    isStillDownloading(context, existing.id) ->
                        return "\"$title\" is already downloading — check Downloads for progress."
                    else -> {
                        // Old attempt died — clear it out and start fresh.
                        runCatching { f.delete() }
                        save(prefs, load(prefs).filterNot { it.path == existing.path })
                    }
                }
            }

            val ext = url.substringBefore('?').substringAfterLast('.', "mp4")
                .take(4).ifBlank { "mp4" }
            val fname = safeName(title) + "_" + System.currentTimeMillis() % 100000 + "." + ext
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle(title)
                .addRequestHeader("User-Agent", Net.UA)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, "downloads", fname)
            val id = dm.enqueue(req)
            val path = File(context.getExternalFilesDir("downloads"), fname).absolutePath
            val expires = System.currentTimeMillis() + DAYS * 24L * 60L * 60L * 1000L
            save(prefs, load(prefs) + Item(id, title, path, expires))
            "Downloading \"$title\" — check the Downloads section. It stays for 14 days."
        } catch (e: Exception) {
            "Couldn't start that download. (${e.message})"
        }
    }
}
