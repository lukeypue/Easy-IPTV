#!/usr/bin/env python3
from pathlib import Path

root=Path(__file__).resolve().parents[1]
base=root/'app/src/main/java/com/easyiptv/player'
rec=base/'Recording.kt'
manifest=root/'app/src/main/AndroidManifest.xml'
scheduler=base/'RecordingScheduler.kt'

scheduler.write_text(r'''package com.easyiptv.player

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

sealed class ScheduleResult {
    data object Scheduled : ScheduleResult()
    data object PermissionRequired : ScheduleResult()
    data class Failed(val message: String) : ScheduleResult()
}

object RecordingScheduler {
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun schedule(
        context: Context,
        triggerAtMs: Long,
        operation: PendingIntent,
        showIntent: PendingIntent
    ): ScheduleResult {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!canScheduleExact(context)) return ScheduleResult.PermissionRequired
        return try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMs, showIntent), operation)
            ScheduleResult.Scheduled
        } catch (_: SecurityException) {
            ScheduleResult.PermissionRequired
        } catch (e: Exception) {
            ScheduleResult.Failed(e.message ?: "Android could not schedule this recording.")
        }
    }

    fun requestExactAlarmAccess(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
''')

text=rec.read_text()
start_marker = '        // Alarm-clock alarms are exact and fire even in power saving.\n'
end_marker = '    fun cancel(context: Context, prefs: SharedPreferences, id: Long) {\n'
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit('v4.48 schedule target not found')
new='''        // Future recording must never crash when Android exact-alarm access is off.
        val result = RecordingScheduler.schedule(
            context = context,
            triggerAtMs = startMs - 60 * 1000,
            operation = pending(context, s),
            showIntent = show
        )
        val fmt = SimpleDateFormat("EEE h:mm a", Locale.getDefault())
        return when (result) {
            ScheduleResult.Scheduled ->
                "Scheduled: \"$title\" on $channelName, ${fmt.format(Date(startMs))}. The device must be powered on at that time."
            ScheduleResult.PermissionRequired -> {
                RecordingScheduler.requestExactAlarmAccess(context)
                "Zako saved this recording. Allow Alarms & reminders, then return to Zako so it can schedule exactly."
            }
            is ScheduleResult.Failed ->
                "Recording saved, but Android could not schedule it yet: ${result.message}"
        }
    }

'''
text = text[:start] + new + text[end:]
# AlarmManager import remains needed by cancel().
rec.write_text(text)

m=manifest.read_text()
perm='<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />'
if perm not in m:
    marker='<uses-permission android:name="android.permission.WAKE_LOCK" />'
    if marker not in m: raise SystemExit('manifest permission insertion target not found')
    m=m.replace(marker,marker+'\n    '+perm,1)
manifest.write_text(m)
print('Applied Zako 4.48 scheduler hardening')
