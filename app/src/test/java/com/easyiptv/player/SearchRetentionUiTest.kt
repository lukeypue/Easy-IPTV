package com.easyiptv.player

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class SearchRetentionUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun activityIsVisibleWhileSearchingAndEndsWithTheLatestResults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("search-activity", Context.MODE_PRIVATE)
        val channel = LiveChannel("l", "Star TV", null, null, "http://127.0.0.1:9/l.ts")
        val started = java.util.concurrent.CountDownLatch(1)
        val finish = java.util.concurrent.CountDownLatch(1)
        val live = object : AbstractList<LiveChannel>() {
            override val size = 1
            override fun get(index: Int): LiveChannel {
                started.countDown()
                check(finish.await(10, java.util.concurrent.TimeUnit.SECONDS))
                return channel
            }
        }
        val data = AppData(emptyList(), live, emptyList(), emptyList(), emptyList(), emptyList())
        try {
            ui.setContent { SearchTab(null, prefs, data, "star", {}, {}, { _, _ -> }, {}) }
            ui.waitUntil(5_000) { started.count == 0L }
            ui.onNodeWithTag("search-activity").assertIsDisplayed()
            ui.onNodeWithText("Nothing found for \"star\".").assertDoesNotExist()
        } finally { finish.countDown() }
        ui.waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            ui.mainClock.advanceTimeByFrame()
            ui.onAllNodesWithText("Star TV").fetchSemanticsNodes().isNotEmpty() &&
                ui.onAllNodesWithTag("search-activity").fetchSemanticsNodes().isEmpty()
        }
        ui.onNodeWithText("Star TV").assertIsDisplayed()
    }

    @Test fun returningFromPlaybackShowsTheSameCrossCatalogResultsBeforeAnyBackgroundSearch() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("search-retain",Context.MODE_PRIVATE)
        val data=AppData(emptyList(),listOf(LiveChannel("l","Star Wars TV",null,null,"http://127.0.0.1:9/l.ts")),emptyList(),
            listOf(Movie("m","Star Wars A New Hope",null,null,"http://127.0.0.1:9/m.mp4")),emptyList(),listOf(SeriesItem("s","Star Wars Clone Wars",null,null)))
        val session=SearchSession()
        val visible=mutableStateOf(true)
        ui.setContent {if(visible.value) SearchTab(null,prefs,data,"star wars",{},{},{_,_->},{},session=session)}
        ui.waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            ui.mainClock.advanceTimeByFrame()
            ui.onAllNodesWithText("Star Wars Clone Wars").fetchSemanticsNodes().isNotEmpty() &&
                ui.onAllNodesWithTag("search-activity").fetchSemanticsNodes().isEmpty()
        }
        ui.onNodeWithText("Star Wars TV").assertExists()
        ui.onNodeWithText("Star Wars A New Hope").assertExists()
        ui.runOnIdle {org.junit.Assert.assertNotNull("Visible results must be retained outside the disposable screen",session.matchesFor(data,"star wars",session.indexFor(data)))}
        ui.runOnIdle {visible.value=false}
        ui.mainClock.autoAdvance=false
        try {
            ui.runOnIdle {visible.value=true}
            ui.mainClock.advanceTimeByFrame()
            ui.onNodeWithText("Star Wars TV").assertExists()
            ui.onNodeWithText("Star Wars A New Hope").assertExists()
            ui.onNodeWithText("Star Wars Clone Wars").assertExists()
            ui.onNodeWithTag("search-activity").assertDoesNotExist()
        } finally {ui.mainClock.autoAdvance=true}
    }
}
