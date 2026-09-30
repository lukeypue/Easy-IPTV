package com.easyiptv.player

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response

/** Cancellation belongs to the channel's entire EPG response, including its body. */
internal object EpgRequests {
    suspend fun get(url: String): String = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).header("User-Agent", Net.UA).build()
        val call = Net.client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isActive) {
                    response.close()
                    return
                }
                try {
                    // Resume only AFTER consumption. Resuming with Response here
                    // would end the cancellation hook while body.string() still reads.
                    val text = response.use {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                        it.body?.string() ?: throw IOException("Empty response")
                    }
                    if (continuation.isActive) continuation.resume(text)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }
}
