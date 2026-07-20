package com.easyiptv.player

import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/* ----------------------------- offline downloads (expire after 7 days) ----------------------------- */

object DownloadStore {
    const val DAYS = 7L
    private const val KEY = "downloads_v2"

    data class Item(val id: Long, val title: String, val path: String, val expires: Long)

    fun load(prefs: SharedPreferences): List<Item> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
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

    /** Delete anything older than 7 days. Call once at app start. */
    fun cleanup(prefs: SharedPreferences) {
        val now = System.currentTimeMillis()
        val keep = ArrayList<Item>()
        load(prefs).forEach { item ->
            if (item.expires < now) {
                runCatching { File(item.path).delete() }
            } else {
                keep.add(item)
            }
        }
        save(prefs, keep)
    }

    fun remove(prefs: SharedPreferences, item: Item) {
        runCatching { File(item.path).delete() }
        save(prefs, load(prefs).filterNot { it.path == item.path })
    }

    private fun safeName(title: String): String =
        title.replace(Regex("[^A-Za-z0-9 _.-]"), "").trim().replace(' ', '_').take(48)
            .ifBlank { "video" }

    /** Starts a system download. Returns a message to show the user. */
    fun start(context: Context, prefs: SharedPreferences, title: String, url: String): String {
        return try {
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
            "Downloading \"$title\" — check the Downloads section in More. It stays for 7 days."
        } catch (e: Exception) {
            "Couldn't start that download. (${e.message})"
        }
    }
}
