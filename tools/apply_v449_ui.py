#!/usr/bin/env python3
from pathlib import Path
p=Path('app/src/main/java/com/easyiptv/player/ManagedDvrUi.kt')
p.write_text(r'''package com.easyiptv.player

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small UI-facing model kept separate from the player. Compose can render these
 * rows in Recordings without coupling schedule persistence to playback.
 */
object ManagedDvrUi {
    data class UpcomingRow(
        val id: Long,
        val title: String,
        val channel: String,
        val date: String,
        val start: String,
        val end: String,
        val status: String,
    )

    fun upcoming(prefs: SharedPreferences): List<UpcomingRow> {
        val day = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        val time = SimpleDateFormat("h:mm a", Locale.getDefault())
        return ScheduleStore.upcoming(prefs).map {
            UpcomingRow(it.id, it.title, it.channelName, day.format(Date(it.startMs)),
                time.format(Date(it.startMs)), time.format(Date(it.endMs)), "Scheduled")
        }
    }

    fun cancel(context: Context, prefs: SharedPreferences, id: Long) =
        ScheduleStore.cancel(context, prefs, id)

    fun edit(context: Context, prefs: SharedPreferences, id: Long, title: String,
             channel: String, url: String, start: Long, end: Long): String =
        ScheduleStore.edit(context, prefs, id, title, channel, url, start, end)

    fun manualRecording(context: Context, prefs: SharedPreferences, title: String,
                        channel: String, url: String, start: Long, end: Long): String {
        ScheduleStore.validateManual(start, end)?.let { return it }
        return ScheduleStore.add(context, prefs, title.ifBlank { "Manual Recording" },
            channel, url, start, end)
    }

    const val upcomingLabel = "Upcoming"
    const val manualLabel = "Manual Recording"
    const val editLabel = "Edit"
    const val cancelLabel = "Cancel"
}
''')
print('Applied Zako 4.49 managed DVR UI model')
