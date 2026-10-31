package com.easyiptv.player

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
class EnlargedUiTest {
    @get:Rule val ui=createComposeRule()
    @Composable private fun enlarged(scale:Float=1.30f,content:@Composable ()->Unit) {
        val density=LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {MaterialTheme(colorScheme=darkColorScheme()) {content()}}
    }
    @Test @Config(qualifiers="w360dp-h800dp-port")
    fun phoneKeyboardActionWordsRemainUnclippedAtTheLargerAppScale() {
        keyboard()
        for(word in listOf("SPACE","DELETE","CLEAR","PREVIOUS","NEXT","DONE")) assertUnclipped(word)
        ui.runOnIdle {TestScreenshots.save("ryzod484-keyboard-phone.png")}
    }
    @Test fun televisionKeyboardRetainsCompactWidthAndReadableActionLabels() {
        keyboard()
        val key=ui.onNodeWithTag("ryzod-keyboard").fetchSemanticsNode().boundsInRoot
        val screen=ui.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(key.width<=screen.width*.61f && key.width>=screen.width*.5f)
        for(word in listOf("DELETE","PREVIOUS","NEXT","DONE")) assertUnclipped(word)
        ui.runOnIdle {TestScreenshots.save("ryzod484-keyboard-tv.png")}
    }
    @Test @Config(qualifiers="w360dp-h800dp-port")
    fun largerPhoneSettingsRemainReachableWithoutClippedOptionLabels() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("large-settings",Context.MODE_PRIVATE)
        ui.setContent {enlarged {SettingsPane(prefs) {}}}
        for(word in listOf("Big (60s)","30s cushion","Steady weak-channel")) {
            ui.onNodeWithText(word).performScrollTo()
            assertUnclipped(word)
        }
        ui.onNodeWithTag("smooth-switch").performScrollTo().assertIsDisplayed()
        ui.runOnIdle {TestScreenshots.save("ryzod484-settings-phone.png")}
    }
    @Test fun enlargedGuideAndVioletMenusKeepCoreControlsOnScreen() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("large-home",Context.MODE_PRIVATE)
        val channels=(0..9).map {LiveChannel("c$it","Channel $it",null,"news","http://127.0.0.1:9/$it.ts")}
        val data=AppData(listOf(Category("news","News and information")),channels,emptyList(),emptyList(),emptyList(),emptyList())
        ui.setContent {enlarged {
            var section by remember {mutableStateOf("live")};var cat by remember {mutableStateOf("all")}
            HomeScreen(prefs=prefs,playlistName="Readable TV",source=null,data=data,loadError=null,catalogLoading=false,catalogError=null,
                onRetryCatalog={},onRefreshMovies={},onRefreshSeries={},activeIdx=0,section=section,depth=1,mini=null,onResumeMini={},onCloseMini={},
                onRoot={section=it},onBackToRoot={},liveCat=cat,onLiveCat={cat=it},movieCat="all",onMovieCat={},seriesCat="all",onSeriesCat={},
                searchQuery="",onSearchQuery={},playlists=emptyList(),onSelectPlaylist={},onDeletePlaylist={},onAddPlaylist={},onRetry={},onPlay={},onPlayLive={_,_->},onSeries={})
        }}
        ui.onNodeWithText("Channel 4").assertIsDisplayed()
        ui.runOnIdle {TestScreenshots.save("ryzod484-guide-tv.png")}
        ui.onNodeWithContentDescription("Open menu").performClick()
        ui.onNodeWithText("★ Favorites").assertIsDisplayed()
        ui.runOnIdle {TestScreenshots.save("ryzod484-violet-categories.png")}
        ui.onNodeWithContentDescription("Open menu").performClick()
        ui.onNodeWithText("Settings").performScrollTo()
        assertUnclipped("Settings")
        ui.runOnIdle {TestScreenshots.save("ryzod484-violet-mainmenu.png")}
        ui.onNodeWithText("Settings").performClick()
        ui.runOnIdle {TestScreenshots.save("ryzod484-settings-tv.png")}
    }
    @Test @Config(qualifiers="w360dp-h800dp-port")
    fun accessibilitySizeKeyboardCanScrollToItsUnclippedDoneKey() {
        keyboard(2.60f)
        ui.onRoot().performKeyInput {repeat(12) {pressKey(Key.DirectionDown)}}
        repeat(3) {ui.mainClock.advanceTimeByFrame()}
        assertUnclipped("DONE")
        ui.runOnIdle {TestScreenshots.save("ryzod484-keyboard-accessibility.png")}
    }
    private fun keyboard(scale:Float=1.30f) {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("large-keyboard",Context.MODE_PRIVATE)
        val data=AppData(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
        ui.setContent {enlarged(scale) {
            val input=LocalInputModeManager.current
            SideEffect {input.requestInputMode(InputMode.Keyboard)}
            var q by remember {mutableStateOf("")};SearchTab(null,prefs,data,q,{q=it},{},{_,_->},{})
        }}
        ui.onNodeWithText("Search").performClick()
    }
    private fun assertUnclipped(word:String) {
        val node=ui.onNodeWithText(word,useUnmergedTree=true).assertIsDisplayed()
        val layouts=mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertTrue("Text layout must be available for $word",layouts.isNotEmpty())
        assertFalse("$word glyphs must fit: ${layouts.map {"size=${it.size}, lines=${it.lineCount}"}}",layouts.any { layout ->
            layout.multiParagraph.didExceedMaxLines || (0 until layout.lineCount).any { line ->
                layout.isLineEllipsized(line) || layout.getLineRight(line)-layout.getLineLeft(line)>layout.size.width+1f || layout.getLineBottom(line)>layout.size.height+1f
            }
        })
    }
}
