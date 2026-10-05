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
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
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

    @Volatile internal var activeScheduleId: Long = -1L
    @Volatile internal var activeSessionId: String? = null

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
    private class NetworkOwner { @Volatile var call: okhttp3.Call? = null }
    private var networkOwner: NetworkOwner? = null
    private var latestStartId=0
    private var session: RecordingRecovery.Session? = null
    @Volatile private var stopRequested = false

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
            .setContentText("RYZOD is recording in the background.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Stop recording", stop)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId=startId
        val prefs=getSharedPreferences("easyiptv",Context.MODE_PRIVATE)
        if(intent?.action==ACTION_STOP) {
            stopRequested=true
            (session ?: RecordingRecovery.load(prefs))?.let { saved ->
                RecordingRecovery.clear(prefs,saved.id)
                Recorder.activeScheduleId=-1
                if(saved.scheduleId>=0) ScheduleStore.cancel(this,prefs,saved.scheduleId)
            }
            networkOwner?.call?.cancel(); job?.cancel()
            if(job==null) stopSelf()
            return START_NOT_STICKY
        }
        val saved=RecordingRecovery.load(prefs)
        val request=intent ?: saved?.let { RecordingRecovery.intent(this,it) }
        if(request?.action!=ACTION_START) return START_NOT_STICKY.also { stopSelf() }
        val url=request.getStringExtra("url") ?: return START_NOT_STICKY.also { stopSelf() }
        val name=request.getStringExtra("name") ?: "channel"
        val scheduleId=request.getLongExtra("schedId",-1L)
        val scheduled=if(scheduleId>=0) ScheduleStore.load(prefs).firstOrNull {
            it.id==scheduleId && it.url==url && it.endMs>System.currentTimeMillis() && it.startMs<=System.currentTimeMillis()+1_000
        } else null
        if(scheduleId>=0 && scheduled==null) {
            if(job==null || job?.isCompleted==true) stopSelf(startId)
            return START_NOT_STICKY
        }
        val end=scheduled?.endMs ?: request.getLongExtra("stopAt",saved?.takeIf { it.url==url && it.name==name }?.end ?: (System.currentTimeMillis()+6*60*60*1000L))
        if(end<=System.currentTimeMillis()) {
            if(job?.isActive!=true) {
                saved?.let { if(it.end<=System.currentTimeMillis()) RecordingRecovery.clear(prefs,it.id) }
                stopSelf()
            }
            return START_NOT_STICKY
        }
        if(job!=null && job?.isCompleted!=true) {
            // Cancellation is asynchronous: the old writer owns cleanup until
            // COMPLETED, not merely until isActive becomes false.
            if(job?.isActive!=true) Recorder.lastStatus.value="Stopping the previous recording. Try again in a moment."
            // Rearming or returning to the app cannot replace/truncate its writer.
            if(scheduleId>=0 && scheduleId!=session?.scheduleId) ScheduleStore.retry(this,prefs,scheduleId)
            return START_REDELIVER_INTENT
        }
        // Ignore a recovery intent whose persisted session was explicitly stopped.
        if(request.hasExtra("sessionId") && request.getStringExtra("sessionId")!=saved?.id) {
            stopSelf(); return START_NOT_STICKY
        }
        val resume=saved?.takeIf { it.url==url && it.name==name && it.end>System.currentTimeMillis() && it.scheduleId==scheduleId }
        val safe=name.replace(Regex("[^A-Za-z0-9 _-]"),"").trim().replace(' ','_').take(40).ifBlank { "channel" }
        val id=resume?.id ?: java.util.UUID.randomUUID().toString()
        val destination=resume?.path ?: File(Recorder.recordingsDir(this),"REC_${safe}_${SimpleDateFormat("MMM-d_h-mm-ss_a",Locale.US).format(Date())}_${id.take(6)}.ts").absolutePath
        val next=resume ?: RecordingRecovery.Session(id,url,name,end,scheduleId,destination)
        session=next;stopRequested=false
        RecordingRecovery.save(prefs,next)
        val sameWatchedChannel=Playback.currentProviderUrl()==url
        val tee=(request.getBooleanExtra("tee",false) && Playback.canTeeRecording()) ||
            (sameWatchedChannel && (Playback.canTeeRecording() || Playback.prepareCurrentForRecording()))
        if(!tee && ProviderStreams.playbackSlots()+ProviderStreams.downloadSlots(this,prefs)+1>ProviderStreams.max(prefs)) {
            DownloadStore.cancelInFlight(this,prefs)
        }
        startForeground(NOTIF_ID,notification(name))
        beginRecording(url,name,next.end,tee)
        return START_REDELIVER_INTENT
    }

    /** Copy the live DVR (timeshift) file into the recording as it grows —
     *  recording the watched channel WITHOUT a second provider connection.
     *  Returns true if it ended because the DVR feed changed/stopped (channel
     *  change) — the caller then finishes via a direct connection if needed. */
    /**
     * ZAKO_V440_RING_RECORDING: record the watched channel from the same rolling
     * DVR ring the player already owns. Start at the current live edge; the
     * RingReader transparently crosses physical segment boundaries and holds a
     * reader lease so an in-use segment cannot be reclaimed underneath us.
     * Returns true only when the live DVR session changed/stopped so the caller
     * can decide whether a direct provider fallback is still appropriate.
     */
    private fun teeFromTimeshift(out: FileOutputStream, stopAt: Long?, isActive: () -> Boolean): Boolean {
        val sessionGen = Timeshift.generation()
        if (!Timeshift.active) return true
        val deadline = android.os.SystemClock.elapsedRealtime() + 12_000L
        while (isActive() && Timeshift.active && Timeshift.generation() == sessionGen &&
            Timeshift.newestVirtualByte() < 188L && android.os.SystemClock.elapsedRealtime() < deadline &&
            (stopAt == null || System.currentTimeMillis() < stopAt)) {
            Thread.sleep(25)
        }
        if (!isActive() || (stopAt != null && System.currentTimeMillis() >= stopAt)) return false
        if (!Timeshift.active || Timeshift.generation() != sessionGen) return true
        val liveEdge = Timeshift.newestVirtualByte()
        val reader = Timeshift.openReader(liveEdge, sessionGen) ?: return true
        return try {
            reader.use { rr ->
                val buf = ByteArray(64 * 1024)
                var sinceCheck = 0L
                while (isActive() && (stopAt == null || System.currentTimeMillis() < stopAt)) {
                    if (!Timeshift.active || Timeshift.generation() != sessionGen) return true
                    val n = rr.read(buf)
                    if (n > 0) {
                        out.write(buf, 0, n)
                        sinceCheck += n
                        if (sinceCheck > 32_000_000L) {
                            sinceCheck = 0L
                            val freeNow = runCatching {
                                android.os.StatFs(Recorder.recordingsDir(this).absolutePath).availableBytes
                            }.getOrDefault(Long.MAX_VALUE)
                            if (freeNow < 2_000_000_000L) return false
                        }
                    } else if (n == 0 && Timeshift.active && Timeshift.generation() == sessionGen) {
                        Thread.sleep(50)
                    } else {
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            true
        }
    }

    private fun beginRecording(url: String, name: String, stopAt: Long?, tee: Boolean) {
        val recordingSession=session ?: return
        val owner=NetworkOwner()
        networkOwner=owner
        Recorder.activeSessionId=recordingSession.id
        Recorder.activeScheduleId=recordingSession.scheduleId
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
            val deadline=launch {
                while(isActive && System.currentTimeMillis()<recordingSession.end) delay(minOf(500L,(recordingSession.end-System.currentTimeMillis()).coerceAtLeast(1L)))
                if(isActive) owner.call?.cancel()
            }
            try {
                val f=File(recordingSession.path)
                f.parentFile?.mkdirs()
                currentFile=f
                FileOutputStream(f,true).use { out ->
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
                        if (!isActive || (stopAt != null && System.currentTimeMillis() >= stopAt)) return@launch
                        Recorder.usesProviderConnection = true
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
                            val released = java.util.concurrent.atomic.AtomicBoolean(false)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                try {
                                    // A cancelled or expired recording must not
                                    // execute a stale takeover when main resumes.
                                    if (isActive && (stopAt == null || System.currentTimeMillis() < stopAt)) {
                                        released.set(runCatching { Playback.releaseAll() }.isSuccess)
                                    }
                                } finally {
                                    latch.countDown()
                                }
                            }
                            // Waiting is on recording IO, never the UI. A slow
                            // main looper is not permission to open a second stream.
                            while (!latch.await(50, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                                if (!isActive || (stopAt != null && System.currentTimeMillis() >= stopAt)) return@launch
                            }
                            if (!released.get()) return@launch
                        }
                        if (!isActive || (stopAt != null && System.currentTimeMillis() >= stopAt)) return@launch
                        // Direct connection (different-channel/scheduled recording,
                        // or a tee that genuinely lost its source).
                        RecordingTransfer(Net.streamClient).copy(url,out,recordingSession.end,
                            active={isActive},
                            space={runCatching { android.os.StatFs(dir.absolutePath).availableBytes>=2_000_000_000L }.getOrDefault(true)},
                            onCall={owner.call=it;if(!isActive || System.currentTimeMillis()>=recordingSession.end) it?.cancel()},
                            onRetry={Recorder.lastStatus.value="Connection lost — retrying until the recording stop time."},
                            onConnected={Recorder.lastStatus.value="Recording: $name"})

                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
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
                deadline.cancel()
                owner.call?.cancel();owner.call=null
                if(networkOwner===owner) networkOwner=null
                val terminal=isActive || stopRequested || System.currentTimeMillis()>=recordingSession.end
                if(terminal) {
                    val prefs=getSharedPreferences("easyiptv",Context.MODE_PRIVATE)
                    RecordingRecovery.clear(prefs,recordingSession.id)
                    if(Recorder.activeSessionId==recordingSession.id) Recorder.activeScheduleId=-1
                    if(recordingSession.scheduleId>=0) ScheduleStore.cancel(this@RecordingService,prefs,recordingSession.scheduleId)
                }
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
                if(Recorder.activeSessionId==recordingSession.id) {
                    Recorder.activeName.value = null
                    Recorder.activeUrl = null
                    Recorder.activeSessionId=null
                    Recorder.activeScheduleId=-1
                    Recorder.usesProviderConnection = false
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Playback.recordingFinished(url)
                    if(session?.id==recordingSession.id) stopSelf(latestStartId)
                }
            }
        }
    }

    override fun onDestroy() {
        networkOwner?.call?.cancel()
        scope.cancel()
        job=null
        if(Recorder.activeSessionId==session?.id) {
            Recorder.activeName.value=null;Recorder.activeUrl=null;Recorder.activeSessionId=null
            Recorder.activeScheduleId=-1;Recorder.usesProviderConnection=false
        }
        runCatching { wakeLock?.let { if(it.isHeld) it.release() } }
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
            putExtra("stopAt", s.endMs)   // Honor the chosen stop time exactly.
            putExtra("schedId", s.id)
        }
        return PendingIntent.getBroadcast(
            context, (s.id % Int.MAX_VALUE).toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Schedule a recording. Returns a message to show the user. */
    @Synchronized
    fun add(context: Context, prefs: SharedPreferences, title: String, channelName: String, url: String, startMs: Long, endMs: Long): String {
        val s = Sched(System.currentTimeMillis(), title, channelName, url, startMs, endMs)
        save(prefs, load(prefs) + s)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        // Future recording must never crash when Android exact-alarm access is off.
        val result = RecordingScheduler.schedule(
            context = context,
            triggerAtMs = startMs,
            operation = pending(context, s),
            showIntent = show
        )
        val fmt = SimpleDateFormat("EEE h:mm a", Locale.getDefault())
        return when (result) {
            ScheduleResult.Scheduled ->
                "Scheduled: " + title + " on " + channelName + ", " + fmt.format(Date(startMs)) + ". The device must be powered on at that time."
            ScheduleResult.PermissionRequired -> {
                RecordingScheduler.requestExactAlarmAccess(context)
                "RYZOD saved this recording. Allow Alarms & reminders, then return to RYZOD so it can schedule exactly."
            }
            is ScheduleResult.Failed ->
                "Recording saved, but Android could not schedule it yet: ${result.message}"
        }
    }

    fun upcoming(prefs: SharedPreferences): List<Sched> {
        val now = System.currentTimeMillis()
        return load(prefs).filter { it.endMs > now }.sortedBy { it.startMs }
    }

    fun validateManual(startMs: Long, endMs: Long, nowMs: Long = System.currentTimeMillis()): String? = when {
        startMs <= nowMs -> "Start time must be in the future."
        endMs <= startMs -> "End time must be after the start time."
        else -> null
    }

    private fun arm(context: Context, s: Sched): ScheduleResult {
        val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return RecordingScheduler.schedule(context, maxOf(System.currentTimeMillis()+1_000,s.startMs), pending(context, s), show)
    }

    fun rearmAll(context: Context, prefs: SharedPreferences) {
        val now = System.currentTimeMillis()
        load(prefs).filter { it.endMs > now && it.id!=Recorder.activeScheduleId }.forEach { arm(context, it) }
        RecordingRecovery.rearm(context,prefs)
    }

    fun retry(context:Context,prefs:SharedPreferences,id:Long) {
        val s=load(prefs).firstOrNull { it.id==id && it.endMs>System.currentTimeMillis()+30_000 } ?: return
        val show=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        RecordingScheduler.schedule(context,System.currentTimeMillis()+30_000,pending(context,s),show)
    }

    @Synchronized
    fun edit(context: Context, prefs: SharedPreferences, id: Long, title: String, channelName: String, url: String, startMs: Long, endMs: Long): String {
        validateManual(startMs, endMs)?.let { return it }
        val old = load(prefs).firstOrNull { it.id == id } ?: return "Recording schedule was not found."
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context, old))
        val replacement = old.copy(title = title.ifBlank { "Manual recording" }, channelName = channelName, url = url, startMs = startMs, endMs = endMs)
        save(prefs, load(prefs).filterNot { it.id == id } + replacement)
        return when (val result = arm(context, replacement)) {
            ScheduleResult.Scheduled -> "Updated recording: TITLE".replace("TITLE", replacement.title)
            ScheduleResult.PermissionRequired -> {
                RecordingScheduler.requestExactAlarmAccess(context)
                "Recording updated. Allow Alarms & reminders so RYZOD can start it exactly."
            }
            is ScheduleResult.Failed -> "Recording updated, but Android could not arm it yet: ERROR".replace("ERROR", result.message)
        }
    }

    @Synchronized
    fun cancel(context: Context, prefs: SharedPreferences, id: Long) {
        RecordingRecovery.load(prefs)?.takeIf { it.scheduleId==id }?.let { RecordingRecovery.clear(prefs,it.id) }
        val list = load(prefs)
        val s = list.firstOrNull { it.id == id } ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context, s))
        save(prefs, list.filterNot { it.id == id })
        if(Recorder.activeScheduleId==id) Recorder.stop(context)
    }

    /** Drop schedules only after their stop time. Call at app start. */
    @Synchronized
    fun cleanup(prefs: SharedPreferences) {
        val now = System.currentTimeMillis()
        save(prefs, load(prefs).filter { it.endMs > now })
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs=context.getSharedPreferences("easyiptv",Context.MODE_PRIVATE)
        val request=if(intent.action==RecordingRecovery.ACTION) {
            val saved=RecordingRecovery.load(prefs) ?: return
            if(saved.end<=System.currentTimeMillis()) {RecordingRecovery.clear(prefs,saved.id);return}
            RecordingRecovery.intent(context,saved)
        } else {
            val id=intent.getLongExtra("schedId",-1L)
            val schedule=ScheduleStore.load(prefs).firstOrNull { it.id==id } ?: return
            if(schedule.endMs<=System.currentTimeMillis()) {ScheduleStore.cancel(context,prefs,id);return}
            Intent(context,RecordingService::class.java).setAction(RecordingService.ACTION_START)
                .putExtra("url",schedule.url).putExtra("name","${schedule.title} (${schedule.channelName})")
                .putExtra("stopAt",schedule.endMs).putExtra("schedId",id)
        }
        try { ContextCompat.startForegroundService(context,request) }
        catch(_:IllegalStateException) { Recorder.lastStatus.value="Recording is saved; open RYZOD to allow it to resume." }
        catch(_:SecurityException) { Recorder.lastStatus.value="Recording is saved; check recording permissions in RYZOD." }
    }
}
