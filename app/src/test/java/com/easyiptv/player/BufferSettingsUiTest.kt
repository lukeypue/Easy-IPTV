package com.easyiptv.player

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class BufferSettingsUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun smoothIsIndependentAndPreservesManualBufferChoicesWhenToggled() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("smooth-settings",Context.MODE_PRIVATE)
        prefs.edit().clear().putInt("buffer_sec",60).putInt("live_start_ms",30_000).putBoolean("live_steady_recovery",true).commit()
        ui.setContent {SettingsPane(prefs) {}}
        ui.onNodeWithTag("smooth-switch").performScrollTo().performClick()
        ui.runOnIdle {
            assertEquals(true,prefs.getBoolean("live_smooth",false))
            assertEquals(60,prefs.getInt("buffer_sec",-1))
            assertEquals(30_000,prefs.getInt("live_start_ms",-1))
            assertEquals(0,StreamBufferPolicy.from(prefs).startMs)
            assertEquals(0,StreamBufferPolicy.from(prefs).rebufferMs)
        }
        ui.onNodeWithText("20s refill").performScrollTo().performClick()
        ui.runOnIdle {assertEquals(true,prefs.getBoolean("live_smooth",false));assertEquals(20,prefs.getInt("buffer_sec",-1))}
        ui.onNodeWithTag("smooth-switch").performScrollTo().performClick()
        ui.runOnIdle {assertEquals(false,prefs.getBoolean("live_smooth",true));assertEquals(30_000,StreamBufferPolicy.from(prefs).startMs)}
        ui.onNodeWithText("Smooth (0s)").assertDoesNotExist()
    }
    @Test fun twentySecondRefillAndRealTimeOverrideAreVisible() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("buffer-clarity",Context.MODE_PRIVATE)
        prefs.edit().clear().putInt("live_start_ms",20_000).commit()
        ui.setContent {SettingsPane(prefs) {}}
        ui.onNodeWithText("20s refill").performScrollTo().performClick()
        ui.runOnIdle {assertEquals(20,prefs.getInt("buffer_sec",0))}
        ui.onNodeWithText("Effective live targets: start 20s · recovery 40s · refill 60s").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Real time (0s)").performScrollTo().performClick()
        ui.onNodeWithText("Real time: no added startup or recovery wait. Other cushion choices are inactive.").performScrollTo().assertIsDisplayed()
        ui.runOnIdle {assertEquals(20_000,prefs.getInt("live_start_ms",0));assertEquals(0,prefs.getInt("buffer_sec",-1))}
    }
    @Test @Config(qualifiers="w360dp-h800dp-port")
    fun bufferChoicesFitOnAPhoneWithoutClippingToTheRight() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("phone-buffer-settings",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        ui.setContent {SettingsPane(prefs) {}}
        for (label in listOf("Big (60s)","10s cushion","30s cushion","Steady weak-channel")) {
            val node=ui.onNodeWithText(label)
            node.performScrollTo().assertIsDisplayed()
            val rect=node.fetchSemanticsNode().boundsInRoot
            val screen=ui.onRoot().fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue("$label must fit within the phone",rect.left>=screen.left && rect.right<=screen.right)
            node.performClick()
        }
        ui.runOnIdle {assertEquals(60,prefs.getInt("buffer_sec",0));assertEquals(30_000,prefs.getInt("live_start_ms",0))}
    }
    @Test fun longAndZeroCushionsAreAccessibleAndApplied() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("new-buffer-settings",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        var applied=0
        ui.setContent {SettingsPane(prefs) {applied++}}
        ui.onNodeWithText("30s cushion").performScrollTo().performClick()
        ui.runOnIdle {assertEquals(30_000,prefs.getInt("live_start_ms",0));assertEquals(1,applied)}
        ui.onNodeWithText("Real time (0s)").performScrollTo().performClick()
        ui.runOnIdle {assertEquals(0,prefs.getInt("buffer_sec",-1));assertEquals(2,applied)}
    }
    @Test fun everyBufferSettingAppliesWithoutAnAppRestart() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("buffer-settings-ui",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        var applied=0
        ui.setContent {SettingsPane(prefs) {applied++}}
        ui.onNodeWithText("Small (10s)").performScrollTo().performClick()
        ui.runOnIdle {assertEquals(10,prefs.getInt("buffer_sec",0));assertEquals(1,applied)}
        ui.onNodeWithText("10s cushion").performScrollTo().performClick()
        ui.runOnIdle {assertEquals(10_000,prefs.getInt("live_start_ms",0));assertEquals(2,applied)}
        ui.onNodeWithText("Steady weak-channel").performScrollTo().performClick()
        ui.runOnIdle {assertEquals(true,prefs.getBoolean("live_steady_recovery",false));assertEquals(3,applied)}
    }
}
