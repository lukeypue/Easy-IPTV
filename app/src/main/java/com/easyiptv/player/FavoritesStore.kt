package com.easyiptv.player

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal enum class FavoriteKind(val label: String) {
    LIVE("Live TV"), MOVIE("Movies"), SERIES("Series")
}

internal data class FavoriteItem(
    val kind: FavoriteKind,
    val id: String,
    val name: String,
    val url: String? = null,
    val icon: String? = null,
    val epgId: String? = null
) {
    val key: String get() = keyFor(kind, id, url)
    val legacyKey: String? get() = if (kind == FavoriteKind.LIVE) "LIVE:$id" else null

    fun channel() = url?.let { LiveChannel(id, name, icon, null, it, epgId) }
    fun movie() = url?.let { Movie(id, name, icon, null, it) }
    fun series() = SeriesItem(id, name, icon, null)

    companion object {
        // M3U IDs are row numbers and can change when the provider reorders entries.
        fun keyFor(kind: FavoriteKind, id: String, url: String?): String =
            "${kind.name}:${if (id.startsWith("m3u_")) url ?: id else id}"

        fun from(channel: LiveChannel) = FavoriteItem(FavoriteKind.LIVE, channel.id, channel.name,
            channel.url, channel.icon, channel.epgId)
        fun from(movie: Movie) = FavoriteItem(FavoriteKind.MOVIE, movie.id, movie.name, movie.url, movie.icon)
        fun from(series: SeriesItem) = FavoriteItem(FavoriteKind.SERIES, series.id, series.name, icon = series.icon)
    }
}

class FavoritesStore(private val prefs: SharedPreferences, playlist: Playlist?, slot: Int) {
    private val key = playlist?.let(::playlistKey) ?: "favorites_slot_$slot"
    internal var items by mutableStateOf(loadItems(prefs.getString(key, null)
        ?: if (playlist == null) encode(legacyLive(prefs, slot)) else null))
        private set

    internal fun contains(item: FavoriteItem): Boolean = items.any { matches(it, item) }

    internal fun toggle(item: FavoriteItem) {
        if (contains(item)) remove(item) else save(items + item)
    }

    internal fun remove(item: FavoriteItem) = save(items.filterNot { matches(it, item) })

    private fun matches(saved: FavoriteItem, item: FavoriteItem): Boolean =
        saved.key == item.key || (saved.kind == FavoriteKind.LIVE && saved.url == null && saved.key == item.legacyKey)

    private fun save(next: List<FavoriteItem>) {
        items = next
        prefs.edit().putString(key, encode(next)).apply()
    }

    companion object {
        // Import every existing playlist before a playlist can be removed or its slot shifts.
        internal fun migrateLive(prefs: SharedPreferences, playlists: List<Playlist>) {
            val pending = playlists.withIndex().filter { prefs.contains("fav_live_${it.index}") }
            if (pending.isEmpty()) return
            val editor = prefs.edit()
            for ((key, slots) in pending.groupBy { playlistKey(it.value) }) {
                val legacy = slots.flatMap { legacyLive(prefs, it.index) }.distinctBy { it.key }
                if (legacy.isNotEmpty() && !prefs.contains(key)) editor.putString(key, encode(legacy))
                for (slot in slots) editor.remove("fav_live_${slot.index}")
            }
            editor.apply()
        }

        private fun playlistKey(playlist: Playlist): String {
            val identity = JSONArray(listOf(playlist.type, playlist.host, playlist.user, playlist.url)).toString()
            val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            return "favorites_" + digest.joinToString("") { "%02x".format(it) }
        }

        private fun legacyLive(prefs: SharedPreferences, slot: Int) =
            prefs.getStringSet("fav_live_$slot", emptySet()).orEmpty().sorted().map {
                FavoriteItem(FavoriteKind.LIVE, it, "Saved channel")
            }

        private fun encode(items: List<FavoriteItem>): String = JSONArray().apply {
            for (item in items) put(JSONObject().put("kind", item.kind.name).put("id", item.id)
                .put("name", item.name).put("url", item.url).put("icon", item.icon).put("epg", item.epgId))
        }.toString()

        private fun loadItems(raw: String?): List<FavoriteItem> {
            val entries = raw?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
            return (0 until entries.length()).mapNotNull { index ->
                val row = entries.optJSONObject(index) ?: return@mapNotNull null
                val kind = FavoriteKind.entries.firstOrNull { it.name == row.optString("kind") } ?: return@mapNotNull null
                val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                FavoriteItem(kind, id, row.optString("name"), row.optString("url").ifBlank { null },
                    row.optString("icon").ifBlank { null }, row.optString("epg").ifBlank { null })
            }.distinctBy { it.key }
        }
    }
}
