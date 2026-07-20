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

    fun recordingsDir(context: Context): File =
        File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }

    /** Start recording now. stopAtMs = auto-stop time (null = record until stopped). */
    fun start(context: Context, url: String, name: String, stopAtMs: Long? = null) {
        val i = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
            putExtra("url", url)
            putExtra("name", name)
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Recording: $name")
            .setContentText("Easy IPTV is recording. Open the app to stop.")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val url = intent.getStringExtra("url") ?: return START_NOT_STICKY.also { stopSelf() }
                val name = intent.getStringExtra("name") ?: "channel"
                val stopAt = if (intent.hasExtra("stopAt")) intent.getLongExtra("stopAt", 0L) else null
                startForeground(NOTIF_ID, notification(name))
                beginRecording(url, name, stopAt)
            }
            ACTION_STOP -> {
                job?.cancel()
                job = null
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun beginRecording(url: String, name: String, stopAt: Long?) {
        job?.cancel()
        Recorder.activeName.value = name
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EasyIPTV:record").apply {
            // Cap the wakelock at 6 hours as a safety net.
            acquire(6L * 60 * 60 * 1000)
        }
        val dir = Recorder.recordingsDir(this)
        job = scope.launch {
            try {
                val req = Request.Builder().url(url).header("User-Agent", Net.UA).build()
                Net.streamClient.newCall(req).execute().use { resp ->
                    val body = resp.body ?: return@use
                    val stamp = SimpleDateFormat("MMM-d_h-mm-ss_a", Locale.US).format(Date())
                    val safe = name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim()
                        .replace(' ', '_').take(40).ifBlank { "channel" }
                    val f = File(dir, "REC_${safe}_$stamp.ts")
                    body.byteStream().use { inp ->
                        FileOutputStream(f).use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (isActive && (stopAt == null || System.currentTimeMillis() < stopAt)) {
                                val n = inp.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // stream closed or network error — the partial file is kept and playable
            } finally {
                Recorder.activeName.value = null
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        Recorder.activeName.value = null
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
