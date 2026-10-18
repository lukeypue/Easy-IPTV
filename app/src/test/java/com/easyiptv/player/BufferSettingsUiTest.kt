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
