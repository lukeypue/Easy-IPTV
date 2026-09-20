#!/usr/bin/env python3
from pathlib import Path
p=Path('app/src/main/java/com/easyiptv/player/Recording.kt')
t=p.read_text()
if 'fun upcoming(prefs:' in t:
    print('4.49 schedule core already applied'); raise SystemExit(0)
anchor='    fun cancel(context: Context, prefs: SharedPreferences, id: Long) {'
if anchor not in t: raise SystemExit('ScheduleStore cancel anchor missing')
code=r'''    fun upcoming(prefs: SharedPreferences): List<Sched> {
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
        return RecordingScheduler.schedule(context, s.startMs - 60 * 1000, pending(context, s), show)
    }

    fun rearmAll(context: Context, prefs: SharedPreferences) {
        val now = System.currentTimeMillis()
        load(prefs).filter { it.endMs > now }.forEach { arm(context, it) }
    }

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
                "Recording updated. Allow Alarms & reminders so Zako can start it exactly."
            }
            is ScheduleResult.Failed -> "Recording updated, but Android could not arm it yet: ERROR".replace("ERROR", result.message)
        }
    }

'''
t=t.replace(anchor,code+anchor,1)
p.write_text(t)
print('Applied Zako 4.49 schedule management core')
