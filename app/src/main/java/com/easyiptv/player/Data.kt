package com.easyiptv.player

import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/* ----------------------------- models ----------------------------- */

data class Category(val id: String, val name: String)
data class LiveChannel(
    val id: String,
    val name: String,
    val icon: String?,
    val categoryId: String?,
    val url: String,
    val epgId: String? = null
)
data class Movie(
    val id: String,
    val name: String,
    val icon: String?,
    val categoryId: String?,
    val url: String
)
data class SeriesItem(
    val id: String,
    val name: String,
    val icon: String?,
    val categoryId: String?
)
data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val episodeNum: Int,
    val url: String
)
data class EpgEntry(
    val title: String,
    val desc: String,
    val startMs: Long,
    val endMs: Long
)

data class AppData(
    val liveCats: List<Category>,
    val live: List<LiveChannel>,
    val vodCats: List<Category>,
    val movies: List<Movie>,
    val seriesCats: List<Category>,
    val series: List<SeriesItem>
)

/* ----------------------------- playlists ----------------------------- */

data class Playlist(
    val name: String,
    val type: String,          // "xtream" or "m3u"
    val host: String = "",
    val user: String = "",
    val pass: String = "",
    val url: String = ""       // m3u link
)

object PlaylistStore {
    const val MAX = 3

    fun load(prefs: SharedPreferences): List<Playlist> {
        val raw = prefs.getString("playlists", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Playlist(
                    name = o.optString("name", "My playlist"),
                    type = o.optString("type", "xtream"),
                    host = o.optString("host", ""),
                    user = o.optString("user", ""),
                    pass = o.optString("pass", ""),
                    url = o.optString("url", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(prefs: SharedPreferences, lists: List<Playlist>) {
        val arr = JSONArray()
        lists.forEach { p ->
            val o = JSONObject()
            o.put("name", p.name)
            o.put("type", p.type)
            o.put("host", p.host)
            o.put("user", p.user)
            o.put("pass", p.pass)
            o.put("url", p.url)
            arr.put(o)
        }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }

    fun activeIndex(prefs: SharedPreferences): Int = prefs.getInt("active_playlist", 0)
    fun setActive(prefs: SharedPreferences, i: Int) {
        prefs.edit().putInt("active_playlist", i).apply()
    }
}

/* ----------------------------- networking ----------------------------- */

object Net {
    const val UA = "EasyIPTV/2.0"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // No read timeout: used for long-running stream recording.
    val streamClient: OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            return resp.body?.string() ?: throw RuntimeException("Empty response")
        }
    }
}

/* ----------------------------- source interface ----------------------------- */

interface Source {
    val supportsEpg: Boolean
    val supportsSeries: Boolean

    /** Link to the provider's full TV guide file (XMLTV), if one is known. */
    fun xmltvUrl(): String?

    /** Returns null when the login works, or a friendly error message. */
    suspend fun test(): String?
    suspend fun loadAll(): AppData
    suspend fun epg(channelId: String, limit: Int): List<EpgEntry>
    suspend fun seriesEpisodes(seriesId: String): Map<Int, List<Episode>>
}

fun buildSource(p: Playlist): Source =
    if (p.type == "m3u") M3uSource(p.url) else XtreamSource(p.host, p.user, p.pass)

/* ----------------------------- Xtream Codes ----------------------------- */

class XtreamSource(rawHost: String, private val user: String, private val pass: String) : Source {
    private val base: String = normalizeHost(rawHost)
    override val supportsEpg = true
    override val supportsSeries = true

    override fun xmltvUrl(): String {
        val u = java.net.URLEncoder.encode(user, "UTF-8")
        val p = java.net.URLEncoder.encode(pass, "UTF-8")
        return "$base/xmltv.php?username=$u&password=$p"
    }

    private fun api(action: String?): String {
        val u = URLEncoder.encode(user, "UTF-8")
        val p = URLEncoder.encode(pass, "UTF-8")
        val sb = StringBuilder(base).append("/player_api.php?username=").append(u)
            .append("&password=").append(p)
        if (action != null) sb.append("&action=").append(action)
        return sb.toString()
    }

    override suspend fun test(): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(Net.get(api(null)))
            val info = json.optJSONObject("user_info")
            val auth = info?.opt("auth")?.toString()
            if (auth == "1") null
            else "Server reached, but the username or password was rejected."
        } catch (e: Exception) {
            "Couldn't reach your service. Double-check the address and port. (${e.message})"
        }
    }

    private fun parseCats(raw: String): List<Category> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Category(o.opt("category_id")?.toString() ?: "", o.optString("category_name", ""))
        }
    }

    override suspend fun loadAll(): AppData = withContext(Dispatchers.IO) {
        coroutineScope {
            val liveCatsD = async { runCatching { parseCats(Net.get(api("get_live_categories"))) }.getOrDefault(emptyList()) }
            val vodCatsD = async { runCatching { parseCats(Net.get(api("get_vod_categories"))) }.getOrDefault(emptyList()) }
            val serCatsD = async { runCatching { parseCats(Net.get(api("get_series_categories"))) }.getOrDefault(emptyList()) }

            val liveD = async {
                val arr = JSONArray(Net.get(api("get_live_streams")))
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val id = o.opt("stream_id")?.toString() ?: ""
                    LiveChannel(
                        id = id,
                        name = o.optString("name", "Channel"),
                        icon = o.optString("stream_icon", "").ifBlank { null },
                        categoryId = o.opt("category_id")?.toString(),
                        url = "$base/live/$user/$pass/$id.ts",
                        epgId = o.optString("epg_channel_id", "").ifBlank { null }
                    )
                }
            }
            val vodD = async {
                runCatching {
                    val arr = JSONArray(Net.get(api("get_vod_streams")))
                    (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        val id = o.opt("stream_id")?.toString() ?: ""
                        val ext = o.optString("container_extension", "mp4").ifBlank { "mp4" }
                        Movie(
                            id = id,
                            name = o.optString("name", "Movie"),
                            icon = o.optString("stream_icon", "").ifBlank { null },
                            categoryId = o.opt("category_id")?.toString(),
                            url = "$base/movie/$user/$pass/$id.$ext"
                        )
                    }
                }.getOrDefault(emptyList())
            }
            val serD = async {
                runCatching {
                    val arr = JSONArray(Net.get(api("get_series")))
                    (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        SeriesItem(
                            id = o.opt("series_id")?.toString() ?: "",
                            name = o.optString("name", "Series"),
                            icon = o.optString("cover", "").ifBlank { null },
                            categoryId = o.opt("category_id")?.toString()
                        )
                    }
                }.getOrDefault(emptyList())
            }

            AppData(
                liveCats = liveCatsD.await(),
                live = liveD.await(),
                vodCats = vodCatsD.await(),
                movies = vodD.await(),
                seriesCats = serCatsD.await(),
                series = serD.await()
            )
        }
    }

    // Most Xtream panels base64-encode guide text, but a few send plain text.
    // Decode only if the result looks like readable text; otherwise keep the original.
    private fun b64(s: String): String {
        if (s.isBlank()) return s
        return try {
            val decoded = String(Base64.decode(s, Base64.DEFAULT)).trim()
            val looksReadable = decoded.isNotBlank() && decoded.none {
                it == '\uFFFD' || (it.isISOControl() && it != '\n' && it != '\r' && it != '\t')
            }
            if (looksReadable) decoded else s
        } catch (e: Exception) {
            s
        }
    }

    // Guide entries come back in different shapes depending on the provider's panel.
    // Accept unix timestamps OR "2026-07-18 20:00:00" style text, and if the primary
    // guide call returns nothing, fall back to the full-day table call.
    private val epgTimeFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)

    private fun epgTime(o: JSONObject, tsField: String, txtField: String): Long {
        val ts = o.optString(tsField, "").toLongOrNull()
        if (ts != null && ts > 0) return ts * 1000
        val txt = o.optString(txtField, "")
        if (txt.isNotBlank()) {
            try {
                return epgTimeFmt.parse(txt)?.time ?: 0L
            } catch (e: Exception) { /* fall through */ }
        }
        return 0L
    }

    private fun parseEpgListings(raw: String): List<EpgEntry> {
        val arr = JSONObject(raw).optJSONArray("epg_listings") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val start = epgTime(o, "start_timestamp", "start")
            val end = epgTime(o, "stop_timestamp", "end").let {
                if (it > 0) it else epgTime(o, "stop_timestamp", "stop")
            }
            if (start <= 0L) null else EpgEntry(
                title = b64(o.optString("title", "")),
                desc = b64(o.optString("description", "")),
                startMs = start,
                endMs = if (end > start) end else start + 30 * 60 * 1000
            )
        }
    }

    override suspend fun epg(channelId: String, limit: Int): List<EpgEntry> =
        withContext(Dispatchers.IO) {
            try {
                var out = runCatching {
                    parseEpgListings(Net.get(api("get_short_epg") + "&stream_id=$channelId&limit=$limit"))
                }.getOrDefault(emptyList())

                if (out.isEmpty()) {
                    // Fallback: full-day guide table, then keep shows that haven't ended yet.
                    out = runCatching {
                        parseEpgListings(Net.get(api("get_simple_data_table") + "&stream_id=$channelId"))
                    }.getOrDefault(emptyList())
                    val now = System.currentTimeMillis()
                    out = out.filter { it.endMs >= now }.sortedBy { it.startMs }.take(limit)
                }
                out
            } catch (e: Exception) {
                emptyList()
            }
        }

    override suspend fun seriesEpisodes(seriesId: String): Map<Int, List<Episode>> =
        withContext(Dispatchers.IO) {
            val raw = Net.get(api("get_series_info") + "&series_id=$seriesId")
            val eps = JSONObject(raw).optJSONObject("episodes") ?: return@withContext emptyMap()
            val out = sortedMapOf<Int, List<Episode>>()
            val keys = eps.keys()
            while (keys.hasNext()) {
                val season = keys.next()
                val arr = eps.optJSONArray(season) ?: continue
                val list = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val id = o.opt("id")?.toString() ?: ""
                    val ext = o.optString("container_extension", "mp4").ifBlank { "mp4" }
                    Episode(
                        id = id,
                        title = o.optString("title", "Episode"),
                        season = season.toIntOrNull() ?: 0,
                        episodeNum = o.opt("episode_num")?.toString()?.toIntOrNull() ?: (i + 1),
                        url = "$base/series/$user/$pass/$id.$ext"
                    )
                }.sortedBy { it.episodeNum }
                out[season.toIntOrNull() ?: 0] = list
            }
            out
        }

    companion object {
        fun normalizeHost(h: String): String {
            var s = h.trim()
            if (s.isEmpty()) return s
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            return s.trimEnd('/')
        }
    }
}

/* ----------------------------- M3U playlists ----------------------------- */

class M3uSource(private val playlistUrl: String) : Source {
    override val supportsEpg = false
    override val supportsSeries = false
    private var tvgUrl: String? = null

    override fun xmltvUrl(): String? = tvgUrl

    private val movieExts = setOf("mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv")

    override suspend fun test(): String? = withContext(Dispatchers.IO) {
        try {
            val body = Net.get(playlistUrl.trim())
            if (body.contains("#EXTINF")) null
            else "That link answered, but it doesn't look like an M3U playlist."
        } catch (e: Exception) {
            "Couldn't open that playlist link. Double-check it. (${e.message})"
        }
    }

    override suspend fun loadAll(): AppData = withContext(Dispatchers.IO) {
        val body = Net.get(playlistUrl.trim())
        val attrRe = Regex("([\\w-]+)=\"(.*?)\"")

        val live = ArrayList<LiveChannel>()
        val movies = ArrayList<Movie>()
        val liveGroups = LinkedHashSet<String>()
        val movieGroups = LinkedHashSet<String>()

        var name = ""
        var logo: String? = null
        var group = ""
        var pending = false
        var idx = 0

        var tvgId: String? = null
        for (rawLine in body.lineSequence()) {
            val line = rawLine.trim()
            if (line.startsWith("#EXTM3U")) {
                // Some playlists announce their guide file here: url-tvg="http://..."
                val m = Regex("url-tvg=\"(.*?)\"").find(line)
                if (m != null && m.groupValues[1].isNotBlank()) tvgUrl = m.groupValues[1]
            } else if (line.startsWith("#EXTINF")) {
                val attrs = attrRe.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                logo = (attrs["tvg-logo"] ?: "").ifBlank { null }
                group = attrs["group-title"] ?: ""
                tvgId = (attrs["tvg-id"] ?: "").ifBlank { null }
                // Display name is whatever follows the comma after the attribute block.
                // Done this way so names that themselves contain commas stay whole.
                name = line.substringAfterLast('"', line)
                    .substringAfter(",", "").trim().ifBlank { "Channel" }
                pending = true
            } else if (pending && line.isNotBlank() && !line.startsWith("#")) {
                idx++
                // Ignore any ?token=... query part when checking the file extension.
                val ext = line.substringBefore('?').substringAfterLast('.', "").lowercase()
                val g = group.ifBlank { "Other" }
                if (ext in movieExts) {
                    movieGroups.add(g)
                    movies.add(Movie("m3u_$idx", name, logo, g, line))
                } else {
                    liveGroups.add(g)
                    live.add(LiveChannel("m3u_$idx", name, logo, g, line, epgId = tvgId))
                }
                pending = false
            }
        }

        AppData(
            liveCats = liveGroups.map { Category(it, it) },
            live = live,
            vodCats = movieGroups.map { Category(it, it) },
            movies = movies,
            seriesCats = emptyList(),
            series = emptyList()
        )
    }

    override suspend fun epg(channelId: String, limit: Int): List<EpgEntry> = emptyList()
    override suspend fun seriesEpisodes(seriesId: String): Map<Int, List<Episode>> = emptyMap()
}

/* ----------------------------- full TV guide (XMLTV) ----------------------------- */

/**
 * Downloads the provider's full guide file once and keeps ~36 hours of it in memory.
 * This is how the big IPTV apps populate their guide (and why it takes a minute).
 */
object EpgStore {
    val loading = androidx.compose.runtime.mutableStateOf(false)
    val loaded = androidx.compose.runtime.mutableStateOf(false)

    private var loadedUrl: String? = null
    private var byChannel: Map<String, List<EpgEntry>> = emptyMap()
    private var nameToId: Map<String, String> = emptyMap()
    private var idToName: Map<String, String> = emptyMap()

    data class GuideHit(val channelXmlId: String, val channelName: String, val entry: EpgEntry)

    /** Search every channel's schedule for upcoming shows matching the text. */
    fun search(q: String, limit: Int = 40): List<GuideHit> {
        val query = q.trim()
        if (query.length < 2 || byChannel.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val out = ArrayList<GuideHit>()
        for ((cid, list) in byChannel) {
            val cname = idToName[cid] ?: cid
            for (e in list) {
                if (e.endMs >= now && e.title.contains(query, ignoreCase = true)) {
                    out.add(GuideHit(cid, cname, e))
                }
            }
        }
        return out.sortedBy { it.entry.startMs }.take(limit)
    }

    private fun norm(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    fun clear() {
        loadedUrl = null
        byChannel = emptyMap()
        nameToId = emptyMap()
        idToName = emptyMap()
        loaded.value = false
        loading.value = false
    }

    /** Guide for one channel: match by guide id first, then by channel name. */
    fun guide(epgId: String?, channelName: String): List<EpgEntry> {
        if (byChannel.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val direct = epgId?.let { byChannel[it.lowercase()] }
        val byName = if (direct == null) {
            nameToId[norm(channelName)]?.let { byChannel[it] }
        } else null
        val list = direct ?: byName ?: return emptyList()
        return list.filter { it.endMs >= now }
    }

    suspend fun load(url: String?) {
        if (url.isNullOrBlank()) return
        if (loadedUrl == url && loaded.value) return
        if (loading.value) return
        loading.value = true
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val fmtZ = java.text.SimpleDateFormat("yyyyMMddHHmmss Z", java.util.Locale.US)
                val fmtNoZ = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US)
                fun parseTime(raw: String): Long {
                    val t = raw.trim()
                    if (t.isEmpty()) return 0L
                    return try {
                        if (t.contains(' ')) fmtZ.parse(t)?.time ?: 0L
                        else fmtNoZ.parse(t)?.time ?: 0L
                    } catch (e: Exception) { 0L }
                }

                val now = System.currentTimeMillis()
                val windowStart = now - 60L * 60 * 1000            // keep last hour
                val windowEnd = now + 7L * 24 * 60 * 60 * 1000     // ...through the next 7 days
                val programmes = HashMap<String, ArrayList<EpgEntry>>()
                val names = HashMap<String, String>()
                val idNames = HashMap<String, String>()

                val req = okhttp3.Request.Builder().url(url).header("User-Agent", Net.UA).build()
                Net.streamClient.newCall(req).execute().use { resp ->
                    val stream = resp.body?.byteStream() ?: return@use
                    val parser = android.util.Xml.newPullParser()
                    parser.setInput(stream, null)

                    var event = parser.eventType
                    var curChannelId: String? = null           // inside <channel>
                    var progChannel: String? = null            // inside <programme>
                    var progStart = 0L
                    var progStop = 0L
                    var progTitle = ""
                    var progDesc = ""
                    var textTarget = 0                          // 1=display-name 2=title 3=desc

                    while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                                "channel" -> curChannelId =
                                    parser.getAttributeValue(null, "id")?.lowercase()
                                "display-name" -> if (curChannelId != null) textTarget = 1
                                "programme" -> {
                                    progChannel = parser.getAttributeValue(null, "channel")?.lowercase()
                                    progStart = parseTime(parser.getAttributeValue(null, "start") ?: "")
                                    progStop = parseTime(parser.getAttributeValue(null, "stop") ?: "")
                                    progTitle = ""; progDesc = ""
                                }
                                "title" -> if (progChannel != null) textTarget = 2
                                "desc" -> if (progChannel != null) textTarget = 3
                            }
                            org.xmlpull.v1.XmlPullParser.TEXT -> when (textTarget) {
                                1 -> {
                                    val id = curChannelId
                                    val nm = parser.text?.trim() ?: ""
                                    if (id != null && nm.isNotBlank()) {
                                        val key = norm(nm)
                                        if (key.isNotBlank() && !names.containsKey(key)) names[key] = id
                                        if (!idNames.containsKey(id)) idNames[id] = nm
                                    }
                                }
                                2 -> progTitle = (progTitle + (parser.text ?: "")).trim()
                                3 -> { /* descriptions skipped: keeps a 7-day guide light in memory */ }
                            }
                            org.xmlpull.v1.XmlPullParser.END_TAG -> when (parser.name) {
                                "channel" -> curChannelId = null
                                "display-name", "title", "desc" -> textTarget = 0
                                "programme" -> {
                                    val ch = progChannel
                                    if (ch != null && progStart in 1 until windowEnd &&
                                        (if (progStop > 0) progStop else progStart) >= windowStart
                                    ) {
                                        val end = if (progStop > progStart) progStop
                                        else progStart + 30 * 60 * 1000
                                        programmes.getOrPut(ch) { ArrayList() }
                                            .add(EpgEntry(progTitle.ifBlank { "Program" }, progDesc, progStart, end))
                                    }
                                    progChannel = null
                                }
                            }
                        }
                        event = parser.next()
                    }
                }

                programmes.values.forEach { it.sortBy { e -> e.startMs } }
                byChannel = programmes
                nameToId = names
                idToName = idNames
                loadedUrl = url
                loaded.value = programmes.isNotEmpty()
            } catch (e: Exception) {
                // Guide stays empty; the app still works fine without it.
            } finally {
                loading.value = false
            }
        }
    }
}
