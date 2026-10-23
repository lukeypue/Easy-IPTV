package com.easyiptv.player

import android.content.Context
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.ListenerSet
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class PlaybackRetrySessionTest {
    private val ctx=ApplicationProvider.getApplicationContext<Context>()
    private val prefs=ctx.getSharedPreferences("retry-session",Context.MODE_PRIVATE)
    private fun open(name:String)=Playback.open(ctx,prefs,listOf(Playable(name,"http://127.0.0.1:9/$name.mp4",false)),0,null,false)
    @Test fun browserCompatibilityRetryPreservesPause() {
        for(code in listOf(403,406)) try {
            val p=open("blocked$code")
            p.pause()
            val field=p.javaClass.getDeclaredField("listeners").apply {isAccessible=true}
            @Suppress("UNCHECKED_CAST")
            val listeners=field.get(p) as ListenerSet<Player.Listener>
            val denied=androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException(
                code,"Denied",null,emptyMap(),androidx.media3.datasource.DataSpec(android.net.Uri.parse("http://127.0.0.1:9/blocked.mp4")),byteArrayOf())
            listeners.sendEvent(Player.EVENT_PLAYER_ERROR) {
                it.onPlayerError(PlaybackException("fixture",denied,PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
            }
            assertFalse("Browser-UA recovery must preserve Pause for HTTP $code",p.playWhenReady)
        } finally {Playback.releaseAll()}
    }
    @Test fun newMovieGetsItsOwnRetryBudget() {
        try {
            open("A")
            val retries=Playback.javaClass.getDeclaredField("retriesP").apply {isAccessible=true}
            retries.setInt(Playback,6)
            open("B")
            assertEquals("Selecting a different movie must not inherit exhausted retries",0,retries.getInt(Playback))
        } finally {Playback.releaseAll()}
    }
    @Test fun retryMustRespectPauseWithinTheSameMovie() {
        try {
            val p=open("A")
            val retries=Playback.javaClass.getDeclaredField("retriesP").apply {isAccessible=true}
            retries.setInt(Playback,0)
            val field=p.javaClass.getDeclaredField("listeners").apply {isAccessible=true}
            @Suppress("UNCHECKED_CAST")
            val listeners=field.get(p) as ListenerSet<Player.Listener>
            listeners.sendEvent(Player.EVENT_PLAYER_ERROR) {
                it.onPlayerError(PlaybackException("fixture",null,PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
            }
            assertEquals("Fixture must schedule a retry",1,retries.getInt(Playback))
            p.pause()
            shadowOf(Looper.getMainLooper()).idleFor(1500,TimeUnit.MILLISECONDS)
            assertFalse("Retry must respect an explicit Pause",p.playWhenReady)
        } finally {Playback.releaseAll()}
    }
    @Test fun oldMovieErrorCannotResumeTheNewMovieAfterTheViewerPausesIt() {
        try {
            val p=open("A")
            val retries=Playback.javaClass.getDeclaredField("retriesP").apply {isAccessible=true}
            retries.setInt(Playback,0)
            val field=p.javaClass.getDeclaredField("listeners").apply {isAccessible=true}
            @Suppress("UNCHECKED_CAST")
            val listeners=field.get(p) as ListenerSet<Player.Listener>
            listeners.sendEvent(Player.EVENT_PLAYER_ERROR) {
                it.onPlayerError(PlaybackException("fixture",null,PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
            }
            assertEquals("Fixture must schedule a retry",1,retries.getInt(Playback))
            open("B").pause()
            p.stop()
            shadowOf(Looper.getMainLooper()).idleFor(1500,TimeUnit.MILLISECONDS)
            assertFalse("A stale error retry must not undo Pause on B",p.playWhenReady)
            assertEquals("A stale error must not prepare the stopped replacement",Player.STATE_IDLE,p.playbackState)
        } finally {Playback.releaseAll()}
    }
}
