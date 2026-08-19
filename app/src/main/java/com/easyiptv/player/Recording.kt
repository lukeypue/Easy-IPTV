package com.easyiptv.player

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
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
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ----------------------------- the DVR engine -----------------------------
 * Recording runs inside a foreground service, so it keeps going even when the
 * app is closed or another app is on screen. The device itself must stay
 * powered on — no software can record through a power cut.
 */

object Recorder {
    /** Name of what's currently recording, or null. Compose-observable. */
    val activeName = androidx.compose.runtime.mutableStateOf<String?>(null)
    /** Last user-facing recording result/status. Compose-observable. */
    val lastStatus = androidx.compose.runtime.mutableStateOf<String?>(null)
    /** True only when the recorder opened its OWN provider HTTP stream. A
     * watched-channel tee recording is false because it shares Live TV's DVR. */
    @Volatile var usesProviderConnection: Boolean = false
        internal set
    @Volatile var activeUrl: String? = null
        internal set

    fun recordingsDir(context: Context): File {
        val prefs = context.getSharedPreferences("easyiptv", Context.MODE_PRIVATE)
        return Storage.baseDir(context, prefs, "recordings")
    }

    /**
     * Storage check before recording. Returns null when fine, a BLOCKING
     * message when there's no safe room, or a WARNING message (prefixed
     * "WARN:") when it can start but might stop early.
     */
    fun spaceCheck(context: Context): String? {
        val free = try {
            android.os.StatFs(recordingsDir(context).absolutePath).availableBytes
        } catch (e: Exception) { return null }
        val gb = String.format(Locale.US, "%.1f", free / 1_073_741_824.0)
        return when {
            free < 2_500_000_000L ->
                "No room to record — only $gb GB free. Delete a download or recording first, then try again."
            free < 4_500_000_000L ->
                "WARN:Heads up — only $gb GB free. A long recording may stop early to protect the device."
            else -> null
        }
    }

    /**
     * Start recording now. stopAtMs = auto-stop time (null = record until stopped).
     * teeFromTimeshift = true when recording the channel currently being watched:
     * the recording copies from the DVR file instead of opening a SECOND provider
     * connection — which single-stream accounts would kill after ~20 seconds.
     */
    fun start(
        context: Context,
        url: String,
        name: String,
        stopAtMs: Long? = null,
        teeFromTimeshift: Boolean = false
    ) {
        val i = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
            putExtra("url", url)
            putExtra("name", name)
            putExtra("tee", teeFromTimeshift)
            if (stopAtMs != null) putExtra("stopAt", stopAtMs)
        }
        ContextCompat.startForegroundService(context, i)
    }

    fun stop(context: Context) {
        val i = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        context.startService(i)
    }
}

class RecordingService : Service() {
    companion object {
        const val ACTION_START = "com.easyiptv.player.RECORD_START"
        const val ACTION_STOP = "com.easyiptv.player.RECORD_STOP"
        private const val CHANNEL_ID = "recording"
        private const val NOTIF_ID = 41
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(name: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, RecordingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Recording: $name")
            .setContentText("Zako is recording in the background.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Stop recording", stop)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Simple Mode = live TV only. Any recording that tries to start
                // (including a scheduled one firing) is skipped while it's on.
                val simple = getSharedPreferences("easyiptv", Context.MODE_PRIVATE)
                    .getBoolean("simple_mode", true)
                if (simple) {
                    Recorder.lastStatus.value = "Recording is off in Smooth Live. Switch to DVR Live, then press Record again."
                    stopSelf()
                    return START_NOT_STICKY
                }
                val url = intent.getStringExtra("url") ?: return START_NOT_STICKY.also { stopSelf() }
                val prefs = getSharedPreferences("easyiptv", Context.MODE_PRIVATE)
                val name = intent.getStringExtra("name") ?: "channel"
                val stopAt = if (intent.hasExtra("stopAt")) intent.getLongExtra("stopAt", 0L) else null
                // If this is the exact channel already playing through DVR Live,
                // share that one stream even for scheduled/manual calls that did
                // not explicitly know to request a tee.
                val sameWatchedChannel = Playback.currentProviderUrl()?.let { it == url } == true
                val tee = intent.getBooleanExtra("tee", false) ||
                    (sameWatchedChannel && Playback.canTeeRecording())

                // A tee costs zero extra provider streams. A direct recording
                // costs one. Only cancel a download when the user's configured
                // 1/2/3-stream budget would otherwise be exceeded.
                if (!tee) {
                    val max = ProviderStreams.max(prefs)
                    val playback = ProviderStreams.playbackSlots()
                    val download = ProviderStreams.downloadSlots(this, prefs)
                    if (playback + download + 1 > max && download > 0) {
                        DownloadStore.cancelInFlight(this, prefs)
                    }
                }
                startForeground(NOTIF_ID, notification(name))
                beginRecording(url, name, stopAt, tee)
            }
            ACTION_STOP -> {
                job?.cancel()
                job = null
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /** Copy the live DVR (timeshift) file into the recording as it grows —
     *  recording the watched channel WITHOUT a second provider connection.
     *  Returns true if it ended because the DVR feed changed/stopped (channel
     *  change) — the caller then finishes via a direct connection if needed. */
    private fun teeFromTimeshift(out: FileOutputStream, stopAt: Long?, isActive: () -> Boolean): Boolean {
        val src = Timeshift.file ?: return true
        return try {
            java.io.RandomAccessFile(src, "r").use { raf ->
                // Start from "now" — the live edge of the DVR file.
                var pos = Timeshift.bytesWritten
                val buf = ByteArray(64 * 1024)
                var sinceCheck = 0L
                while (isActive() && (stopAt == null || System.currentTimeMillis() < stopAt)) {
                    if (Timeshift.file !== src) return true   // channel changed
                    val avail = minOf(Timeshift.bytesWritten, raf.length()) - pos
                    if (avail > 0) {
                        raf.seek(pos)
                        val want = if (buf.size.toLong() < avail) buf.size else avail.toInt()
                        val n = raf.read(buf, 0, want)
                        if (n > 0) {
                            out.write(buf, 0, n)
                            pos += n
                            sinceCheck += n
                            if (sinceCheck > 32_000_000) {
                                sinceCheck = 0
                                val freeNow = runCatching {
                                    android.os.StatFs(Recorder.recordingsDir(this).absolutePath).availableBytes
                                }.getOrDefault(Long.MAX_VALUE)
                                if (freeNow < 2_000_000_000L) return false
                            }
                        }
                    } else if (!Timeshift.active) {
                        return true   // DVR feed stopped
                    } else {
                        Thread.sleep(50)
                    }
                }
            }
            false
        } catch (e: Exception) {
            true
        }
    }

    private fun beginRecording(url: String, name: String, stopAt: Long?, tee: Boolean) {
        job?.cancel()
        Recorder.activeName.value = name
        Recorder.activeUrl = url
        Recorder.usesProviderConnection = false
        Recorder.lastStatus.value = "Recording: $name"
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EasyIPTV:record").apply {
            // Cap the wakelock at 6 hours as a safety net.
            acquire(6L * 60 * 60 * 1000)
        }
        val dir = Recorder.recordingsDir(this)
        job = scope.launch {
            var currentFile: File? = null
            try {
                val stamp = SimpleDateFormat("MMM-d_h-mm-ss_a", Locale.US).format(Date())
                val safe = name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim()
                    .replace(' ', '_').take(40).ifBlank { "channel" }
                val f = File(dir, "REC_${safe}_$stamp.ts")
                currentFile = f
                FileOutputStream(f).use { out ->
                    var needNetwork = !tee
                    if (tee) {
                        // SAME-CHANNEL RECORDING: copy from the live DVR file,
                        // which costs no second provider connection. If the DVR
                        // file is briefly recreated (retune/recovery), retry the
                        // attachment a few times before giving up.
                        var tries = 0
                        while (isActive && (stopAt == null || System.currentTimeMillis() < stopAt)) {
                            val ended = teeFromTimeshift(out, stopAt) { isActive }
                            if (!ended || !isActive || (stopAt != null && System.currentTimeMillis() >= stopAt)) {
                                needNetwork = false
                                break
                            }
                            val stillSame = Playback.currentProviderUrl() == url
                            if (stillSame && Playback.canTeeRecording() && tries++ < 4) {
                                Thread.sleep(150)
                                continue
                            }
                            needNetwork = true
                            break
                        }
                    }
                    if (needNetwork) {
                        val prefs = getSharedPreferences("easyiptv", Context.MODE_PRIVATE)
                        // Direct recording costs a provider slot. Respect the
                        // customer setting: with 1 stream, recording takes over;
                        // with 2/3 streams, live playback may continue.
                        if (ProviderStreams.playbackSlots() + ProviderStreams.downloadSlots(this@RecordingService, prefs) + 1 > ProviderStreams.max(prefs)) {
                            // First sacrifice a background download, not live TV.
                            DownloadStore.cancelInFlight(this@RecordingService, prefs)
                        }
                        if (ProviderStreams.playbackSlots() + 1 > ProviderStreams.max(prefs)) {
                            val latch = java.util.concurrent.CountDownLatch(1)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                Playback.releaseAll()
                                latch.countDown()
                            }
                            runCatching { latch.await(1200, java.util.concurrent.TimeUnit.MILLISECONDS) }
                        }
                        Recorder.usesProviderConnection = true
                        // Direct connection (different-channel/scheduled recording,
                        // or a tee that genuinely lost its source).
                        val req = Request.Builder().url(url).header("User-Agent", Net.UA).build()
                        Net.streamClient.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
                            val body = resp.body
                            if (body != null) {
                                body.byteStream().use { inp ->
                                    val buf = ByteArray(64 * 1024)
                                    var sinceCheck = 0L
                                    while (isActive && (stopAt == null || System.currentTimeMillis() < stopAt)) {
                                        val n = inp.read(buf)
                                        if (n < 0) break
                                        out.write(buf, 0, n)
                                        // Storage guard: Fire Sticks corrupt themselves when
                                        // storage fills. Stop the recording gracefully while
                                        // there's still 2 GB of breathing room.
                                        sinceCheck += n
                                        if (sinceCheck > 32_000_000) {
                                            sinceCheck = 0
                                            val free = runCatching {
                                                android.os.StatFs(dir.absolutePath).availableBytes
                                            }.getOrDefault(Long.MAX_VALUE)
                                            if (free < 2_000_000_000L) break
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Stream closed or network error — keep any non-empty partial
                // recording because it may still be playable, but tell the user.
                val partialBytes = currentFile?.takeIf { it.exists() }?.length() ?: 0L
                Recorder.lastStatus.value = if (partialBytes > 0L) {
                    val mb = partialBytes / (1024.0 * 1024.0)
                    "Recording stopped early — saved ${String.format(Locale.US, "%.1f", mb)} MB."
                } else {
                    "Recording failed — no video data was received. ${e.message ?: "Check the channel and try again."}"
                }
            } finally {
                // Never leave a fake 0 MB recording behind after a 403, dead
                // socket, or failed storage open. This was confusing in v4.17.
                currentFile?.let { f ->
                    val bytes = if (f.exists()) f.length() else 0L
                    if (bytes == 0L) {
                        runCatching { f.delete() }
                        if (Recorder.lastStatus.value?.startsWith("Recording failed") != true) {
                            Recorder.lastStatus.value = "Recording failed — no video data was saved."
                        }
                    } else if (Recorder.lastStatus.value?.startsWith("Recording stopped early") != true) {
                        val mb = bytes / (1024.0 * 1024.0)
                        Recorder.lastStatus.value = "Saved recording: ${String.format(Locale.US, "%.1f", mb)} MB"
                    }
                }
                Recorder.activeName.value = null
                Recorder.activeUrl = null
                Recorder.usesProviderConnection = false
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        Recorder.activeName.value = null
        Recorder.activeUrl = null
        Recorder.usesProviderConnection = false
        runCatching { wakeLock?.let { if (it.isHeld) it.release() } }
        super.onDestroy()
    }
}

/* ----------------------------- scheduled recordings ----------------------------- */

object ScheduleStore {
    data class Sched(
        val id: Long,
        val title: String,
        val channelName: String,
        val url: String,
        val startMs: Long,
        val endMs: Long
    )

    private const val KEY = "schedules_v1"

    fun load(prefs: SharedPreferences): List<Sched> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Sched(
                    id = o.optLong("id"),
                    title = o.optString("title"),
                    channelName = o.optString("channel"),
                    url = o.optString("url"),
                    startMs = o.optLong("start"),
                    endMs = o.optLong("end")
                )
            }.sortedBy { it.startMs }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(prefs: SharedPreferences, list: List<Sched>) {
        val arr = JSONArray()
        list.forEach { s ->
            val o = JSONObject()
            o.put("id", s.id)
            o.put("title", s.title)
            o.put("channel", s.channelName)
            o.put("url", s.url)
            o.put("start", s.startMs)
            o.put("end", s.endMs)
            arr.put(o)
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun pending(context: Context, s: Sched): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("url", s.url)
            putExtra("name", "${s.title} (${s.channelName})")
            putExtra("stopAt", s.endMs + 2 * 60 * 1000)   // small pad after the show
            putExtra("schedId", s.id)
        }
        return PendingIntent.getBroadcast(
            context, (s.id % Int.MAX_VALUE).toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Schedule a recording. Returns a message to show the user. */
    fun add(context: Context, prefs: SharedPreferences, title: String, channelName: String, url: String, startMs: Long, endMs: Long): String {
        val s = Sched(System.currentTimeMillis(), title, channelName, url, startMs, endMs)
        save(prefs, load(prefs) + s)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        // Alarm-clock alarms are exact and fire even in power saving.
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(startMs - 60 * 1000, show),   // wake 1 min early
            pending(context, s)
        )
        val fmt = SimpleDateFormat("EEE h:mm a", Locale.getDefault())
        return "Scheduled: \"$title\" on $channelName, ${fmt.format(Date(startMs))}. The device must be powered on at that time."
    }

    fun cancel(context: Context, prefs: SharedPreferences, id: Long) {
        val list = load(prefs)
        val s = list.firstOrNull { it.id == id } ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context, s))
        save(prefs, list.filterNot { it.id == id })
    }

    /** Drop schedules whose start time is long past. Call at app start. */
    fun cleanup(prefs: SharedPreferences) {
        val now = System.currentTimeMillis()
        save(prefs, load(prefs).filter { it.startMs > now - 5 * 60 * 1000 })
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra("url") ?: return
        val name = intent.getStringExtra("name") ?: "Scheduled recording"
        val stopAt = intent.getLongExtra("stopAt", 0L)
        val schedId = intent.getLongExtra("schedId", -1L)
        val prefs = context.getSharedPreferences("easyiptv", Context.MODE_PRIVATE)
        if (schedId >= 0) ScheduleStore.cancel(context, prefs, schedId)
        val i = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
            putExtra("url", url)
            putExtra("name", name)
            if (stopAt > 0) putExtra("stopAt", stopAt)
        }
        ContextCompat.startForegroundService(context, i)
    }
}
