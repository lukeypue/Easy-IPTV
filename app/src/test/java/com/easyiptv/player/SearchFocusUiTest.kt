package com.easyiptv.player

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land")
@OptIn(ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
class SearchFocusUiTest {
    @get:Rule val ui=createComposeRule()

    @Test fun typingAfterReturningFromSubmittedResultsKeepsKeyboardFocusAcrossSettledQueries() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("search-focus-owner",Context.MODE_PRIVATE)
        val data=AppData(emptyList(),listOf(LiveChannel("1","Alpha TV",null,null,"http://127.0.0.1:9/live.ts")),emptyList(),emptyList(),emptyList(),emptyList())
        val query=mutableStateOf("a")
        val session=SearchSession()
        ui.setContent {
            val input=LocalInputModeManager.current
            SideEffect {input.requestInputMode(InputMode.Keyboard)}
            SearchTab(null,prefs,data,query.value,{query.value=it},{},{_,_->},{},session=session)
        }
        settle(session,data,"a")
        ui.onNodeWithText("Search").performClick()
        ui.onNodeWithText("DONE").performClick()
        ui.waitUntil(10_000) {pump();ui.onAllNodes(isFocused() and hasText("Alpha TV")).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Search").performClick()
        ui.onNodeWithTag("ryzod-keyboard").assertIsFocused()
        for ((letter,expected) in listOf("l" to "al","p" to "alp")) {
            ui.onNodeWithText(letter,useUnmergedTree=true).performClick()
            settle(session,data,expected)
            ui.onNodeWithTag("ryzod-keyboard").assertIsFocused()
            ui.onNodeWithText("Alpha TV").assertIsNotFocused()
        }
        ui.onRoot().performKeyInput {repeat(5) {pressKey(Key.DirectionDown)}}
        ui.onNodeWithText("Alpha TV").assertIsFocused()
        ui.onRoot().performKeyInput {pressKey(Key.DirectionUp)}
        ui.onNodeWithTag("ryzod-keyboard").assertIsFocused()
        ui.onNodeWithText("h",useUnmergedTree=true).performClick()
        settle(session,data,"alph")
        ui.onNodeWithTag("ryzod-keyboard").assertIsFocused()
        ui.onNodeWithText("DONE").performClick()
        ui.waitUntil(10_000) {pump();ui.onAllNodes(isFocused() and hasText("Alpha TV")).fetchSemanticsNodes().isNotEmpty()}
    }

    private fun settle(session:SearchSession,data:AppData,q:String) {
        ui.waitUntil(10_000) {pump();ui.runOnIdle {session.matchesFor(data,q,session.indexFor(data))!=null}}
        repeat(4) {pump()}
        ui.onNodeWithText("Alpha TV").assertExists()
    }
    private fun pump() {shadowOf(Looper.getMainLooper()).idle();ui.mainClock.advanceTimeByFrame()}
}
