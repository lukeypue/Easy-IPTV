package com.easyiptv.player

import android.content.Context
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 36], qualifiers = "w960dp-h540dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StreamHealthUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val prefs get() = ui.activity.getSharedPreferences("health-ui", Context.MODE_PRIVATE)
    private val channel = Playable("Health channel", "http://127.0.0.1:9/health.ts", true)
    private var exits = 0

    private fun player() {
        prefs.edit().clear().putBoolean("live_smooth", true).commit()
        ui.runOnUiThread { ui.activity.setContent { MaterialTheme {
            PlayerScreen(listOf(channel), 0, source = null, prefs = prefs,
                onOpenSettings = { _, _ -> }, onBack = { _, _ -> exits++ })
        } } }
        ui.runOnIdle { AppInputGate.startupLocked = false }
    }

    @Test fun infoOpensDiagnosticsInSmoothWithoutPausingOrReplacingTheStream() = checkInfo("tv")
    @Test @Config(qualifiers = "w360dp-h800dp-port")
    fun phoneDiagnosticsCanScrollAndCloseWithoutStoppingPlayback() = checkInfo("phone")

    private fun checkInfo(name: String) {
        try {
            player()
            val running = Playback.player!!
            val slots = ProviderStreams.playbackSlots()
            val playing = running.playWhenReady
            ui.runOnIdle { assertTrue("INFO must open diagnostics", PlayerKeys.priority?.invoke(KeyEvent.KEYCODE_INFO) == true) }
            ui.onNodeWithText("Stream health").assertIsDisplayed()
            ui.onNodeWithText("Incoming stream").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Stream demand").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Quality options are not known yet.").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("LIMIT TO 720p").assertIsNotEnabled()
            ui.onNodeWithText("CLOSE").assertIsFocused()
            ui.onNode(isDialog()).performKeyInput { pressKey(Key.DirectionUp); repeat(12) { pressKey(Key.DirectionUp) } }
            ui.onNodeWithContentDescription("Stream health details").assertIsFocused()
            ui.onNodeWithText("Incoming stream").assertIsDisplayed()
            ui.onNode(isDialog()).performKeyInput { pressKey(Key.DirectionLeft) }
            ui.onNodeWithText("CLOSE").assertIsFocused()
            ui.runOnIdle { TestScreenshots.save("ryzod487-health-$name.png") }
            ui.runOnIdle {
                assertEquals(0, exits)
                assertSame(running, Playback.player)
                assertEquals(slots, ProviderStreams.playbackSlots())
                assertEquals(playing, running.playWhenReady)
                ui.activity.onBackPressedDispatcher.onBackPressed()
            }
            ui.onNodeWithText("Stream health").assertDoesNotExist()
            ui.runOnIdle { assertEquals(0, exits); assertSame(running, Playback.player); assertEquals(playing, running.playWhenReady) }
        } finally { ui.runOnIdle { Playback.releaseAll() } }
    }

    @Test fun dualTrialSwitchDefaultsOffAndPersistsOnlyExplicitChanges() {
        prefs.edit().clear().commit()
        try {
            ui.runOnUiThread { ui.activity.setContent { MaterialTheme {
                SettingsPane(prefs, onModeChanged = { Playback.releaseAll() })
            } } }
            val toggle = ui.onNodeWithTag("dual-stream-switch").performScrollTo()
            toggle.assertIsOff().performClick().assertIsOn()
            ui.runOnIdle { assertTrue(prefs.getBoolean("dual_stream_trial", false)); assertNull(Playback.player) }
            toggle.performClick().assertIsOff()
            ui.runOnIdle { assertFalse(prefs.getBoolean("dual_stream_trial", true)); assertNull(Playback.player) }
        } finally { ui.runOnIdle { Playback.releaseAll() } }
    }

    @Test fun experimentalDualReservesTwoProviderSlotsAndReleaseFreesThem() {
        prefs.edit().clear().putBoolean("dual_stream_trial", true).putInt("provider_streams", 2).commit()
        try {
            ui.runOnIdle {
                Playback.resumeFromBackground()
                Playback.open(ui.activity, prefs, listOf(channel.copy(url = "http://127.0.0.1:9/live.m3u8")), 0, null, false)
                assertEquals("The redundant trial must reserve both plan streams", 2, ProviderStreams.playbackSlots())
                assertFalse(ProviderStreams.canUse(ui.activity, prefs, 1))
                ProviderStreams.setMax(prefs, 1)
                Playback.onProviderBudgetChanged()
                assertEquals("Lowering the account limit must return the trial to one stream", 1, ProviderStreams.playbackSlots())
                Playback.releaseAll()
                assertEquals(0, ProviderStreams.playbackSlots())
            }
        } finally { ui.runOnIdle { Playback.releaseAll() } }
    }

    @Test fun guideOffersHealthForTheAlreadyPlayingChannel() {
        prefs.edit().clear().putBoolean("live_smooth", true).commit()
        val channels = listOf(LiveChannel("health", channel.name, null, null, channel.url))
        val data = AppData(emptyList(), channels, emptyList(), emptyList(), emptyList(), emptyList())
        try {
            ui.runOnIdle { Playback.open(ui.activity, prefs, listOf(channel), 0, null, false) }
            val running = Playback.player!!
            ui.runOnUiThread { ui.activity.setContent { MaterialTheme {
                HomeScreen(prefs=prefs,playlistName="Health TV",source=null,data=data,loadError=null,catalogLoading=false,catalogError=null,
                    onRetryCatalog={},onRefreshMovies={},onRefreshSeries={},activeIdx=0,section="live",depth=1,mini=MiniState(listOf(channel),0,0),onResumeMini={},onCloseMini={},
                    onRoot={},onBackToRoot={},liveCat="all",onLiveCat={},movieCat="all",onMovieCat={},seriesCat="all",onSeriesCat={},
                    searchQuery="",onSearchQuery={},playlists=emptyList(),onSelectPlaylist={},onDeletePlaylist={},onAddPlaylist={},onRetry={},onPlay={},onPlayLive={_,_->},onSeries={})
            } } }
            ui.onNodeWithText("STREAM HEALTH").performClick()
            ui.onNodeWithText("Stream health").assertIsDisplayed()
            ui.onNode(hasText("Health channel") and hasAnyAncestor(isDialog())).assertIsDisplayed()
            ui.onNodeWithText("CLOSE").performClick()
            ui.onNodeWithText("Stream health").assertDoesNotExist()
            ui.runOnIdle { assertSame(running, Playback.player) }
        } finally { ui.runOnIdle { Playback.releaseAll() } }
    }
}
