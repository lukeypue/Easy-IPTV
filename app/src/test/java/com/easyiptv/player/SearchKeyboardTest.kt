package com.easyiptv.player

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
class SearchKeyboardTest {
    @get:Rule val ui = createComposeRule()
    @Test fun keyboardStaysWithinSixtyPercentOfTheTelevisionWidth() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("keyboard-width",Context.MODE_PRIVATE)
        val data=AppData(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
        ui.setContent {var q by remember {mutableStateOf("")};SearchTab(null,prefs,data,q,{q=it},{},{_,_->},{})}
        ui.onNodeWithText("Search").performClick()
        val keyboard=ui.onNodeWithTag("ryzod-keyboard").fetchSemanticsNode().boundsInRoot
        val screen=ui.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Keyboard should occupy about 60% of television width",keyboard.width<=screen.width*.61f && keyboard.width>=screen.width*.50f)
        ui.onNodeWithText("a",useUnmergedTree=true).assertIsDisplayed()
        ui.onNodeWithText("DONE").assertIsDisplayed()
        ui.runOnIdle {TestScreenshots.save("ryzod482-keyboard.png")}
    }
    @Test @Config(qualifiers="w360dp-h800dp-port")
    fun phoneKeyboardKeepsLettersAndSymbolPagesWithinTheScreen() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("keyboard-phone",Context.MODE_PRIVATE)
        val data=AppData(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
        ui.setContent {var q by remember {mutableStateOf("")};SearchTab(null,prefs,data,q,{q=it},{},{_,_->},{})}
        ui.onNodeWithText("Search").performClick()
        val screen=ui.onRoot().fetchSemanticsNode().boundsInRoot
        val keyboard=ui.onNodeWithTag("ryzod-keyboard").fetchSemanticsNode().boundsInRoot
        assertTrue(keyboard.width>=screen.width*.8f && keyboard.right<=screen.right && keyboard.left>=screen.left)
        ui.onNodeWithText("#$%",useUnmergedTree=true).performClick()
        ui.onNodeWithText("/",useUnmergedTree=true).assertIsDisplayed()
        ui.onNodeWithText("DONE").assertIsDisplayed()
        ui.runOnIdle {TestScreenshots.save("ryzod482-keyboard-phone.png")}
    }
    @Test fun returningUpFromResultsDoesNotActivateTheHighlightedLetterOrTuneChannel() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("keyboard-return",Context.MODE_PRIVATE)
        val data=AppData(emptyList(),listOf(LiveChannel("1","Alpha TV",null,null,"https://example.invalid/live.ts")),emptyList(),emptyList(),emptyList(),emptyList())
        val q=mutableStateOf("a")
        var played=0
        ui.setContent {val mode=LocalInputModeManager.current;SideEffect {mode.requestInputMode(InputMode.Keyboard)};SearchTab(null,prefs,data,q.value,{q.value=it},{},{_,_->played++},{})}
        ui.onNodeWithText("Search").performClick()
        ui.waitUntil(10_000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();ui.mainClock.advanceTimeByFrame();ui.onAllNodesWithText("Alpha TV").fetchSemanticsNodes().isNotEmpty()}
        ui.onRoot().performKeyInput {repeat(5) {pressKey(Key.DirectionDown)};keyDown(Key.DirectionUp)}
        // A key-up after focus changes must be navigation only.
        ui.onRoot().performKeyInput {keyUp(Key.DirectionUp)}
        ui.runOnIdle {assertEquals("a",q.value);assertEquals(0,played)}
    }
    @Test fun movingWhileConfirmIsHeldDoesNotTypeTheNewlyHighlightedLetter() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("keyboard-confirm-owner",Context.MODE_PRIVATE)
        val data=AppData(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
        val q=mutableStateOf("")
        ui.setContent {val mode=LocalInputModeManager.current;SideEffect {mode.requestInputMode(InputMode.Keyboard)};SearchTab(null,prefs,data,q.value,{q.value=it},{},{_,_->},{})}
        ui.onNodeWithText("Search").performClick()
        ui.onRoot().performKeyInput {keyDown(Key.DirectionCenter);pressKey(Key.DirectionDown);keyUp(Key.DirectionCenter)}
        ui.runOnIdle {assertEquals("Navigating cancels a pending keyboard click","",q.value)}
    }
    @Test fun remoteDoneClosesKeyboardAndMovesToResults() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("easyiptv",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val data=AppData(emptyList(),listOf(LiveChannel("1","Alpha TV",null,null,"https://example.invalid/live.ts")),emptyList(),emptyList(),emptyList(),emptyList())
        ui.setContent {
            val input=LocalInputModeManager.current
            SideEffect {input.requestInputMode(InputMode.Keyboard)}
            var q by remember {mutableStateOf("a")}
            SearchTab(null,prefs,data,q,{q=it},{},{_,_->},{})
        }
        ui.onNodeWithText("Search").performClick()
        ui.waitUntil(10_000) {ui.onAllNodesWithText("Alpha TV").fetchSemanticsNodes().isNotEmpty()}
        ui.onRoot().performKeyInput {
            repeat(4) {pressKey(Key.DirectionDown)}
            repeat(2) {pressKey(Key.DirectionRight)}
            pressKey(Key.DirectionCenter)
        }
        ui.onNodeWithText("DONE").assertDoesNotExist()
        ui.onNodeWithText("Alpha TV").assertIsFocused()
    }
    @Test fun downPastBottomRowFocusesResultsAndUpReturnsToOpenKeyboard() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("search-down",Context.MODE_PRIVATE)
        val data=AppData(emptyList(),listOf(LiveChannel("1","Alpha TV",null,null,"https://example.invalid/live.ts")),emptyList(),emptyList(),emptyList(),emptyList())
        ui.setContent {
            val input=LocalInputModeManager.current
            SideEffect {input.requestInputMode(InputMode.Keyboard)}
            var q by remember {mutableStateOf("a")}
            SearchTab(null,prefs,data,q,{q=it},{},{_,_->},{})
        }
        ui.onNodeWithText("Search").performClick()
        ui.waitUntil(10_000) {ui.onAllNodesWithText("Alpha TV").fetchSemanticsNodes().isNotEmpty()}
        ui.onRoot().performKeyInput {repeat(5) {pressKey(Key.DirectionDown)}}
        ui.onNodeWithText("DONE").assertExists()
        ui.onNodeWithText("Alpha TV").assertIsFocused()
        ui.onRoot().performKeyInput {pressKey(Key.DirectionUp)}
        ui.onNodeWithText("Alpha TV").assertIsNotFocused()
        ui.onRoot().performKeyInput {pressKey(Key.DirectionDown)}
        ui.onNodeWithText("Alpha TV").assertIsFocused()
    }
    @Test fun firstLetterFindsResultsWhileKeyboardRemainsOpen() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("easyiptv", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val data = AppData(emptyList(), listOf(LiveChannel("1", "Alpha TV", null, null, "https://example.invalid/live.ts")), emptyList(), emptyList(), emptyList(), emptyList())
        ui.setContent {
            var q by remember { mutableStateOf("") }
            SearchTab(null, prefs, data, q, { q=it }, {}, { _,_ -> }, {})
        }
        ui.onNodeWithText("SEARCH movies, shows, actors, directors & live TV").performClick()
        ui.onNodeWithText("a", useUnmergedTree=true).performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Alpha TV").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Alpha TV").assertIsDisplayed()
        ui.onNodeWithText("b", useUnmergedTree=true).assertIsDisplayed()
        ui.runOnIdle { TestScreenshots.save("ryzod-471-search.png") }
    }
    @Test fun alphabetAndAccentPagesTypeTheReferenceCharacters() {
        val alphabet=KeyboardLayout.rows(0,false)
        assertEquals("abcdefghij", alphabet.first { it.contains("a") }.joinToString(""))
        val accent=KeyboardLayout.rows(2,false).flatten()
        for (c in listOf("à","á","â","ä","æ","ç","é","ñ","ø","ß","ü","ý")) assertTrue("Missing $c", c in accent)
        val upper=KeyboardLayout.rows(2,true).flatten()
        assertTrue("Case must apply to accents", "É" in upper)
    }
}
