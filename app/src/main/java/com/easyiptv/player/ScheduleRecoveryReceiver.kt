package com.easyiptv.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScheduleRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("easyiptv", Context.MODE_PRIVATE)
        ScheduleStore.rearmAll(context, prefs)
    }
}
