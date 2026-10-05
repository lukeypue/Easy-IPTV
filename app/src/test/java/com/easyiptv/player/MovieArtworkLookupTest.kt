package com.easyiptv.player

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MovieArtworkLookupTest {
    @Test fun manyVisibleMoviesCannotExceedTheLookupConnectionBudget() = runBlocking {
        val active=AtomicInteger()
        val maximum=AtomicInteger()
        val release=CompletableDeferred<Unit>()
        val entered=CompletableDeferred<Unit>()
        val lookup=MovieArtworkLookup {
            val count=active.incrementAndGet()
            maximum.updateAndGet {old -> maxOf(old,count)}
            if(count==2) entered.complete(Unit)
            try {release.await();"https://example/poster.jpg"} finally {active.decrementAndGet()}
        }
        val jobs=(1..12).map {id -> async {lookup.resolve("$id")}}
        try {
            withTimeout(1000) {entered.await()}
            delay(50)
            assertEquals(2,maximum.get())
        } finally {release.complete(Unit);jobs.awaitAll()}
        assertEquals(2,maximum.get())
    }
    @Test fun failedArtworkInvalidatesOnlyItsOwnCachedAddress() = runBlocking {
        var calls=0
        val lookup=MovieArtworkLookup {calls++;"https://example/$calls.jpg"}
        assertEquals("https://example/1.jpg",lookup.resolve("1"))
        lookup.forget("1","https://example/unrelated.jpg")
        assertEquals("https://example/1.jpg",lookup.resolve("1"))
        lookup.forget("1","https://example/1.jpg")
        assertEquals("https://example/2.jpg",lookup.resolve("1"))
        assertEquals(2,calls)
    }
    @Test fun simultaneousRequestsForOneMovieShareOneLookup() = runBlocking {
        var calls=0
        val lookup=MovieArtworkLookup {calls++;delay(50);"https://example/one.jpg"}
        val posters=(1..12).map {async {lookup.resolve("one")}}.awaitAll()
        assertTrue(posters.all {it=="https://example/one.jpg"})
        assertEquals(1,calls)
    }
    @Test fun stalledPosterDoesNotBlockAnotherVisibleMovie() = runBlocking {
        val entered=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        val lookup=MovieArtworkLookup {id ->
            if(id=="slow") {entered.complete(Unit);release.await()}
            "https://example/$id.jpg"
        }
        val slow=async {lookup.resolve("slow")}
        try {
            entered.await()
            assertEquals("https://example/fast.jpg",withTimeoutOrNull(1000) {lookup.resolve("fast")})
        } finally {release.complete(Unit);slow.cancelAndJoin()}
    }
    @Test fun cancellationReleasesLookupWithoutCachingFailure() = runBlocking {
        val calls=AtomicInteger()
        val entered=CompletableDeferred<Unit>()
        val lookup=MovieArtworkLookup {
            if(calls.incrementAndGet()==1) {entered.complete(Unit);awaitCancellation()}
            "https://example/recovered.jpg"
        }
        val first=launch {lookup.resolve("1")}
        entered.await();first.cancelAndJoin()
        assertEquals("https://example/recovered.jpg",withTimeout(1000) {lookup.resolve("1")})
        assertEquals(2,calls.get())
    }
    @Test fun repeatedMissingArtworkDoesNotCreateARetryStorm() = runBlocking {
        var calls=0
        val lookup=MovieArtworkLookup {calls++;null}
        repeat(10) {assertNull(lookup.resolve("missing"))}
        assertEquals(1,calls)
    }
}
