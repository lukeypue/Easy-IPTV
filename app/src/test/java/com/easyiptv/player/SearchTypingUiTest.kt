package com.easyiptv.player

import android.content.Context
import androidx.compose.runtime.*
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
class SearchTypingUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun typingAfterImmediateSubmissionKeepsTheSubmittedResults() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("search-submit-typing",Context.MODE_PRIVATE)
        val query=mutableStateOf("alpha")
        val data=AppData(emptyList(),listOf(
            LiveChannel("1","Alpha TV",null,null,"http://127.0.0.1:9/alpha.ts"),
            LiveChannel("2","Beta TV",null,null,"http://127.0.0.1:9/beta.ts")),
            emptyList(),emptyList(),emptyList(),emptyList())
        ui.setContent {SearchTab(null,prefs,data,query.value,{query.value=it},{},{_,_->},{})}
        ui.waitUntil(10_000) {ui.onAllNodesWithText("Alpha TV").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Search").performClick()
        ui.mainClock.autoAdvance=false
        try {
            val typedAt=ui.mainClock.currentTime
            ui.runOnIdle {query.value="beta"}
            ui.mainClock.advanceTimeByFrame()
            // Done uses the same immediate-submit callback as bottom-row Down.
            ui.onNodeWithText("DONE").performClick()
            ui.mainClock.advanceTimeByFrame()
            ui.waitUntil(10_000) {
                ui.mainClock.advanceTimeByFrame()
                ui.onAllNodesWithText("Beta TV").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue("The probe must return to typing before the old debounce can finish",
                ui.mainClock.currentTime-typedAt<190)
            ui.onNodeWithText("Search").performClick()
            ui.mainClock.advanceTimeByFrame()
            ui.onNodeWithText("SPACE").performClick()
            ui.mainClock.advanceTimeByFrame()
            ui.onNodeWithText("t",useUnmergedTree=true).performClick()
            ui.mainClock.advanceTimeByFrame()
            assertTrue("The probe must exercise the pending-debounce window",
                ui.mainClock.currentTime-typedAt<250)
            ui.onNodeWithText("Beta TV").assertExists()
            ui.onNodeWithText("Alpha TV").assertDoesNotExist()
        } finally {ui.mainClock.autoAdvance=true}
    }

    @Test fun rapidTypingKeepsExistingResultsMountedUntilTheNewQuerySettles() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("search-typing",Context.MODE_PRIVATE)
        val query=mutableStateOf("a")
        val data=AppData(emptyList(),listOf(LiveChannel("1","Alpha TV",null,null,"http://127.0.0.1:9/live.ts")),
            emptyList(),emptyList(),emptyList(),emptyList())
        ui.setContent {SearchTab(null,prefs,data,query.value,{query.value=it},{},{_,_->},{})}
        ui.waitUntil(10_000) {ui.onAllNodesWithText("Alpha TV").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Search").performClick()
        ui.onNodeWithText("DONE").assertExists()
        ui.mainClock.autoAdvance=false
        try {
            // Actual keyboard clicks must update the text immediately while the
            // expensive result tree remains mounted through a burst of letters.
            for (letter in listOf("l","p","h","a")) {
                ui.onNodeWithText(letter,useUnmergedTree=true).performClick()
                ui.mainClock.advanceTimeByFrame()
                ui.onNodeWithText("Alpha TV").assertExists()
                ui.onNodeWithText("DONE").assertExists()
            }
            ui.runOnIdle {assertEquals("alpha",query.value)}
        } finally {ui.mainClock.autoAdvance=true}
        ui.waitUntil(10_000) {ui.onAllNodesWithText("Alpha TV").fetchSemanticsNodes().isNotEmpty()}
    }
}
