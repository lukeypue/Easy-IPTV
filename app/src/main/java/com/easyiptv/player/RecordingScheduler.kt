package com.easyiptv.player

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
