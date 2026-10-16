package com.easyiptv.player

import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class LiveTvUiRegressionTest {
    @get:Rule val ui=createComposeRule()
    @Test fun miniGuideOpensWithoutPausingOrStartingTemporaryStorage() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("live-ui",Context.MODE_PRIVATE)
        try {
            ui.setContent {PlayerScreen(listOf(Playable("Live","http://127.0.0.1:9/live.ts",true)),0,source=null,prefs=prefs,onOpenSettings={_,_->},onBack={_,_->})}
            ui.runOnIdle {
                assertTrue(PlayerKeys.priority?.invoke(KeyEvent.KEYCODE_DPAD_CENTER)==true)
            }
            ui.onNodeWithText("❚❚ PAUSE").assertExists()
            ui.runOnIdle {
                assertTrue(Playback.player!!.playWhenReady)
                assertFalse(Timeshift.active)
            }
        } finally {ui.runOnIdle {Playback.releaseAll()}}
    }
    @Test fun miniGuideStopRecordingRequiresConfirmation() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("live-ui",Context.MODE_PRIVATE)
        try {
            Recorder.activeName.value="Live"
            ui.setContent {PlayerScreen(listOf(Playable("Live","http://127.0.0.1:9/live.ts",true,canRecord=true)),0,source=null,prefs=prefs,onOpenSettings={_,_->},onBack={_,_->})}
            ui.runOnIdle {PlayerKeys.priority?.invoke(KeyEvent.KEYCODE_DPAD_CENTER)}
            ui.onNodeWithText("■ STOP REC").performClick()
            ui.onNodeWithText("Stop the recording now?").assertIsDisplayed()
            ui.runOnIdle {assertEquals("Live",Recorder.activeName.value)}
            ui.onNodeWithText("CLOSE",useUnmergedTree=true).performClick()
            ui.runOnIdle {assertEquals("Live",Recorder.activeName.value)}
        } finally {ui.runOnIdle {Recorder.activeName.value=null;Playback.releaseAll()}}
    }
}
