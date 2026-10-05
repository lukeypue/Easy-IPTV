package com.easyiptv.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class CatalogRepairTest {
    @Test fun cancelledMovieArtworkLookupClosesItsSocket() = runBlocking {
        CatalogFixture(stall=true).use {server ->
            val source=XtreamSource(server.host,"u","p")
            val task=launch(Dispatchers.IO) {source.movieArtwork("1")}
            try {
                withTimeout(5000) {while(server.requests.isEmpty()) delay(10)}
                task.cancel()
                assertTrue(server.closed.await(1500,java.util.concurrent.TimeUnit.MILLISECONDS))
            } finally {server.close();task.cancelAndJoin()}
        }
    }
    @Test fun movieDetailsCanSupplyArtworkMissingFromTheList() = runBlocking {
        CatalogFixture().use { server ->
            val source=XtreamSource(server.host,"u","p")
            assertEquals("${server.host}/posters/detail.jpg",source.movieArtwork("1"))
            assertEquals("${server.host}/posters/detail.jpg",source.movieArtwork("1"))
            assertEquals("One detail lookup should serve repeated cards",1,server.requests.count {it.contains("action=get_vod_info")})
        }
    }
    @Test fun duplicateMovieRowCannotEraseEarlierArtwork() = runBlocking {
        CatalogFixture(moviesBody="""[{"stream_id":1,"name":"Film","stream_icon":"https://example/poster.jpg"},{"stream_id":1,"name":"Film","stream_icon":""}]""").use { server ->
            val movies=XtreamSource(server.host,"u","p").loadMoviesOnly().movies
            assertEquals(1,movies.size)
            assertEquals("https://example/poster.jpg",movies.single().icon)
        }
    }
    @Test fun blankRefreshPreservesSavedArtworkForTheSameMovie() = runBlocking {
        CatalogFixture(moviesBody="""[{"stream_id":1,"name":"Film","stream_icon":""}]""").use { server ->
            val source=XtreamSource(server.host,"u","p")
            val old=AppData(emptyList(),emptyList(),emptyList(),listOf(Movie("1","Film","https://example/poster.jpg",null,"${server.host}/movie/u/p/1.mp4")),emptyList(),emptyList())
            val result=CatalogRefresh.load(source,old,CatalogRefresh.Section.MOVIES)
            assertEquals("https://example/poster.jpg",result.data.movies.single().icon)
        }
    }
    @Test fun blankMovieIconUsesAlternateArtworkAndSurvivesCacheReload() = runBlocking {
        CatalogFixture().use { server ->
            val data=XtreamSource(server.host,"u","p").loadOnDemandOnly()
            assertEquals("https://images.example/movie.jpg",data.movies.single().icon)
            assertEquals("https://images.example/series.jpg",data.series.single().icon)
            val context=ApplicationProvider.getApplicationContext<Context>()
            DataCache.save(context,"artwork-repair",data)
            assertEquals("https://images.example/movie.jpg",DataCache.load(context,"artwork-repair")!!.movies.single().icon)
        }
    }
    @Test fun failedMovieRefreshCannotMasqueradeAsSuccessfulEmptyMovies() = runBlocking {
        CatalogFixture(failMovies=true).use { server ->
            val result=runCatching {XtreamSource(server.host,"u","p").loadOnDemandOnly()}
            assertTrue("A failed movie endpoint must not replace existing movies with an empty catalog",result.isFailure)
        }
    }
    @Test fun movieOnlyUpdatePreservesLiveAndSeriesWithoutFetchingThem() = runBlocking {
        CatalogFixture().use { server ->
            val old=AppData(listOf(Category("l","Live")),listOf(LiveChannel("l","News",null,"l","https://example/live")),
                emptyList(),emptyList(),listOf(Category("s","Series")),listOf(SeriesItem("old","Saved series",null,"s")))
            val result=CatalogRefresh.load(XtreamSource(server.host,"u","p"),old,CatalogRefresh.Section.MOVIES)
            assertNull(result.warning)
            assertEquals(old.live,result.data.live)
            assertEquals(old.series,result.data.series)
            assertEquals(old.seriesCats,result.data.seriesCats)
            assertEquals("Example Movie",result.data.movies.single().name)
            assertFalse(server.requests.any {it.contains("action=get_series") || it.contains("action=get_live")})
        }
    }
    @Test fun failedMoviesKeepSavedArtworkWhileSeriesCanStillUpdate() = runBlocking {
        CatalogFixture(failMovies=true).use { server ->
            val old=AppData(emptyList(),emptyList(),listOf(Category("m","Saved movies")),
                listOf(Movie("old","Saved film","https://example/poster.jpg","m","https://example/film")),emptyList(),emptyList())
            val result=CatalogRefresh.load(XtreamSource(server.host,"u","p"),old,CatalogRefresh.Section.ALL)
            assertEquals(old.movies,result.data.movies)
            assertEquals(old.vodCats,result.data.vodCats)
            assertEquals("Example Series",result.data.series.single().name)
            assertTrue(result.warning!!.contains("Movies could not update"))
        }
    }
    @Test fun cancelledCatalogClosesNetworkReadInsteadOfCompetingWithPlayback() = runBlocking {
        CatalogFixture(stall=true).use { server ->
            val task=launch(Dispatchers.IO) {runCatching {XtreamSource(server.host,"u","p").loadOnDemandOnly()}}
            try {
                withTimeout(5000) {while(server.requests.isEmpty()) delay(10)}
                task.cancel()
                assertTrue("Cancelling catalog must close its in-flight socket",server.closed.await(1500,java.util.concurrent.TimeUnit.MILLISECONDS))
            } finally { server.close(); task.cancelAndJoin() }
        }
    }
}

internal class CatalogFixture(private val failMovies:Boolean=false,private val stall:Boolean=false,private val moviesBody:String?=null):AutoCloseable {
    private val server=ServerSocket(0)
    private val sockets=CopyOnWriteArrayList<Socket>()
    val requests=CopyOnWriteArrayList<String>()
    val closed=java.util.concurrent.CountDownLatch(1)
    val host="http://127.0.0.1:${server.localPort}"
    init {thread(isDaemon=true) {
        while(!server.isClosed) {
            val socket=try {server.accept()}catch(_:Exception){break}
            sockets+=socket
            thread(isDaemon=true) {runCatching {socket.use {s ->
                val reader=s.getInputStream().bufferedReader()
                val request=reader.readLine().orEmpty()
                while(!reader.readLine().isNullOrEmpty()) {}
                requests+=request
                if(stall) {while(s.getInputStream().read()!=-1) {};closed.countDown()}
                else {
                    val failed=failMovies && request.contains("action=get_vod_streams")
                    val body=when {
                        request.contains("action=get_vod_info") -> """{"info":{"movie_image":"/posters/detail.jpg"},"movie_data":{"stream_icon":""}}"""
                        request.contains("action=get_vod_streams") -> moviesBody ?: """[{"stream_id":1,"name":"Example Movie","stream_icon":"","movie_image":"https://images.example/movie.jpg","container_extension":"mp4"}]"""
                        request.contains("action=get_series&") || request.contains("action=get_series ") -> """[{"series_id":2,"name":"Example Series","cover":"https://images.example/series.jpg"}]"""
                        else -> "[]"
                    }
                    val payload=body.toByteArray()
                    s.getOutputStream().write(("HTTP/1.1 ${if(failed) "503 Unavailable" else "200 OK"}\r\nContent-Type: application/json\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n").toByteArray())
                    s.getOutputStream().write(payload)
                }
            }}}
        }
    }}
    override fun close() {runCatching {server.close()};sockets.forEach {runCatching {it.close()}}}
}
