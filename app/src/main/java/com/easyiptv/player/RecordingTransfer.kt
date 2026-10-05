package com.easyiptv.player

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

internal class RecordingStorageException(message: String, cause: Throwable? = null) : IOException(message,cause)

/** Append every recovered connection. An EOF is a disconnect, not a completed recording. */
internal class RecordingTransfer(client: OkHttpClient) {
    private val client=client.newBuilder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).build()
    fun copy(url:String,out:OutputStream,end:Long,active:()->Boolean,space:()->Boolean,
        onCall:(Call?)->Unit,onRetry:()->Unit,onConnected:()->Unit) {
        var backoff=1_000L
        fun running()=active() && System.currentTimeMillis()<end
        while(running()) {
            if(!space()) throw RecordingStorageException("Storage is almost full")
            var received=false
            val call=client.newCall(Request.Builder().url(url).header("User-Agent",Net.UA).build())
            onCall(call)
            if(!running()) {call.cancel();onCall(null);break}
            try {
                call.execute().use { response ->
                    if(!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body=response.body ?: throw IOException("Empty stream")
                    body.byteStream().use { input ->
                        val buffer=ByteArray(64*1024)
                        var sinceCheck=0L
                        while(running()) {
                            val count=input.read(buffer)
                            if(count<0) break
                            if(!running()) break
                            if(count==0) continue
                            try { out.write(buffer,0,count) } catch(e:IOException) { throw RecordingStorageException("Cannot save recording",e) }
                            if(!received) {received=true;onConnected()}
                            sinceCheck+=count
                            if(sinceCheck>=8_000_000) {
                                sinceCheck=0
                                if(!space()) throw RecordingStorageException("Storage is almost full")
                            }
                        }
                    }
                }
            } catch(e:RecordingStorageException) { throw e }
            catch(_:IOException) { /* Keep the same file and original end time. */ }
            finally { onCall(null) }
            if(!running()) break
            onRetry()
            if(received) backoff=1_000L
            val retryAt=minOf(System.currentTimeMillis()+backoff,end)
            while(running() && System.currentTimeMillis()<retryAt) Thread.sleep(minOf(100L,(retryAt-System.currentTimeMillis()).coerceAtLeast(1L)))
            if(!received) backoff=(backoff*2).coerceAtMost(30_000L)
        }
    }
}
