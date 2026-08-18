package com.easyiptv.player

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/* ----------------------------- offline downloads (user-selected retention) ----------------------------- */

/**
 * v4.20 download engine.
 *
 * New IPTV downloads no longer use Android DownloadManager. On the tested Fire
 * TV, EZTV itself can write recordings to the removable USB, while the system
 * DownloadManager job stayed stuck after enqueue(). Keep networking + storage in
 * EZTV's own process: one foreground service, one socket, one streaming file
 * copy, no whole-file RAM buffer.
 *
 * DownloadManager remains referenced ONLY so completed downloads created by an
 * older EZTV version can be recognized during migration instead of being erased.
 */
object DownloadStore {
    const val KEEP_FOREVER = 0
    private const val KEY = "downloads_v2"
    private const val RETENTION_KEY = "download_keep_days"
    private const val MIGRATION_KEY = "download_retention_v417_migrated"
    private const val ENGINE_MIGRATION_KEY = "download_engine_v420_migrated"

    const val STATE_UNKNOWN = 0
    const val STATE_PENDING = 1
    const val STATE_RUNNING = 2
    const val STATE_SUCCESS = 3
    const val STATE_FAILED = 4

    private fun stateKey(id: Long) = "download_state_$id"
    private fun doneKey(id: Long) = "download_done_$id"
    private fun totalKey(id: Long) = "download_total_$id"
    private fun errorKey(id: Long) = "download_error_$id"
    private fun appPrefs(context: Context) =
        context.getSharedPreferences("easyiptv", Context.MODE_PRIVATE)

    fun retentionDays(prefs: SharedPreferences): Int = prefs.getInt(RETENTION_KEY, KEEP_FOREVER)
    fun setRetentionDays(prefs: SharedPreferences, days: Int) {
        val safeDays = days.coerceAtLeast(0)
        prefs.edit().putInt(RETENTION_KEY, safeDays).apply()
        val expires = if (safeDays == 0) Long.MAX_VALUE
        else System.currentTimeMillis() + safeDays * 86_400_000L
        save(prefs, load(prefs).map { it.copy(expires = expires) })
    }

    fun migrateLegacyRetention(prefs: SharedPreferences) {
        if (prefs.getBoolean(MIGRATION_KEY, false)) return
        val migrated = load(prefs).map { it.copy(expires = Long.MAX_VALUE) }
        save(prefs, migrated)
        prefs.edit().putBoolean(MIGRATION_KEY, true).apply()
    }

    /**
     * One-time v4.20 migration from system DownloadManager to EZTV's own
     * downloader. Preserve genuinely completed files; cancel/remove old jobs
     * that were pending/running/paused/failed so a v4.19 zombie cannot block
     * the first v4.20 download forever.
     */
    fun migrateLegacyEngine(context: Context, prefs: SharedPreferences) {
        if (prefs.getBoolean(ENGINE_MIGRATION_KEY, false)) return
        val dm = runCatching {
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        }.getOrNull()
        val keep = ArrayList<Item>()
        load(prefs).forEach { item ->
            if (hasNativeState(context, item.id)) {
                keep.add(item)
                return@forEach
            }
            val f = File(item.path)
            val legacy = legacyManagerStatus(context, item.id)
            val completed = legacy == DownloadManager.STATUS_SUCCESSFUL && f.exists() && f.length() > 0L
            val orphanComplete = legacy == null && f.exists() && f.length() > 0L
            if (completed || orphanComplete) {
                mark(context, item.id, STATE_SUCCESS, f.length(), f.length())
                keep.add(item)
            } else {
                runCatching { dm?.remove(item.id) }
                runCatching { f.delete() }
                runCatching { File(item.path + ".part").delete() }
                clearState(context, item.id)
            }
        }
        save(prefs, keep)
        prefs.edit().putBoolean(ENGINE_MIGRATION_KEY, true).apply()
    }

    fun expiryForNewDownload(prefs: SharedPreferences, now: Long = System.currentTimeMillis()): Long {
        val days = retentionDays(prefs)
        return if (days <= 0) Long.MAX_VALUE else now + days * 86_400_000L
    }

    data class Item(val id: Long, val title: String, val path: String, val expires: Long, val url: String = "")

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
                    expires = o.optLong("expires"),
                    url = o.optString("url", "")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
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
            arr.put(
                JSONObject()
                    .put("id", d.id)
                    .put("title", d.title)
                    .put("path", d.path)
                    .put("expires", d.expires)
                    .put("url", d.url)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    internal fun mark(
        context: Context,
        id: Long,
        state: Int,
        done: Long = 0L,
        total: Long = -1L,
        error: String = ""
    ) {
        appPrefs(context).edit()
            .putInt(stateKey(id), state)
            .putLong(doneKey(id), done.coerceAtLeast(0L))
            .putLong(totalKey(id), total)
            .putString(errorKey(id), error.take(240))
            .apply()
    }

    private fun hasNativeState(context: Context, id: Long): Boolean =
        appPrefs(context).contains(stateKey(id))

    /** Older EZTV builds used DownloadManager. Read its old row only for migration. */
    private fun legacyManagerStatus(context: Context, id: Long): Int? = try {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (c.moveToFirst()) c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) else null
        }
    } catch (_: Exception) {
        null
    }

    fun state(context: Context, id: Long): Int {
        val prefs = appPrefs(context)
        if (prefs.contains(stateKey(id))) return prefs.getInt(stateKey(id), STATE_UNKNOWN)
        return when (legacyManagerStatus(context, id)) {
            DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED -> STATE_PENDING
            DownloadManager.STATUS_RUNNING -> STATE_RUNNING
            DownloadManager.STATUS_SUCCESSFUL -> STATE_SUCCESS
            DownloadManager.STATUS_FAILED -> STATE_FAILED
            else -> STATE_UNKNOWN
        }
    }

    fun error(context: Context, id: Long): String =
        appPrefs(context).getString(errorKey(id), "").orEmpty()

    fun isInFlight(context: Context, id: Long): Boolean = when (state(context, id)) {
        STATE_PENDING, STATE_RUNNING -> true
        else -> false
    }

    fun isReady(context: Context, item: Item): Boolean {
        val f = File(item.path)
        if (!f.exists() || f.length() <= 0L) return false
        return when (state(context, item.id)) {
            STATE_SUCCESS -> true
            STATE_PENDING, STATE_RUNNING, STATE_FAILED -> false
            // Legacy file whose old system row has been purged: preserve it.
            else -> true
        }
    }

    fun hasInFlight(context: Context, prefs: SharedPreferences): Boolean =
        load(prefs).any { isInFlight(context, it.id) }

    fun cleanup(context: Context, prefs: SharedPreferences) {
        val now = System.currentTimeMillis()
        val keep = ArrayList<Item>()
        load(prefs).forEach { item ->
            val f = File(item.path)
            when {
                item.expires != Long.MAX_VALUE && item.expires < now -> {
                    runCatching { f.delete() }
                    runCatching { File(item.path + ".part").delete() }
                    clearState(context, item.id)
                }
                state(context, item.id) == STATE_PENDING && item.url.isBlank() -> {
                    mark(context, item.id, STATE_FAILED, 0L, -1L, "Old download entry — select the title again to queue it.")
                    keep.add(item)
                }
                state(context, item.id) == STATE_RUNNING &&
                    hasNativeState(context, item.id) && !DownloadService.isActive(item.id) -> {
                    // A RUNNING transfer with no service really was interrupted.
                    // PENDING items are legitimate v4.21 queue entries and must
                    // survive app navigation/restarts.
                    runCatching { File(item.path + ".part").delete() }
                    mark(context, item.id, STATE_FAILED, 0L, -1L, "Download was interrupted. Start it again.")
                    keep.add(item)
                }
                isInFlight(context, item.id) -> keep.add(item)
                isReady(context, item) -> keep.add(item)
                state(context, item.id) == STATE_FAILED -> keep.add(item) // show the failure until user deletes/retries
                else -> {
                    runCatching { f.delete() }
                    runCatching { File(item.path + ".part").delete() }
                    clearState(context, item.id)
                }
            }
        }
        save(prefs, keep)
    }

    private fun clearState(context: Context, id: Long) {
        appPrefs(context).edit()
            .remove(stateKey(id))
            .remove(doneKey(id))
            .remove(totalKey(id))
            .remove(errorKey(id))
            .apply()
    }

    fun remove(prefs: SharedPreferences, item: Item, context: Context? = null) {
        runCatching { File(item.path).delete() }
        runCatching { File(item.path + ".part").delete() }
        save(prefs, load(prefs).filterNot { it.path == item.path })
        context?.let { clearState(it, item.id) }
    }

    internal fun removeById(context: Context, prefs: SharedPreferences, id: Long) {
        load(prefs).firstOrNull { it.id == id }?.let { remove(prefs, it, context) }
    }

    fun stopAndRemove(context: Context, prefs: SharedPreferences, item: Item) {
        if (isInFlight(context, item.id)) DownloadService.cancel(context, item.id)
        remove(prefs, item, context)
    }

    /** (bytes so far, total bytes or -1 if unknown). */
    fun progress(context: Context, id: Long): Pair<Long, Long>? {
        val prefs = appPrefs(context)
        if (prefs.contains(stateKey(id))) {
            return prefs.getLong(doneKey(id), 0L) to prefs.getLong(totalKey(id), -1L)
        }
        // Legacy DownloadManager progress only.
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (!c.moveToFirst()) {
                    null
                } else {
                    val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    done to total
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun safeName(title: String): String =
        title.replace(Regex("[^A-Za-z0-9 _.-]"), "").trim().replace(' ', '_').take(48)
            .ifBlank { "video" }

    /** Cancel every EZTV/provider download still in progress. Finished files stay. */
    fun cancelInFlight(context: Context, prefs: SharedPreferences): Int {
        val active = load(prefs).filter { isInFlight(context, it.id) }
        if (active.isEmpty()) return 0
        active.forEach { d ->
            DownloadService.cancel(context, d.id)
            runCatching { File(d.path + ".part").delete() }
            runCatching { File(d.path).delete() }
            clearState(context, d.id)
        }
        save(prefs, load(prefs).filterNot { d -> active.any { it.id == d.id } })
        return active.size
    }

    fun freeBytes(context: Context, prefs: SharedPreferences? = null): Long = try {
        val dir = if (prefs != null) Storage.baseDir(context, prefs, "downloads")
        else context.getExternalFilesDir("downloads") ?: context.filesDir
        android.os.StatFs(dir.absolutePath).availableBytes
    } catch (_: Exception) {
        -1L
    }

    /** Start or queue an app-owned streaming VOD download.
     * Only ONE transfer socket runs at a time; additional picks are tiny
     * metadata queue entries, so parents can line up a trip's worth of shows
     * without adding RAM/CPU pressure. */
    fun start(context: Context, prefs: SharedPreferences, title: String, url: String): String {
        return try {
            val free = freeBytes(context, prefs)
            if (free in 0 until 3_000_000_000L) {
                val gb = String.format(Locale.US, "%.1f", free / 1_073_741_824.0)
                return "Not enough storage to download safely — only $gb GB free. Free at least 3 GB and try again."
            }

            val existing = load(prefs).firstOrNull { it.title.trim().equals(title.trim(), ignoreCase = true) }
            if (existing != null) {
                when {
                    isInFlight(context, existing.id) ->
                        return "\"$title\" is already downloading or queued — check Downloads."
                    isReady(context, existing) ->
                        return "\"$title\" is already downloaded — find it in Downloads."
                    else -> remove(prefs, existing, context)
                }
            }

            if (url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
                return "This provider is delivering that title as HLS. EZTV downloads direct movie/episode files; HLS offline saving is not enabled yet."
            }

            val extRaw = url.substringBefore('?').substringAfterLast('.', "mp4")
            val ext = extRaw.takeIf { it.length in 1..5 && it.all { ch -> ch.isLetterOrDigit() } } ?: "mp4"
            val fname = safeName(title) + "_" + (System.currentTimeMillis() % 100000) + "." + ext
            val destDir = Storage.baseDir(context, prefs, "downloads")
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = File(destDir, fname)

            val alreadyDownloading = DownloadService.hasActive() ||
                load(prefs).any { state(context, it.id) == STATE_RUNNING }

            // Provider budget matters only for a transfer that will start NOW.
            // A queued title owns no socket/stream until it reaches the front.
            var stoppedPlayback = false
            if (!alreadyDownloading) {
                val maxStreams = ProviderStreams.max(prefs)
                if (ProviderStreams.used(context, prefs) + 1 > maxStreams) {
                    if (Recorder.activeName.value != null) {
                        return "No provider stream is free. Stop the recording or raise Provider streams only if your IPTV plan really allows more."
                    }
                    if (Playback.providerConnectionSlots() > 0) {
                        Playback.releaseAll()
                        stoppedPlayback = true
                    }
                }
                if (ProviderStreams.used(context, prefs) + 1 > ProviderStreams.max(prefs)) {
                    return "No provider stream is free for this download. Check Settings → Provider streams."
                }
            }

            var id = System.currentTimeMillis()
            while (load(prefs).any { it.id == id }) id++
            val item = Item(
                id = id,
                title = title,
                path = destFile.absolutePath,
                expires = expiryForNewDownload(prefs),
                url = url
            )
            save(prefs, load(prefs) + item)
            mark(context, id, STATE_PENDING, 0L, -1L, "")

            val started = kickQueue(context, prefs)
            val where = if (Storage.usingDrive(context, prefs)) " to your external drive" else ""
            val keep = retentionDays(prefs)
            val keepText = if (keep <= 0) "kept until you delete it"
                else "kept for $keep day${if (keep == 1) "" else "s"}"
            val pendingAhead = load(prefs).count {
                it.id != id && state(context, it.id) == STATE_PENDING
            }
            val base = if (started) {
                "Downloading \"$title\"$where — $keepText."
            } else {
                "Queued \"$title\"$where — ${pendingAhead + 1} in the download queue. $keepText."
            }
            val connectionNote = if (stoppedPlayback) {
                " Playback was stopped because your Provider streams setting had no free connection."
            } else ""
            base + connectionNote
        } catch (e: Exception) {
            "Couldn't start that download. (${e.message})"
        }
    }

    /** Start the oldest queued item when no download socket is active.
     * Returns true when a transfer was launched. */
    fun kickQueue(context: Context, prefs: SharedPreferences): Boolean {
        if (DownloadService.hasActive()) return false
        val next = load(prefs).firstOrNull {
            state(context, it.id) == STATE_PENDING && it.url.isNotBlank()
        } ?: return false

        // Queue entries cost zero provider connections until this point.
        val usedWithoutDownload = ProviderStreams.playbackSlots() + ProviderStreams.recordingSlots()
        if (usedWithoutDownload + 1 > ProviderStreams.max(prefs)) return false

        return try {
            // Reserve the queue head synchronously so rapid-fire selections do
            // not enqueue duplicate ACTION_START intents before the foreground
            // service has time to set its activeId.
            mark(context, next.id, STATE_RUNNING, 0L, -1L, "")
            DownloadService.start(context, next.id, next.title, next.url, next.path)
            true
        } catch (t: Throwable) {
            mark(context, next.id, STATE_FAILED, 0L, -1L, t.message ?: "Couldn't start queued download")
            false
        }
    }

}

/** One lightweight downloader for Fire TV: one socket + one file writer. */
class DownloadService : Service() {
    companion object {
        private const val ACTION_START = "com.easyiptv.player.DOWNLOAD_START"
        private const val ACTION_STOP = "com.easyiptv.player.DOWNLOAD_STOP"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIF_ID = 42

        @Volatile private var activeId: Long = 0L
        @Volatile private var activeCall: Call? = null

        fun start(context: Context, id: Long, title: String, url: String, path: String) {
            val i = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra("id", id)
                putExtra("title", title)
                putExtra("url", url)
                putExtra("path", path)
            }
            ContextCompat.startForegroundService(context, i)
        }

        fun cancel(context: Context, id: Long) {
            // Removing a QUEUED item must never stop the currently active movie.
            if (activeId == id) {
                runCatching { activeCall?.cancel() }
                context.stopService(Intent(context, DownloadService::class.java))
            }
        }

        fun isActive(id: Long): Boolean = id != 0L && activeId == id
        fun hasActive(): Boolean = activeId != 0L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var userCancelled = false

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(title: String, done: Long, total: Long): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, DownloadService::class.java).apply {
                action = ACTION_STOP
                putExtra("id", activeId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading: $title")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_delete, "Stop", stop)
        if (total > 0L) {
            val pct = ((done * 100L) / total).toInt().coerceIn(0, 100)
            b.setContentText("$pct% • ${done / 1_048_576L} MB of ${total / 1_048_576L} MB")
                .setProgress(100, pct, false)
        } else {
            b.setContentText("${done / 1_048_576L} MB downloaded")
                .setProgress(0, 0, true)
        }
        return b.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (job?.isActive == true) return START_NOT_STICKY
                val id = intent.getLongExtra("id", 0L)
                val title = intent.getStringExtra("title") ?: "video"
                val url = intent.getStringExtra("url") ?: return START_NOT_STICKY.also { stopSelf() }
                val path = intent.getStringExtra("path") ?: return START_NOT_STICKY.also { stopSelf() }
                activeId = id
                userCancelled = false
                startForeground(NOTIF_ID, notification(title, 0L, -1L))
                begin(id, title, url, File(path))
            }
            ACTION_STOP -> {
                val requested = intent.getLongExtra("id", activeId)
                if (requested == 0L || requested == activeId) {
                    userCancelled = true
                    runCatching { activeCall?.cancel() }
                    job?.cancel()
                    val prefs = getSharedPreferences("easyiptv", Context.MODE_PRIVATE)
                    if (activeId != 0L) DownloadStore.removeById(this, prefs, activeId)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun begin(id: Long, title: String, url: String, finalFile: File) {
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EasyIPTV:download")
            .apply { acquire(6L * 60 * 60 * 1000) }

        job = scope.launch {
            val part = File(finalFile.absolutePath + ".part")
            var done = 0L
            var total = -1L
            try {
                finalFile.parentFile?.mkdirs()
                runCatching { part.delete() }
                runCatching { finalFile.delete() }
                DownloadStore.mark(this@DownloadService, id, DownloadStore.STATE_RUNNING, 0L, -1L)

                val client = Net.client.newBuilder()
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

                fun executeWithUa(ua: String): okhttp3.Response {
                    val req = Request.Builder().url(url).header("User-Agent", ua).build()
                    val call = client.newCall(req)
                    activeCall = call
                    return call.execute()
                }

                var resp = executeWithUa(Net.UA)
                if (resp.code == 403 || resp.code == 406) {
                    resp.close()
                    resp = executeWithUa(
                        "Mozilla/5.0 (Linux; Android 9; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"
                    )
                }

                resp.use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    val body = r.body ?: throw IOException("Empty response")
                    total = body.contentLength()
                    DownloadStore.mark(this@DownloadService, id, DownloadStore.STATE_RUNNING, 0L, total)

                    body.byteStream().use { inp ->
                        FileOutputStream(part, false).use { out ->
                            val buf = ByteArray(128 * 1024)
                            var sinceState = 0L
                            var sinceSpace = 0L
                            var lastUi = System.currentTimeMillis()
                            while (isActive) {
                                val n = inp.read(buf)
                                if (n < 0) break
                                if (n == 0) continue
                                out.write(buf, 0, n)
                                done += n
                                sinceState += n
                                sinceSpace += n
                                val now = System.currentTimeMillis()
                                if (sinceState >= 4L * 1024 * 1024 || now - lastUi >= 1_500L) {
                                    sinceState = 0L
                                    lastUi = now
                                    DownloadStore.mark(this@DownloadService, id, DownloadStore.STATE_RUNNING, done, total)
                                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                                        .notify(NOTIF_ID, notification(title, done, total))
                                }
                                if (sinceSpace >= 32L * 1024 * 1024) {
                                    sinceSpace = 0L
                                    val free = runCatching { android.os.StatFs(finalFile.parentFile!!.absolutePath).availableBytes }
                                        .getOrDefault(Long.MAX_VALUE)
                                    if (free < 2_000_000_000L) throw IOException("Storage is almost full")
                                }
                            }
                            out.flush()
                        }
                    }
                }

                if (!isActive || userCancelled) throw IOException("Cancelled")
                if (total > 0L && done < total) throw IOException("Download ended early ($done of $total bytes)")
                if (!part.renameTo(finalFile)) throw IOException("Couldn't finish the file on this storage device")

                DownloadStore.mark(this@DownloadService, id, DownloadStore.STATE_SUCCESS, done, if (total > 0L) total else done)
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
                    NOTIF_ID,
                    NotificationCompat.Builder(this@DownloadService, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle("Downloaded: $title")
                        .setContentText("Saved for offline watching.")
                        .setAutoCancel(true)
                        .setContentIntent(
                            PendingIntent.getActivity(
                                this@DownloadService, 0, Intent(this@DownloadService, MainActivity::class.java),
                                PendingIntent.FLAG_IMMUTABLE
                            )
                        )
                        .build()
                )
            } catch (t: Throwable) {
                if (!userCancelled) {
                    runCatching { part.delete() }
                    DownloadStore.mark(
                        this@DownloadService,
                        id,
                        DownloadStore.STATE_FAILED,
                        done,
                        total,
                        t.message ?: "Download failed"
                    )
                }
            } finally {
                activeCall = null
                activeId = 0L
                runCatching { wakeLock?.let { if (it.isHeld) it.release() } }
                wakeLock = null
                // Clear the job reference before launching the next queued item,
                // otherwise onStartCommand would think the old transfer is still
                // active and discard the next ACTION_START.
                job = null
                stopForeground(STOP_FOREGROUND_DETACH)
                val prefs = getSharedPreferences("easyiptv", Context.MODE_PRIVATE)
                val startedNext = DownloadStore.kickQueue(this@DownloadService, prefs)
                if (!startedNext) stopSelf()
            }
        }
    }

    override fun onDestroy() {
        runCatching { activeCall?.cancel() }
        job?.cancel()
        job = null
        activeCall = null
        activeId = 0L
        runCatching { wakeLock?.let { if (it.isHeld) it.release() } }
        super.onDestroy()
    }
}
