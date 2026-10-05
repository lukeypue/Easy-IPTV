package com.easyiptv.player

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Visible-card lookups only. Scrolling away cancels both the queue wait and socket. */
internal class MovieArtworkLookup(private val fetch:suspend (String)->String?) {
    private data class Entry(val icon:String?,val time:Long)
    private data class Slot(val mutex:Mutex=Mutex(),var users:Int=0)
    private val gate=kotlinx.coroutines.sync.Semaphore(2)
    private val cache=LinkedHashMap<String,Entry>()
    private val slots=HashMap<String,Slot>()
    fun forget(id:String,failedAddress:String) {synchronized(cache) {if(cache[id]?.icon==failedAddress) cache.remove(id)}}
    suspend fun resolve(id:String):String? {
        val slot=synchronized(slots) {slots.getOrPut(id) {Slot()}.also {it.users++}}
        try {
            return slot.mutex.withLock {
                val now=System.currentTimeMillis()
                synchronized(cache) {cache[id]}?.let {
                    if(it.icon!=null || now-it.time<60_000) return@withLock it.icon
                }
                val icon=gate.withPermit {withTimeout(7_000) {fetch(id)}}
                synchronized(cache) {
                    cache[id]=Entry(icon,now)
                    while(cache.size>512) cache.remove(cache.keys.first())
                }
                icon
            }
        } finally {
            synchronized(slots) {slot.users--;if(slot.users==0) slots.remove(id)}
        }
    }
}

/** Successful fallback pictures survive lazy-grid disposal and app restarts. */
internal object MoviePosterCache {
    private fun key(movie:Movie):String = java.security.MessageDigest.getInstance("SHA-256")
        .digest((movie.url+"\n"+movie.name).toByteArray()).joinToString("") {"%02x".format(it)}
    fun read(context:Context,movie:Movie):String? = context.getSharedPreferences("movie_posters",Context.MODE_PRIVATE).getString(key(movie),null)
    fun save(context:Context,movie:Movie,icon:String) {
        val prefs=context.getSharedPreferences("movie_posters",Context.MODE_PRIVATE)
        // This is a small URL sidecar, never an unbounded bitmap cache.
        val edit=prefs.edit()
        if(prefs.all.size>=512 && !prefs.contains(key(movie))) prefs.all.keys.take(64).forEach {edit.remove(it)}
        edit.putString(key(movie),icon).apply()
    }
    fun remove(context:Context,movie:Movie,failedAddress:String) {
        val prefs=context.getSharedPreferences("movie_posters",Context.MODE_PRIVATE)
        if(prefs.getString(key(movie),null)==failedAddress) prefs.edit().remove(key(movie)).apply()
    }
}

@Composable
internal fun MoviePosterImage(movie:Movie,source:Source?,description:String,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    var address by remember(movie.url,movie.name,movie.icon,source) {mutableStateOf(movie.icon)}
    var failed by remember(movie.url,movie.name,movie.icon,source) {mutableStateOf(false)}
    var triedDetails by remember(movie.url,movie.name,movie.icon,source) {mutableStateOf(false)}
    var retry by remember(movie.url,movie.name,movie.icon,source) {mutableIntStateOf(0)}
    LaunchedEffect(movie.url,movie.name,movie.icon,source,failed) {
        if(address.isNullOrBlank() || failed) {
            if(!triedDetails) {
                val result=try {
                    withContext(Dispatchers.IO) {
                        val cached=MoviePosterCache.read(context,movie)?.takeIf {it!=address}
                        if(cached!=null) cached to false
                        else (source?.movieArtwork(movie.id)?.takeIf {it!=address}) to true
                    }
                } catch(cancelled:CancellationException) {throw cancelled
                } catch(_:Exception) {null to true}
                triedDetails=result.second
                if(!result.first.isNullOrBlank()) {address=result.first;failed=false;return@LaunchedEffect}
            }
            if(failed && retry==0 && !address.isNullOrBlank()) {
                delay(500)
                retry=1
                failed=false
            }
        }
    }
    Box(modifier,contentAlignment=Alignment.Center) {
        // Keep the card stable while its picture loads or retries.
        Text(movie.name.take(1).uppercase(),color=Color(0xFFFFB84D),fontSize=38.sp,fontWeight=FontWeight.Bold)
        if(!address.isNullOrBlank()) key(address,retry) {
            val requestedAddress=address!!
            AsyncImage(model=requestedAddress,contentDescription=description,contentScale=ContentScale.Crop,
                modifier=Modifier.fillMaxSize(),onError={
                    if(address==requestedAddress) {
                        MoviePosterCache.remove(context,movie,requestedAddress)
                        source?.forgetMovieArtwork(movie.id,requestedAddress)
                        failed=true
                    }
                },onSuccess={
                    if(address==requestedAddress && requestedAddress!=movie.icon)
                        MoviePosterCache.save(context,movie,requestedAddress)
                })
        }
    }
}
