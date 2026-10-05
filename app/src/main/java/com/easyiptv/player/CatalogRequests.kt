package com.easyiptv.player

import android.util.JsonReader
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response

/** Keep cancellation attached until the streamed body has been fully parsed. */
internal object CatalogRequests {
    suspend fun <T> read(url:String, parse:(JsonReader)->T):T = suspendCancellableCoroutine { continuation ->
        val call=Net.client.newCall(Request.Builder().url(url).header("User-Agent",Net.UA).build())
        continuation.invokeOnCancellation {call.cancel()}
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:IOException) {
                if(continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call:Call,response:Response) {
                try {
                    val result=response.use {
                        if(!continuation.isActive) return
                        if(!it.isSuccessful) throw IOException("HTTP ${it.code}")
                        val body=it.body ?: throw IOException("Empty catalog response")
                        body.charStream().use {chars -> JsonReader(chars).use {reader ->
                            reader.isLenient=true
                            parse(reader)
                        }}
                    }
                    if(continuation.isActive) continuation.resume(result)
                } catch(e:Exception) {
                    if(continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
}
