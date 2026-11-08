package com.easyiptv.player

import android.content.Context
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import java.net.InetAddress
import java.net.ServerSocket
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land")
@OptIn(ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
class AppStartupUiTest {
    @get:Rule val ui = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun savedChannelsAreUsableWhileProviderIsStillWaiting() = withStalledProvider { playlist ->
        DataCache.save(context, DataCache.keyFor(playlist), AppData(
            liveCats = emptyList(),
            live = listOf(LiveChannel("1", "Saved channel", null, null, "${playlist.url}/live.ts")),
            vodCats = emptyList(), movies = emptyList(), seriesCats = emptyList(), series = emptyList()
        ))
        ui.setContent { App() }
        ui.waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            ui.mainClock.advanceTimeByFrame()
            ui.onAllNodesWithText("Saved channel").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText("Saved channel").assertIsDisplayed()
        ui.runOnIdle { assertFalse(AppInputGate.startupLocked) }
    }

    @Test fun noSavedPlaylistDataStillAllowsMenusDuringSlowProviderRequest() = withStalledProvider {
        ui.mainClock.autoAdvance = false
        try {
            ui.setContent {
                val inputMode = LocalInputModeManager.current
                SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
                App()
            }
            ui.mainClock.advanceTimeByFrame()
            ui.onNodeWithText("Preparing your TV experience…").assertExists()
            ui.mainClock.advanceTimeBy(5_200)
            ui.onNodeWithContentDescription("Open menu").assertIsDisplayed().assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionCenter) }
            ui.mainClock.advanceTimeByFrame()
            ui.onNodeWithText("Main menu", substring = true).assertExists()
            ui.runOnIdle { assertFalse(AppInputGate.startupLocked) }
        } finally {
            ui.mainClock.autoAdvance = true
        }
    }

    private fun withStalledProvider(block: (Playlist) -> Unit) {
        // Accept TCP connections but never send an HTTP response during the test.
        ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).use { provider ->
            val playlist = Playlist("Test provider", "m3u", url = "http://127.0.0.1:${provider.localPort}/list")
            val prefs = context.getSharedPreferences("easyiptv", Context.MODE_PRIVATE)
            PlaylistStore.save(prefs, listOf(playlist))
            PlaylistStore.setActive(prefs, 0)
            block(playlist)
        }
    }
}
