package com.easyiptv.player

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 36], qualifiers = "w960dp-h540dp-land")
class VodRemoteNavigationTest {
    @Test fun downOpensTheEpisodeTimelineAndArrowsSeekWithinTheEpisode() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val player = SeekableEpisode()
            val view = PlayerView(activity.get()).apply {
                wireStockPlayerDpad(this)
                this.player = player
            }
            activity.get().setContentView(view)
            shadowOf(Looper.getMainLooper()).idle()
            view.hideController()
            assertTrue(handleVodNavigationKey(view, KeyEvent.KEYCODE_DPAD_DOWN))
            val bar = view.findViewById<View>(androidx.media3.ui.R.id.exo_progress)
            assertTrue("Down must reach the timeline from hidden controls", bar.hasFocus())
            assertTrue(bar.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
            bar.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
            shadowOf(Looper.getMainLooper()).idleFor(1500, TimeUnit.MILLISECONDS)
            assertTrue("Timeline arrows must move the episode", player.currentPosition > 60_000L)
            assertTrue("The timeline stays focused after committing the seek", bar.hasFocus())
            assertEquals("Timeline focus keeps controls visible", 0, view.controllerShowTimeoutMs)
        } finally { activity.pause().stop().destroy() }
    }

    @Test fun dedicatedForwardAndRewindButtonsSeekTheCurrentEpisode() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val player = SeekableEpisode()
            val view = PlayerView(activity.get()).apply {
                wireStockPlayerDpad(this)
                this.player = player
            }
            activity.get().setContentView(view)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(handleVodNavigationKey(view, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
            assertTrue(player.currentPosition > 60_000L)
            val forwardPosition = player.currentPosition
            assertTrue(handleVodNavigationKey(view, KeyEvent.KEYCODE_MEDIA_REWIND))
            assertTrue(player.currentPosition < forwardPosition)
        } finally { activity.pause().stop().destroy() }
    }

    private class SeekableEpisode : SimpleBasePlayer(Looper.getMainLooper()) {
        private var snapshot = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAll(
                Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_METADATA,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_FORWARD, Player.COMMAND_SEEK_BACK
            ).build())
            .setPlaylist(listOf(MediaItemData.Builder("episode")
                .setMediaItem(MediaItem.fromUri("https://example.invalid/episode.mp4"))
                .setIsSeekable(true).setDurationUs(1_800_000_000L).build()))
            .setPlaybackState(Player.STATE_READY).setContentPositionMs(60_000L).build()
        override fun getState() = snapshot
        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            snapshot = snapshot.buildUpon().setContentPositionMs(positionMs).build()
            return Futures.immediateVoidFuture()
        }
    }
}
