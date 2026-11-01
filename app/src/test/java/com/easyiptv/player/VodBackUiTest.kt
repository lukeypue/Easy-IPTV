package com.easyiptv.player

import androidx.activity.compose.setContent
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.ui.PlayerView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 36], qualifiers = "w960dp-h540dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VodBackUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private var exits = 0
    private var exitIndex = -1
    private var exitPosition = -1L
    private val movie = Playable("Test movie", "http://127.0.0.1:9/movie.mp4", false)
    private val prefs get() = ui.activity.getSharedPreferences("vod-back", android.content.Context.MODE_PRIVATE)

    private fun open(queue: List<Playable> = listOf(movie), start: Int = 0) {
        prefs.edit().clear().commit()
        val visible = mutableStateOf(true)
        ui.runOnUiThread { ui.activity.setContent {
            MaterialTheme {
                if (visible.value) PlayerScreen(queue, start, source = null, prefs = prefs,
                    onOpenSettings = { _, _ -> }, onBack = { index, position ->
                        exits++; exitIndex = index; exitPosition = position; visible.value = false
                    })
            }
        } }
        ui.runOnIdle {
            AppInputGate.startupLocked = false
            playerView().controllerShowTimeoutMs = 0
            playerView().showController()
        }
    }

    private fun playerView(): PlayerView {
        fun find(view: View): PlayerView? {
            if (view is PlayerView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        return requireNotNull(find(ui.activity.window.decorView))
    }
    private fun systemBack() = ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
    private fun remoteBack(repeats: Int = 0) = ui.runOnIdle {
        val now = android.os.SystemClock.uptimeMillis()
        ui.activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0))
        repeat(repeats) { ui.activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, it + 1)) }
        ui.activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0))
    }

    @Test fun systemBackHidesMovieControlsWithoutLeavingOrPausing() {
        try {
            open()
            val player = Playback.player
            val wasPlaying = player!!.playWhenReady
            systemBack()
            ui.runOnIdle {
                assertEquals("Hiding controls must not leave the player", 0, exits)
                assertFalse(playerView().isControllerFullyVisible)
                assertSame(player, Playback.player)
                assertEquals(wasPlaying, player.playWhenReady)
            }
            ui.onNodeWithText("Stop watching?").assertDoesNotExist()
            ui.onNodeWithContentDescription("Back").assertDoesNotExist()
        } finally { ui.runOnIdle { Playback.releaseAll() } }
    }

    @Test fun hiddenControlsBackOffersCancellationWithoutChangingAPausedMovie() {
        try {
            open()
            val player = Playback.player!!
            ui.runOnIdle { player.seekTo(45_000L); Playback.setPlaying(false); playerView().hideController() }
            systemBack()
            ui.onNodeWithText("Stop watching?").assertIsDisplayed()
            ui.onNodeWithText("KEEP WATCHING").assertIsFocused()
            ui.runOnIdle { TestScreenshots.save("ryzod486-stop-watching.png") }
            ui.onNodeWithText("KEEP WATCHING").performClick()
            ui.runOnIdle {
                assertEquals(0, exits)
                assertSame(player, Playback.player)
                assertFalse(player.playWhenReady)
                assertEquals(45_000L, player.currentPosition)
            }
            systemBack()
            ui.onNodeWithText("Stop watching?").assertIsDisplayed()
            systemBack()
            ui.onNodeWithText("Stop watching?").assertDoesNotExist()
            ui.runOnIdle { assertEquals(0, exits); assertSame(player, Playback.player); assertFalse(player.playWhenReady) }
        } finally { ui.runOnIdle { Playback.releaseAll() } }
    }

    @Test fun stopWatchingRequiresConfirmationSavesProgressAndReleasesPlayback() {
        try {
            open()
            ui.runOnIdle { Playback.player!!.seekTo(45_000L); playerView().hideController() }
            systemBack()
            ui.runOnIdle { assertEquals(0, exits); assertNotNull(Playback.player) }
            ui.onNodeWithText("STOP WATCHING").performClick()
            ui.runOnIdle {
                assertEquals(1, exits)
                assertEquals(0, exitIndex)
                assertEquals(45_000L, exitPosition)
                assertNull("Confirmed stop must release the decoder", Playback.player)
                assertTrue(Playback.queue.isEmpty())
                assertEquals(0, ProviderStreams.playbackSlots())
                assertEquals(45_000L, WatchStore.get(prefs, movie.url)?.pos)
            }
        } finally { ui.runOnIdle { Playback.releaseAll() } }
    }

    @Test fun heldFireTvBackHidesEpisodeControlsOnceThenAnotherPressAsks() {
        try {
            open(listOf(movie.copy(name = "Episode 1"), movie.copy(name = "Episode 2", url = "http://127.0.0.1:9/episode2.mp4")), 1)
            remoteBack(repeats = 3)
            ui.runOnIdle { assertEquals(0, exits); assertFalse(playerView().isControllerFullyVisible) }
            ui.onNodeWithText("Stop watching?").assertDoesNotExist()
            remoteBack()
            ui.onNodeWithText("Stop watching?").assertIsDisplayed()
            ui.onNodeWithText("STOP WATCHING").performClick()
            ui.runOnIdle { assertEquals(1, exits); assertEquals(1, exitIndex); assertNull(Playback.player) }
        } finally { ui.runOnIdle { Playback.releaseAll() } }
    }

    @Test fun changingEpisodesKeepsRemoteBackWorkingForTheNewEpisode() {
        val episode2 = movie.copy(name = "Episode 2", url = "http://127.0.0.1:9/episode2.mp4")
        try {
            open(listOf(movie.copy(name = "Episode 1"), episode2))
            ui.runOnIdle { Playback.player!!.seekTo(1, 45_000L) }
            ui.onNodeWithText("Episode 2").assertIsDisplayed()
            remoteBack()
            ui.runOnIdle { assertEquals(0, exits); assertFalse(playerView().isControllerFullyVisible) }
            remoteBack()
            ui.onNodeWithText("Stop watching?").assertIsDisplayed()
            ui.onNodeWithText("STOP WATCHING").performClick()
            ui.runOnIdle {
                assertEquals(1, exits)
                assertEquals(1, exitIndex)
                assertEquals(45_000L, WatchStore.get(prefs, episode2.url)?.pos)
                assertNull(Playback.player)
            }
        } finally { ui.runOnIdle { Playback.releaseAll() } }
    }

    @Test fun touchBackArrowHidesControlsBeforeSystemBackCanAskToStop() {
        try {
            open()
            ui.onNodeWithContentDescription("Back").performClick()
            ui.runOnIdle { assertEquals(0, exits); assertFalse(playerView().isControllerFullyVisible) }
            ui.onNodeWithText("Stop watching?").assertDoesNotExist()
            systemBack()
            ui.onNodeWithText("Stop watching?").assertIsDisplayed()
            ui.onNodeWithText("KEEP WATCHING").performClick()
            ui.runOnIdle { assertEquals(0, exits); assertNotNull(Playback.player) }
        } finally { ui.runOnIdle { Playback.releaseAll() } }
    }
}
