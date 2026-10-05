package com.easyiptv.player

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class RecordingNetworkTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @Before fun reset() {
        Playback.releaseAll()
        context.getSharedPreferences("easyiptv",Context.MODE_PRIVATE).edit().clear().putInt("provider_streams",3).commit()
        val dir=Recorder.recordingsDir(context)
        dir.listFiles()?.forEach { it.delete() }
        ShadowStatFs.registerStats(dir.absolutePath, 4_000_000, 3_000_000, 3_000_000)
    }
    private fun waitFor(message:String,timeout:Long=5000,condition:()->Boolean) {
        val until=System.nanoTime()+timeout*1_000_000
        while(!condition()&&System.nanoTime()<until) Thread.sleep(20)
        assertTrue(message,condition())
    }
    private fun start(url:String,end:Long)=Intent(context,RecordingService::class.java).apply {
        action=RecordingService.ACTION_START; putExtra("url",url);putExtra("name","recovery");putExtra("stopAt",end)
    }
    @Test fun cleanProviderDisconnectReconnectsAndAppendsToTheSameFile() {
        Provider(false).use { provider ->
            val service=Robolectric.buildService(RecordingService::class.java).create()
            try {
                service.get().onStartCommand(start(provider.url,System.currentTimeMillis()+60_000),0,1)
                waitFor("Recording did not reconnect after clean EOF") { provider.requests.get()>=2 }
                waitFor("Recovered bytes missing") { Recorder.recordingsDir(context).listFiles()?.any { it.length()>=6 }==true }
                val files=Recorder.recordingsDir(context).listFiles()!!.filter { it.length()>0 }
                assertEquals(1,files.size)
                assertTrue(files.single().readText().startsWith("ABCDEF"))
                service.get().onStartCommand(start(provider.url,System.currentTimeMillis()+60_000),0,2)
                Thread.sleep(100)
                assertEquals("Duplicate start must not create or truncate files",1,Recorder.recordingsDir(context).listFiles()!!.count { it.length()>0 })
            } finally {
                service.get().onStartCommand(Intent().setAction(RecordingService.ACTION_STOP),0,3);service.destroy()
            }
        }
    }
    @Test fun deadlineCancelsAStalledHttpRead() {
        Provider(true).use { provider ->
            val service=Robolectric.buildService(RecordingService::class.java).create()
            try {
                service.get().onStartCommand(start(provider.url,System.currentTimeMillis()+1000),0,1)
                waitFor("Provider never connected") { provider.requests.get()>0 }
                waitFor("Recording exceeded its stop time on a stalled connection") { Recorder.activeName.value==null }
            } finally { service.get().onStartCommand(Intent().setAction(RecordingService.ACTION_STOP),0,2); service.destroy() }
        }
    }
    @Test fun restartingServiceAppendsThePersistedRecordingAndStopClearsRecovery() {
        Provider(false).use { provider ->
            val prefs=context.getSharedPreferences("easyiptv",Context.MODE_PRIVATE)
            val file=java.io.File(Recorder.recordingsDir(context),"REC_restored.ts").apply {writeText("OLD")}
            val session=RecordingRecovery.Session("recover-1",provider.url,"recovery",System.currentTimeMillis()+60_000,-1,file.absolutePath)
            RecordingRecovery.save(prefs,session)
            val service=Robolectric.buildService(RecordingService::class.java).create()
            try {
                service.get().onStartCommand(null,0,1)
                waitFor("Persisted file was not resumed") {file.length()>=6}
                assertTrue(file.readText().startsWith("OLDABC"))
                service.get().onStartCommand(Intent().setAction(RecordingService.ACTION_STOP),0,2)
                waitFor("Stop left recovery enabled") {RecordingRecovery.load(prefs)==null}
                assertTrue(file.length()>=6)
                val requests=provider.requests.get()
                Thread.sleep(300)
                assertEquals("Stop must cancel retries",requests,provider.requests.get())
            } finally {service.get().onStartCommand(Intent().setAction(RecordingService.ACTION_STOP),0,3);service.destroy()}
        }
    }
    private class Provider(private val stall:Boolean):AutoCloseable {
        val server=ServerSocket(0)
        val requests=AtomicInteger()
        val sockets=CopyOnWriteArrayList<Socket>()
        val url="http://127.0.0.1:${server.localPort}/live.ts"
        init { thread(isDaemon=true) {
            while(!server.isClosed) {
                val socket=try { server.accept() } catch(_:Exception) { break }
                sockets+=socket
                thread(isDaemon=true) {
                    runCatching {
                        socket.use { s ->
                            val reader=s.getInputStream().bufferedReader()
                            while(!reader.readLine().isNullOrEmpty()) {}
                            val n=requests.incrementAndGet()
                            if(stall) { while(s.getInputStream().read()!=-1) {} }
                            else { val bytes=if(n==1) "ABC" else "DEF"; s.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: 3\r\nConnection: close\r\n\r\n"+bytes).toByteArray()) }
                        }
                    }
                }
            }
        } }
        override fun close() { server.close(); sockets.forEach { runCatching { it.close() } } }
    }
}
