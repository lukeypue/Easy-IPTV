package com.easyiptv.player

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class HomeGuideNavigationTest {
    @get:Rule val ui=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun render(depth: Int, onBack: () -> Unit = {}) {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("home-back",Context.MODE_PRIVATE)
        val channels=listOf(LiveChannel("one","Test channel",null,null,"https://example.invalid/live.ts"))
        val data=AppData(emptyList(),channels,emptyList(),emptyList(),emptyList(),emptyList())
        ui.setContent {
            HomeScreen(prefs=prefs,playlistName="Test",source=null,data=data,loadError=null,
                catalogLoading=false,catalogError=null,onRetryCatalog={},onRefreshMovies={},onRefreshSeries={},
                activeIdx=0,section="live",depth=depth,mini=null,onResumeMini={},onCloseMini={},
                onRoot={},onBackToRoot=onBack,liveCat="all",onLiveCat={},movieCat="all",onMovieCat={},
                seriesCat="all",onSeriesCat={},searchQuery="",onSearchQuery={},playlists=emptyList(),
                onSelectPlaylist={},onDeletePlaylist={},onAddPlaylist={},onRetry={},onPlay={},onPlayLive={_,_->},onSeries={})
        }
        ui.onNodeWithText("Test channel").assertIsDisplayed()
    }
    @Test fun rootGuideBackShowsExitConfirmation() {
        render(0)
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithText("Exit?").assertIsDisplayed()
    }
    @Test fun categoryGuideBackReturnsToRoot() {
        var returned=0
        render(1) { returned++ }
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.runOnIdle { assertEquals(1,returned) }
    }
}
