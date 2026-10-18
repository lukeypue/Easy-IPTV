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
    @Test @Config(qualifiers="w360dp-h800dp-port")
    fun bufferChoicesFitOnAPhoneWithoutClippingToTheRight() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("phone-buffer-settings",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        ui.setContent {SettingsPane(prefs) {}}
        for (label in listOf("Big (60s)","Max (10s)","30s cushion","Steady weak-channel")) {
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
        ui.onNodeWithText("Off (0s)").performScrollTo().performClick()
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
        ui.onNodeWithText("Max (10s)").performScrollTo().performClick()
        ui.runOnIdle {assertEquals(10_000,prefs.getInt("live_start_ms",0));assertEquals(2,applied)}
        ui.onNodeWithText("Steady weak-channel").performScrollTo().performClick()
        ui.runOnIdle {assertEquals(true,prefs.getBoolean("live_steady_recovery",false));assertEquals(3,applied)}
    }
}
