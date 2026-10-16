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
    @org.junit.After fun restoreBudget() { BackgroundTransferBudget.onForeground() }
    @Before fun reset() { BackgroundTransferBudget.onForeground(); prefs.edit().clear().commit(); while(shadowOf(context as android.app.Application).nextStartedService!=null) {} }
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
    @Test @Config(sdk=[28,35,36]) fun systemTimeoutStopsServiceAndDoesNotRearmInterruptedRecording() {
        ongoing()
        val partial=java.io.File(context.cacheDir,"timeout-recording.ts").apply {writeText("SAVED VIDEO")}
        RecordingRecovery.save(prefs,RecordingRecovery.Session("timeout", "https://example.invalid/stream.ts", "Movie (TV)",end,123,partial.path))
        val service=org.robolectric.Robolectric.buildService(RecordingService::class.java).create()
        try {
            service.get().onTimeout(7,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            assertTrue("Android must receive an immediate stop",shadowOf(service.get()).isStoppedBySelf)
            assertNull("An exhausted recording must not restart through recovery",RecordingRecovery.load(prefs))
            assertTrue("Timed-out schedule must not immediately rearm",ScheduleStore.load(prefs).isEmpty())
            assertEquals("SAVED VIDEO",partial.readText())
        } finally {service.destroy();partial.delete()}
    }

    @Test fun systemTimeoutClosesActiveRecordingWriterAndKeepsReceivedVideo() {
        Playback.releaseAll()
        prefs.edit().clear().putInt("provider_streams",3).commit()
        org.robolectric.shadows.ShadowStatFs.registerStats(Recorder.recordingsDir(context).absolutePath,2_000_000,1_500_000,1_500_000)
        val service=org.robolectric.Robolectric.buildService(RecordingService::class.java).create()
        val disconnected=java.util.concurrent.CountDownLatch(1)
        java.net.ServerSocket(0).use { provider ->
            val worker=kotlin.concurrent.thread(isDaemon=true) {
                provider.accept().use { socket ->
                    socket.soTimeout=5000
                    val input=socket.getInputStream().bufferedReader()
                    while(!input.readLine().isNullOrEmpty()) {}
                    val bytes=ByteArray(188*100) { if(it%188==0) 0x47 else 0x11 }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 100000000\r\nContent-Type: video/mp2t\r\n\r\n".toByteArray())
                        write(bytes);flush()
                    }
                    if(socket.getInputStream().read()==-1) disconnected.countDown()
                }
            }
            var path:java.io.File?=null
            try {
                service.get().onStartCommand(Intent().setAction(RecordingService.ACTION_START)
                    .putExtra("url","http://127.0.0.1:${provider.localPort}/live.ts")
                    .putExtra("name","timeout-owned-writer").putExtra("stopAt",System.currentTimeMillis()+60_000),0,1)
                val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
                while(System.nanoTime()<deadline) {
                    path=RecordingRecovery.load(prefs)?.path?.let {java.io.File(it)}
                    if((path?.length() ?: 0)>0) break
                    Thread.sleep(10)
                }
                assertTrue("Provider video must reach the real recording file",(path?.length() ?: 0)>0)
                val job=RecordingService::class.java.getDeclaredField("job").apply {isAccessible=true}.get(service.get()) as kotlinx.coroutines.Job
                service.get().onTimeout(1,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                kotlinx.coroutines.runBlocking {kotlinx.coroutines.withTimeout(5000) {job.join()}}
                assertTrue("Timeout must close the provider socket",disconnected.await(5,java.util.concurrent.TimeUnit.SECONDS))
                assertNull(RecordingRecovery.load(prefs))
                assertNull(Recorder.activeName.value)
                assertTrue("Partial recorded video must survive",path!!.length()>0)
                assertTrue(shadowOf(service.get()).isStoppedBySelf)
            } finally {service.destroy();path?.delete();worker.join(1000)}
        }
    }

}
