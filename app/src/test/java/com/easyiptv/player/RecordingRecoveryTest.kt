package com.easyiptv.player

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class RecordingRecoveryTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private val prefs get()=context.getSharedPreferences("easyiptv",Context.MODE_PRIVATE)
    private var end=0L
    @Before fun reset() { prefs.edit().clear().commit(); while(shadowOf(context as android.app.Application).nextStartedService!=null) {} }
    private fun ongoing() {
        val now=System.currentTimeMillis(); end=now+3_600_000
        val row=JSONObject().put("id",123L).put("title","Movie").put("channel","TV").put("url","https://example.invalid/stream.ts")
            .put("start",now-1_800_000).put("end",end)
        prefs.edit().putString("schedules_v1",JSONArray().put(row).toString()).commit()
    }
    @Test fun openingHalfwayThroughKeepsTheRemainingSchedule() {
        ongoing(); ScheduleStore.cleanup(prefs)
        assertEquals("Ongoing recordings must survive cleanup",1,ScheduleStore.load(prefs).size)
        ScheduleStore.rearmAll(context,prefs)
        val alarm=shadowOf(context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).nextScheduledAlarm
        assertNotNull("The remaining recording must be armed",alarm)
    }
    @Test fun firingAnAlarmKeepsItRecoverableUntilItsEnd() {
        ongoing()
        AlarmReceiver().onReceive(context,Intent().putExtra("schedId",123L).putExtra("url","https://example.invalid/stream.ts").putExtra("name","Movie (TV)").putExtra("stopAt",end))
        assertEquals(1,ScheduleStore.load(prefs).size)
        val started=shadowOf(context as android.app.Application).nextStartedService
        assertEquals(end,started.getLongExtra("stopAt",0L))
        assertEquals(123L,started.getLongExtra("schedId",-1L))
    }
    @Test fun cancelAfterProcessDeathInvalidatesPersistedRecovery() {
        ongoing()
        RecordingRecovery.save(prefs,RecordingRecovery.Session("dead-process","https://example.invalid/stream.ts","Movie (TV)",end,123,"/tmp/not-opened.ts"))
        Recorder.activeScheduleId=-1
        ScheduleStore.cancel(context,prefs,123)
        assertNull("Cancel must remove the persisted writer",RecordingRecovery.load(prefs))
        AlarmReceiver().onReceive(context,Intent().setAction(RecordingRecovery.ACTION))
        assertNull(shadowOf(context as android.app.Application).nextStartedService)
    }
    @Test fun stopBeforeServiceRestoreClearsPersistedRecovery() {
        ongoing()
        RecordingRecovery.save(prefs,RecordingRecovery.Session("dead-process","https://example.invalid/stream.ts","Movie (TV)",end,123,"/tmp/not-opened.ts"))
        val service=org.robolectric.Robolectric.buildService(RecordingService::class.java).create()
        try {
            service.get().onStartCommand(Intent().setAction(RecordingService.ACTION_STOP),0,1)
            assertNull(RecordingRecovery.load(prefs))
            assertTrue(ScheduleStore.load(prefs).isEmpty())
        } finally {service.destroy()}
    }
    @Test fun cancelledScheduledStartIsRejectedByTheServiceToo() {
        val service=org.robolectric.Robolectric.buildService(RecordingService::class.java).create()
        try {
            service.get().onStartCommand(Intent().setAction(RecordingService.ACTION_START)
                .putExtra("schedId",123L).putExtra("url","https://example.invalid/stream.ts")
                .putExtra("stopAt",System.currentTimeMillis()+60_000),0,1)
            assertNull("Stale scheduled intent must not persist a new session",RecordingRecovery.load(prefs))
        } finally {service.get().onStartCommand(Intent().setAction(RecordingService.ACTION_STOP),0,2);service.destroy()}
    }
    @Test fun aCancelledOrExpiredAlarmDoesNotStartARecording() {
        AlarmReceiver().onReceive(context,Intent().putExtra("schedId",123L).putExtra("url","https://example.invalid/stream.ts").putExtra("stopAt",System.currentTimeMillis()-1))
        assertNull(shadowOf(context as android.app.Application).nextStartedService)
    }
}
