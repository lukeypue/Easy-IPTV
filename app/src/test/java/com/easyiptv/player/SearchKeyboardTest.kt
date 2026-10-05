package com.easyiptv.player

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
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
class SearchKeyboardTest {
    @get:Rule val ui = createComposeRule()
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
