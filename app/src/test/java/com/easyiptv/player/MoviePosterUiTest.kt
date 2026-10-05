package com.easyiptv.player

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class MoviePosterUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun staleSavedFallbackFetchesFreshDetailsInsteadOfRetryingForever() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(context).build())
        val file=File(context.cacheDir,"fresh-details.png")
        val bitmap=Bitmap.createBitmap(24,36,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
        val good=file.toURI().toString()
        val movie=Movie("stale","Stale film",null,null,"https://example/stale")
        MoviePosterCache.save(context,movie,"file:///missing-fallback.png")
        val calls=java.util.concurrent.atomic.AtomicInteger()
        val source=object:Source by XtreamSource("https://example.invalid","u","p") {
            override suspend fun movieArtwork(movieId:String):String? {calls.incrementAndGet();return good}
        }
        ui.setContent {MoviePosterImage(movie,source,movie.name,androidx.compose.ui.Modifier.size(174.dp,256.dp))}
        ui.waitUntil(5000) {ui.runOnIdle {MoviePosterCache.read(context,movie)==good}}
        ui.runOnIdle {assertEquals(1,calls.get())}
    }
    @Test fun failedPrimaryKeepsWorkingFallbackWhileProviderIsUnavailable() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val file=File(context.cacheDir,"working-fallback.png")
        val bitmap=Bitmap.createBitmap(24,36,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
        val good=file.toURI().toString()
        val movie=Movie("offline","Offline film","file:///missing-primary.png",null,"https://example/offline")
        MoviePosterCache.save(context,movie,good)
        val successes=java.util.concurrent.atomic.AtomicInteger()
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(context).eventListener(object:coil.EventListener {
            override fun onSuccess(request:coil.request.ImageRequest,result:coil.request.SuccessResult) {successes.incrementAndGet()}
        }).build())
        val calls=java.util.concurrent.atomic.AtomicInteger()
        val source=object:Source by XtreamSource("https://example.invalid","u","p") {
            override suspend fun movieArtwork(movieId:String):String? {calls.incrementAndGet();error("Provider offline")}
        }
        ui.setContent {MoviePosterImage(movie,source,movie.name,androidx.compose.ui.Modifier.size(174.dp,256.dp))}
        ui.waitUntil(5000) {ui.runOnIdle {successes.get()>0}}
        ui.runOnIdle {
            assertEquals(good,MoviePosterCache.read(context,movie))
            assertEquals(0,calls.get())
        }
    }
    @Test fun moviePosterSurvivesScrollingAwayAndBackWithoutRefetchingMetadata() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("movie_posters",Context.MODE_PRIVATE).edit().clear().commit()
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(context).build())
        val file=File(context.cacheDir,"poster-scroll-test.png")
        val bitmap=Bitmap.createBitmap(24,36,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
        val address=file.toURI().toString()
        val calls=ConcurrentHashMap<String,Int>()
        val source=object:Source by XtreamSource("https://example.invalid","u","p") {
            override suspend fun movieArtwork(movieId:String):String? {
                calls.compute(movieId) {_,old -> (old ?: 0)+1}
                return address
            }
        }
        val movies=(0..49).map {Movie("$it","Movie $it",null,"poster-test","https://example/movie/$it")}
        val data=AppData(emptyList(),emptyList(),emptyList(),movies,emptyList(),emptyList())
        ui.setContent {MoviesPane(source,context.getSharedPreferences("easyiptv",Context.MODE_PRIVATE),data,"poster-test",{})}
        ui.waitUntil(5000) {calls.containsKey("0")}
        ui.waitForIdle()
        ui.waitUntil(5000) {ui.runOnIdle {MoviePosterCache.read(context,movies[0])==address}}
        ui.onNode(hasScrollToIndexAction()).performScrollToIndex(30)
        ui.waitUntil(5000) {ui.runOnIdle {MoviePosterCache.read(context,movies[30])==address}}
        ui.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        ui.onNodeWithContentDescription("Movie 0",useUnmergedTree=true).assertIsDisplayed()
        ui.runOnIdle {assertEquals(1,calls["0"])}
    }
}
