package com.easyiptv.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import org.json.JSONObject

/** Exactly one recorder is supported; its destination survives process/device restarts. */
internal object RecordingRecovery {
    const val ACTION="com.easyiptv.player.RECOVER_RECORDING"
    private const val KEY="active_recording_v471"
    data class Session(val id:String,val url:String,val name:String,val end:Long,val scheduleId:Long,val path:String)
    @Synchronized
    fun load(prefs:SharedPreferences):Session?=runCatching {
        val o=JSONObject(prefs.getString(KEY,null) ?: return null)
        Session(o.getString("id"),o.getString("url"),o.getString("name"),o.getLong("end"),o.optLong("scheduleId",-1),o.getString("path"))
    }.getOrNull()
    @Synchronized
    fun save(prefs:SharedPreferences,s:Session) {
        prefs.edit().putString(KEY,JSONObject().put("id",s.id).put("url",s.url).put("name",s.name)
            .put("end",s.end).put("scheduleId",s.scheduleId).put("path",s.path).toString()).commit()
    }
    @Synchronized
    fun clear(prefs:SharedPreferences,id:String) { if(load(prefs)?.id==id) prefs.edit().remove(KEY).commit() }
    private fun operation(context:Context)=PendingIntent.getBroadcast(context,471,
        Intent(context,AlarmReceiver::class.java).setAction(ACTION),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    fun rearm(context:Context,prefs:SharedPreferences) {
        val s=load(prefs) ?: return
        if(s.end<=System.currentTimeMillis()) {clear(prefs,s.id);return}
        if(Recorder.activeSessionId==s.id) return
        val show=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        RecordingScheduler.schedule(context,System.currentTimeMillis()+1_000,operation(context),show)
    }
    fun intent(context:Context,s:Session)=Intent(context,RecordingService::class.java).setAction(RecordingService.ACTION_START)
        .putExtra("url",s.url).putExtra("name",s.name).putExtra("stopAt",s.end).putExtra("schedId",s.scheduleId).putExtra("sessionId",s.id)
}
