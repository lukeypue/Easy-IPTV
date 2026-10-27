package com.easyiptv.player

import android.content.Context
import androidx.compose.runtime.*
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
@Config(sdk=[28],qualifiers="w960dp-h540dp-land")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class SeriesSeasonsUiTest {
    @get:Rule val ui=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun seasonsOpenOnlyTheirEpisodesAndBackReturnsToSeasons() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("season-ui",Context.MODE_PRIVATE)
        val episodes=linkedMapOf(2 to listOf(Episode("b","Second",2,1,"http://127.0.0.1/b.mp4")),
            1 to listOf(Episode("a2","Pilot part two",1,2,"http://127.0.0.1/a2.mp4"),Episode("a","Pilot",1,1,"http://127.0.0.1/a.mp4")))
        val source=object:Source by XtreamSource("https://example.invalid","u","p") {
            override suspend fun seriesEpisodes(seriesId:String)=episodes
        }
        var left=0
        var played:List<Playable> = emptyList()
        var index=-1
        ui.setContent {SeriesDetailScreen(source,SeriesItem("s","Series",null,null),prefs,{q,i->played=q;index=i},{left++})}
        ui.onNodeWithText("Season 1").assertIsDisplayed()
        ui.onNodeWithText("E1  Pilot").assertDoesNotExist()
        ui.onNodeWithText("Season 1").performClick()
        ui.onNodeWithText("E1  Pilot").assertIsDisplayed()
        ui.onNodeWithText("E1  Second").assertDoesNotExist()
        ui.onNodeWithText("E1  Pilot").performClick()
        ui.onNodeWithText("▶ PLAY").performClick()
        ui.runOnIdle {assertEquals(listOf("http://127.0.0.1/a.mp4","http://127.0.0.1/a2.mp4","http://127.0.0.1/b.mp4"),played.map{it.url});assertEquals(0,index)}
        // Play dismisses the dialog; visible Back returns to seasons.
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithText("Season 2").assertIsDisplayed()
        ui.onNodeWithText("E1  Pilot").assertDoesNotExist()
        ui.runOnIdle {assertEquals(0,left)}
        ui.onNodeWithText("Season 2").performClick()
        ui.onNodeWithText("E1  Second").assertIsDisplayed()
        ui.runOnIdle {ui.activity.onBackPressedDispatcher.onBackPressed()}
        ui.onNodeWithText("Season 1").assertIsDisplayed()
        ui.onNodeWithContentDescription("Back").performClick()
        ui.runOnIdle {assertEquals(1,left)}
    }
}
