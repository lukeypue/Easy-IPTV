package com.easyiptv.player

import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,36],qualifiers="w960dp-h540dp-land")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class LiveTvUiRegressionTest {
    @get:Rule val ui=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun smoothOkReturnsToFullGuideWithoutStartingMiniGuideOrEpgRequest() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("smooth-live-ui",Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("live_smooth",true).commit()
        var guideIndex=-1
        var epgCalls=0
        val source=object:Source by XtreamSource("https://example.invalid","u","p") {
            override suspend fun epg(streamId:String,limit:Int):List<EpgEntry> {epgCalls++;return emptyList()}
        }
        try {
            ui.setContent {PlayerScreen(listOf(Playable("Live","http://127.0.0.1:9/live.ts",true,epgId="1")),0,source=source,prefs=prefs,onOpenSettings={_,_->},onBack={i,_->guideIndex=i})}
            ui.runOnIdle {assertTrue(PlayerKeys.priority?.invoke(KeyEvent.KEYCODE_DPAD_CENTER)==true)}
            ui.onNodeWithTag("live-buffer-health").assertDoesNotExist()
            ui.onNodeWithText("Connecting…").assertDoesNotExist()
            ui.runOnIdle {assertEquals(0,guideIndex);assertEquals(0,epgCalls);assertFalse(Timeshift.active)}
        } finally {ui.runOnIdle {Playback.releaseAll()}}
    }
    @Test fun miniGuideOpensWithoutPausingOrStartingTemporaryStorage() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("live-ui",Context.MODE_PRIVATE)
        try {
            ui.setContent {PlayerScreen(listOf(Playable("Live","http://127.0.0.1:9/live.ts",true)),0,source=null,prefs=prefs,onOpenSettings={_,_->},onBack={_,_->})}
            ui.runOnIdle {
                assertTrue(PlayerKeys.priority?.invoke(KeyEvent.KEYCODE_DPAD_CENTER)==true)
            }
            ui.onNodeWithText("❚❚ PAUSE").assertExists()
            ui.onNode(hasText("LIVE") and hasClickAction(),useUnmergedTree=true).assertDoesNotExist()
            ui.onNodeWithTag("live-buffer-health").assertExists()
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
    @Test fun androidXBackClosesGuideWithoutLeavingOrPausingLiveTv() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("live-ui",Context.MODE_PRIVATE)
        var leftPlayer=false
        try {
            ui.setContent {PlayerScreen(listOf(Playable("Live","http://127.0.0.1:9/live.ts",true)),0,source=null,prefs=prefs,onOpenSettings={_,_->},onBack={_,_->leftPlayer=true})}
            ui.runOnIdle {PlayerKeys.priority?.invoke(KeyEvent.KEYCODE_DPAD_CENTER)}
            ui.onNodeWithText("❚❚ PAUSE").assertExists()
            ui.runOnIdle {ui.activity.onBackPressedDispatcher.onBackPressed()}
            ui.onNodeWithText("❚❚ PAUSE").assertDoesNotExist()
            ui.runOnIdle {
                assertFalse(leftPlayer)
                assertTrue(Playback.player!!.playWhenReady)
            }
        } finally {ui.runOnIdle {Playback.releaseAll()}}
    }

    @Test fun loadingGateDefersAudioUntilVisiblePlayerIsReady() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("live-ui",Context.MODE_PRIVATE)
        val ready=androidx.compose.runtime.mutableStateOf(false)
        Playback.open(ctx,prefs,listOf(Playable("Previous","http://127.0.0.1:9/previous.ts",true)),0,null,false)
        Playback.resumeFromBackground()
        Playback.suspendForBackground()
        try {
            ui.setContent {
                SyncPlaybackVisibility(ready.value)
                PlayerScreen(listOf(Playable("First","http://127.0.0.1:9/first.ts",true),Playable("Selected","http://127.0.0.1:9/selected.ts",true)),1,source=null,prefs=prefs,onOpenSettings={_,_->},onBack={_,_->})
            }
            ui.runOnIdle {assertFalse("Loading must stay silent",Playback.player!!.playWhenReady)}
            ui.runOnIdle {ready.value=true}
            ui.runOnIdle {assertTrue("A ready visible video can resume",Playback.player!!.playWhenReady)
                assertEquals("Visibility must retain the selected channel",1,Playback.currentIdxC.intValue)
                assertEquals("http://127.0.0.1:9/selected.ts",Playback.currentProviderUrl())}
            ui.runOnIdle {ready.value=false}
            ui.runOnIdle {assertFalse("Returning to loading must silence video",Playback.player!!.playWhenReady)}
        } finally {ui.runOnIdle {Playback.releaseAll()}}
    }

    @Test fun visibilityDoesNotPlayBehindSavedProgressPrompt() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("prompt-gate",Context.MODE_PRIVATE)
        val movie=Playable("Movie","http://127.0.0.1:9/movie.mp4",false)
        WatchStore.setProgress(prefs,movie.url,60_000,600_000)
        Playback.open(ctx,prefs,listOf(Playable("Previous","http://127.0.0.1:9/previous.ts",true)),0,null,false)
        Playback.resumeFromBackground()
        Playback.suspendForBackground()
        try {
            ui.setContent {
                SyncPlaybackVisibility(true)
                PlayerScreen(listOf(movie),0,source=null,prefs=prefs,onOpenSettings={_,_->},onBack={_,_->})
            }
            ui.onNodeWithText("Resume Movie?").assertIsDisplayed()
            ui.runOnIdle {assertFalse("Resume prompt must retain its intentional pause",Playback.player!!.playWhenReady)}
            ui.onNodeWithText("Start over").performClick()
            ui.runOnIdle {assertTrue(Playback.player!!.playWhenReady)}
        } finally {ui.runOnIdle {Playback.releaseAll()}}
    }

}
